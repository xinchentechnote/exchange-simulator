package com.xinchentechnote.exchange.simulator.szse;

import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
public class SzseBinServer implements IEventsHandler {
    private final int port;
    private ExchangeApi api;
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

    @Override
    public void commandResult(ApiCommandResult commandResult) {
        log.info("Received command result: {}", commandResult);
    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        log.info("Received trade event: {}", tradeEvent);
    }

    @Override
    public void rejectEvent(RejectEvent rejectEvent) {
        log.info("Received reject event: {}", rejectEvent);
    }

    @Override
    public void reduceEvent(ReduceEvent reduceEvent) {
        log.info("Received reduce event: {}", reduceEvent);
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        log.info("Received order book update: {}", orderBook);
    }
}
