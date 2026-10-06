package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.codec.BinaryCodec;
import com.finproto.szse.bin.messages.CancelReject;
import com.finproto.szse.bin.messages.ExecutionConfirm;
import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.OrderCancelRequest;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.common.OrderGateway;
import com.xinchentechnote.exchange.simulator.convertor.cmd.ApiCommandConvertorContext;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import com.xinchentechnote.exchange.simulator.szse.confirm.SzseConfirmConvertor;
import com.xinchentechnote.exchange.simulator.szse.trade.SzseTradeReportConvertor;
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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class SzseBinServer implements IEventsHandler, OrderGateway {

    /** 撤单拒绝原因文本：未知订单 */
    public static final String REJECT_UNKNOWN_ORDER = "unknown order";

    private final int port;
    private ExchangeApi api;

    private final Map<Long, CommandWrapper> cache = new ConcurrentHashMap<>();
    /** ClOrdId → 内部 orderId，供撤单请求按 origClOrdId 反查原订单 */
    private final Map<String, Long> clOrdIdIndex = new ConcurrentHashMap<>();
    /** HTTP 通道同步等待委托确认的 futures（orderId → future），commandResult 时完成 */
    private final Map<Long, CompletableFuture<ApiCommandResult>> pendingResults = new ConcurrentHashMap<>();

    public SzseBinServer(int port) {
        this.port = port;
    }

    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup group = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(2);
        bootstrap.group(group, workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new SzseBinServerInitializer(this));
        try {
            // 绑定失败必须终止启动，否则问题会推迟到客户端连不上时才暴露
            bootstrap.bind(port).sync();
            log.info("SzseBinServer started on port :{}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to start SzseBinServer on port " + port, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start SzseBinServer on port " + port, e);
        }
    }

    public void onMessage(SzseBinary msg, Channel channel) {
        // 处理接收到的消息
        BinaryCodec body = msg.getBody();
        if (body instanceof OrderCancelRequest) {
            handleOrderCancel((OrderCancelRequest) body, channel);
            return;
        }
        IApiCommandConverter converter = ApiCommandConvertorContext.getInstance().get(body);
        if (converter == null) {
            log.error("No converter found for msg type: {}", msg.getMsgType());
            return;
        }
        ApiCommand apiCommand = converter.convertNewOrder(body);
        if (apiCommand instanceof ApiPlaceOrder) {
            NewOrder order = (NewOrder) body;
            cacheOrder(((ApiPlaceOrder) apiCommand).orderId, order.getClOrdId(), apiCommand, msg, channel);
        }
        api.submitCommandAsync(apiCommand);
    }

    /**
     * HTTP 通道提交委托：无连接与原始协议报文，回报仅记录日志。
     */
    public void submitExternalOrder(ApiPlaceOrder command) {
        cacheOrder(command.orderId, null, command, null, null);
        api.submitCommandAsync(command);
    }

    /**
     * 注册委托确认等待（HTTP 同步返回用），须在提交命令前调用。
     */
    public CompletableFuture<ApiCommandResult> registerPendingResult(long orderId) {
        CompletableFuture<ApiCommandResult> future = new CompletableFuture<>();
        pendingResults.put(orderId, future);
        return future;
    }

    private void cacheOrder(long orderId, String clOrdId, ApiCommand command, SzseBinary originMsg, Channel channel) {
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
    private void evictOrder(long orderId) {
        CommandWrapper wrapper = cache.remove(orderId);
        if (wrapper != null && wrapper.getClOrdId() != null) {
            clOrdIdIndex.remove(wrapper.getClOrdId());
        }
    }

    // ---------- 撤单 ----------

    private void handleOrderCancel(OrderCancelRequest request, Channel channel) {
        Long orderId = clOrdIdIndex.get(request.getOrigClOrdId());
        CommandWrapper wrapper = orderId == null ? null : cache.get(orderId);
        if (orderId == null || wrapper == null || !(wrapper.getApiCommand() instanceof ApiPlaceOrder)) {
            log.warn("Cancel rejected: unknown origClOrdId {}", request.getOrigClOrdId());
            sendCancelReject(channel, request, REJECT_UNKNOWN_ORDER);
            return;
        }
        ApiPlaceOrder original = (ApiPlaceOrder) wrapper.getApiCommand();
        api.submitCommandAsync(ApiCancelOrder.builder()
                .orderId(orderId)
                .uid(original.uid)
                .symbol(original.symbol)
                .build());
    }

    private void sendCancelConfirm(CommandWrapper wrapper, long cumQty) {
        if (!wrapper.fromWire()) {
            log.info("Cancel confirmed for non-wire order {}, no report sent", wrapper.getUniqueId());
            return;
        }
        SzseBinary originMsg = (SzseBinary) wrapper.getOriginMsg();
        NewOrder orderSingle = (NewOrder) originMsg.getBody();
        ExecutionReport report = new ExecutionReport();
        fillCommonFields(report, orderSingle);
        report.setExecType(ExecType.CANCELED);
        report.setOrdStatus(ExecType.CANCELED);
        report.setCumQty(cumQty);
        report.setLeavesQty(0);
        report.setApplExtend(new com.finproto.szse.bin.messages.Extend200115());
        sendReport(wrapper.getChannel(), report);
    }

    private void sendCancelReject(Channel channel, OrderCancelRequest request, String rejectText) {
        if (channel == null || !channel.isActive()) {
            log.warn("Channel is not active, cannot send cancel reject for clOrdId {}", request.getClOrdId());
            return;
        }
        CancelReject reject = new CancelReject();
        reject.setApplId(request.getApplId());
        reject.setSubmittingPbuid(request.getSubmittingPbuid());
        reject.setReportingPbuid(request.getSubmittingPbuid());
        reject.setSecurityId(request.getSecurityId());
        reject.setClOrdId(request.getClOrdId());
        reject.setOrigClOrdId(request.getOrigClOrdId());
        reject.setSide(request.getSide());
        reject.setOrdStatus(ExecType.NEW);
        reject.setRejectText(rejectText);
        reject.setTransactTime(request.getTransactTime());
        reject.setUserInfo(request.getUserInfo());
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.setMsgType(SzseMsgType.CANCEL_REJECT);
        szseBinary.setBody(reject);
        log.info("Sending cancel reject: {}", reject);
        ByteBuf buf = Unpooled.buffer();
        szseBinary.encode(buf);
        channel.writeAndFlush(buf);
    }

    // ---------- 连接断开清理 ----------

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

    // ---------- 撮合事件回调 ----------

    @Override
    public void commandResult(ApiCommandResult commandResult) {
        log.info("Received command result: {}", commandResult);
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
            return;
        }

        //委托确认
        CommandWrapper commandWrapper = cache.get(orderId);
        if (commandWrapper == null) {
            return;
        }
        SzseBinary originMsg = (SzseBinary) commandWrapper.getOriginMsg();
        if (originMsg == null || !(originMsg.getBody() instanceof NewOrder)) {
            return;
        }
        ExecutionConfirm confirm = new SzseConfirmConvertor().convert((NewOrder) originMsg.getBody(), commandResult);
        if (confirm == null) {
            return;
        }
        sendConfirm(commandWrapper.getChannel(), confirm);
        if (ExecType.REJECTED.equals(confirm.getExecType())) {
            //申报被拒，订单终态，移除缓存
            evictOrder(orderId);
        }
    }

    private void handleCancelResult(long orderId, ApiCommandResult commandResult) {
        CommandWrapper wrapper = cache.get(orderId);
        if (commandResult.getResultCode() == CommandResultCode.SUCCESS) {
            long cumQty = wrapper == null ? 0 : wrapper.getCumQty().get();
            if (wrapper != null) {
                sendCancelConfirm(wrapper, cumQty);
            }
            evictOrder(orderId);
        } else {
            log.warn("Cancel failed for order {}: {}", orderId, commandResult.getResultCode());
            if (wrapper != null && wrapper.fromWire()) {
                OrderCancelRequest echo = new OrderCancelRequest();
                echo.setOrigClOrdId(wrapper.getClOrdId());
                echo.setApplId(applIdOf(wrapper));
                echo.setSecurityId(String.valueOf(((ApiCancelOrder) commandResult.getCommand()).symbol));
                sendCancelReject(wrapper.getChannel(), echo, "cancel failed: " + commandResult.getResultCode());
            }
        }
    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        log.info("Trade event: {}", tradeEvent);
        //逐笔回报：每笔成交分别生成 taker 与 maker 回报，成交价取实际成交价
        SzseTradeReportConvertor convertor = new SzseTradeReportConvertor();
        List<Trade> trades = tradeEvent.getTrades();
        if (trades == null) {
            return;
        }
        for (Trade trade : trades) {
            sendTradeReport(convertor, cache.get(tradeEvent.getTakerOrderId()), trade);
            sendTradeReport(convertor, cache.get(trade.getMakerOrderId()), trade);
        }
        if (tradeEvent.isTakeOrderCompleted()) {
            //taker 全部成交，订单终态，移除缓存
            evictOrder(tradeEvent.getTakerOrderId());
        }
    }

    private void sendTradeReport(SzseTradeReportConvertor convertor, CommandWrapper wrapper, Trade trade) {
        if (wrapper == null) {
            log.warn("Order for trade report not found in cache, skip report of trade {}", trade);
            return;
        }
        if (!wrapper.fromWire()) {
            log.info("Trade report for non-wire order {} skipped: lastPx={}, lastQty={}",
                    wrapper.getUniqueId(), trade.price, trade.volume);
            return;
        }
        long cumQty = wrapper.getCumQty().addAndGet(trade.volume);
        ExecutionReport report = convertor.convert(trade, wrapper);
        report.setCumQty(cumQty);
        report.setLeavesQty(Math.max(0, orderQtyOf(wrapper) - cumQty));
        sendReport(wrapper.getChannel(), report);
    }

    private long orderQtyOf(CommandWrapper wrapper) {
        if (wrapper.getOriginMsg() != null
                && ((SzseBinary) wrapper.getOriginMsg()).getBody() instanceof NewOrder) {
            return ((NewOrder) ((SzseBinary) wrapper.getOriginMsg()).getBody()).getOrderQty();
        }
        return 0;
    }

    private String applIdOf(CommandWrapper wrapper) {
        if (wrapper.getOriginMsg() != null
                && ((SzseBinary) wrapper.getOriginMsg()).getBody() instanceof NewOrder) {
            return ((NewOrder) ((SzseBinary) wrapper.getOriginMsg()).getBody()).getApplId();
        }
        return "010";
    }

    /** 从原始委托回填回报公共字段（账号/证券/PBU 等）。 */
    private void fillCommonFields(ExecutionReport report, NewOrder orderSingle) {
        report.setAccountId(orderSingle.getAccountId());
        report.setSecurityId(orderSingle.getSecurityId());
        report.setApplId(orderSingle.getApplId());
        report.setSubmittingPbuid(orderSingle.getSubmittingPbuid());
        report.setReportingPbuid(orderSingle.getSubmittingPbuid());
        report.setClOrdId(orderSingle.getClOrdId());
        report.setBranchId(orderSingle.getBranchId());
        report.setSide(orderSingle.getSide());
        report.setUserInfo(orderSingle.getUserInfo());
        report.setTransactTime(orderSingle.getTransactTime());
        report.setLastPx(0);
        report.setLastQty(0);
    }

    private void sendReport(Channel channel, ExecutionReport report) {
        if (channel == null || !channel.isActive()) {
            log.warn("Channel is not active, cannot send report: {}", report);
            return;
        }
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.setMsgType(SzseMsgType.EXECUTION_REPORT);
        szseBinary.setBody(report);
        log.info("Sending report: {}", report);
        ByteBuf buf = Unpooled.buffer();
        szseBinary.encode(buf);
        channel.writeAndFlush(buf);
    }

    private void sendConfirm(Channel channel, ExecutionConfirm confirm) {
        if (channel == null || !channel.isActive()) {
            log.warn("Channel is not active, cannot send confirm: {}", confirm);
            return;
        }
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.setMsgType(SzseMsgType.EXECUTION_CONFIRM);
        szseBinary.setBody(confirm);
        log.info("Sending confirm: {}", confirm);
        ByteBuf buf = Unpooled.buffer();
        szseBinary.encode(buf);
        channel.writeAndFlush(buf);
    }

    @Override
    public void rejectEvent(RejectEvent rejectEvent) {
        log.info("Received reject event: {}", rejectEvent);
    }

    @Override
    public void reduceEvent(ReduceEvent reduceEvent) {
        log.info("Received reduce event: {}", reduceEvent);
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        log.info("Received order book update: {}", orderBook);
    }
}
