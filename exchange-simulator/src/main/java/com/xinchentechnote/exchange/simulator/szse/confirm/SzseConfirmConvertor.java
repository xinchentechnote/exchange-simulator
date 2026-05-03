package com.xinchentechnote.exchange.simulator.szse.confirm;

import com.xinchentechnote.exchange.simulator.szse.ExecType;
import com.finproto.szse.bin.messages.ExecutionConfirm;
import com.finproto.szse.bin.messages.NewOrder;
import com.xinchentechnote.exchange.simulator.convertor.CommandResultConvertor;
import exchange.core2.core.IEventsHandler;

public class SzseConfirmConvertor implements CommandResultConvertor<NewOrder, ExecutionConfirm> {
    @Override
   public ExecutionConfirm convert(NewOrder newOrder, IEventsHandler.ApiCommandResult commandResult){
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
       confirm.setExecType(ExecType.NEW);
       confirm.setUserInfo(newOrder.getUserInfo());
       confirm.setTransactTime(newOrder.getTransactTime());
       confirm.setClearingFirm(newOrder.getClearingFirm());
       confirm.setOrdStatus(ExecType.NEW);
       confirm.setOrdType(newOrder.getOrdType());
       confirm.setLeavesQty(newOrder.getOrderQty());
       confirm.setOwnerType(newOrder.getOwnerType());
       confirm.setApplId(newOrder.getApplId());
       return confirm;
    }
}
