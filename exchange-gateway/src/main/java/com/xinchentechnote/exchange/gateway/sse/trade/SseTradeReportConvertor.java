package com.xinchentechnote.exchange.gateway.sse.trade;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.gateway.sse.CommandWrapper;
import exchange.core2.core.IEventsHandler;

public class SseTradeReportConvertor implements IReportConvertor<IEventsHandler.Trade, Report> {

    @Override
    public Report convert(IEventsHandler.Trade trade, CommandWrapper requestWrapper) {
        SseBinary originMsg = requestWrapper.getOriginMsg();
        BinaryCodec body = originMsg.getBody();
        Report report = new Report();
        if (body instanceof NewOrderSingle) {
            NewOrderSingle orderSingle = (NewOrderSingle) body;
            report.setAccount(orderSingle.getAccount());
            report.setSecurityId(orderSingle.getSecurityId());
            report.setBizId(orderSingle.getBizId());
            report.setExecType("F");
            report.setPbu(orderSingle.getBizPbu());
            report.setBizPbu(orderSingle.getBizPbu());
            report.setClOrdId(orderSingle.getClOrdId());
            report.setBranchId(orderSingle.getBranchId());
            report.setSide(orderSingle.getSide());
        }
        return report;
    }
}
