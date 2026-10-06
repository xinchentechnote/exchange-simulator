package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.CancelReject;
import com.finproto.sse.bin.messages.Confirm;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.OrderCancel;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.Constant;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.common.OrderGateway;
import com.xinchentechnote.exchange.simulator.convertor.cmd.ApiCommandConvertorContext;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import com.xinchentechnote.exchange.simulator.sse.confirm.SseConfirmConvertor;
import com.xinchentechnote.exchange.simulator.sse.trade.SseTradeReportConvertor;
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
import java.util.concurrent.atomic.AtomicLong;

@Data
@Slf4j
public class SseBinServer implements IEventsHandler, OrderGateway {

    /** 撤单拒绝原因：未知订单 */
    public static final int CXL_REJ_UNKNOWN_ORDER = 1;
    /** 撤单拒绝原因：其他（撮合核心返回失败） */
    public static final int CXL_REJ_OTHER = 99;

    private ExchangeApi api;
    private int port;

    private final Map<Long, CommandWrapper> cache = new ConcurrentHashMap<>();
    /** ClOrdId → 内部 orderId，供撤单请求按 origClOrdId 反查原订单 */
    private final Map<String, Long> clOrdIdIndex = new ConcurrentHashMap<>();
    /** HTTP 通道同步等待委托确认的 futures（orderId → future），commandResult 时完成 */
    private final Map<Long, CompletableFuture<ApiCommandResult>> pendingResults = new ConcurrentHashMap<>();
    /** 待决撤单：orderId → 撤单请求自身的 ClOrdId（构造 CancelReject 回执时回显） */
    private final Map<Long, String> pendingCancelClOrdId = new ConcurrentHashMap<>();

    public SseBinServer(int port) {
        this.port = port;
    }

    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup group = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(2);
        bootstrap.group(group, workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new SseBinServerInitializer(this));
        try {
            // 绑定失败必须终止启动，否则问题会推迟到客户端连不上时才暴露
            bootstrap.bind(port).sync();
            log.info("SseBinServer started on port :{}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to start SseBinServer on port " + port, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start SseBinServer on port " + port, e);
        }
    }

