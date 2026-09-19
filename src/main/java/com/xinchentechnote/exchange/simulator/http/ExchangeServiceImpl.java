package com.xinchentechnote.exchange.simulator.http;

import exchange.core2.core.ExchangeApi;
import exchange.core2.core.common.api.ApiPlaceOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ExchangeServiceImpl implements ExchangeService {

    @Autowired
    private ExchangeApi exchangeApi;

    // 订单ID生成器
    private final AtomicLong orderIdGenerator = new AtomicLong(System.currentTimeMillis());

    @Override
    public boolean submitOrder(OrderRequest request) {
        // 构建下单命令
        ApiPlaceOrder placeOrder = buildPlaceOrderCommand(request);

        // 提交订单
        exchangeApi.submitCommandAsync(placeOrder);
        return true;
    }


    @Override
    public ApiPlaceOrder buildPlaceOrderCommand(OrderRequest request) {
        // 生成订单ID（如果请求中没有）
        long orderId = request.getOrderId() != null && !request.getOrderId().isEmpty()
                ? Long.parseLong(request.getOrderId())
                : orderIdGenerator.incrementAndGet();

        // reservePrice 可选，缺省回落到委托价（与二进制通道转换器行为一致）
        long reservePrice = request.getReservePrice() != null
                ? request.getReservePrice().longValue()
                : request.getPrice().longValue();

        return ApiPlaceOrder.builder()
                .orderId(orderId)
                .uid(request.getUserId())
                .price(request.getPrice().longValue())
                .size(request.getSize().longValue())
                .reservePrice(reservePrice)
                .action(request.getAction())
                .orderType(request.getOrderType())
                .symbol(request.getSymbol())
                .userCookie(0)  // 用户自定义数据
                .build();
    }
}
