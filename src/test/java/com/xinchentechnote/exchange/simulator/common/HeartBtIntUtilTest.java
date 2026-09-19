package com.xinchentechnote.exchange.simulator.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartBtIntUtilTest {

    @Test
    void shouldClampBelowMin() {
        assertEquals(HeartBtIntUtil.MIN, HeartBtIntUtil.calculate(0));
        assertEquals(HeartBtIntUtil.MIN, HeartBtIntUtil.calculate(1));
    }

    @Test
    void shouldClampAboveMax() {
        assertEquals(HeartBtIntUtil.MAX, HeartBtIntUtil.calculate(3600));
    }

    @Test
    void shouldKeepValueInRange() {
        assertEquals(10, HeartBtIntUtil.calculate(10));
    }
}
