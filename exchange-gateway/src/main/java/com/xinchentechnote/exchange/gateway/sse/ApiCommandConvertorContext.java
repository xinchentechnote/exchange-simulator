package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.codec.BinaryCodec;
import exchange.core2.core.common.api.ApiCommand;

import java.util.HashMap;
import java.util.Map;

public class ApiCommandConvertorContext {
    private static final ApiCommandConvertorContext instance = new ApiCommandConvertorContext();

    public static ApiCommandConvertorContext getInstance() {
        return instance;
    }

    private ApiCommandConvertorContext() {
        // Register convertors here
        registerConvertor(new NewOrderSingleToApiPlaceOrderConvertor());
    }

    private final Map<Class<?>, ApiCommandConvertor<?>> convertorMap = new HashMap<>();

    public void registerConvertor(ApiCommandConvertor<?> convertor) {
        convertorMap.put(convertor.support(), convertor);
    }

    public ApiCommandConvertor get(BinaryCodec msg){
       return convertorMap.get(msg.getClass());
    }
}
