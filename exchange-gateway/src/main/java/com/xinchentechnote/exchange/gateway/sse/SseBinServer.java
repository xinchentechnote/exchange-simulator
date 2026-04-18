package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.Confirm;
import com.finproto.sse.bin.messages.NewOrderSingle;
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
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
                .channel(NioServerSocketChannel.class)
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
        sseBinary.encode(buf);
        channel.writeAndFlush(buf);
    }

    @Override
    public void reduceEvent(ReduceEvent reduceEvent) {
        //暂不处理
        System.out.println("Reduce event: " + reduceEvent);
    }

    @Override
    public void rejectEvent(RejectEvent rejectEvent) {
        //拒绝，委托拒绝和撤单拒绝
        System.out.println("Reject event: " + rejectEvent);
    }

    @Override
    public void commandResult(ApiCommandResult commandResult) {
        //委托确认
        System.out.println("Command result: " + commandResult);
        ApiCommand command = commandResult.getCommand();
        if (command instanceof ApiPlaceOrder) {
            ApiPlaceOrder apiPlaceOrder = (ApiPlaceOrder) command;
            long orderId = apiPlaceOrder.orderId;
            CommandWrapper commandWrapper = cache.get(orderId);
            if (commandWrapper != null) {
                switch (commandResult.getResultCode()) {
                    case SUCCESS:
                        SseBinary originMsg = commandWrapper.getOriginMsg();
                        NewOrderSingle orderSingle = (NewOrderSingle) originMsg.getBody();
                        Confirm confirm = new Confirm();
                        confirm.setAccount(orderSingle.getAccount());
                        confirm.setSecurityId(orderSingle.getSecurityId());
                        confirm.setClOrdId(orderSingle.getClOrdId());
                        confirm.setBizId(orderSingle.getBizId());
                        confirm.setBranchId(orderSingle.getBranchId());
                        confirm.setBizPbu(orderSingle.getBizPbu());
                        confirm.setPbu(orderSingle.getBizPbu());
                        confirm.setSide(orderSingle.getSide());
                        confirm.setOrderQty(orderSingle.getOrderQty());
                        confirm.setPrice(orderSingle.getPrice());
                        confirm.setExecType("0");
                        confirm.setUserInfo(orderSingle.getUserInfo());
                        confirm.setTransactTime(orderSingle.getTransactTime());
                        confirm.setTradeDate((int) (orderSingle.getTransactTime()/1000_000));
                        confirm.setClearingFirm(orderSingle.getClearingFirm());
                        confirm.setCreditTag(orderSingle.getCreditTag());
                        confirm.setOrdStatus("0");
                        confirm.setTimeInForce(orderSingle.getTimeInForce());
                        confirm.setOrdType(orderSingle.getOrdType());
                        confirm.setLeavesQty(orderSingle.getOrderQty());
                        confirm.setOwnerType(orderSingle.getOwnerType());
                        sendConfirm(commandWrapper.getChannel(), confirm);
                        break;
                    default:
                        // 其他结果码暂不处理
                }
            }
        }
    }

    private void sendConfirm(Channel channel, Confirm confirm) {
        SseBinary sseBinary = new SseBinary();
        sseBinary.setMsgType(32);
        sseBinary.setBody(confirm);
        ByteBuf buf = Unpooled.buffer();
        sseBinary.encode(buf);
        channel.writeAndFlush(buf);
    }

    @Override
    public void orderBook(OrderBook orderBook) {
        //行情发布
        System.out.println("OrderBook event: " + orderBook);
    }
}
