package com.xinchentechnote.exchange.simulator.szse;

/**
 * SZSE 二进制协议消息类型常量（finproto szse-bin 未提供枚举，此处集中维护，避免魔法数字）。
 */
public final class SzseMsgType {
    /** 登录 */
    public static final int LOGON = 1;
    /** 登出 */
    public static final int LOGOUT = 2;
    /** 心跳 */
    public static final int HEARTBEAT = 3;
    /** 业务拒绝 */
    public static final int BUSINESS_REJECT = 4;
    /** 撤单请求 */
    public static final int ORDER_CANCEL_REQUEST = 190007;
    /** 撤单拒绝 */
    public static final int CANCEL_REJECT = 290008;
    /** 委托确认（ExecutionConfirm） */
    public static final int EXECUTION_CONFIRM = 200102;
    /** 成交回报（ExecutionReport） */
    public static final int EXECUTION_REPORT = 200115;

    private SzseMsgType() {
    }
}
