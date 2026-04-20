package com.xinchentechnote.exchange.gateway.sse;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

public class SseBinServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SseBinServer sseBinServer;

    public SseBinServerInitializer(SseBinServer sseBinServer) {
        this.sseBinServer = sseBinServer;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        //字段名 类型 说明
        //MsgType uint32 消息类型
        //MsgSeqNum uint64 消息序号
        //MsgBodyLen uint32 消息体长度
        pipeline.addLast("frame",new LengthFieldBasedFrameDecoder(
                1024 * 1024, // 最大帧长度
                12,           // 长度字段偏移量（MsgType占4字节）
                4,           // 长度字段长度（MsgSeqNum占8字节）
                4,           // 长度调整 + 4byte checksum
                0           // 初始字节剥离
        )).addLast("idle",new IdleStateHandler(3,0,0));
        pipeline.addLast(new SseBinServerHandler(this.sseBinServer));
    }
}
