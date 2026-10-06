package com.xinchentechnote.exchange.simulator.http;

import exchange.core2.core.common.api.ApiPlaceOrder;


public interface ExchangeService {

    /**
     * 提交订单到指定市场（request.market: sse/szse，缺省 sse）。
     * request.waitTimeoutMs>0 时同步等待撮合确认，否则异步受理立即返回。
     */
    OrderResponse submitOrder(OrderRequest request);

    /**
     * 构建API下单命令
     */
    ApiPlaceOrder buildPlaceOrderCommand(OrderRequest request);
}