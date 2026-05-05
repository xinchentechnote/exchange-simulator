package com.xinchentechnote.exchange.simulator.sse;

import com.finproto.codec.BinaryCodec;
import com.finproto.sse.bin.messages.SseBinary;
import exchange.core2.core.common.api.ApiCommand;
import io.netty.channel.Channel;
import lombok.Data;



@Data
public class CommandWrapper {
    private long uniqueId;
    private Channel channel;
    private BinaryCodec originMsg;
    private ApiCommand apiCommand;
}
