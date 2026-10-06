package com.xinchentechnote.exchange.simulator.http;

import com.xinchentechnote.exchange.simulator.SerialUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 下单接口响应：
 * - 异步受理（未指定 waitTimeoutMs）：pending=true，success 表示已提交；
 * - 同步等待（waitTimeoutMs>0）：撮合核心确认后返回 execType/ordStatus（0=受理成功，8=拒绝）；
 *   超时未收到确认时 pending=true 且 success 按已提交处理。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse implements Serializable {

    private static final long serialVersionUID = SerialUID.ORDER_RESPONSE_UID;

    private boolean success;
    private String orderId;
    private String market;
    private String message;
    /** true=尚未收到撮合确认（异步受理或等待超时） */
    private boolean pending;
    /** 委托确认的 ExecType：0=申报成功，8=申报拒绝（同步等待到结果时返回） */
    private String execType;
    /** 委托状态，与 execType 同值 */
    private String ordStatus;
    private long timestamp = System.currentTimeMillis();

    public static OrderResponse accepted(OrderRequest request, String market, String message) {
        OrderResponse response = new OrderResponse();
        response.setSuccess(true);
        response.setOrderId(request.getOrderId());
        response.setMarket(market);
        response.setPending(true);
        response.setMessage(message);
        return response;
    }

    public static OrderResponse confirmed(OrderRequest request, String market, String execType) {
        OrderResponse response = new OrderResponse();
        response.setSuccess(true);
        response.setOrderId(request.getOrderId());
        response.setMarket(market);
        response.setPending(false);
        response.setExecType(execType);
        response.setOrdStatus(execType);
        response.setMessage("0".equals(execType) ? "委托已受理" : "委托被拒绝");
        return response;
    }

    public static OrderResponse failed(OrderRequest request, String market, String errorMessage) {
        OrderResponse response = new OrderResponse();
        response.setSuccess(false);
        response.setOrderId(request != null ? request.getOrderId() : null);
        response.setMarket(market);
        response.setPending(true);
        response.setMessage(errorMessage);
        return response;
    }
}
