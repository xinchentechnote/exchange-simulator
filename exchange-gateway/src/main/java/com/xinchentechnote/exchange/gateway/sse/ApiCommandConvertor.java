package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.codec.BinaryCodec;
import exchange.core2.core.common.api.ApiCommand;

public interface ApiCommandConvertor<S extends BinaryCodec> {
    Class<?> support();
    ApiCommand convert(S s);
}
