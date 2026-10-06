package com.xinchentechnote.exchange.simulator.http;

import com.xinchentechnote.exchange.simulator.SerialUID;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest implements Serializable {

    private static final long serialVersionUID = SerialUID.ORDER_REQUEST_UID;

    @NotBlank(message = "订单号不能为空")
    @Pattern(regexp = "\\d+", message = "订单号必须为数字")
    private String orderId;

    /** 目标市场：sse / szse，缺省 sse */
    @Pattern(regexp = "sse|szse", message = "market 只支持 sse 或 szse")
    private String market;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;

    @NotNull(message = "订单方向不能为空")
    private OrderAction action;  // ASK 或 BID

    @NotNull(message = "订单类型不能为空")
    private OrderType orderType; // GTC, IOC, FOK_BUDGET 等

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private BigDecimal size;

    @NotNull(message = "Symbol ID不能为空")
    private Integer symbol;

    // 可选的预留价格（用于冰山订单等），缺省回落到委托价
    private BigDecimal reservePrice;

    // 可选：同步等待委托确认的最长毫秒数，缺省异步受理立即返回
    @PositiveOrZero(message = "waitTimeoutMs 必须大于等于0")
    private Long waitTimeoutMs;
}
