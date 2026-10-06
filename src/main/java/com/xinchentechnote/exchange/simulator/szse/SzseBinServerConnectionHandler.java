package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.codec.BinaryCodec;
import com.finproto.szse.bin.messages.Heartbeat;
import com.finproto.szse.bin.messages.Logon;
import com.finproto.szse.bin.messages.Logout;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.common.AbstractBinConnectionHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * SZSE 会话层：登录/登出/心跳判定与解码在基类骨架下实现。
 */
public class SzseBinServerConnectionHandler extends AbstractBinConnectionHandler<SzseBinary> {

    @Override
    protected SzseBinary decodeMessage(ByteBuf msg) {
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.decode(msg);
        return szseBinary;
    }

    @Override
    protected boolean isLogonMessage(SzseBinary msg) {
        return msg.getBody() instanceof Logon;
    }

    @Override
    protected boolean isLogoutMessage(SzseBinary msg) {
        return msg.getBody() instanceof Logout;
    }

    @Override
    protected boolean isHeartbeatMessage(SzseBinary msg) {
        return msg.getBody() instanceof Heartbeat;
    }

    @Override
    protected int heartBtIntOf(SzseBinary msg) {
        return ((Logon) msg.getBody()).getHeartBtint();
    }

    @Override
    protected void forward(ChannelHandlerContext ctx, SzseBinary msg) {
        ctx.fireChannelRead(msg);
    }
}
