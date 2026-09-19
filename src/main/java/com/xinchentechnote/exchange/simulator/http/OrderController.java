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
     * 委托下单接口：异步提交到撮合核心，立即返回是否受理成功。
     * 订单的确认/成交情况由撮合核心事件回调输出到日志（同步等待订单状态的能力尚未实现）。
     *
     * @param request 订单请求
     * @return true=已受理提交，false=提交过程异常
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
