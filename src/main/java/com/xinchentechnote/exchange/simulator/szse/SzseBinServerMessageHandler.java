package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.szse.bin.messages.SzseBinary;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SzseBinServerMessageHandler extends SimpleChannelInboundHandler<SzseBinary> {
    private final SzseBinServer szseBinServer;

    public SzseBinServerMessageHandler(SzseBinServer szseBinServer) {
        this.szseBinServer = szseBinServer;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Connection closed from " + ctx.channel().remoteAddress());
        //连接断开，清理该会话全部委托缓存，避免 Channel 引用泄漏
        szseBinServer.onChannelInactive(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SzseBinary szseBinary) throws Exception {
        log.debug("Received msg: {}", szseBinary);
        if (szseBinary.getMsgType() != SzseMsgType.HEARTBEAT) {
            szseBinServer.onMessage(szseBinary, ctx.channel());
        }
    }
}
