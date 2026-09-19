package com.xinchentechnote.exchange.simulator;

import exchange.core2.core.ExchangeApi;
import exchange.core2.core.ExchangeCore;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.SimpleEventsProcessor;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.SymbolType;
import exchange.core2.core.common.api.ApiAddUser;
import exchange.core2.core.common.api.ApiAdjustUserBalance;
import exchange.core2.core.common.api.binary.BatchAddSymbolsCommand;
import exchange.core2.core.common.config.ExchangeConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP 测试接口专用的演示撮合核心（与 SSE/SZSE 协议市场相互独立）。
 * TODO(P1-12): 与协议市场打通或移除，见 docs/TODO.md。
 */
@Slf4j
@Configuration
public class ExchangeConfig {

    @Bean
    public ExchangeApi exchangeApi() {
        // simple async events handler
        SimpleEventsProcessor eventsProcessor = new SimpleEventsProcessor(new IEventsHandler() {
            @Override
            public void tradeEvent(TradeEvent tradeEvent) {
                log.info("Trade event: {}", tradeEvent);
            }

            @Override
            public void reduceEvent(ReduceEvent reduceEvent) {
                log.info("Reduce event: {}", reduceEvent);
            }

            @Override
            public void rejectEvent(RejectEvent rejectEvent) {
                log.info("Reject event: {}", rejectEvent);
            }

            @Override
            public void commandResult(ApiCommandResult commandResult) {
                log.info("Command result: {}", commandResult);
            }

            @Override
            public void orderBook(OrderBook orderBook) {
                log.info("OrderBook event: {}", orderBook);
            }
        });

        // default exchange configuration
        ExchangeConfiguration conf = ExchangeConfiguration.defaultBuilder().build();

        // build exchange core
        ExchangeCore exchangeCore = ExchangeCore.builder()
                .resultsConsumer(eventsProcessor)
                .exchangeConfiguration(conf)
                .build();

        // start up disruptor threads
        exchangeCore.startup();

        // currency code constants
        final int currencyCodeXbt = 11;
        final int currencyCodeLtc = 15;

        // symbol constants
        final int symbolXbtLtc = 10086;

        // 获取API
        ExchangeApi api = exchangeCore.getApi();
        api.submitCommandAsync(ApiAddUser.builder().uid(1001).build());
        api.submitCommandAsync(ApiAddUser.builder().uid(1002).build());

        CoreSymbolSpecification symbolSpecification = CoreSymbolSpecification.builder()
                .symbolId(symbolXbtLtc)
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(currencyCodeXbt)
                .quoteCurrency(currencyCodeLtc)
                .baseScaleK(1000000)
                .quoteScaleK(10000)
                .takerFee(1000)
                .makerFee(100)
                .build();
        api.submitBinaryDataAsync(
                new BatchAddSymbolsCommand(symbolSpecification));

        api.submitCommandAsync(ApiAdjustUserBalance.builder().uid(1001)
                .currency(currencyCodeLtc)
                .amount(20000000).transactionId(1).build());
        api.submitCommandAsync(ApiAdjustUserBalance.builder().uid(1002)
                .currency(currencyCodeXbt)
                .amount(20000000).transactionId(2).build());
        return api;
    }
}
