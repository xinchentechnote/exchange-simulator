package com.xinchentechnote.exchange.simulator.sse.cmd;

import com.finproto.sse.bin.messages.NewOrderSingle;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.api.ApiPlaceOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseApiCommandConverterTest {

    private final SseApiCommandConverter converter = new SseApiCommandConverter();

    private NewOrderSingle order(String side) {
        NewOrderSingle order = new NewOrderSingle();
        order.setSecurityId("600000");
        order.setAccount("10001");
        order.setSide(side);
        order.setPrice(1050L);
        order.setOrderQty(200L);
        return order;
    }

    @Test
    void shouldConvertBuyOrder() {
        ApiPlaceOrder command = converter.convertNewOrder(order("1"));
        assertEquals(600000, command.symbol);
        assertEquals(10001L, command.uid);
        assertEquals(OrderAction.BID, command.action);
        assertEquals(OrderType.GTC, command.orderType);
        assertEquals(1050L, command.price);
        assertEquals(1050L, command.reservePrice);
        assertEquals(200L, command.size);
    }

    @Test
    void shouldConvertSellOrder() {
        ApiPlaceOrder command = converter.convertNewOrder(order("2"));
        assertEquals(OrderAction.ASK, command.action);
    }
}
