package com.xinchentechnote.exchange.simulator.szse;

import com.xinchentechnote.exchange.simulator.sse.Constant;
import com.xinchentechnote.exchange.simulator.utils.NettyLoggingUtil;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

class SzseBinServerInitializer extends ChannelInitializer<SocketChannel> {
    private SzseBinServer szseBinServer;

    public SzseBinServerInitializer(SzseBinServer szseBinServer) {
        this.szseBinServer = szseBinServer;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (NettyLoggingUtil.isLoggingEnabled()) {
            pipeline.addLast(NettyLoggingUtil.getLoggingHandlerName(), new LoggingHandler(NettyLoggingUtil.getLoggingLevel()));
        }
        //MsgType uint32 消息类型
        //MsgBodyLen uint32 消息体长度
        pipeline.addLast(Constant.FRAME, new LengthFieldBasedFrameDecoder(1024 * 1024, // 最大帧长度
                4,           // 长度字段偏移量（MsgType占4字节）
                4,           // 长度字段长度（MsgBodyLen占4字节）
                4,           // 长度调整 + 4byte checksum
                0           // 初始字节剥离
        ));
        pipeline.addLast(Constant.CONNECTION, new SzseBinServerConnectionHandler());//登录、登出、心跳处理
        pipeline.addLast(Constant.IDLE, new IdleStateHandler(3,0,0));
        pipeline.addLast(Constant.MESSAGE, new SzseBinServerMessageHandler(this.szseBinServer));//业务处理
    }
}
