package com.xinchentechnote.exchange.gateway;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest implements Serializable {

    private static final long serialVersionUID = SerialUID.ORDER_REQUEST_UID;

    @NotBlank(message = "订单号不能为空")
    private String orderId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "订单方向不能为空")
    private OrderAction action;  // ASK 或 BID

    @NotNull(message = "订单类型不能为空")
    private OrderType orderType; // GTC, IOC, FOK_BUDGET 等

    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量必须大于0")
    private BigDecimal size;

    @NotNull(message = "Symbol ID不能为空")
    private Integer symbol;

    // 可选的预留价格（用于冰山订单等）
    private BigDecimal reservePrice;
}