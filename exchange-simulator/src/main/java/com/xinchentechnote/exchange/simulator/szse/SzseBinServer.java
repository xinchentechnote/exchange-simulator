package com.xinchentechnote.exchange.simulator.szse;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SzseBinServer {
    private final int port;

    public SzseBinServer(int port) {
        this.port = port;
    }


    public void start(){
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup group = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(2);
        bootstrap.group(group,workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new SzseBinServerInitializer());
        bootstrap.bind(port).addListener(future -> {
            if (future.isSuccess()) {
                log.info("SzseBinServer started on port :{}" , port);
            } else {
                log.error("Failed to start SzseBinServer on port :{},{}" , port,future.cause());
            }
        });
    }

}
