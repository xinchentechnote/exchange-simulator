package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.sse.bin.messages.Logon;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.Constant;
import com.xinchentechnote.exchange.simulator.common.HeartBtIntUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SseBinServerConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final int HEARTBEAT_TIMEOUT_TIMES = 3;

    private int heartbeatTimeoutCounter = 0;

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
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf decodeBuf = msg.duplicate();
        SseBinary sseBinary = new SseBinary();
        sseBinary.decode(decodeBuf);
        heartbeatTimeoutCounter = 0;
        log.debug("Received msg: {}", sseBinary);
        SseBinary.BodyMessageFactory.MessageType messageType = SseBinary.BodyMessageFactory.MessageType.fromValue(sseBinary.getMsgType());
        switch (messageType) {
            case HEARTBEAT:
                //心跳逻辑
                if (!isLogon(ctx)) {
                    return;
                }
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                //传递下游，心跳检测
                ctx.fireChannelRead(sseBinary);
                break;
            case LOGON: {
                //登陆逻辑
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                ChannelPipeline pipeline = ctx.channel().pipeline();
                Logon logon = (Logon) sseBinary.getBody();
                ctx.channel().attr(Constant.LOGON).set(true);
                // 按协商心跳间隔重建空闲检测；replace 对重复 Logon 也是安全的
                pipeline.replace(Constant.IDLE, Constant.IDLE,
                        new IdleStateHandler(HeartBtIntUtil.calculate(logon.getHeartBtInt()), 0, 0));
                break;
            }
            case LOGOUT:
                //登出逻辑
                if (!isLogon(ctx)) {
                    return;
                }
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                ctx.executor().schedule(() -> ctx.close(), 1, TimeUnit.SECONDS);
                break;
            default:
                if (!isLogon(ctx)) {
                    return;
                }
                ctx.fireChannelRead(sseBinary);
        }
    }

    private boolean isLogon(ChannelHandlerContext ctx) {
        Boolean logon = ctx.channel().attr(Constant.LOGON).get();
        if (logon == null || !logon) {
            log.debug("Received non-logon message before login, closing connection: {}", ctx.channel().remoteAddress());
            ctx.close();
            return false;
        }
        return true;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            heartbeatTimeoutCounter++;
            if (heartbeatTimeoutCounter >= HEARTBEAT_TIMEOUT_TIMES) {
                log.warn("Idle timeout, closing connection: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
