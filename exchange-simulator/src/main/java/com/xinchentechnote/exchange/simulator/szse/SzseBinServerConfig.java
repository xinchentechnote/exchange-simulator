package com.xinchentechnote.exchange.simulator.szse;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "szse.bin.server")
public class SzseBinServerConfig implements InitializingBean {

    private int port = 9011;
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println(this);
    }

    @Bean
    public SzseBinServer szseBinServer(){
        SzseBinServer szseBinServer = new SzseBinServer(port);
        szseBinServer.start();
        return szseBinServer;
    }
}
