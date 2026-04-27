package com.xinchentechnote.exchange.simulator.http;

import exchange.core2.core.common.api.ApiPlaceOrder;


public interface ExchangeService {

    /**
     * 提交订单（同步，带超时）
     */
    boolean submitOrder(OrderRequest request);

    /**
     * 构建API下单命令
     */
    ApiPlaceOrder buildPlaceOrderCommand(OrderRequest request);
}