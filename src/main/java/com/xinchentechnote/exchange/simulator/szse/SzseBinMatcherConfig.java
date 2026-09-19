package com.xinchentechnote.exchange.simulator.szse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "szse.bin.matcher")
public class SzseBinMatcherConfig {
    private String symbolInfoPath;
    private String accountInfoPath;
}
