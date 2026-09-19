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
        return new OrderRequest("10001", 1001L, OrderAction.BID, OrderType.GTC,
                new BigDecimal("50.0"), new BigDecimal("3"), 10086, new BigDecimal("50.0"));
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
        OrderRequest request = valid();
        OrderRequest blank = new OrderRequest("", request.getUserId(), request.getAction(),
                request.getOrderType(), request.getPrice(), request.getSize(), request.getSymbol(), request.getReservePrice());
        assertTrue(validate(blank).stream().anyMatch(v -> "orderId".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nonNumericOrderIdShouldBeRejected() {
        OrderRequest request = valid();
        OrderRequest bad = new OrderRequest("abc", request.getUserId(), request.getAction(),
                request.getOrderType(), request.getPrice(), request.getSize(), request.getSymbol(), request.getReservePrice());
        assertTrue(validate(bad).stream().anyMatch(v -> "orderId".equals(v.getPropertyPath().toString())));
    }

    @Test
    void nullPriceShouldBeRejected() {
        OrderRequest request = valid();
        OrderRequest bad = new OrderRequest(request.getOrderId(), request.getUserId(), request.getAction(),
                request.getOrderType(), null, request.getSize(), request.getSymbol(), request.getReservePrice());
        assertTrue(validate(bad).stream().anyMatch(v -> "price".equals(v.getPropertyPath().toString())));
    }

    @Test
    void zeroPriceShouldBeRejected() {
        OrderRequest request = valid();
        OrderRequest bad = new OrderRequest(request.getOrderId(), request.getUserId(), request.getAction(),
                request.getOrderType(), BigDecimal.ZERO, request.getSize(), request.getSymbol(), request.getReservePrice());
        assertTrue(validate(bad).stream().anyMatch(v -> "price".equals(v.getPropertyPath().toString())));
    }

    @Test
    void missingReservePriceShouldBeAllowed() {
        OrderRequest request = valid();
        OrderRequest noReserve = new OrderRequest(request.getOrderId(), request.getUserId(), request.getAction(),
                request.getOrderType(), request.getPrice(), request.getSize(), request.getSymbol(), null);
        assertTrue(validate(noReserve).isEmpty(), "reservePrice is optional");
    }
}
