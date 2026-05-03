package com.xinchentechnote.exchange.simulator.sse.confirm;

import com.finproto.sse.bin.messages.Confirm;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.xinchentechnote.exchange.simulator.convertor.CommandResultConvertor;
import com.xinchentechnote.exchange.simulator.sse.ExecType;
import exchange.core2.core.IEventsHandler;

public class SseConfirmConvertor implements CommandResultConvertor<NewOrderSingle, Confirm> {
    @Override
    public Confirm convert(NewOrderSingle orderSingle, IEventsHandler.ApiCommandResult commandResult) {
        String execType;
        switch (commandResult.getResultCode()) {
            case SUCCESS:
                execType = ExecType.NEW;
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
        confirm.setExecType(execType);
        confirm.setUserInfo(orderSingle.getUserInfo());
        confirm.setTransactTime(orderSingle.getTransactTime());
        confirm.setTradeDate((int) (orderSingle.getTransactTime() / 1000_000));
        confirm.setClearingFirm(orderSingle.getClearingFirm());
        confirm.setCreditTag(orderSingle.getCreditTag());
        confirm.setOrdStatus(execType);
        confirm.setTimeInForce(orderSingle.getTimeInForce());
        confirm.setOrdType(orderSingle.getOrdType());
        confirm.setLeavesQty(orderSingle.getOrderQty());
        confirm.setOwnerType(orderSingle.getOwnerType());
        return confirm;
    }
}
