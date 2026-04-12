package com.xinchentechnote.exchange.gateway.sse;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SseBinServer {
    private int port;


    public void start() {
        SseBinServerHandler handler = new SseBinServerHandler();
        ServerBootstrap bootstrap = new ServerBootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioEventLoopGroup workGroup = new NioEventLoopGroup(3);
        bootstrap.group(group, workGroup)
                .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
                .childHandler(new SseBinServerInitializer(handler));
        bootstrap.bind(port).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("SseBinServer started on port " + port);
            } else {
                System.err.println("Failed to start SseBinServer on port " + port);
                future.cause().printStackTrace();
            }
        });
    }
}
