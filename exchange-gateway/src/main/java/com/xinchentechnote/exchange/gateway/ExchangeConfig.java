package com.xinchentechnote.exchange.gateway;

import exchange.core2.core.ExchangeApi;
import exchange.core2.core.ExchangeCore;
import exchange.core2.core.IEventsHandler;
import exchange.core2.core.SimpleEventsProcessor;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.SymbolType;
import exchange.core2.core.common.api.ApiAddUser;
import exchange.core2.core.common.api.binary.BatchAddSymbolsCommand;
import exchange.core2.core.common.config.ExchangeConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExchangeConfig {

    @Bean
    public ExchangeApi exchangeApi() {
        // simple async events handler
        SimpleEventsProcessor eventsProcessor = new SimpleEventsProcessor(new IEventsHandler() {
            @Override
            public void tradeEvent(TradeEvent tradeEvent) {
                System.out.println("Trade event: " + tradeEvent);
            }

            @Override
            public void reduceEvent(ReduceEvent reduceEvent) {
                System.out.println("Reduce event: " + reduceEvent);
            }

            @Override
            public void rejectEvent(RejectEvent rejectEvent) {
                System.out.println("Reject event: " + rejectEvent);
            }

            @Override
            public void commandResult(ApiCommandResult commandResult) {
                System.out.println("Command result: " + commandResult);
            }

            @Override
            public void orderBook(OrderBook orderBook) {
                System.out.println("OrderBook event: " + orderBook);
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

        // 获取API
        ExchangeApi api = exchangeCore.getApi();
        api.submitCommandAsync(ApiAddUser.builder().uid(1001).build());
        api.submitCommandAsync(ApiAddUser.builder().uid(1002).build());

        CoreSymbolSpecification symbolSpecification = CoreSymbolSpecification.builder()
                        .symbolId(10086)
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(100000000)
                .quoteScaleK(100000000)
                .takerFee(10)
                .makerFee(1)
                                .build();
        api.submitBinaryDataAsync(
                new BatchAddSymbolsCommand(symbolSpecification));

//        api.submitBinaryDataAsync(new BatchAddAccountsCommand());
        return api;
    }
}