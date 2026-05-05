package com.xinchentechnote.exchange.simulator.szse.trade;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.sse.bin.messages.Report;
import com.finproto.sse.bin.messages.SseBinary;
import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.convertor.trade.IReportConvertor;
import com.xinchentechnote.exchange.simulator.sse.CommandWrapper;
import exchange.core2.core.IEventsHandler;

public class SzseTradeEventReportConvertor implements IReportConvertor<IEventsHandler.TradeEvent, ExecutionReport> {

    @Override
    public ExecutionReport convert(IEventsHandler.TradeEvent tradeEvent, CommandWrapper requestWrapper) {
        SzseBinary originMsg =(SzseBinary) requestWrapper.getOriginMsg();
        BinaryCodec body = originMsg.getBody();
        ExecutionReport report = new ExecutionReport();
        if (body instanceof NewOrder) {
            NewOrder orderSingle = (NewOrder) body;
            report.setAccountId(orderSingle.getAccountId());
            report.setSecurityId(orderSingle.getSecurityId());
            report.setApplId(orderSingle.getApplId());
            report.setExecType("F");
            report.setSubmittingPbuid(orderSingle.getSubmittingPbuid());
            report.setReportingPbuid(orderSingle.getSubmittingPbuid());
            report.setClOrdId(orderSingle.getClOrdId());
            report.setBranchId(orderSingle.getBranchId());
            report.setSide(orderSingle.getSide());
            report.setUserInfo(orderSingle.getUserInfo());
            report.setTransactTime(orderSingle.getTransactTime());
            report.setLeavesQty(orderSingle.getOrderQty());
            report.setOrdStatus("0");
            report.setLastPx(orderSingle.getPrice());
            report.setLastQty(tradeEvent.totalVolume);
        }
        return report;
    }
}
