package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.CancelReject;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.OrderCancel;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiCancelOrder;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SSE 撤单链路：撤单请求 → ApiCancelOrder → 撤单确认（Report ExecType=4）/ 撤单拒绝（CancelReject）。
 */
class SseCancelTest {

    private SseBinServer server;
    private ExchangeApi api;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        server = new SseBinServer(9010);
        api = mock(ExchangeApi.class);
        server.setApi(api);
        channel = new EmbeddedChannel(true, true);
    }

    private void placeOrder(String clOrdId) {
        NewOrderSingle order = new NewOrderSingle();
        order.setSecurityId("600000");
        order.setAccount("10001");
        order.setClOrdId(clOrdId);
        order.setSide("1");
        order.setOrdType("1");
        order.setTimeInForce("1");
        order.setPrice(1050L);
        order.setOrderQty(200L);
        SseBinary msg = new SseBinary();
        msg.setMsgType(58);
        msg.setMsgSeqNum(1);
        msg.setBody(order);
        server.onMessage(msg, channel);
    }

    private OrderCancel cancelReq(String clOrdId, String origClOrdId) {
        OrderCancel cancel = new OrderCancel();
        cancel.setClOrdId(clOrdId);
        cancel.setOrigClOrdId(origClOrdId);
        cancel.setSecurityId("600000");
        cancel.setSide("1");
        cancel.setTransactTime(20240501101530L);
        return cancel;
    }

    private void sendCancel(OrderCancel cancel) {
        SseBinary msg = new SseBinary();
        msg.setMsgType(61);
        msg.setMsgSeqNum(2);
        msg.setBody(cancel);
        server.onMessage(msg, channel);
    }

    private SseBinary readOutbound() {
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf, "expect outbound message");
        SseBinary decoded = new SseBinary();
        decoded.decode(buf.duplicate());
        return decoded;
    }

    @Test
    void cancelShouldSubmitApiCancelOrderWithOriginalContext() {
        placeOrder("CL-1");
        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> orderCaptor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api).submitCommandAsync(orderCaptor.capture());
        long placedOrderId = ((ApiPlaceOrder) orderCaptor.getValue()).orderId;

        sendCancel(cancelReq("CX-1", "CL-1"));

        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> cancelCaptor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api, org.mockito.Mockito.times(2)).submitCommandAsync(cancelCaptor.capture());
        ApiCancelOrder cancel = (ApiCancelOrder) cancelCaptor.getAllValues().get(1);
        assertEquals(placedOrderId, cancel.orderId);
        assertEquals(10001L, cancel.uid);
        assertEquals(600000, cancel.symbol);
    }

    @Test
    void unknownOrigClOrdIdShouldRejectWithoutSubmitting() {
        sendCancel(cancelReq("CX-1", "NOT-EXIST"));

        SseBinary out = readOutbound();
        assertEquals(SseBinary.BodyMessageFactory.MessageType.CANCEL_REJECT.getValue(), out.getMsgType());
        assertEquals(1, ((CancelReject) out.getBody()).getCxlRejReason());
        verify(api, never()).submitCommandAsync(any());
        channel.finishAndReleaseAll();
    }

    @Test
    void cancelSuccessShouldSendCanceledReportAndEvict() {
        placeOrder("CL-1");
        sendCancel(cancelReq("CX-1", "CL-1"));

        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> captor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api, org.mockito.Mockito.times(2)).submitCommandAsync(captor.capture());
        ApiCancelOrder cancelCmd = (ApiCancelOrder) captor.getAllValues().get(1);

        server.commandResult(new IEventsHandler.ApiCommandResult(cancelCmd, CommandResultCode.SUCCESS, 2L));

        SseBinary out = readOutbound();
        assertEquals(103, out.getMsgType());
        Report report = (Report) out.getBody();
        assertEquals(ExecType.CANCELED, report.getExecType());
        assertEquals(ExecType.CANCELED, report.getOrdStatus());
        assertEquals(0, report.getLeavesQty());
        assertNull(server.getCache().values().stream().findFirst().orElse(null));
        channel.finishAndReleaseAll();
    }

    @Test
    void cancelFailureShouldSendCancelRejectAndKeepOrder() {
        placeOrder("CL-1");
        sendCancel(cancelReq("CX-1", "CL-1"));

        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> captor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api, org.mockito.Mockito.times(2)).submitCommandAsync(captor.capture());
        ApiCancelOrder cancelCmd = (ApiCancelOrder) captor.getAllValues().get(1);

        server.commandResult(new IEventsHandler.ApiCommandResult(cancelCmd, CommandResultCode.MATCHING_UNKNOWN_ORDER_ID, 2L));

        SseBinary out = readOutbound();
        assertEquals(SseBinary.BodyMessageFactory.MessageType.CANCEL_REJECT.getValue(), out.getMsgType());
        CancelReject reject = (CancelReject) out.getBody();
        assertEquals("CX-1", reject.getClOrdId());
        assertEquals("CL-1", reject.getOrigClOrdId());
        //撤单失败后原订单保留，仍可继续成交或再次撤单
        assertEquals(1, server.getCache().size());
        channel.finishAndReleaseAll();
    }
}
