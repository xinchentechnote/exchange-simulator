package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.CancelReject;
import com.finproto.sse.bin.messages.Confirm;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.OrderCancel;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.AbstractBinServer;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.Constant;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.convertor.cmd.ApiCommandConvertorContext;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import com.xinchentechnote.exchange.simulator.sse.confirm.SseConfirmConvertor;
import com.xinchentechnote.exchange.simulator.sse.trade.SseTradeReportConvertor;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPlaceOrder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 二进制协议接入服务：会话/缓存/撤单调度在 {@link AbstractBinServer}，
 * 这里仅实现 SSE 报文编解码与确认/回报/拒绝报文的组装。
 */
@Slf4j
public class SseBinServer extends AbstractBinServer {

    /** 撤单拒绝原因：未知订单 */
    public static final int CXL_REJ_UNKNOWN_ORDER = 1;
    /** 撤单拒绝原因：其他（撮合核心返回失败） */
    public static final int CXL_REJ_OTHER = 99;

    public SseBinServer(int port) {
        super(port);
    }

    @Override
    protected ChannelInitializer<Channel> createInitializer() {
        return new SseBinServerInitializer(this);
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
            submitOrder(((ApiPlaceOrder) apiCommand).orderId, order.getClOrdId(), apiCommand, msg, channel);
            return;
        }
        api.submitCommandAsync(apiCommand);
    }

    private void handleOrderCancel(OrderCancel request, Channel channel) {
        CommandWrapper wrapper = submitCancelOrder(request.getOrigClOrdId(), request.getClOrdId());
        if (wrapper == null) {
            sendCancelRejectForRequest(channel, request, CXL_REJ_UNKNOWN_ORDER);
        }
    }

    // ---------- 协议钩子 ----------

    @Override
    protected boolean sendOrderConfirm(CommandWrapper wrapper, ApiCommandResult commandResult) {
        SseBinary originMsg = (SseBinary) wrapper.getOriginMsg();
        if (originMsg == null || !(originMsg.getBody() instanceof NewOrderSingle)) {
            return false;
        }
        NewOrderSingle orderSingle = (NewOrderSingle) originMsg.getBody();
        Confirm confirm = new SseConfirmConvertor().convert(orderSingle, commandResult);
        if (confirm == null) {
            return false;
        }
        sendConfirm(wrapper.getChannel(), confirm);
        return ExecType.REJECTED.equals(confirm.getExecType());
    }

    @Override
    protected void sendCancelConfirm(CommandWrapper wrapper, long cumQty) {
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

    @Override
    protected void sendCancelReject(CommandWrapper wrapper, String cancelClOrdId, String rejectReason) {
        OrderCancel echo = new OrderCancel();
        echo.setClOrdId(cancelClOrdId);
        echo.setOrigClOrdId(wrapper.getClOrdId());
        echo.setSecurityId(String.valueOf(((ApiPlaceOrder) wrapper.getApiCommand()).symbol));
        sendCancelRejectForRequest(wrapper.getChannel(), echo, CXL_REJ_OTHER);
    }

    private void sendCancelRejectForRequest(Channel channel, OrderCancel request, int reason) {
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

    @Override
    protected void sendTradeReport(CommandWrapper wrapper, Trade trade) {
        long cumQty = wrapper.getCumQty().addAndGet(trade.volume);
        Report report = new SseTradeReportConvertor().convert(trade, wrapper);
        //SSE Report 无 CumQty 字段，以 LeavesQty 体现剩余量
        report.setLeavesQty(Math.max(0, orderQtyOf(wrapper) - cumQty));
        sendReport(wrapper.getChannel(), report);
    }

    @Override
    protected long orderQtyOf(CommandWrapper wrapper) {
        SseBinary originMsg = (SseBinary) wrapper.getOriginMsg();
        if (originMsg != null && originMsg.getBody() instanceof NewOrderSingle) {
            return ((NewOrderSingle) originMsg.getBody()).getOrderQty();
        }
        return 0;
    }

    // ---------- 下行发送 ----------

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
}
