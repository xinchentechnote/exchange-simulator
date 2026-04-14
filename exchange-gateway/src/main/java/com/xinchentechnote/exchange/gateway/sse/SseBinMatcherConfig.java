package com.xinchentechnote.exchange.gateway.sse;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "sse.bin.matcher")
public class SseBinMatcherConfig implements InitializingBean {
    private String symbolInfoPath;
    private String accountInfoPath;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println(this);
    }
}
