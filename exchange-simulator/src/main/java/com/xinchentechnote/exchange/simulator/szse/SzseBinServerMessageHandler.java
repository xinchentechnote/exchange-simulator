package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.szse.bin.messages.SzseBinary;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SzseBinServerMessageHandler extends SimpleChannelInboundHandler<SzseBinary> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SzseBinary msg) throws Exception {
            log.info("Received message: {}", msg);
    }
}
