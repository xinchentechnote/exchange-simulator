package com.xinchentechnote.exchange.gateway;

import java.util.concurrent.atomic.AtomicLong;

public class GlobalUniqueId {
    private static final AtomicLong counter = new AtomicLong(1);

    public static long getAndIncrement(){
        return counter.getAndIncrement();
    }
}
