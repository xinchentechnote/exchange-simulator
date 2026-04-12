package com.xinchentechnote.exchange.gateway.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SseBinServerConfig {

    @Bean
    public SseBinServer sseBinServer() {
        SseBinServer sseBinServer = new SseBinServer(9010);
        sseBinServer.start();
        return sseBinServer;
    }
}
