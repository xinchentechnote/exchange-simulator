package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.szse.bin.messages.SzseBinary;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class SzseBinServerConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.decode(msg);
        ctx.fireChannelRead(szseBinary);
    }
}
