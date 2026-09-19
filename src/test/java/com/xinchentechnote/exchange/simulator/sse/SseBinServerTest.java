package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.Confirm;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SSE 下行链路测试：委托确认、按会话独立的消息序号、拒绝后缓存清理、断连清理。
 */
class SseBinServerTest {

    private SseBinServer newServer() {
        return new SseBinServer(9010);
    }

    private CommandWrapper cacheOrder(SseBinServer server, long orderId, EmbeddedChannel channel) {
        NewOrderSingle order = new NewOrderSingle();
        order.setSecurityId("600000");
        order.setAccount("10001");
        order.setClOrdId("clOrd-" + orderId);
        order.setSide("1");
        order.setOrdType("1");
        order.setTimeInForce("1");
        order.setPrice(1050L);
        order.setOrderQty(200L);
        SseBinary msg = new SseBinary();
        msg.setMsgType(58);
        msg.setMsgSeqNum(1);
        msg.setBody(order);

        CommandWrapper wrapper = new CommandWrapper();
        wrapper.setUniqueId(orderId);
        wrapper.setChannel(channel);
        wrapper.setOriginMsg(msg);
        wrapper.setApiCommand(placeOrder(orderId));
        server.getCache().put(orderId, wrapper);
        return wrapper;
    }

    private ApiPlaceOrder placeOrder(long orderId) {
        return ApiPlaceOrder.builder().orderId(orderId).build();
    }

    private SseBinary readOutbound(EmbeddedChannel channel) {
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf, "expect outbound message");
        SseBinary decoded = new SseBinary();
        decoded.decode(buf.duplicate());
        return decoded;
    }

    @Test
    void successCommandResultShouldSendConfirm() {
        SseBinServer server = newServer();
        EmbeddedChannel channel = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channel);

        server.commandResult(new IEventsHandler.ApiCommandResult(placeOrder(1L), CommandResultCode.SUCCESS, 1L));

        SseBinary out = readOutbound(channel);
        assertEquals(32, out.getMsgType());
        assertEquals(1L, out.getMsgSeqNum());
        Confirm confirm = (Confirm) out.getBody();
        assertEquals(ExecType.NEW, confirm.getExecType());
        assertNotNull(server.getCache().get(1L));
        channel.finishAndReleaseAll();
    }

    @Test
    void msgSeqNumShouldBePerSession() {
        SseBinServer server = newServer();
        EmbeddedChannel channelA = new EmbeddedChannel(true, true);
        EmbeddedChannel channelB = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channelA);
        cacheOrder(server, 2L, channelB);

        server.commandResult(new IEventsHandler.ApiCommandResult(placeOrder(1L), CommandResultCode.SUCCESS, 1L));
        server.commandResult(new IEventsHandler.ApiCommandResult(placeOrder(2L), CommandResultCode.SUCCESS, 2L));

        assertEquals(1L, readOutbound(channelA).getMsgSeqNum());
        assertEquals(1L, readOutbound(channelB).getMsgSeqNum(), "seq must be per-session, not global");

        cacheOrder(server, 1L, channelA);
        server.commandResult(new IEventsHandler.ApiCommandResult(placeOrder(1L), CommandResultCode.SUCCESS, 3L));
        assertEquals(2L, readOutbound(channelA).getMsgSeqNum(), "seq must increase within session");
        channelA.finishAndReleaseAll();
        channelB.finishAndReleaseAll();
    }

    @Test
    void rejectedCommandResultShouldSendRejectAndEvictCache() {
        SseBinServer server = newServer();
        EmbeddedChannel channel = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channel);

        server.commandResult(new IEventsHandler.ApiCommandResult(placeOrder(1L), CommandResultCode.RISK_NSF, 1L));

        SseBinary out = readOutbound(channel);
        Confirm confirm = (Confirm) out.getBody();
        assertEquals(ExecType.REJECTED, confirm.getExecType());
        assertNull(server.getCache().get(1L), "rejected order should be removed from cache");
        channel.finishAndReleaseAll();
    }

    @Test
    void channelInactiveShouldEvictSessionOrders() {
        SseBinServer server = newServer();
        EmbeddedChannel channelA = new EmbeddedChannel(true, true);
        EmbeddedChannel channelB = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channelA);
        cacheOrder(server, 2L, channelA);
        cacheOrder(server, 3L, channelB);

        server.onChannelInactive(channelA);

        assertNull(server.getCache().get(1L));
        assertNull(server.getCache().get(2L));
        assertNotNull(server.getCache().get(3L), "orders of other sessions must survive");
        channelA.finishAndReleaseAll();
        channelB.finishAndReleaseAll();
    }
}
