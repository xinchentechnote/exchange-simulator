package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.sse.bin.messages.SseBinary;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SseBinServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final SseBinServer sseBinServer;

    public SseBinServerHandler(SseBinServer sseBinServer) {
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
        log.error("Exception in channel {}, {} " , ctx.channel().remoteAddress(), cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf decodeBuf = msg.duplicate();
        SseBinary sseBinary = new SseBinary();
        sseBinary.decode(decodeBuf);
        log.info("Received msg: {}" , sseBinary);
        switch (sseBinary.getMsgType()) {
            case 33:
                //心跳逻辑
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                break;
            case 40:
                //登陆逻辑
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                break;
            case 41:
                //登出逻辑
                msg.retain().resetReaderIndex();
                ctx.channel().writeAndFlush(msg);
                ctx.executor().schedule(() -> ctx.close(), 3, java.util.concurrent.TimeUnit.SECONDS);
                break;
            default:
                sseBinServer.onMessage(sseBinary, ctx.channel());
        }
    }
}
