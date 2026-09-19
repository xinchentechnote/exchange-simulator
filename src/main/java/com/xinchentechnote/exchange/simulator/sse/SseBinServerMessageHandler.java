package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.SseBinary;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SseBinServerMessageHandler extends SimpleChannelInboundHandler<SseBinary> {

    private final SseBinServer sseBinServer;

    public SseBinServerMessageHandler(SseBinServer sseBinServer) {
        this.sseBinServer = sseBinServer;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Connection closed from " + ctx.channel().remoteAddress());
        //连接断开，清理该会话全部委托缓存，避免 Channel 引用泄漏
        sseBinServer.onChannelInactive(ctx.channel());
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
