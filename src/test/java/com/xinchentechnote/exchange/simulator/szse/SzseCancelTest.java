package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.szse.bin.messages.CancelReject;
import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.OrderCancelRequest;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiCancelOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SZSE 撤单链路：OrderCancelRequest(190007) → ApiCancelOrder → 撤单确认 / CancelReject(290008)。
 */
class SzseCancelTest {

    private SzseBinServer server;
    private ExchangeApi api;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        server = new SzseBinServer(9011);
        api = mock(ExchangeApi.class);
        server.setApi(api);
        channel = new EmbeddedChannel(true, true);
    }

    private void placeOrder(String clOrdId) {
        NewOrder order = new NewOrder();
        order.setSecurityId("000001");
        order.setAccountId("20001");
        order.setClOrdId(clOrdId);
        order.setApplId("010");
        order.setSide("1");
        order.setPrice(1250L);
        order.setOrderQty(100L);
        SzseBinary msg = new SzseBinary();
        msg.setMsgType(100101);
        msg.setBody(order);
        server.onMessage(msg, channel);
    }

    private void sendCancel(String clOrdId, String origClOrdId) {
        OrderCancelRequest cancel = new OrderCancelRequest();
        cancel.setClOrdId(clOrdId);
        cancel.setOrigClOrdId(origClOrdId);
        cancel.setApplId("010");
        cancel.setSecurityId("000001");
        cancel.setSide("1");
        cancel.setTransactTime(20240501101530L);
        SzseBinary msg = new SzseBinary();
        msg.setMsgType(SzseMsgType.ORDER_CANCEL_REQUEST);
        msg.setBody(cancel);
        server.onMessage(msg, channel);
    }

    private SzseBinary readOutbound() {
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf, "expect outbound message");
        SzseBinary decoded = new SzseBinary();
        decoded.decode(buf.duplicate());
        return decoded;
    }

    @Test
    void cancelShouldSubmitApiCancelOrderWithOriginalContext() {
        placeOrder("CL-1");
        sendCancel("CX-1", "CL-1");

        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> captor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api, times(2)).submitCommandAsync(captor.capture());
        ApiCancelOrder cancel = (ApiCancelOrder) captor.getAllValues().get(1);
        exchange.core2.core.common.api.ApiPlaceOrder placed = (exchange.core2.core.common.api.ApiPlaceOrder) captor.getAllValues().get(0);
        assertEquals(placed.orderId, cancel.orderId);
        assertEquals(20001L, cancel.uid);
        assertEquals(1, cancel.symbol);
    }

    @Test
    void unknownOrigClOrdIdShouldRejectWithoutSubmitting() {
        sendCancel("CX-1", "NOT-EXIST");

        SzseBinary out = readOutbound();
        assertEquals(SzseMsgType.CANCEL_REJECT, out.getMsgType());
        assertEquals(SzseBinServer.REJECT_UNKNOWN_ORDER, ((CancelReject) out.getBody()).getRejectText());
        verify(api, never()).submitCommandAsync(any());
        channel.finishAndReleaseAll();
    }

    @Test
    void cancelSuccessShouldSendCanceledReportAndEvict() {
        placeOrder("CL-1");
        sendCancel("CX-1", "CL-1");

        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> captor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api, times(2)).submitCommandAsync(captor.capture());
        ApiCancelOrder cancelCmd = (ApiCancelOrder) captor.getAllValues().get(1);

        server.commandResult(new IEventsHandler.ApiCommandResult(cancelCmd, CommandResultCode.SUCCESS, 2L));

        SzseBinary out = readOutbound();
        assertEquals(SzseMsgType.EXECUTION_REPORT, out.getMsgType());
        ExecutionReport report = (ExecutionReport) out.getBody();
        assertEquals(ExecType.CANCELED, report.getExecType());
        assertEquals(ExecType.CANCELED, report.getOrdStatus());
        assertEquals(0, report.getLeavesQty());
        assertEquals(0, server.getCache().size());
        channel.finishAndReleaseAll();
    }

    @Test
    void cancelFailureShouldSendCancelRejectAndKeepOrder() {
        placeOrder("CL-1");
        sendCancel("CX-1", "CL-1");

        ArgumentCaptor<exchange.core2.core.common.api.ApiCommand> captor = ArgumentCaptor.forClass(exchange.core2.core.common.api.ApiCommand.class);
        verify(api, times(2)).submitCommandAsync(captor.capture());
        ApiCancelOrder cancelCmd = (ApiCancelOrder) captor.getAllValues().get(1);

        server.commandResult(new IEventsHandler.ApiCommandResult(cancelCmd, CommandResultCode.MATCHING_UNKNOWN_ORDER_ID, 2L));

        SzseBinary out = readOutbound();
        assertEquals(SzseMsgType.CANCEL_REJECT, out.getMsgType());
        assertEquals("CL-1", ((CancelReject) out.getBody()).getOrigClOrdId());
        assertEquals(1, server.getCache().size());
        channel.finishAndReleaseAll();
    }
}
