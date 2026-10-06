package com.xinchentechnote.exchange.simulator.http;

import com.google.common.collect.ImmutableMap;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.common.OrderGateway;
import com.xinchentechnote.exchange.simulator.sse.SseBinServer;
import com.xinchentechnote.exchange.simulator.szse.SzseBinServer;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ExchangeServiceImpl implements ExchangeService {

    public static final String MARKET_SSE = "sse";
    public static final String MARKET_SZSE = "szse";

    private final Map<String, OrderGateway> gateways;

    // 订单ID生成器（请求未携带 orderId 时兜底）
    private final AtomicLong orderIdGenerator = new AtomicLong(System.currentTimeMillis());

    public ExchangeServiceImpl(@Autowired SseBinServer sseBinServer,
                               @Autowired SzseBinServer szseBinServer) {
        ImmutableMap.Builder<String, OrderGateway> builder = ImmutableMap.builder();
        builder.put(MARKET_SSE, sseBinServer);
        builder.put(MARKET_SZSE, szseBinServer);
        this.gateways = builder.build();
    }

    @Override
    public OrderResponse submitOrder(OrderRequest request) {
        String market = resolveMarket(request);
        OrderGateway gateway = gateways.get(market);
        ApiPlaceOrder placeOrder = buildPlaceOrderCommand(request);

        // 先注册等待再提交，避免确认早于注册的竞态
        boolean syncWait = request.getWaitTimeoutMs() != null && request.getWaitTimeoutMs() > 0;
        CompletableFuture<IEventsHandler.ApiCommandResult> future =
                syncWait ? gateway.registerPendingResult(placeOrder.orderId) : null;

        gateway.submitExternalOrder(placeOrder);

        if (future == null) {
            return OrderResponse.accepted(request, market, "已异步提交到 " + market + " 撮合核心");
        }
        return awaitConfirm(request, market, future, request.getWaitTimeoutMs());
    }

    private OrderResponse awaitConfirm(OrderRequest request, String market,
                                       CompletableFuture<IEventsHandler.ApiCommandResult> future, long timeoutMs) {
        try {
            IEventsHandler.ApiCommandResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            String execType = result.getResultCode() == CommandResultCode.SUCCESS
                    ? ExecType.NEW : ExecType.REJECTED;
            return OrderResponse.confirmed(request, market, execType);
        } catch (TimeoutException e) {
            log.warn("等待委托确认超时: orderId={}, market={}, timeout={}ms",
                    request.getOrderId(), market, timeoutMs);
            return OrderResponse.accepted(request, market, "已提交，等待确认超时(" + timeoutMs + "ms)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OrderResponse.failed(request, market, "等待确认被中断");
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("等待委托确认异常: orderId={}", request.getOrderId(), e.getCause());
            return OrderResponse.failed(request, market, "等待确认异常: " + e.getCause().getMessage());
        }
    }

    private String resolveMarket(OrderRequest request) {
        return request.getMarket() == null || request.getMarket().isEmpty()
                ? MARKET_SSE : request.getMarket();
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
