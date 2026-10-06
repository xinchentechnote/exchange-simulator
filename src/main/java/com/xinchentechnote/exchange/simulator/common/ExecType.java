package com.xinchentechnote.exchange.simulator.common;

/**
 * SSE/SZSE 共用的 ExecType / OrdStatus 取值。
 * ExecType: 0=New 订单申报成功 / 4=Cancelled 订单被主动撤单 / 8=Rejected 申报拒绝 / F=Trade 已成交
 * OrdStatus（SZSE 规范 Ver1.29 数据字典）: 0=New 新订单 / 1=Partially filled 部分成交 /
 * 2=Filled 全部成交 / 4=Cancelled 已撤销 / 8=Reject 已拒绝
 */
public class ExecType {
    public static final String NEW = "0";
    public static final String PARTIALLY_FILLED = "1";
    public static final String FILLED = "2";
    public static final String CANCELED = "4";
    public static final String REJECTED = "8";
    public static final String TRADE = "F";
}
