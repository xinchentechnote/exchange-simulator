package com.xinchentechnote.exchange.gateway.sse;

import com.finproto.sse.bin.messages.NewOrderSingle;
import com.xinchentechnote.exchange.gateway.GlobalUniqueId;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.api.ApiCommand;
import exchange.core2.core.common.api.ApiPlaceOrder;
import org.springframework.stereotype.Component;

@Component
public class NewOrderSingleToApiPlaceOrderConvertor implements ApiCommandConvertor<NewOrderSingle> {
    @Override
    public Class<?> support() {
        return NewOrderSingle.class;
    }

    @Override
    public ApiCommand convert(NewOrderSingle s) {
        ApiPlaceOrder.ApiPlaceOrderBuilder builder = ApiPlaceOrder.builder();
        builder.orderId(GlobalUniqueId.getAndIncrement())
                .symbol(Integer.parseInt(s.getSecurityId()))
                .uid(Long.parseLong(s.getAccount()))
                .action("1".equals(s.getSide())? OrderAction.BID:OrderAction.ASK)
                .orderType(OrderType.GTC) // 这里默认使用GTC，可以根据需要调整
                .size(s.getOrderQty())
                .price(s.getPrice());
        return builder.build();
    }
}
