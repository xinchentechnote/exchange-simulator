package com.xinchentechnote.exchange.simulator.szse.cmd;

import com.finproto.sse.bin.messages.NewOrderSingle;
import com.finproto.szse.bin.messages.NewOrder;
import com.xinchentechnote.exchange.simulator.GlobalUniqueId;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.api.ApiPlaceOrder;
import org.springframework.stereotype.Component;

@Component
public class SzseApiCommandConverter implements IApiCommandConverter<NewOrder, ApiPlaceOrder> {

    @Override
    public Class<?> support() {
        return NewOrder.class;
    }

    @Override
    public ApiPlaceOrder convertNewOrder(NewOrder order) {
        ApiPlaceOrder.ApiPlaceOrderBuilder builder = ApiPlaceOrder.builder();
        long price = order.getPrice();
        builder.orderId(GlobalUniqueId.getAndIncrement())
                .symbol(Integer.parseInt(order.getSecurityId()))
                .uid(Long.parseLong(order.getAccountId()))
                .action("1".equals(order.getSide()) ? OrderAction.BID : OrderAction.ASK)
                .orderType(OrderType.GTC) // 这里默认使用GTC，可以根据需要调整
                .reservePrice(price)
                .size(order.getOrderQty())
                .price(price);
        return builder.build();
    }


}
