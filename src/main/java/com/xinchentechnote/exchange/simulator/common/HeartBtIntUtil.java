package com.xinchentechnote.exchange.simulator.common;

public class HeartBtIntUtil {

    public static final int MIN = 5;
    public static final int MAX = 60;

    /**
     * 将客户端协商的心跳间隔夹取到 [MIN, MAX] 秒，用于服务端空闲检测窗口。
     */
    public static int calculate(int heartBtInt) {
        if (heartBtInt < MIN) {
            return MIN;
        }
        return Math.min(heartBtInt, MAX);
    }
}
