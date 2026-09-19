package com.xinchentechnote.exchange.simulator.common;

/**
 * SSE/SZSE 共用的 ExecType / OrdStatus 取值。
 * 0=New 订单申报成功
 * 4=Canceled 订单撤销成功
 * 8=Reject 订单申报拒绝
 * F=Trade 已成交
 */
public class ExecType {
    public static final String NEW = "0";
    public static final String CANCELED = "4";
    public static final String REJECTED = "8";
    public static final String TRADE = "F";
}
