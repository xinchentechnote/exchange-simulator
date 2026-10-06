package com.xinchentechnote.exchange.simulator.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    @Autowired
    private ExchangeService exchangeService;

    /**
     * 委托下单接口。
     * market 指定目标市场（sse/szse，缺省 sse）；waitTimeoutMs>0 时同步等待撮合确认，
     * 否则异步受理立即返回（pending=true）。订单须使用目标市场 data/*.csv 中存在的账户与证券。
     *
     * @param request 订单请求
     * @return 订单受理/确认结果
     */
    @PostMapping("/place")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        log.info("收到委托请求: {}", request);

        try {
            OrderResponse response = exchangeService.submitOrder(request);
            log.info("委托处理完成: orderId={}, success={}, pending={}",
                    request.getOrderId(), response.isSuccess(), response.isPending());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("委托处理异常: orderId={}", request.getOrderId(), e);
            return ResponseEntity.ok(OrderResponse.failed(request,
                    request.getMarket(), "提交异常: " + e.getMessage()));
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
