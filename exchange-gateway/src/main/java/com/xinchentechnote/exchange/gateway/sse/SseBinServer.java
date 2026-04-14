package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.SseBinary;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SseBinServer implements IEventsHandler {

    private ExchangeApi api;
    private int port;

    private Map<Long,CommandWrapper> cache = new ConcurrentHashMap<>();

    public SseBinServer(int port) {
        this.port = port;
    }

    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioEventLoopGroup workGroup = new NioEventLoopGroup(3);
        bootstrap.group(group, workGroup)
                .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
                .childHandler(new SseBinServerInitializer());
        bootstrap.bind(port).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("SseBinServer started on port " + port);
            } else {
                System.err.println("Failed to start SseBinServer on port " + port);
                future.cause().printStackTrace();
            }
        });
    }

    public void onMessage(SseBinary msg) {
        // 处理接收到的消息
        System.out.println("Received msg: " + msg);
        BinaryCodec body = msg.getBody();

    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        System.out.println("Trade event: " + tradeEvent);
    }

    @Override
    public void reduceEvent(ReduceEvent reduceEvent) {
        System.out.println("Reduce event: " + reduceEvent);
    }

    @Override
    public void rejectEvent(RejectEvent rejectEvent) {
        System.out.println("Reject event: " + rejectEvent);
    }

    @Override
    public void commandResult(ApiCommandResult commandResult) {
        System.out.println("Command result: " + commandResult);
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        System.out.println("OrderBook event: " + orderBook);
    }
}
