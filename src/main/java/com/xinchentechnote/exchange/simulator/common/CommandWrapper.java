package com.xinchentechnote.exchange.simulator.common;

import com.finproto.codec.BinaryCodec;
import exchange.core2.core.common.api.ApiCommand;
import io.netty.channel.Channel;
import lombok.Data;


/**
 * 委托上下文：撮合核心回调只携带 orderId 等撮合侧字段，
 * 回填协议报文（账号、营业部等）时需要回查原始委托与其来源连接。
 */
@Data
public class CommandWrapper {
    private long uniqueId;
    private Channel channel;
    private BinaryCodec originMsg;
    private ApiCommand apiCommand;
}
