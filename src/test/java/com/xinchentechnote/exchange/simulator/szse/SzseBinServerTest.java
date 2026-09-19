package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.api.ApiPlaceOrder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SZSE 下行链路测试：成交回报、cache 未命中防护、终态清理。
 */
class SzseBinServerTest {

    private SzseBinServer newServer() {
        return new SzseBinServer(9011);
    }

    private CommandWrapper cacheOrder(SzseBinServer server, long orderId, EmbeddedChannel channel) {
        NewOrder order = new NewOrder();
        order.setSecurityId("000001");
        order.setAccountId("20001");
        order.setClOrdId("clOrd-" + orderId);
        order.setSide("1");
        order.setApplId("010");
        order.setPrice(1250L);
        order.setOrderQty(100L);
        SzseBinary msg = new SzseBinary();
        msg.setMsgType(200101);
        msg.setBody(order);

        CommandWrapper wrapper = new CommandWrapper();
        wrapper.setUniqueId(orderId);
        wrapper.setChannel(channel);
        wrapper.setOriginMsg(msg);
        wrapper.setApiCommand(ApiPlaceOrder.builder().orderId(orderId).build());
        server.getCache().put(orderId, wrapper);
        return wrapper;
    }

    @Test
    void tradeEventShouldSendTakerAndMakerReports() {
        SzseBinServer server = newServer();
        EmbeddedChannel takerChannel = new EmbeddedChannel(true, true);
        EmbeddedChannel makerChannel = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, takerChannel);
        cacheOrder(server, 2L, makerChannel);

        IEventsHandler.Trade trade = new IEventsHandler.Trade(2L, 20002L, false, 1250L, 100L);
        IEventsHandler.TradeEvent event = new IEventsHandler.TradeEvent(
                1, 100L, 1L, 20001L, OrderAction.BID, false, System.currentTimeMillis(),
                Collections.singletonList(trade));
        server.tradeEvent(event);

        SseLikeDecoder takerOut = readOutbound(takerChannel);
        assertEquals(SzseMsgType.EXECUTION_REPORT, takerOut.msgType);
        assertEquals(ExecType.TRADE, ((ExecutionReport) takerOut.body).getExecType());

        SseLikeDecoder makerOut = readOutbound(makerChannel);
        assertEquals(SzseMsgType.EXECUTION_REPORT, makerOut.msgType);
        assertEquals(1250L, ((ExecutionReport) makerOut.body).getLastPx());

        assertNotNull(server.getCache().get(1L), "taker not completed, should stay in cache");
        takerChannel.finishAndReleaseAll();
        makerChannel.finishAndReleaseAll();
    }

    @Test
    void tradeEventWithUnknownOrdersShouldNotThrow() {
        SzseBinServer server = newServer();
        EmbeddedChannel channel = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channel);

        // taker=1 在缓存，maker=999 不在缓存：taker 回报正常下发，maker 跳过且不抛异常
        IEventsHandler.Trade trade = new IEventsHandler.Trade(999L, 99999L, false, 1250L, 100L);
        IEventsHandler.TradeEvent event = new IEventsHandler.TradeEvent(
                1, 100L, 1L, 20001L, OrderAction.BID, false, System.currentTimeMillis(),
                Collections.singletonList(trade));
        server.tradeEvent(event);

        assertNotNull(readOutbound(channel));
        assertNull(channel.readOutbound(), "maker report should be skipped silently");

        // taker 不在缓存 + maker 不在缓存：全部跳过，不抛异常
        IEventsHandler.TradeEvent unknownTakerEvent = new IEventsHandler.TradeEvent(
                888, 100L, 888L, 88888L, OrderAction.ASK, true, System.currentTimeMillis(),
                Collections.emptyList());
        server.tradeEvent(unknownTakerEvent);
        channel.finishAndReleaseAll();
    }

    @Test
    void completedTakerShouldBeEvicted() {
        SzseBinServer server = newServer();
        EmbeddedChannel channel = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channel);

        IEventsHandler.TradeEvent event = new IEventsHandler.TradeEvent(
                1, 100L, 1L, 20001L, OrderAction.BID, true, System.currentTimeMillis(),
                Collections.emptyList());
        server.tradeEvent(event);

        assertNotNull(readOutbound(channel));
        assertNull(server.getCache().get(1L), "completed taker should be removed from cache");
        channel.finishAndReleaseAll();
    }

    @Test
    void rejectedCommandResultShouldEvictCache() {
        SzseBinServer server = newServer();
        EmbeddedChannel channel = new EmbeddedChannel(true, true);
        cacheOrder(server, 1L, channel);

        server.commandResult(new IEventsHandler.ApiCommandResult(
                ApiPlaceOrder.builder().orderId(1L).build(), CommandResultCode.RISK_NSF, 1L));

        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf);
        SzseBinary out = new SzseBinary();
        out.decode(buf.duplicate());
        assertEquals(SzseMsgType.EXECUTION_CONFIRM, out.getMsgType());
        assertEquals(ExecType.REJECTED, ((com.finproto.szse.bin.messages.ExecutionConfirm) out.getBody()).getExecType());
        assertNull(server.getCache().get(1L));
        channel.finishAndReleaseAll();
    }

    private static class SseLikeDecoder {
        final int msgType;
        final Object body;

        SseLikeDecoder(ByteBuf buf) {
            SzseBinary binary = new SzseBinary();
            binary.decode(buf.duplicate());
            this.msgType = binary.getMsgType();
            this.body = binary.getBody();
        }
    }

    private SseLikeDecoder readOutbound(EmbeddedChannel channel) {
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf, "expect outbound message");
        return new SseLikeDecoder(buf);
    }
}
