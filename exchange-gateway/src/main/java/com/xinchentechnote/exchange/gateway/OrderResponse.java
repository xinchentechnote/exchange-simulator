package com.xinchentechnote.exchange.gateway;

import com.xinchentechnote.exchange.gateway.OrderRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.api.ApiPlaceOrder;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse implements Serializable {

    private static final long serialVersionUID = SerialUID.ORDER_RESPONSE_UID;

    private boolean success;
    private String orderId;
    private String message;
    private OrderResult result;
    private long timestamp = System.currentTimeMillis();

    // 请求的订单信息
    private OrderRequest request;

    // 内部生成的命令
    private ApiPlaceOrder apiCommand;

    public static OrderResponse success(OrderRequest request, ApiPlaceOrder command, OrderResult result) {
        OrderResponse response = new OrderResponse();
        response.setSuccess(true);
        response.setOrderId(request.getOrderId());
        response.setRequest(request);
        response.setApiCommand(command);
        response.setResult(result);
        response.setMessage("委托成功");
        return response;
    }

    public static OrderResponse error(OrderRequest request, String errorMessage) {
        OrderResponse response = new OrderResponse();
        response.setSuccess(false);
        response.setOrderId(request != null ? request.getOrderId() : null);
        response.setRequest(request);
        response.setMessage(errorMessage);
        return response;
    }
}