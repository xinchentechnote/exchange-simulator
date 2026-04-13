package com.xinchentechnote.exchange.gateway.sse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "sse.bin.server")
public class SseBinServerConfig {

    private int port = 9010;

    @Bean
    public SseBinServer sseBinServer() {
        SseBinServer sseBinServer = new SseBinServer(this.port);
        sseBinServer.start();
        return sseBinServer;
    }
}
