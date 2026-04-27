package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.SseBinary;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SseBinServerMessageHandler extends SimpleChannelInboundHandler<SseBinary> {

    private final SseBinServer sseBinServer;

    public SseBinServerMessageHandler(SseBinServer sseBinServer) {
        this.sseBinServer = sseBinServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("New connection from " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Connection closed from " + ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in channel {}, {} ", ctx.channel().remoteAddress(), cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SseBinary sseBinary) throws Exception {
        log.debug("Received msg: {}", sseBinary);
        if (sseBinary.getMsgType() != SseBinary.BodyMessageFactory.MessageType.HEARTBEAT.getValue()) {
            sseBinServer.onMessage(sseBinary, ctx.channel());
        }
    }


}
