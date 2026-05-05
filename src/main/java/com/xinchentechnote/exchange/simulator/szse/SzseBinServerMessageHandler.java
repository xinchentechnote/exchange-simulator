package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.szse.bin.messages.SzseBinary;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SzseBinServerMessageHandler extends SimpleChannelInboundHandler<SzseBinary> {
    private SzseBinServer szseBinServer;

    public SzseBinServerMessageHandler(SzseBinServer szseBinServer) {
        this.szseBinServer = szseBinServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SzseBinary szseBinary) throws Exception {
        log.debug("Received msg: {}", szseBinary);
        if (szseBinary.getMsgType() != 3) {
            szseBinServer.onMessage(szseBinary, ctx.channel());
        }
    }
}
