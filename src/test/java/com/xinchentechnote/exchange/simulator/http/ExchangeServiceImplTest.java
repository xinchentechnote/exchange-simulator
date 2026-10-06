package com.xinchentechnote.exchange.simulator.http;

import com.xinchentechnote.exchange.simulator.common.ExecType;
import com.xinchentechnote.exchange.simulator.sse.SseBinServer;
import com.xinchentechnote.exchange.simulator.szse.SzseBinServer;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.api.ApiPlaceOrder;
import exchange.core2.core.common.cmd.CommandResultCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP 下单：市场路由（sse/szse）、异步受理、同步等待确认/超时。
 */
class ExchangeServiceImplTest {

    private final SseBinServer sse = mock(SseBinServer.class);
    private final SzseBinServer szse = mock(SzseBinServer.class);
    private final ExchangeServiceImpl service = new ExchangeServiceImpl(sse, szse);

    private OrderRequest request(String market, Long waitTimeoutMs) {
        return new OrderRequest("9001", market, 10001L, OrderAction.BID, OrderType.GTC,
                new BigDecimal("10"), new BigDecimal("100"), 600000, null, waitTimeoutMs);
    }

    private IEventsHandler.ApiCommandResult result(CommandResultCode code) {
        return new IEventsHandler.ApiCommandResult(ApiPlaceOrder.builder().build(), code, 1L);
    }

    @Test
    void defaultMarketShouldRouteToSse() {
        OrderResponse response = service.submitOrder(request(null, null));
        assertTrue(response.isSuccess());
        assertTrue(response.isPending());
        assertEquals("sse", response.getMarket());
        verify(sse).submitExternalOrder(any(ApiPlaceOrder.class));
        verify(szse, never()).submitExternalOrder(any(ApiPlaceOrder.class));
    }

    @Test
    void szseMarketShouldRouteToSzse() {
        OrderResponse response = service.submitOrder(request("szse", null));
        assertEquals("szse", response.getMarket());
        verify(szse).submitExternalOrder(any(ApiPlaceOrder.class));
        verify(sse, never()).submitExternalOrder(any(ApiPlaceOrder.class));
    }

    @Test
    void syncWaitShouldReturnConfirmedExecType() {
        CompletableFuture<IEventsHandler.ApiCommandResult> future = new CompletableFuture<>();
        future.complete(result(CommandResultCode.SUCCESS));
        when(szse.registerPendingResult(9001L)).thenReturn(future);

        OrderResponse response = service.submitOrder(request("szse", 1000L));

        assertFalse(response.isPending());
        assertEquals(ExecType.NEW, response.getExecType());
        verify(szse).registerPendingResult(9001L);
        verify(szse).submitExternalOrder(any(ApiPlaceOrder.class));
    }

    @Test
    void syncWaitShouldReturnRejectedExecType() {
        CompletableFuture<IEventsHandler.ApiCommandResult> future = new CompletableFuture<>();
        future.complete(result(CommandResultCode.RISK_NSF));
        when(sse.registerPendingResult(9001L)).thenReturn(future);

        OrderResponse response = service.submitOrder(request("sse", 1000L));

        assertFalse(response.isPending());
        assertEquals(ExecType.REJECTED, response.getExecType());
    }

    @Test
    void syncWaitTimeoutShouldReturnPendingAccepted() {
        when(sse.registerPendingResult(anyLong())).thenReturn(new CompletableFuture<>());

        OrderResponse response = service.submitOrder(request("sse", 100L));

        assertTrue(response.isPending());
        assertTrue(response.getMessage().contains("超时"));
    }
}
