package com.xinchentechnote.exchange.simulator.szse.cmd;

import com.finproto.szse.bin.messages.NewOrder;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.api.ApiPlaceOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SzseApiCommandConverterTest {

    private final SzseApiCommandConverter converter = new SzseApiCommandConverter();

    private NewOrder order(String side) {
        NewOrder order = new NewOrder();
        order.setSecurityId("000001");
        order.setAccountId("20001");
        order.setSide(side);
        order.setPrice(1250L);
        order.setOrderQty(100L);
        return order;
    }

    @Test
    void shouldConvertBuyOrder() {
        ApiPlaceOrder command = converter.convertNewOrder(order("1"));
        assertEquals(1, command.symbol);
        assertEquals(20001L, command.uid);
        assertEquals(OrderAction.BID, command.action);
        assertEquals(1250L, command.price);
        assertEquals(100L, command.size);
    }

    @Test
    void shouldConvertSellOrder() {
        ApiPlaceOrder command = converter.convertNewOrder(order("2"));
        assertEquals(OrderAction.ASK, command.action);
    }
}
