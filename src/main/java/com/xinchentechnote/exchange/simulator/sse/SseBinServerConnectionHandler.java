package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.AbstractBinConnectionHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * SSE 会话层：登录/登出/心跳判定与解码在基类骨架下实现。
 */
public class SseBinServerConnectionHandler extends AbstractBinConnectionHandler<SseBinary> {

    @Override
    protected SseBinary decodeMessage(ByteBuf msg) {
        SseBinary sseBinary = new SseBinary();
        sseBinary.decode(msg.duplicate());
        return sseBinary;
    }

    @Override
    protected boolean isLogonMessage(SseBinary msg) {
        return msg.getMsgType() == SseBinary.BodyMessageFactory.MessageType.LOGON.getValue();
    }

    @Override
    protected boolean isLogoutMessage(SseBinary msg) {
        return msg.getMsgType() == SseBinary.BodyMessageFactory.MessageType.LOGOUT.getValue();
    }

    @Override
    protected boolean isHeartbeatMessage(SseBinary msg) {
        return msg.getMsgType() == SseBinary.BodyMessageFactory.MessageType.HEARTBEAT.getValue();
    }

    @Override
    protected int heartBtIntOf(SseBinary msg) {
        return ((com.finproto.sse.bin.messages.Logon) msg.getBody()).getHeartBtInt();
    }

    @Override
    protected void forward(ChannelHandlerContext ctx, SseBinary msg) {
        //心跳与业务报文均向下游转发：心跳用于空闲计数重置，业务报文进入处理
        ctx.fireChannelRead(msg);
    }
}
