package com.xinchentechnote.exchange.simulator.common;

import com.finproto.codec.BinaryCodec;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiCancelOrder;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE/SZSE 二进制接入服务的公共骨架（模板方法）：
 * 委托缓存与 ClOrdId 反查索引、HTTP 网关、撤单/确认/成交回报的调度流程在此实现，
 * 协议差异（报文编解码、确认/回报/拒绝报文的组装）由子类钩子实现。
 * <p>
 * 子类需提供：{@link #createInitializer()}（Netty 管线）、
 * {@link #sendOrderConfirm}、{@link #sendCancelConfirm}、{@link #sendCancelReject}、
 * {@link #sendTradeReport}、{@link #orderQtyOf(CommandWrapper)}。
 */
@Getter
@Slf4j
public abstract class AbstractBinServer implements IEventsHandler, OrderGateway {

    protected final int port;
    @Setter
    protected ExchangeApi api;

    protected final Map<Long, CommandWrapper> cache = new ConcurrentHashMap<>();
    /** ClOrdId → 内部 orderId，供撤单请求按 origClOrdId 反查原订单 */
    protected final Map<String, Long> clOrdIdIndex = new ConcurrentHashMap<>();
    /** HTTP 通道同步等待委托确认的 futures（orderId → future），commandResult 时完成 */
    protected final Map<Long, CompletableFuture<ApiCommandResult>> pendingResults = new ConcurrentHashMap<>();
    /** 待决撤单：orderId → 撤单请求自身的 ClOrdId（撤单拒绝回执回显用） */
    protected final Map<Long, String> pendingCancelClOrdId = new ConcurrentHashMap<>();

    protected AbstractBinServer(int port) {
        this.port = port;
    }

    /**
     * 创建协议专属的 Netty 管线（拆帧参数、会话/业务 handler 不同）。
     */
    protected abstract ChannelInitializer<Channel> createInitializer();

    // ---------- 生命周期 ----------

    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup group = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(2);
        bootstrap.group(group, workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(createInitializer());
        try {
            // 绑定失败必须终止启动，否则问题会推迟到客户端连不上时才暴露
            bootstrap.bind(port).sync();
            log.info("{} started on port :{}", getClass().getSimpleName(), port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to start " + getClass().getSimpleName() + " on port " + port, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start " + getClass().getSimpleName() + " on port " + port, e);
        }
    }

    // ---------- 上行入口 ----------

    /**
     * 协议报文提交委托后调用：登记缓存与 ClOrdId 索引再提交撮合核心。
     */
    protected void submitOrder(Long orderId, String clOrdId, ApiCommand command, BinaryCodec originMsg, Channel channel) {
        cacheOrder(orderId, clOrdId, command, originMsg, channel);
        api.submitCommandAsync(command);
    }

    /**
     * 处理撤单请求的公共流程：反查原订单 → 组装 ApiCancelOrder 提交。
     *
     * @return 反查到的原订单；未知订单返回 null（调用方负责下发撤单拒绝）
     */
    protected CommandWrapper submitCancelOrder(String origClOrdId, String cancelClOrdId) {
        Long orderId = clOrdIdIndex.get(origClOrdId);
        CommandWrapper wrapper = orderId == null ? null : cache.get(orderId);
        if (orderId == null || wrapper == null || !(wrapper.getApiCommand() instanceof ApiPlaceOrder)) {
            log.warn("Cancel rejected: unknown origClOrdId {}", origClOrdId);
            return null;
        }
        ApiPlaceOrder original = (ApiPlaceOrder) wrapper.getApiCommand();
        pendingCancelClOrdId.put(orderId, cancelClOrdId);
        api.submitCommandAsync(ApiCancelOrder.builder()
                .orderId(orderId)
                .uid(original.uid)
                .symbol(original.symbol)
                .build());
        return wrapper;
    }

    /**
     * HTTP 通道提交委托：无连接与原始协议报文，回报仅记录日志。
     */
    @Override
    public void submitExternalOrder(ApiPlaceOrder command) {
        cacheOrder(command.orderId, null, command, null, null);
        api.submitCommandAsync(command);
    }

    /**
     * 注册委托确认等待（HTTP 同步返回用），须在提交命令前调用。
     */
    @Override
    public CompletableFuture<ApiCommandResult> registerPendingResult(long orderId) {
        CompletableFuture<ApiCommandResult> future = new CompletableFuture<>();
        pendingResults.put(orderId, future);
        return future;
    }

    // ---------- 缓存管理 ----------

    protected void cacheOrder(Long orderId, String clOrdId, ApiCommand command, BinaryCodec originMsg, Channel channel) {
        CommandWrapper commandWrapper = new CommandWrapper();
        commandWrapper.setUniqueId(orderId);
        commandWrapper.setApiCommand(command);
        commandWrapper.setOriginMsg(originMsg);
        commandWrapper.setChannel(channel);
        commandWrapper.setClOrdId(clOrdId);
        cache.put(orderId, commandWrapper);
        if (clOrdId != null) {
            clOrdIdIndex.put(clOrdId, orderId);
        }
    }

    /**
     * 订单终态统一清理：委托缓存 + ClOrdId 反查索引。
     */
    protected void evictOrder(long orderId) {
        CommandWrapper wrapper = cache.remove(orderId);
        if (wrapper != null && wrapper.getClOrdId() != null) {
            clOrdIdIndex.remove(wrapper.getClOrdId());
        }
    }

    /**
     * 连接断开：清理该会话全部委托缓存，释放 Channel 引用。
     */
    public void onChannelInactive(Channel channel) {
        int removed = 0;
        for (CommandWrapper wrapper : cache.values()) {
            if (wrapper.getChannel() == channel && cache.remove(wrapper.getUniqueId()) != null) {
                if (wrapper.getClOrdId() != null) {
                    clOrdIdIndex.remove(wrapper.getClOrdId());
                }
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Removed {} cached orders for closed channel {}", removed, channel.remoteAddress());
        }
    }

    protected boolean fromWire(CommandWrapper wrapper) {
        return wrapper != null && wrapper.fromWire();
    }

    /**
     * 原始委托的申报数量（LeavesQty 扣减基准）。
     */
    protected abstract long orderQtyOf(CommandWrapper wrapper);

    // ---------- 撮合事件回调（模板方法） ----------

    @Override
    public void commandResult(ApiCommandResult commandResult) {
        log.info("Command result: {}", commandResult);
        ApiCommand command = commandResult.getCommand();
        long orderId;
        if (command instanceof ApiPlaceOrder) {
            orderId = ((ApiPlaceOrder) command).orderId;
        } else if (command instanceof ApiCancelOrder) {
            orderId = ((ApiCancelOrder) command).orderId;
        } else {
            return;
        }
        //完成 HTTP 同步等待（无论订单是否仍在缓存）
        CompletableFuture<ApiCommandResult> pending = pendingResults.remove(orderId);
        if (pending != null) {
            pending.complete(commandResult);
        }

        if (command instanceof ApiCancelOrder) {
            handleCancelResult(orderId, commandResult);
        } else {
            handleOrderResult(orderId, commandResult);
        }
    }

    private void handleOrderResult(long orderId, ApiCommandResult commandResult) {
        CommandWrapper wrapper = cache.get(orderId);
        if (wrapper == null) {
            return;
        }
        boolean rejected = sendOrderConfirm(wrapper, commandResult);
        if (rejected) {
            //申报被拒，订单终态，移除缓存
            evictOrder(orderId);
        }
    }

    private void handleCancelResult(long orderId, ApiCommandResult commandResult) {
        CommandWrapper wrapper = cache.get(orderId);
        String cancelClOrdId = pendingCancelClOrdId.remove(orderId);
        if (commandResult.getResultCode() == CommandResultCode.SUCCESS) {
            long cumQty = wrapper == null ? 0 : wrapper.getCumQty().get();
            if (fromWire(wrapper)) {
                sendCancelConfirm(wrapper, cumQty);
            } else {
                log.info("Cancel confirmed for non-wire order {}, no report sent", orderId);
            }
            evictOrder(orderId);
        } else {
            log.warn("Cancel failed for order {}: {}", orderId, commandResult.getResultCode());
            if (fromWire(wrapper)) {
                sendCancelReject(wrapper, cancelClOrdId,
                        commandResult.getResultCode() + ": cancel failed");
            }
        }
    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        log.info("Trade event: {}", tradeEvent);
        //逐笔回报：每笔成交分别生成 taker 与 maker 回报，成交价取实际成交价
        List<Trade> trades = tradeEvent.getTrades();
        if (trades == null) {
            return;
        }
        for (Trade trade : trades) {
            reportTrade(cache.get(tradeEvent.getTakerOrderId()), trade);
            reportTrade(cache.get(trade.getMakerOrderId()), trade);
        }
        if (tradeEvent.isTakeOrderCompleted()) {
            //taker 全部成交，订单终态，移除缓存
            evictOrder(tradeEvent.getTakerOrderId());
        }
    }

    private void reportTrade(CommandWrapper wrapper, Trade trade) {
        if (wrapper == null) {
            log.warn("Order for trade report not found in cache, skip report of trade {}", trade);
            return;
        }
        if (!fromWire(wrapper)) {
            log.info("Trade report for non-wire order {} skipped: lastPx={}, lastQty={}",
                    wrapper.getUniqueId(), trade.price, trade.volume);
            return;
        }
        sendTradeReport(wrapper, trade);
    }

    @Override
    public void reduceEvent(ReduceEvent reduceEvent) {
        //减量/撤单成交侧事件，订单状态以 commandResult 为准
        log.info("Reduce event: {}", reduceEvent);
    }

    @Override
    public void rejectEvent(RejectEvent rejectEvent) {
        //拒绝，委托拒绝和撤单拒绝
        log.info("Reject event: {}", rejectEvent);
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        //行情发布
        log.info("OrderBook event: {}", orderBook);
    }

    // ---------- 协议差异钩子 ----------

    /**
     * 下发委托确认（Confirm/ExecutionConfirm）。
     *
     * @return true 表示申报被拒（调用方将清理订单缓存）
     */
    protected abstract boolean sendOrderConfirm(CommandWrapper wrapper, ApiCommandResult commandResult);

    /**
     * 下发撤单成功回报（SSE: Report ExecType=4；SZSE: ExecutionConfirm 20xx02，见规范 §7.1）。
     */
    protected abstract void sendCancelConfirm(CommandWrapper wrapper, long cumQty);

    /**
     * 下发撤单拒绝（SSE: CancelReject 59；SZSE: CancelReject 290008，OrdStatus 填委托当前状态）。
     *
     * @param cancelClOrdId 撤单请求自身的 ClOrdId（回显用，可为空）
     * @param rejectReason  拒绝原因（文本或代码语义由协议决定）
     */
    protected abstract void sendCancelReject(CommandWrapper wrapper, String cancelClOrdId, String rejectReason);

    /**
     * 逐笔成交回报（LastPx/LastQty 取成交价量，LeavesQty/CumQty 扣减累计）。
     */
    protected abstract void sendTradeReport(CommandWrapper wrapper, Trade trade);
}
