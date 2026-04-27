package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.Logon;
import com.finproto.sse.bin.messages.SseBinary;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.TimeUnit;

@Log4j2
public class SseBinServerConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

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
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf decodeBuf = msg.duplicate();
        SseBinary sseBinary = new SseBinary();
        sseBinary.decode(decodeBuf);
        log.debug("Received msg: {}", sseBinary);
        SseBinary.BodyMessageFactory.MessageType messageType = SseBinary.BodyMessageFactory.MessageType.fromValue(sseBinary.getMsgType());
        switch (messageType) {
            case HEARTBEAT:
                //心跳逻辑
                if (!ctx.channel().attr(Constant.LOGON).get()) {
                    log.debug("Received non-logon message before login, closing connection: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                //传递下游，心跳检测
                ctx.fireChannelRead(sseBinary);
                break;
            case LOGON:
                //登陆逻辑
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                ChannelPipeline pipeline = ctx.channel().pipeline();
                Logon logon = (Logon) sseBinary.getBody();
                //登录逻辑
                ctx.channel().attr(Constant.LOGON).set(true);
                short heartBtInt = logon.getHeartBtInt();
                pipeline.remove(Constant.IDLE);
                pipeline.addAfter(Constant.CONNECTION, Constant.IDLE, new IdleStateHandler(heartBtInt, 0, 0));
                break;
            case LOGOUT:
                //登出逻辑
                if (!ctx.channel().attr(Constant.LOGON).get()) {
                    log.debug("Received non-logon message before login, closing connection: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                ctx.executor().schedule(() -> ctx.close(), 3, TimeUnit.SECONDS);
                break;
            default:
                if (!ctx.channel().attr(Constant.LOGON).get()) {
                    log.debug("Received non-logon message before login, closing connection: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }
                ctx.fireChannelRead(sseBinary);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            //TODO 超时检测
            log.warn("Idle timeout, closing connection: {}", ctx.channel().remoteAddress());
        }
        super.userEventTriggered(ctx, evt);
    }
}
