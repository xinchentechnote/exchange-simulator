package com.xinchentechnote.exchange.gateway.utils;

import io.netty.handler.logging.LogLevel;

public class NettyLoggingUtil {

    public static boolean isLoggingEnabled() {
        // 多种方式配置，优先级从高到低
        String enabled = System.getenv("NETTY_LOGGING_ENABLED");
        if (enabled == null || enabled.isEmpty()) {
            enabled = System.getProperty("netty.logging.enabled");
        }
        if (enabled == null || enabled.isEmpty()) {
            enabled = "false"; // 默认关闭
        }
        return Boolean.parseBoolean(enabled);
    }

    public static LogLevel getLoggingLevel() {
        String level = System.getenv("NETTY_LOGGING_LEVEL");
        if (level == null || level.isEmpty()) {
            level = System.getProperty("netty.logging.level");
        }
        if (level == null || level.isEmpty()) {
            level = "INFO"; // 默认级别
        }
        try {
            return LogLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LogLevel.INFO;
        }
    }

    public static String getLoggingHandlerName() {
        return System.getenv().getOrDefault("NETTY_LOGGING_HANDLER_NAME",
                System.getProperty("netty.logging.handler.name", "logging"));
    }
}
