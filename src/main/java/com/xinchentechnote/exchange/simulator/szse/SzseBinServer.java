package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.codec.BinaryCodec;
import com.finproto.szse.bin.messages.CancelReject;
import com.finproto.szse.bin.messages.ExecutionConfirm;
import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.OrderCancelRequest;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.common.AbstractBinServer;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.convertor.cmd.ApiCommandConvertorContext;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import com.xinchentechnote.exchange.simulator.szse.confirm.SzseConfirmConvertor;
import com.xinchentechnote.exchange.simulator.szse.trade.SzseTradeReportConvertor;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPlaceOrder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import lombok.extern.slf4j.Slf4j;

/**
 * SZSE 二进制协议接入服务：会话/缓存/撤单调度在 {@link AbstractBinServer}，
 * 这里仅实现 SZSE 报文编解码与确认/回报/拒绝报文的组装。
 */
@Slf4j
public class SzseBinServer extends AbstractBinServer {

    /** 撤单拒绝原因文本：未知订单 */
    public static final String REJECT_UNKNOWN_ORDER = "unknown order";

    public SzseBinServer(int port) {
        super(port);
    }

    @Override
    protected ChannelInitializer<Channel> createInitializer() {
        return new SzseBinServerInitializer(this);
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
            submitOrder(((ApiPlaceOrder) apiCommand).orderId, order.getClOrdId(), apiCommand, msg, channel);
            return;
        }
        api.submitCommandAsync(apiCommand);
    }

    private void handleOrderCancel(OrderCancelRequest request, Channel channel) {
        CommandWrapper wrapper = submitCancelOrder(request.getOrigClOrdId(), request.getClOrdId());
        if (wrapper == null) {
            sendCancelRejectForRequest(channel, request, REJECT_UNKNOWN_ORDER, ExecType.NEW);
        }
    }

    // ---------- 协议钩子 ----------

    @Override
    protected boolean sendOrderConfirm(CommandWrapper wrapper, ApiCommandResult commandResult) {
        SzseBinary originMsg = (SzseBinary) wrapper.getOriginMsg();
        if (originMsg == null || !(originMsg.getBody() instanceof NewOrder)) {
            return false;
        }
        ExecutionConfirm confirm = new SzseConfirmConvertor().convert((NewOrder) originMsg.getBody(), commandResult);
        if (confirm == null) {
            return false;
        }
        sendConfirm(wrapper.getChannel(), confirm);
        return ExecType.REJECTED.equals(confirm.getExecType());
    }

    /**
     * 撤单成功回报：按规范（Ver1.29 §7.1）使用 ExecutionConfirm(20xx02)，
     * ExecType=4、OrdStatus=4、CumQty=已成交量、LeavesQty=0。
     */
    @Override
    protected void sendCancelConfirm(CommandWrapper wrapper, long cumQty) {
        SzseBinary originMsg = (SzseBinary) wrapper.getOriginMsg();
        NewOrder orderSingle = (NewOrder) originMsg.getBody();
        ExecutionConfirm confirm = new ExecutionConfirm();
        fillConfirmFields(confirm, orderSingle);
        confirm.setExecType(ExecType.CANCELED);
        confirm.setOrdStatus(ExecType.CANCELED);
        confirm.setCumQty(cumQty);
        confirm.setLeavesQty(0);
        confirm.setApplExtend(new com.finproto.szse.bin.messages.Extend200102());
        sendConfirm(wrapper.getChannel(), confirm);
    }

    @Override
    protected void sendCancelReject(CommandWrapper wrapper, String cancelClOrdId, String rejectReason) {
        OrderCancelRequest echo = new OrderCancelRequest();
        echo.setClOrdId(cancelClOrdId);
        echo.setOrigClOrdId(wrapper.getClOrdId());
        echo.setApplId(applIdOf(wrapper));
        echo.setSecurityId(String.valueOf(((ApiPlaceOrder) wrapper.getApiCommand()).symbol));
        echo.setSide(sideOf(wrapper));
        sendCancelRejectForRequest(wrapper.getChannel(), echo, rejectReason, orderStatusOf(wrapper));
    }

    private void sendCancelRejectForRequest(Channel channel, OrderCancelRequest request,
                                            String rejectText, String currentOrdStatus) {
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
        //按规范 OrdStatus 填目标委托当前状态
        reject.setOrdStatus(currentOrdStatus);
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

    @Override
    protected void sendTradeReport(CommandWrapper wrapper, Trade trade) {
        long cumQty = wrapper.getCumQty().addAndGet(trade.volume);
        ExecutionReport report = new SzseTradeReportConvertor().convert(trade, wrapper);
        //按规范区分部分成交(1)/全部成交(2)
        report.setCumQty(cumQty);
        long leavesQty = Math.max(0, orderQtyOf(wrapper) - cumQty);
        report.setLeavesQty(leavesQty);
        report.setOrdStatus(leavesQty == 0 ? ExecType.FILLED : ExecType.PARTIALLY_FILLED);
        sendReport(wrapper.getChannel(), report);
    }

    @Override
    protected long orderQtyOf(CommandWrapper wrapper) {
        NewOrder orderSingle = originOrderOf(wrapper);
        return orderSingle != null ? orderSingle.getOrderQty() : 0;
    }

    /**
     * 撤单拒绝的 OrdStatus 按规范填目标委托当前状态（0=新订单/1=部分成交/2=全部成交）。
     */
    private String orderStatusOf(CommandWrapper wrapper) {
        if (wrapper == null) {
            return ExecType.NEW;
        }
        long cumQty = wrapper.getCumQty().get();
        long orderQty = orderQtyOf(wrapper);
        if (cumQty <= 0) {
            return ExecType.NEW;
        }
        return cumQty >= orderQty ? ExecType.FILLED : ExecType.PARTIALLY_FILLED;
    }

    private String sideOf(CommandWrapper wrapper) {
        NewOrder orderSingle = originOrderOf(wrapper);
        return orderSingle != null ? orderSingle.getSide() : "";
    }

    private String applIdOf(CommandWrapper wrapper) {
        NewOrder orderSingle = originOrderOf(wrapper);
        return orderSingle != null ? orderSingle.getApplId() : "010";
    }

    private NewOrder originOrderOf(CommandWrapper wrapper) {
        if (wrapper.getOriginMsg() != null
                && ((SzseBinary) wrapper.getOriginMsg()).getBody() instanceof NewOrder) {
            return (NewOrder) ((SzseBinary) wrapper.getOriginMsg()).getBody();
        }
        return null;
    }

    /** 从原始委托回填确认报文公共字段（账号/证券/PBU 等）。 */
    private void fillConfirmFields(ExecutionConfirm confirm, NewOrder orderSingle) {
        confirm.setAccountId(orderSingle.getAccountId());
        confirm.setSecurityId(orderSingle.getSecurityId());
        confirm.setApplId(orderSingle.getApplId());
        confirm.setSubmittingPbuid(orderSingle.getSubmittingPbuid());
        confirm.setReportingPbuid(orderSingle.getSubmittingPbuid());
        confirm.setClOrdId(orderSingle.getClOrdId());
        confirm.setBranchId(orderSingle.getBranchId());
        confirm.setSide(orderSingle.getSide());
        confirm.setUserInfo(orderSingle.getUserInfo());
        confirm.setTransactTime(orderSingle.getTransactTime());
        confirm.setClearingFirm(orderSingle.getClearingFirm());
        confirm.setOrdType(orderSingle.getOrdType());
        confirm.setOrderQty(orderSingle.getOrderQty());
        confirm.setPrice(orderSingle.getPrice());
        confirm.setOwnerType(orderSingle.getOwnerType());
    }

    // ---------- 下行发送 ----------

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
}
