package com.xinchentechnote.exchange.simulator.szse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.Extend200115;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.convertor.cmd.ApiCommandConvertorContext;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import com.xinchentechnote.exchange.simulator.sse.CommandWrapper;
import com.xinchentechnote.exchange.simulator.sse.trade.SseTradeEventReportConvertor;
import com.xinchentechnote.exchange.simulator.sse.trade.SseTradeReportConvertor;
import com.xinchentechnote.exchange.simulator.szse.trade.SzseTradeEventReportConvertor;
import com.xinchentechnote.exchange.simulator.szse.trade.SzseTradeReportConvertor;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPlaceOrder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Data
public class SzseBinServer implements IEventsHandler {
    private final int port;
    private ExchangeApi api;

    private Map<Long, CommandWrapper> cache = new ConcurrentHashMap<>();

    public SzseBinServer(int port) {
        this.port = port;
    }


    public void start(){
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup group = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(2);
        bootstrap.group(group,workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new SzseBinServerInitializer(this));
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
        //TODO confirm
    }

    @Override
    public void tradeEvent(TradeEvent tradeEvent) {
        log.info("Trade event: {}" , tradeEvent);
        long takerOrderId = tradeEvent.getTakerOrderId();
        CommandWrapper commandWrapper = cache.get(takerOrderId);
        SzseTradeEventReportConvertor reportConvertor = new SzseTradeEventReportConvertor();
        ExecutionReport report = reportConvertor.convert(tradeEvent, commandWrapper);
        Channel channel = commandWrapper.getChannel();
        sendReport(channel, report);

        List<Trade> trades = tradeEvent.getTrades();
        SzseTradeReportConvertor sseTradeReportConvertor = new SzseTradeReportConvertor();
        if (!CollectionUtils.isEmpty(trades)) {
            for (Trade trade : trades) {
                CommandWrapper origin = cache.get(trade.getMakerOrderId());
                ExecutionReport rep = sseTradeReportConvertor.convert(trade, origin);
                sendReport(origin.getChannel(), rep);
            }
        }
    }

    private void sendReport(Channel channel, ExecutionReport report) {
        if (!channel.isActive()){
            log.warn("Channel is not active, cannot send report: {}" , report);
            return;
        }
        report.setApplExtend(new Extend200115());
        SzseBinary szseBinary = new SzseBinary();
        szseBinary.setMsgType(200115);
        szseBinary.setBody(report);
        log.info("Sending report: {}" , report);
        ByteBuf buf = Unpooled.buffer();
        szseBinary.encode(buf);
        channel.writeAndFlush(buf);
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

    public void onMessage(SzseBinary msg, Channel channel) {
        // 处理接收到的消息
        BinaryCodec body = msg.getBody();
        IApiCommandConverter converter = ApiCommandConvertorContext.getInstance().get(body);
        if (converter == null) {
            log.error("No converter found for msg type: {}" , msg.getMsgType());
            return;
        }
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
}
