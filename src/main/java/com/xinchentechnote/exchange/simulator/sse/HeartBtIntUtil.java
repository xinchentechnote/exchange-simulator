package com.xinchentechnote.exchange.simulator.sse;

public class HeartBtIntUtil {

    public static final int MIN = 5;
    public static final int MAX = 60;

    public static boolean isMin(int heatByInt) {
        return heatByInt == MIN;
    }

    public static int calculate(int heartBtInt) {
        if (heartBtInt < MIN) {
            return MIN;
        }
        return Math.min(heartBtInt, MAX);
    }

}
