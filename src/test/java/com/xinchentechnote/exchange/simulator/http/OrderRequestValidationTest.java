package com.xinchentechnote.exchange.simulator.http;

import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验依赖必须是 javax.validation 系（Boot 2.7），
 * 本测试同时用于防止误升级为 jakarta.validation 导致校验静默失效。
 */
class OrderRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private OrderRequest valid() {
        return new OrderRequest("10001", "sse", 1001L, OrderAction.BID, OrderType.GTC,
                new BigDecimal("50.0"), new BigDecimal("3"), 600000, new BigDecimal("50.0"), null);
    }

    private OrderRequest withOrderId(OrderRequest base, String orderId) {
        return new OrderRequest(orderId, base.getMarket(), base.getUserId(), base.getAction(),
                base.getOrderType(), base.getPrice(), base.getSize(), base.getSymbol(),
                base.getReservePrice(), base.getWaitTimeoutMs());
    }

    private Set<ConstraintViolation<OrderRequest>> validate(OrderRequest request) {
        return validator.validate(request);
    }

    @Test
    void validRequestShouldPass() {
        assertTrue(validate(valid()).isEmpty());
    }

    @Test
    void blankOrderIdShouldBeRejected() {
        OrderRequest bad = withOrderId(valid(), "");
        assertTrue(validate(bad).stream().anyMatch(v -> "orderId".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nonNumericOrderIdShouldBeRejected() {
        OrderRequest bad = withOrderId(valid(), "abc");
        assertTrue(validate(bad).stream().anyMatch(v -> "orderId".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nullPriceShouldBeRejected() {
        OrderRequest base = valid();
        OrderRequest bad = new OrderRequest(base.getOrderId(), base.getMarket(), base.getUserId(), base.getAction(),
                base.getOrderType(), null, base.getSize(), base.getSymbol(),
                base.getReservePrice(), base.getWaitTimeoutMs());
        assertTrue(validate(bad).stream().anyMatch(v -> "price".equals(v.getPropertyPath().toString())));
    }

    @Test
    void zeroPriceShouldBeRejected() {
        OrderRequest base = valid();
        OrderRequest bad = new OrderRequest(base.getOrderId(), base.getMarket(), base.getUserId(), base.getAction(),
                base.getOrderType(), BigDecimal.ZERO, base.getSize(), base.getSymbol(),
                base.getReservePrice(), base.getWaitTimeoutMs());
        assertTrue(validate(bad).stream().anyMatch(v -> "price".equals(v.getPropertyPath().toString())));
    }

    @Test
    void missingReservePriceShouldBeAllowed() {
        OrderRequest base = valid();
        OrderRequest noReserve = new OrderRequest(base.getOrderId(), base.getMarket(), base.getUserId(), base.getAction(),
                base.getOrderType(), base.getPrice(), base.getSize(), base.getSymbol(),
                null, base.getWaitTimeoutMs());
        assertTrue(validate(noReserve).isEmpty(), "reservePrice is optional");
    }

    @Test
    void invalidMarketShouldBeRejected() {
        OrderRequest base = valid();
        OrderRequest bad = new OrderRequest(base.getOrderId(), "sh", base.getUserId(), base.getAction(),
                base.getOrderType(), base.getPrice(), base.getSize(), base.getSymbol(),
                base.getReservePrice(), base.getWaitTimeoutMs());
        assertTrue(validate(bad).stream().anyMatch(v -> "market".equals(v.getPropertyPath().toString())));
    }

    @Test
    void negativeWaitTimeoutShouldBeRejected() {
        OrderRequest base = valid();
        OrderRequest bad = new OrderRequest(base.getOrderId(), base.getMarket(), base.getUserId(), base.getAction(),
                base.getOrderType(), base.getPrice(), base.getSize(), base.getSymbol(),
                base.getReservePrice(), -1L);
        assertTrue(validate(bad).stream().anyMatch(v -> "waitTimeoutMs".equals(v.getPropertyPath().toString())));
    }
}
