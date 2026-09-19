package com.xinchentechnote.exchange.simulator.sse.confirm;

import com.finproto.sse.bin.messages.Confirm;
import com.finproto.sse.bin.messages.NewOrderSingle;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SseConfirmConvertorTest {

    private final SseConfirmConvertor convertor = new SseConfirmConvertor();

    private NewOrderSingle order() {
        NewOrderSingle order = new NewOrderSingle();
        order.setSecurityId("600000");
        order.setAccount("10001");
        order.setClOrdId("clOrd-1");
        order.setSide("1");
        order.setPrice(1050L);
        order.setOrderQty(200L);
        return order;
    }

    private IEventsHandler.ApiCommandResult result(CommandResultCode code) {
        return new IEventsHandler.ApiCommandResult(ApiPlaceOrder.builder().build(), code, 1L);
    }

    @Test
    void successShouldBeNew() {
        Confirm confirm = convertor.convert(order(), result(CommandResultCode.SUCCESS));
        assertEquals(ExecType.NEW, confirm.getExecType());
        assertEquals("600000", confirm.getSecurityId());
        assertEquals("10001", confirm.getAccount());
        assertEquals("clOrd-1", confirm.getClOrdId());
    }

    @Test
    void nsfShouldBeRejected() {
        Confirm confirm = convertor.convert(order(), result(CommandResultCode.RISK_NSF));
        assertEquals(ExecType.REJECTED, confirm.getExecType());
    }

    @Test
    void unmappedCodeShouldFallbackToReject() {
        //未映射的结果码也必须下发拒绝回执，客户端不能无响应
        Confirm confirm = convertor.convert(order(), result(CommandResultCode.MATCHING_UNKNOWN_ORDER_ID));
        assertNotNull(confirm);
        assertEquals(ExecType.REJECTED, confirm.getExecType());
    }
}
