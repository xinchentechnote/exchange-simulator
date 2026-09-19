package com.xinchentechnote.exchange.simulator.common;

import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE/SZSE 共用的 Netty pipeline 与 channel 属性常量。
 */
public class Constant {
    public static final String FRAME = "frameHandler";
    public static final String IDLE = "idleHandler";
    public static final String CONNECTION = "connectionHandler";
    public static final String MESSAGE = "messageHandler";

    /** 会话是否已登录 */
    public static final AttributeKey<Boolean> LOGON = AttributeKey.newInstance("logon");
    /** 下行报文序号（按会话独立计数） */
    public static final AttributeKey<AtomicLong> MSG_SEQ_NUM = AttributeKey.newInstance("msgSeqNum");
}
