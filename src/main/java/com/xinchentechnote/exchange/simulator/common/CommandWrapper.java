package com.xinchentechnote.exchange.simulator.common;

import com.finproto.codec.BinaryCodec;
import exchange.core2.core.common.api.ApiCommand;
import io.netty.channel.Channel;
import lombok.Data;


/**
 * 委托上下文：撮合核心回调只携带 orderId 等撮合侧字段，
 * 回填协议报文（账号、营业部等）时需要回查原始委托与其来源连接。
 * channel/originMsg 可为空（HTTP 通道提交的订单无连接与原始协议报文）。
 */
@Data
public class CommandWrapper {
    private long uniqueId;
    private Channel channel;
    private BinaryCodec originMsg;
    private ApiCommand apiCommand;
    /** 委托报文中的 ClOrdId，用于撤单请求按 origClOrdId 反查原订单 */
    private String clOrdId;
    /** 累计成交量：成交回报的 CumQty/LeavesQty 依赖它（撮合结果线程串行回调，原子类仅为可见性） */
    private final java.util.concurrent.atomic.AtomicLong cumQty = new java.util.concurrent.atomic.AtomicLong();

    public boolean fromWire() {
        return channel != null && originMsg != null;
    }
}
