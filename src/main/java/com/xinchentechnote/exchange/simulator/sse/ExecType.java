package com.xinchentechnote.exchange.simulator.sse;

/**
 * 0=订单申报成功
 * 4=订单撤销成功
 * 8=订单申报拒绝
 */
public class ExecType {
    public static final String NEW = "0";
    public static final String CANCELED = "4";
    public static final String REJECTED = "8";
}
