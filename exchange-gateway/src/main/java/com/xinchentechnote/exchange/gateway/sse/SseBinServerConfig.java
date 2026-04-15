package com.xinchentechnote.exchange.gateway.sse;

import com.xinchentechnote.exchange.gateway.sse.loaddata.AccountInfoLoadService;
import com.xinchentechnote.exchange.gateway.sse.loaddata.SymbolInfoLoadService;
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
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "sse.bin.server")
public class SseBinServerConfig implements InitializingBean {

    private int port = 9010;

    @Autowired
    private SseBinMatcherConfig sseBinMatcherConfig;
    @Autowired
    private SymbolInfoLoadService symbolInfoLoadService;
    @Autowired
    private AccountInfoLoadService accountInfoLoadService;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println(this);
    }

    @Bean
    public SseBinServer sseBinServer() {
        SseBinServer sseBinServer = new SseBinServer(this.port);
        ExchangeApi exchangeApi = exchangeApi(sseBinServer);
        sseBinServer.setApi(exchangeApi);
        initBaseInfo(exchangeApi);
        sseBinServer.start();
        return sseBinServer;
    }

    private void initBaseInfo(ExchangeApi api) {
        //load symbol and account info
        List<ApiAdjustUserBalance> userBalances = accountInfoLoadService.loadData(sseBinMatcherConfig.getAccountInfoPath());
        List<CoreSymbolSpecification> coreSymbolSpecifications = symbolInfoLoadService.loadData(sseBinMatcherConfig.getSymbolInfoPath());

        userBalances.stream().map(u -> u.uid).distinct().forEach(uid -> {
            api.submitCommandAsync(ApiAddUser.builder().uid(uid).build());
        });

        api.submitBinaryDataAsync(new BatchAddSymbolsCommand(coreSymbolSpecifications));

        userBalances.forEach(api::submitCommandAsync);

    }

    public ExchangeApi exchangeApi(SseBinServer sseBinServer) {
        // simple async events handler
        SimpleEventsProcessor eventsProcessor = new SimpleEventsProcessor(sseBinServer);

        // default exchange configuration
        ExchangeConfiguration conf = ExchangeConfiguration.defaultBuilder().build();

        // build exchange core
        ExchangeCore exchangeCore = ExchangeCore.builder().resultsConsumer(eventsProcessor).exchangeConfiguration(conf).build();

        // start up disruptor threads
        exchangeCore.startup();
        // 获取API
        return exchangeCore.getApi();
    }
}
