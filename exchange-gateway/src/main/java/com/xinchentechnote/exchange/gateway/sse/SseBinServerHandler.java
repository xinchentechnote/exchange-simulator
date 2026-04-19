package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.sse.bin.messages.SseBinary;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.sql.SQLOutput;

public class SseBinServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final SseBinServer sseBinServer;

    public SseBinServerHandler(SseBinServer sseBinServer) {
        this.sseBinServer = sseBinServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("New connection from " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Connection closed from " + ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        SseBinary sseBinary = new SseBinary();
        sseBinary.decode(msg);
        System.out.println("Received msg: " + sseBinary);
        sseBinServer.onMessage(sseBinary,ctx.channel());
    }
}
