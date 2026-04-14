package com.xinchentechnote.exchange.gateway.sse.trade;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.gateway.sse.CommandWrapper;
import exchange.core2.core.IEventsHandler;

public class SseTradeEventReportConvertor implements IReportConvertor<IEventsHandler.TradeEvent, Report> {

    @Override
    public Report convert(IEventsHandler.TradeEvent tradeEvent, CommandWrapper requestWrapper) {
        SseBinary originMsg = requestWrapper.getOriginMsg();
        BinaryCodec body = originMsg.getBody();
        Report report = new Report();
        if (body instanceof NewOrderSingle) {
            NewOrderSingle orderSingle = (NewOrderSingle) body;
            report.setAccount(orderSingle.getAccount());
        }
        return report;
    }
}
