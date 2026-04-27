package com.xinchentechnote.exchange.simulator.sse.cmd;

import com.finproto.codec.BinaryCodec;

import java.util.HashMap;
import java.util.Map;

public class ApiCommandConvertorContext {
    private static final ApiCommandConvertorContext instance = new ApiCommandConvertorContext();

    public static ApiCommandConvertorContext getInstance() {
        return instance;
    }

    private ApiCommandConvertorContext() {
        // Register convertors here
        registerConvertor(new SseApiCommandConverter());
    }

    private final Map<Class<?>, IApiCommandConverter<?,?>> convertorMap = new HashMap<>();

    public void registerConvertor(IApiCommandConverter<?,?> convertor) {
        convertorMap.put(convertor.support(), convertor);
    }

    public IApiCommandConverter get(BinaryCodec msg) {
        return convertorMap.get(msg.getClass());
    }
}
