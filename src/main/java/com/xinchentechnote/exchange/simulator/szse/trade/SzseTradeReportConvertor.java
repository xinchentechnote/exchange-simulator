package com.xinchentechnote.exchange.simulator.szse.trade;

import com.finproto.szse.bin.messages.ExecutionReport;
import com.finproto.szse.bin.messages.Extend200115;
import com.finproto.szse.bin.messages.NewOrder;
import com.finproto.szse.bin.messages.SzseBinary;
import com.xinchentechnote.exchange.simulator.common.CommandWrapper;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.convertor.trade.IReportConvertor;
import exchange.core2.core.IEventsHandler;

public class SzseTradeReportConvertor implements IReportConvertor<IEventsHandler.Trade, ExecutionReport> {

    @Override
    public ExecutionReport convert(IEventsHandler.Trade trade, CommandWrapper requestWrapper) {
        SzseBinary originMsg = (SzseBinary) requestWrapper.getOriginMsg();
        ExecutionReport report = new ExecutionReport();
        if (originMsg.getBody() instanceof NewOrder) {
            NewOrder orderSingle = (NewOrder) originMsg.getBody();
            report.setAccountId(orderSingle.getAccountId());
            report.setSecurityId(orderSingle.getSecurityId());
            report.setApplId(orderSingle.getApplId());
            report.setExecType(ExecType.TRADE);
            report.setReportingPbuid(orderSingle.getSubmittingPbuid());
            report.setSubmittingPbuid(orderSingle.getSubmittingPbuid());
            report.setClOrdId(orderSingle.getClOrdId());
            report.setBranchId(orderSingle.getBranchId());
            report.setSide(orderSingle.getSide());
            report.setUserInfo(orderSingle.getUserInfo());
            report.setTransactTime(orderSingle.getTransactTime());
            report.setLeavesQty(orderSingle.getOrderQty());
            report.setOrdStatus(ExecType.NEW);
            report.setLastPx(trade.price);
            report.setLastQty(trade.volume);
            report.setApplExtend(new Extend200115());
        }
        return report;
    }
}
