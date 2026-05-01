package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.codec.BinaryCodec;
import com.finproto.szse.bin.messages.Heartbeat;
import com.finproto.szse.bin.messages.Logon;
import com.finproto.szse.bin.messages.Logout;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.sse.Constant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

@Log4j2
public class SzseBinServerConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.decode(msg);
        BinaryCodec body = szseBinary.getBody();
        if (body instanceof Logon){
            msg.retain().resetReaderIndex();
            ctx.writeAndFlush(msg);
            ChannelPipeline pipeline = ctx.channel().pipeline();
            Logon logon = (Logon) szseBinary.getBody();
            ctx.channel().attr(Constant.LOGON).set(true);
            int heartBtint = logon.getHeartBtint();
            pipeline.remove(Constant.IDLE);
            pipeline.addAfter(Constant.CONNECTION, Constant.IDLE, new IdleStateHandler(heartBtint,0,0));
        } else if (body instanceof Logout){
            if (!ctx.channel().attr(Constant.LOGON).get()) {
                log.debug("Received non-logon message before login, closing connection: {}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
             msg.retain().resetReaderIndex();
            ctx.writeAndFlush(msg);
            ctx.executor().schedule(() -> {
                ctx.close();
            }, 1, TimeUnit.SECONDS);
        } else if (body instanceof Heartbeat){
            msg.retain().resetReaderIndex();
            ctx.writeAndFlush(msg);
            ctx.fireChannelRead(szseBinary);
        } else {
            ctx.fireChannelRead(szseBinary);
        }
    }
}
