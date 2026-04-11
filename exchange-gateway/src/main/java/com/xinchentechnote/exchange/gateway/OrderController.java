package com.xinchentechnote.exchange.gateway;

import exchange.core2.core.common.api.ApiPlaceOrder;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    @Autowired
    private ExchangeService exchangeService;

    /**
     * 委托下单接口
     * 如果订单能立即成交，则立即返回
     * 否则等待最多3秒返回订单状态
     *
     * @param request 订单请求
     * @return 订单响应
     */
    @PostMapping("/place")
    public ResponseEntity<Boolean> placeOrder(@Valid @RequestBody OrderRequest request) {
        log.info("收到委托请求: {}", request);

        try {
            boolean response = exchangeService.submitOrder(request);

            log.info("委托处理完成: orderId={}, success={}", request.getOrderId(), response);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("委托处理异常: orderId={}", request.getOrderId(), e);
            return ResponseEntity.ok(false);
        }
    }


    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok().body("{\"status\": \"OK\", \"service\": \"exchange-http\"}");
    }
}