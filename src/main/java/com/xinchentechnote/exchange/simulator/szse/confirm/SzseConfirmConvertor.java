package com.xinchentechnote.exchange.simulator.szse.confirm;

import com.finproto.szse.bin.messages.ExecutionConfirm;
import com.finproto.szse.bin.messages.NewOrder;
import com.xinchentechnote.exchange.simulator.convertor.CommandResultConvertor;
import com.xinchentechnote.exchange.simulator.sse.ExecType;
import exchange.core2.core.IEventsHandler;

public class SzseConfirmConvertor implements CommandResultConvertor<NewOrder, ExecutionConfirm> {
    @Override
    public ExecutionConfirm convert(NewOrder newOrder, IEventsHandler.ApiCommandResult commandResult) {
        String execType;
        switch (commandResult.getResultCode()) {
            case SUCCESS:
                execType = com.xinchentechnote.exchange.simulator.sse.ExecType.NEW;
                break;
            case RISK_NSF:
            case RISK_MARGIN_TRADING_DISABLED:
            case RISK_INVALID_RESERVE_BID_PRICE:
            case RISK_ASK_PRICE_LOWER_THAN_FEE:
            case USER_MGMT_USER_NOT_FOUND:
                execType = ExecType.REJECTED;
                break;
            default:
                return null;
        }
        ExecutionConfirm confirm = new ExecutionConfirm();
        confirm.setAccountId(newOrder.getAccountId());
        confirm.setSecurityId(newOrder.getSecurityId());
        confirm.setClOrdId(newOrder.getClOrdId());
        confirm.setBranchId(newOrder.getBranchId());
        confirm.setSubmittingPbuid(newOrder.getSubmittingPbuid());
        confirm.setReportingPbuid(newOrder.getSubmittingPbuid());
        confirm.setSide(newOrder.getSide());
        confirm.setOrderQty(newOrder.getOrderQty());
        confirm.setPrice(newOrder.getPrice());
        confirm.setExecType(execType);
        confirm.setUserInfo(newOrder.getUserInfo());
        confirm.setTransactTime(newOrder.getTransactTime());
        confirm.setClearingFirm(newOrder.getClearingFirm());
        confirm.setOrdStatus(execType);
        confirm.setOrdType(newOrder.getOrdType());
        confirm.setLeavesQty(newOrder.getOrderQty());
        confirm.setOwnerType(newOrder.getOwnerType());
        confirm.setApplId(newOrder.getApplId());
        return confirm;
    }
}
