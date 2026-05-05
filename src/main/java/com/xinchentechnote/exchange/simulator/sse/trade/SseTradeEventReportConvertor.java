package com.xinchentechnote.exchange.simulator.sse.trade;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.convertor.trade.IReportConvertor;
import com.xinchentechnote.exchange.simulator.sse.CommandWrapper;
import exchange.core2.core.IEventsHandler;

public class SseTradeEventReportConvertor implements IReportConvertor<IEventsHandler.TradeEvent, Report> {

    @Override
    public Report convert(IEventsHandler.TradeEvent tradeEvent, CommandWrapper requestWrapper) {
        SseBinary originMsg =(SseBinary) requestWrapper.getOriginMsg();
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
            report.setUserInfo(orderSingle.getUserInfo());
            report.setTransactTime(orderSingle.getTransactTime());
            report.setTradeDate((int) (orderSingle.getTransactTime()/1000_000));
            report.setOrderQty(orderSingle.getOrderQty());
            report.setOrdStatus("0");
            report.setLastPx(orderSingle.getPrice());
            report.setLastQty(tradeEvent.totalVolume);
            report.setCreditTag(orderSingle.getCreditTag());
        }
        return report;
    }
}
