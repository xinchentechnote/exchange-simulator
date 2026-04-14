package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.sse.bin.messages.SseBinary;
import exchange.core2.core.common.api.ApiCommand;
import io.netty.channel.Channel;
import lombok.Data;



@Data
public class CommandWrapper {
    private long uniqueId;
    private Channel channel;
    private SseBinary originMsg;
    private ApiCommand apiCommand;
}
