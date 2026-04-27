package com.xinchentechnote.exchange.simulator.http;

import com.xinchentechnote.exchange.simulator.SerialUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResult implements Serializable {

    private static final long serialVersionUID = SerialUID.ORDER_RESULT_UID;

    // 订单状态
    private String status;

    // 委托价格
    private BigDecimal price;

    // 委托数量
    private BigDecimal size;

    // 已成交数量
    private BigDecimal filledSize = BigDecimal.ZERO;

    // 剩余数量
    private BigDecimal remainingSize = BigDecimal.ZERO;

    // 订单方向
    private OrderAction action;

    // 订单类型
    private OrderType orderType;

    // 委托时间
    private long timestamp;

    // 最后更新时间
    private long lastUpdateTime;

    // 成交明细
    private List<TradeDetail> trades;

    // 累计成交额
    private BigDecimal filledValue = BigDecimal.ZERO;

    // 平均成交价
    private BigDecimal avgPrice = BigDecimal.ZERO;

    // 手续费
    private BigDecimal fee = BigDecimal.ZERO;

    // 订单来源
    private String source = "HTTP_API";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeDetail implements Serializable {
        private static final long serialVersionUID = SerialUID.TRADE_DETAIL_UID;

        private String tradeId;
        private BigDecimal price;
        private BigDecimal size;
        private BigDecimal value;
        private long timestamp;
        private BigDecimal fee;
        private Long takerOrderId;
        private Long makerOrderId;
    }

}