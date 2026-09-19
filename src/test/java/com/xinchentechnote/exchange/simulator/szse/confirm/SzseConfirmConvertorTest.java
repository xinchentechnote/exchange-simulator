package com.xinchentechnote.exchange.simulator.szse.confirm;

import com.finproto.szse.bin.messages.ExecutionConfirm;
import com.finproto.szse.bin.messages.NewOrder;
import com.xinchentechnote.exchange.simulator.common.ExecType;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SzseConfirmConvertorTest {

    private final SzseConfirmConvertor convertor = new SzseConfirmConvertor();

    private NewOrder order() {
        NewOrder order = new NewOrder();
        order.setSecurityId("000001");
        order.setAccountId("20001");
        order.setClOrdId("clOrd-1");
        order.setSide("1");
        order.setPrice(1250L);
        order.setOrderQty(100L);
        return order;
    }

    private IEventsHandler.ApiCommandResult result(CommandResultCode code) {
        return new IEventsHandler.ApiCommandResult(ApiPlaceOrder.builder().build(), code, 1L);
    }

    @Test
    void successShouldBeNew() {
        ExecutionConfirm confirm = convertor.convert(order(), result(CommandResultCode.SUCCESS));
        assertEquals(ExecType.NEW, confirm.getExecType());
        assertEquals("000001", confirm.getSecurityId());
        assertEquals("20001", confirm.getAccountId());
    }

    @Test
    void unmappedCodeShouldFallbackToReject() {
        ExecutionConfirm confirm = convertor.convert(order(), result(CommandResultCode.USER_MGMT_USER_ALREADY_EXISTS));
        assertNotNull(confirm);
        assertEquals(ExecType.REJECTED, confirm.getExecType());
    }
}