    public void onMessage(SseBinary msg, Channel channel) {
        // 处理接收到的消息
        BinaryCodec body = msg.getBody();
        if (body instanceof OrderCancel) {
            handleOrderCancel((OrderCancel) body, channel);
            return;
        }
        IApiCommandConverter converter = ApiCommandConvertorContext.getInstance().get(body);
        if (converter == null) {
            log.error("No converter found for msg type: {}", msg.getMsgType());
            return;
        }
        ApiCommand apiCommand = converter.convertNewOrder(body);
        if (apiCommand instanceof ApiPlaceOrder) {
            NewOrderSingle order = (NewOrderSingle) body;
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

    private void cacheOrder(long orderId, String clOrdId, ApiCommand command, SseBinary originMsg, Channel channel) {
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

    private void handleOrderCancel(OrderCancel request, Channel channel) {
        Long orderId = clOrdIdIndex.get(request.getOrigClOrdId());
        CommandWrapper wrapper = orderId == null ? null : cache.get(orderId);
        if (orderId == null || wrapper == null || !(wrapper.getApiCommand() instanceof ApiPlaceOrder)) {
            log.warn("Cancel rejected: unknown origClOrdId {}", request.getOrigClOrdId());
            sendCancelReject(channel, request, CXL_REJ_UNKNOWN_ORDER);
            return;
        }
        ApiPlaceOrder original = (ApiPlaceOrder) wrapper.getApiCommand();
        pendingCancelClOrdId.put(orderId, request.getClOrdId());
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
        SseBinary originMsg = (SseBinary) wrapper.getOriginMsg();
        NewOrderSingle orderSingle = (NewOrderSingle) originMsg.getBody();
        Report report = new Report();
        report.setAccount(orderSingle.getAccount());
        report.setSecurityId(orderSingle.getSecurityId());
        report.setBizId(orderSingle.getBizId());
        report.setExecType(ExecType.CANCELED);
        report.setPbu(orderSingle.getBizPbu());
        report.setBizPbu(orderSingle.getBizPbu());
        report.setClOrdId(orderSingle.getClOrdId());
        report.setBranchId(orderSingle.getBranchId());
        report.setSide(orderSingle.getSide());
        report.setUserInfo(orderSingle.getUserInfo());
        report.setTransactTime(orderSingle.getTransactTime());
        report.setTradeDate((int) (orderSingle.getTransactTime() / 1000_000));
        report.setOrderQty(orderSingle.getOrderQty());
        report.setLeavesQty(0);
        report.setOrdStatus(ExecType.CANCELED);
        sendReport(wrapper.getChannel(), report);
    }

    private void sendCancelReject(Channel channel, OrderCancel request, int reason) {
        if (channel == null || !channel.isActive()) {
            log.warn("Channel is not active, cannot send cancel reject for clOrdId {}", request.getClOrdId());
            return;
        }
        CancelReject reject = new CancelReject();
        reject.setBizId(request.getBizId());
        reject.setBizPbu(request.getBizPbu());
        reject.setPbu(request.getBizPbu());
        reject.setClOrdId(request.getClOrdId());
        reject.setOrigClOrdId(request.getOrigClOrdId());
        reject.setSecurityId(request.getSecurityId());
        reject.setBranchId(request.getBranchId());
        reject.setCxlRejReason(reason);
        reject.setTransactTime(request.getTransactTime());
        reject.setTradeDate((int) (request.getTransactTime() / 1000_000));
        reject.setUserInfo(request.getUserInfo());
        SseBinary sseBinary = new SseBinary();
        sseBinary.setMsgSeqNum(nextSeqNum(channel));
        sseBinary.setMsgType(SseBinary.BodyMessageFactory.MessageType.CANCEL_REJECT.getValue());
        sseBinary.setBody(reject);
        log.info("Sending cancel reject: {}", reject);
        ByteBuf buf = Unpooled.buffer();
        sseBinary.encode(buf);
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
    public void tradeEvent(TradeEvent tradeEvent) {
        log.info("Trade event: {}", tradeEvent);
        //逐笔回报：每笔成交分别生成 taker 与 maker 回报，成交价取实际成交价
        SseTradeReportConvertor convertor = new SseTradeReportConvertor();
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

    private void sendTradeReport(SseTradeReportConvertor convertor, CommandWrapper wrapper, Trade trade) {
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
        Report report = convertor.convert(trade, wrapper);
        //SSE Report 无 CumQty 字段，以 LeavesQty 体现剩余量
        report.setLeavesQty(Math.max(0, orderQtyOf(wrapper) - cumQty));
        sendReport(wrapper.getChannel(), report);
    }

    private long orderQtyOf(CommandWrapper wrapper) {
        if (wrapper.getOriginMsg() != null
                && ((SseBinary) wrapper.getOriginMsg()).getBody() instanceof NewOrderSingle) {
            return ((NewOrderSingle) ((SseBinary) wrapper.getOriginMsg()).getBody()).getOrderQty();
        }
        return 0;
    }

    private void sendReport(Channel channel, Report report) {
        if (channel == null || !channel.isActive()) {
            log.warn("Channel is not active, cannot send report: {}", report);
            return;
        }
        SseBinary sseBinary = new SseBinary();
        sseBinary.setMsgType(103);
        sseBinary.setBody(report);
        sseBinary.setMsgSeqNum(nextSeqNum(channel));
        log.info("Sending report: {}", report);
        ByteBuf buf = Unpooled.buffer();
        sseBinary.encode(buf);
        channel.writeAndFlush(buf);
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
            return;
        }

        //委托确认
        CommandWrapper commandWrapper = cache.get(orderId);
        if (commandWrapper == null) {
            return;
        }
        SseBinary originMsg = (SseBinary) commandWrapper.getOriginMsg();
        if (originMsg == null || !(originMsg.getBody() instanceof NewOrderSingle)) {
            return;
        }
        NewOrderSingle orderSingle = (NewOrderSingle) originMsg.getBody();
        Confirm confirm = new SseConfirmConvertor().convert(orderSingle, commandResult);
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
        String cancelClOrdId = pendingCancelClOrdId.remove(orderId);
        if (commandResult.getResultCode() == CommandResultCode.SUCCESS) {
            long cumQty = wrapper == null ? 0 : wrapper.getCumQty().get();
            if (wrapper != null) {
                sendCancelConfirm(wrapper, cumQty);
            }
            evictOrder(orderId);
        } else {
            log.warn("Cancel failed for order {}: {}", orderId, commandResult.getResultCode());
            if (wrapper != null && wrapper.fromWire()) {
                OrderCancel echo = new OrderCancel();
                echo.setClOrdId(cancelClOrdId);
                echo.setOrigClOrdId(wrapper.getClOrdId());
                echo.setSecurityId(String.valueOf(((ApiCancelOrder) commandResult.getCommand()).symbol));
                sendCancelReject(wrapper.getChannel(), echo, CXL_REJ_OTHER);
            }
        }
    }

    private void sendConfirm(Channel channel, Confirm confirm) {
        if (channel == null || !channel.isActive()) {
            log.warn("Channel is not active, cannot send confirm: {}", confirm);
            return;
        }
        SseBinary sseBinary = new SseBinary();
        sseBinary.setMsgSeqNum(nextSeqNum(channel));
        sseBinary.setMsgType(32);
        sseBinary.setBody(confirm);
        log.info("Sending confirm: {}", confirm);
        ByteBuf buf = Unpooled.buffer();
        sseBinary.encode(buf);
        channel.writeAndFlush(buf);
    }

    /**
     * 下行报文序号按会话独立计数（协议要求会话内单调递增）。
     */
    private long nextSeqNum(Channel channel) {
        AtomicLong seq = channel.attr(Constant.MSG_SEQ_NUM).get();
        if (seq == null) {
            AtomicLong created = new AtomicLong(1);
            AtomicLong existing = channel.attr(Constant.MSG_SEQ_NUM).setIfAbsent(created);
            seq = existing != null ? existing : created;
        }
        return seq.getAndIncrement();
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        //行情发布
        log.info("OrderBook event: {}", orderBook);
    }
}
