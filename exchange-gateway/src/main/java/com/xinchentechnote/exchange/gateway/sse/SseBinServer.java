package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.gateway.sse.cmd.ApiCommandConvertorContext;
import com.xinchentechnote.exchange.gateway.sse.cmd.IApiCommandConverter;
import com.xinchentechnote.exchange.gateway.sse.trade.SseTradeEventReportConvertor;
import com.xinchentechnote.exchange.gateway.sse.trade.SseTradeReportConvertor;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPlaceOrder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Data;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SseBinServer implements IEventsHandler {

    private ExchangeApi api;
    private int port;

    private Map<Long, CommandWrapper> cache = new ConcurrentHashMap<>();

    public SseBinServer(int port) {
        this.port = port;
    }

    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioEventLoopGroup workGroup = new NioEventLoopGroup(3);
        bootstrap.group(group, workGroup)
                .channel(io.netty.channel.socket.nio.NioServerSocketChannel.class)
                .childHandler(new SseBinServerInitializer(this));
        bootstrap.bind(port).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("SseBinServer started on port " + port);
            } else {
                System.err.println("Failed to start SseBinServer on port " + port);
                future.cause().printStackTrace();
            }
        });
    }

    public void onMessage(SseBinary msg, Channel channel) {
        // 处理接收到的消息
        System.out.println("Received msg: " + msg);
        BinaryCodec body = msg.getBody();
        IApiCommandConverter converter = ApiCommandConvertorContext.getInstance().get(body);
        ApiCommand apiCommand = converter.convertNewOrder(body);
        if (apiCommand instanceof ApiPlaceOrder){
            CommandWrapper commandWrapper = new CommandWrapper();
            commandWrapper.setUniqueId(((ApiPlaceOrder) apiCommand).orderId);
            commandWrapper.setApiCommand(apiCommand);
            commandWrapper.setOriginMsg(msg);
            commandWrapper.setChannel(channel);
            cache.put(commandWrapper.getUniqueId(), commandWrapper);
        }
        api.submitCommandAsync(apiCommand);
    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        System.out.println("Trade event: " + tradeEvent);
        long takerOrderId = tradeEvent.getTakerOrderId();
        CommandWrapper commandWrapper = cache.get(takerOrderId);
        SseTradeEventReportConvertor reportConvertor = new SseTradeEventReportConvertor();
        Report report = reportConvertor.convert(tradeEvent, commandWrapper);
        Channel channel = commandWrapper.getChannel();
        sendReport(channel, report);

        List<Trade> trades = tradeEvent.getTrades();
        SseTradeReportConvertor sseTradeReportConvertor = new SseTradeReportConvertor();
        if (!CollectionUtils.isEmpty(trades)) {
            for (Trade trade : trades) {
                CommandWrapper origin = cache.get(trade.getMakerOrderId());
                Report rep = sseTradeReportConvertor.convert(trade, origin);
                sendReport(origin.getChannel(),rep);
            }
        }

    }

    private void sendReport(Channel channel, Report report) {
        SseBinary sseBinary = new SseBinary();
        sseBinary.setMsgType(103);
        sseBinary.setBody(report);

        ByteBuf buf = Unpooled.buffer();
        report.encode(buf);
        channel.write(buf);
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
