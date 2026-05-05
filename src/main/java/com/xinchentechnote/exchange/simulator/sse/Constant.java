package com.xinchentechnote.exchange.simulator.sse;

import io.netty.util.AttributeKey;

public class Constant {
    public static final String FRAME = "frameHandler";
    public static final String CONNECTION = "connectionHandler";
    public static final String IDLE = "idleHandler";
    public static final String MESSAGE = "messageHandler";
    public static final AttributeKey<Boolean> LOGON = AttributeKey.newInstance("logon");
}
