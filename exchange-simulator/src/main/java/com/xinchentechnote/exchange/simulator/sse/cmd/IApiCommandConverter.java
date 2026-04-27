package com.xinchentechnote.exchange.simulator.sse.cmd;

import com.finproto.codec.BinaryCodec;
import exchange.core2.core.common.api.ApiCommand;

public interface IApiCommandConverter<O extends BinaryCodec,T extends ApiCommand> {
    T convertNewOrder(O origin);

    Class<?> support();
}

