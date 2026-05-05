package com.xinchentechnote.exchange.simulator.szse;

import com.xinchentechnote.exchange.simulator.GlobalUniqueId;
import com.xinchentechnote.exchange.simulator.loaddata.AccountInfoLoadService;
import com.xinchentechnote.exchange.simulator.loaddata.SymbolInfoLoadService;
import com.xinchentechnote.exchange.simulator.sse.SseBinServer;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.ExchangeCore;
import exchange.core2.core.SimpleEventsProcessor;
import exchange.core2.core.common.CoreSymbolSpecification;
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
@ConfigurationProperties(prefix = "szse.bin.server")
public class SzseBinServerConfig implements InitializingBean {

    private int port = 9011;

    @Autowired
    private SzseBinMatcherConfig szseBinMatcherConfig;
    @Autowired
    private SymbolInfoLoadService symbolInfoLoadService;
    @Autowired
    private AccountInfoLoadService accountInfoLoadService;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println(this);
    }

    @Bean
    public SzseBinServer szseBinServer(){
        SzseBinServer szseBinServer = new SzseBinServer(port);
        ExchangeApi exchangeApi = creatExchangeApi(szseBinServer);
        szseBinServer.setApi(exchangeApi);
        initBaseInfo(exchangeApi);
        szseBinServer.start();
        return szseBinServer;
    }



    private void initBaseInfo(ExchangeApi api) {
        //load symbol and account info
        List<ApiAdjustUserBalance> userBalances = accountInfoLoadService.loadData(szseBinMatcherConfig.getAccountInfoPath());
        List<CoreSymbolSpecification> coreSymbolSpecifications = symbolInfoLoadService.loadData(szseBinMatcherConfig.getSymbolInfoPath());

        userBalances.stream().map(u -> u.uid).distinct().forEach(uid -> {
            api.submitCommandAsync(ApiAddUser.builder().uid(uid).build());
        });

        api.submitBinaryDataAsync(new BatchAddSymbolsCommand(coreSymbolSpecifications));

        userBalances.forEach(ub->{
            ApiAdjustUserBalance build = ApiAdjustUserBalance.builder().uid(ub.uid).currency(ub.currency).amount(ub.amount).transactionId(GlobalUniqueId.getAndIncrement()).build();
            api.submitCommandAsync(build);
        });

    }

    public ExchangeApi creatExchangeApi(SzseBinServer szseBinServer) {
        // simple async events handler
        SimpleEventsProcessor eventsProcessor = new SimpleEventsProcessor(szseBinServer);

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
