package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.Logon;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.Constant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基于真实 pipeline 的会话层测试：登录、登录前拦截、心跳回显、空闲超时。
 */
class SseBinServerPipelineTest {

    private EmbeddedChannel newChannel() {
        return new EmbeddedChannel(new SseBinServerInitializer(new SseBinServer(9010)));
    }

    private ByteBuf encode(SseBinary msg) {
        ByteBuf buf = Unpooled.buffer();
        msg.encode(buf);
        return buf;
    }

    private SseBinary logonMsg(int heartBtInt) {
        Logon logon = new Logon();
        logon.setSenderCompId("oms");
        logon.setTargetCompId("sim");
        logon.setHeartBtInt((short) heartBtInt);
        logon.setPrtclVersion("1.00");
        logon.setTradeDate(20240501);
        logon.setQsize(0);
        SseBinary msg = new SseBinary();
        msg.setMsgType(40);
        msg.setMsgSeqNum(1);
        msg.setBody(logon);
        return msg;
    }

    private SseBinary newOrderMsg() {
        NewOrderSingle order = new NewOrderSingle();
        order.setSecurityId("600000");
        order.setAccount("10001");
        order.setClOrdId("clOrd-1");
        order.setSide("1");
        order.setOrdType("1");
        order.setTimeInForce("1");
        order.setPrice(1050L);
        order.setOrderQty(200L);
        SseBinary msg = new SseBinary();
        msg.setMsgType(58);
        msg.setMsgSeqNum(2);
        msg.setBody(order);
        return msg;
    }

    @Test
    void logonShouldBeEchoedAndMarkSession() {
        EmbeddedChannel channel = newChannel();
        channel.writeInbound(encode(logonMsg(10)));

        ByteBuf echo = channel.readOutbound();
        assertNotNull(echo, "logon should be echoed");
        SseBinary decoded = new SseBinary();
        decoded.decode(echo.duplicate());
        assertEquals(40, decoded.getMsgType());

        assertEquals(Boolean.TRUE, channel.attr(Constant.LOGON).get());
        assertTrue(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    @Test
    void businessMessageBeforeLogonShouldCloseConnection() {
        EmbeddedChannel channel = newChannel();
        channel.writeInbound(encode(newOrderMsg()));
        assertFalse(channel.isOpen(), "connection must be closed before logon");
        channel.finishAndReleaseAll();
    }

    @Test
    void heartbeatAfterLogonShouldBeEchoed() {
        EmbeddedChannel channel = newChannel();
        channel.writeInbound(encode(logonMsg(10)));

        SseBinary heartbeat = new SseBinary();
        heartbeat.setMsgType(33);
        heartbeat.setMsgSeqNum(2);
        channel.writeInbound(encode(heartbeat));

        // logon echo + heartbeat echo
        assertNotNull(channel.readOutbound());
        ByteBuf hbEcho = channel.readOutbound();
        assertNotNull(hbEcho, "heartbeat should be echoed");
        SseBinary decoded = new SseBinary();
        decoded.decode(hbEcho.duplicate());
        assertEquals(33, decoded.getMsgType());
        channel.finishAndReleaseAll();
    }

    @Test
    void heartbeatBeforeLogonShouldCloseConnection() {
        EmbeddedChannel channel = newChannel();
        SseBinary heartbeat = new SseBinary();
        heartbeat.setMsgType(33);
        heartbeat.setMsgSeqNum(1);
        channel.writeInbound(encode(heartbeat));
        assertFalse(channel.isOpen(), "heartbeat before logon must close connection");
        channel.finishAndReleaseAll();
    }

    @Test
    void idleHandlerMustBeBeforeConnectionHandler() {
        // IdleStateEvent 只向 pipeline 下游传播，idleHandler 必须位于 connectionHandler 之前，
        // 否则空闲超时断连逻辑成为死代码（P0-2 回归防护）
        EmbeddedChannel channel = newChannel();
        java.util.List<String> names = channel.pipeline().names();
        assertTrue(names.indexOf(Constant.IDLE) < names.indexOf(Constant.CONNECTION),
                "pipeline order must be " + Constant.IDLE + " before " + Constant.CONNECTION + ", actual: " + names);
        channel.finishAndReleaseAll();
    }

    @Test
    void idleTimeoutShouldCloseConnection() {
        // EmbeddedChannel 虚拟时钟对 IdleStateHandler 排期任务不生效，
        // 此处直接注入 IdleStateEvent 验证连接层的三振断连逻辑
        EmbeddedChannel channel = newChannel();
        channel.writeInbound(encode(logonMsg(10)));
        channel.readOutbound();

        for (int i = 0; i < 3; i++) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        }
        assertFalse(channel.isOpen(), "idle timeout must close connection");
        channel.finishAndReleaseAll();
    }

    @Test
    void heartbeatShouldResetIdleCounter() {
        EmbeddedChannel channel = newChannel();
        channel.writeInbound(encode(logonMsg(10)));
        channel.readOutbound();

        // 每两次空闲之间穿插一次心跳（重置计数），不应断连
        for (int i = 0; i < 3; i++) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
            SseBinary heartbeat = new SseBinary();
            heartbeat.setMsgType(33);
            heartbeat.setMsgSeqNum(10 + i);
            channel.writeInbound(encode(heartbeat));
            channel.readOutbound(); // 心跳回显
            assertTrue(channel.isOpen());
        }
        // 连续三次空闲后断连
        for (int i = 0; i < 3; i++) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        }
        assertFalse(channel.isOpen(), "3 consecutive idle events must close connection");
        channel.finishAndReleaseAll();
    }
}
