package com.xinchentechnote.exchange.simulator.sse.cmd;

import com.finproto.sse.bin.messages.NewOrderSingle;
import com.xinchentechnote.exchange.simulator.GlobalUniqueId;
import com.xinchentechnote.exchange.simulator.convertor.cmd.IApiCommandConverter;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.api.ApiPlaceOrder;

/**
 * 非Spring管理，由 {@link com.xinchentechnote.exchange.simulator.convertor.cmd.ApiCommandConvertorContext} 手动注册。
 */
public class SseApiCommandConverter implements IApiCommandConverter<NewOrderSingle, ApiPlaceOrder> {

    @Override
    public Class<?> support() {
        return NewOrderSingle.class;
    }

    @Override
    public ApiPlaceOrder convertNewOrder(NewOrderSingle order) {
        ApiPlaceOrder.ApiPlaceOrderBuilder builder = ApiPlaceOrder.builder();
        long price = order.getPrice();
        builder.orderId(GlobalUniqueId.getAndIncrement()).symbol(Integer.parseInt(order.getSecurityId())).uid(Long.parseLong(order.getAccount())).action("1".equals(order.getSide()) ? OrderAction.BID : OrderAction.ASK).orderType(OrderType.GTC) // 这里默认使用GTC，可以根据需要调整
                .reservePrice(price).size(order.getOrderQty()).price(price);
        return builder.build();
    }


}
