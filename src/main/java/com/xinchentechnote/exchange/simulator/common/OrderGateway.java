package com.xinchentechnote.exchange.simulator.common;

import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiPlaceOrder;

import java.util.concurrent.CompletableFuture;

/**
 * 市场撮合网关：HTTP 通道与协议市场解耦的路由抽象。
 */
public interface OrderGateway {

    /**
     * 提交外部委托（无协议连接，回报仅记录日志）。
     */
    void submitExternalOrder(ApiPlaceOrder command);

    /**
     * 注册委托确认等待，须在提交命令前调用。
     */
    CompletableFuture<IEventsHandler.ApiCommandResult> registerPendingResult(long orderId);
}
