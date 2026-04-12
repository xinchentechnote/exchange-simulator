package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.sse.bin.messages.SseBinary;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class SseBinServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // 处理接收到的二进制数据
        SseBinary sseBinary = new SseBinary();
        sseBinary.decode(msg);
        System.out.println("Received msg: " + sseBinary);
    }
}
