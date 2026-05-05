package com.xinchentechnote.exchange.simulator.sse;

import com.xinchentechnote.exchange.simulator.utils.NettyLoggingUtil;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LoggingHandler;
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
        if (NettyLoggingUtil.isLoggingEnabled()) {
            pipeline.addLast(NettyLoggingUtil.getLoggingHandlerName(), new LoggingHandler(NettyLoggingUtil.getLoggingLevel()));
        }
        pipeline.addLast(Constant.FRAME, new LengthFieldBasedFrameDecoder(1024 * 1024, // 最大帧长度
                12,           // 长度字段偏移量（MsgType占4字节+MsgSeqNum占8字节）
                4,           // 长度字段长度（MsgBodyLen占4字节）
                4,           // 长度调整 + 4byte checksum
                0           // 初始字节剥离
        ));
        pipeline.addLast(Constant.CONNECTION, new SseBinServerConnectionHandler());//登录、登出、心跳处理
        pipeline.addLast(Constant.IDLE, new IdleStateHandler(3, 0, 0));
        pipeline.addLast(Constant.MESSAGE, new SseBinServerMessageHandler(this.sseBinServer));//业务处理
    }
}
