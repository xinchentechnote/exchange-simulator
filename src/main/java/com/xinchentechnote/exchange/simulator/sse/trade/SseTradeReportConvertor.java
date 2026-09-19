package com.xinchentechnote.exchange.simulator.sse.trade;

import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.convertor.trade.IReportConvertor;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import exchange.core2.core.IEventsHandler;

public class SseTradeReportConvertor implements IReportConvertor<IEventsHandler.Trade, Report> {

    @Override
    public Report convert(IEventsHandler.Trade trade, CommandWrapper requestWrapper) {
        SseBinary originMsg = (SseBinary) requestWrapper.getOriginMsg();
        Report report = new Report();
        if (originMsg.getBody() instanceof NewOrderSingle) {
            NewOrderSingle orderSingle = (NewOrderSingle) originMsg.getBody();
            report.setAccount(orderSingle.getAccount());
            report.setSecurityId(orderSingle.getSecurityId());
            report.setBizId(orderSingle.getBizId());
            report.setExecType(ExecType.TRADE);
            report.setPbu(orderSingle.getBizPbu());
            report.setBizPbu(orderSingle.getBizPbu());
            report.setClOrdId(orderSingle.getClOrdId());
            report.setBranchId(orderSingle.getBranchId());
            report.setSide(orderSingle.getSide());
            report.setUserInfo(orderSingle.getUserInfo());
            report.setTransactTime(orderSingle.getTransactTime());
            report.setTradeDate((int) (orderSingle.getTransactTime() / 1000_000));
            report.setOrderQty(orderSingle.getOrderQty());
            report.setOrdStatus(ExecType.NEW);
            report.setLastPx(trade.price);
            report.setLastQty(trade.volume);
            report.setCreditTag(orderSingle.getCreditTag());
        }
        return report;
    }
}
