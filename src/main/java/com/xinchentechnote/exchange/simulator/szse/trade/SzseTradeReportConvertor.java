package com.xinchentechnote.exchange.simulator.szse.trade;

import com.finproto.codec.BinaryCodec;
import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.convertor.trade.IReportConvertor;
import com.xinchentechnote.exchange.simulator.sse.CommandWrapper;
import exchange.core2.core.IEventsHandler;

public class SzseTradeReportConvertor implements IReportConvertor<IEventsHandler.Trade, ExecutionReport> {

    @Override
    public ExecutionReport convert(IEventsHandler.Trade trade, CommandWrapper requestWrapper) {
        SzseBinary originMsg = (SzseBinary) requestWrapper.getOriginMsg();
        BinaryCodec body = originMsg.getBody();
        ExecutionReport report = new ExecutionReport();
        if (body instanceof NewOrder) {
            NewOrder orderSingle = (NewOrder) body;
            report.setAccountId(orderSingle.getAccountId());
            report.setSecurityId(orderSingle.getSecurityId());
            report.setApplId(orderSingle.getApplId());
            report.setExecType("F");
            report.setReportingPbuid(orderSingle.getSubmittingPbuid());
            report.setSubmittingPbuid(orderSingle.getSubmittingPbuid());
            report.setClOrdId(orderSingle.getClOrdId());
            report.setBranchId(orderSingle.getBranchId());
            report.setSide(orderSingle.getSide());
            report.setUserInfo(orderSingle.getUserInfo());
            report.setTransactTime(orderSingle.getTransactTime());
            report.setLeavesQty(orderSingle.getOrderQty());
            report.setOrdStatus("0");
            report.setLastPx(trade.price);
            report.setLastQty(trade.volume);
        }
        return report;
    }
}
