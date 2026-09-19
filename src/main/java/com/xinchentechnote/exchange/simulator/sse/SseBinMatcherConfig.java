package com.xinchentechnote.exchange.simulator.sse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "sse.bin.matcher")
public class SseBinMatcherConfig {
    private String symbolInfoPath;
    private String accountInfoPath;
}
