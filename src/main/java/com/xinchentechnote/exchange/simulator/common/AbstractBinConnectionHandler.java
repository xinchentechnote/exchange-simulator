package com.xinchentechnote.exchange.simulator.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * SSE/SZSE 会话层公共骨架（模板方法）：登录校验、登录后按协商间隔重建空闲检测、
 * 心跳/登出回显、读空闲三振断连在此实现；协议差异（解码、消息类型判定、心跳间隔提取、
 * 下游转发）由子类实现。
 *
 * @param <M> 协议顶层消息类型（SseBinary / SzseBinary）
 */
@Slf4j
public abstract class AbstractBinConnectionHandler<M> extends SimpleChannelInboundHandler<ByteBuf> {

    private static final int HEARTBEAT_TIMEOUT_TIMES = 3;

    private int heartbeatTimeoutCounter = 0;

    /** 从原始帧解码协议消息（不得消费读索引，回显仍使用原始 ByteBuf）。 */
    protected abstract M decodeMessage(ByteBuf msg);

    protected abstract boolean isLogonMessage(M msg);

    protected abstract boolean isLogoutMessage(M msg);

    protected abstract boolean isHeartbeatMessage(M msg);

    /** 登录报文中协商的心跳间隔（秒）。 */
    protected abstract int heartBtIntOf(M msg);

    /** 向下游转发业务/心跳消息。 */
    protected abstract void forward(ChannelHandlerContext ctx, M msg);

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
        M decoded = decodeMessage(msg);
        heartbeatTimeoutCounter = 0;
        log.debug("Received msg: {}", decoded);
        if (isLogonMessage(decoded)) {
            //登陆逻辑：回显登录报文，标记会话，按协商心跳间隔重建空闲检测（重复 Logon 安全）
            echoRaw(ctx, msg);
            ctx.channel().attr(Constant.LOGON).set(true);
            ctx.channel().pipeline().replace(Constant.IDLE, Constant.IDLE,
                    new IdleStateHandler(HeartBtIntUtil.calculate(heartBtIntOf(decoded)), 0, 0));
        } else if (isLogoutMessage(decoded)) {
            if (!requireLogon(ctx)) {
                return;
            }
            echoRaw(ctx, msg);
            ctx.executor().schedule((Runnable) ctx::close, 1, TimeUnit.SECONDS);
        } else {
            if (!requireLogon(ctx)) {
                return;
            }
            if (isHeartbeatMessage(decoded)) {
                echoRaw(ctx, msg);
            }
            forward(ctx, decoded);
        }
    }

    private void echoRaw(ChannelHandlerContext ctx, ByteBuf msg) {
        msg.retain().resetReaderIndex();
        ctx.channel().writeAndFlush(msg);
    }

    private boolean requireLogon(ChannelHandlerContext ctx) {
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
