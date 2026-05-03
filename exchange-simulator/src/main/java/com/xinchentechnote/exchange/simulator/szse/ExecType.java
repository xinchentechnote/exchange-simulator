package com.xinchentechnote.exchange.simulator.szse;

/**
 * 0=New，表示新订单
 * 4=Cancelled，表示已撤销
 * 8=Reject，表示已拒绝
 * F=Trade，表示已成交
 */
public class ExecType extends com.xinchentechnote.exchange.simulator.sse.ExecType {
    public static final String TRADE = "F";
}
