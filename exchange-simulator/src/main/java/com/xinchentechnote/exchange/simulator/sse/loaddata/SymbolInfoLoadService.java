package com.xinchentechnote.exchange.simulator.sse.loaddata;

import com.google.common.io.Files;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.SymbolType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SymbolInfoLoadService implements IDataLoadService<CoreSymbolSpecification> {

    @Override
    public List<CoreSymbolSpecification> loadData(String path) {
        // 从CSV文件加载：
        List<CoreSymbolSpecification> symbolInfos = new ArrayList<>();
        try {
            List<String> strings = Files.readLines(new File(path), StandardCharsets.UTF_8);
            for (int i = 1; i < strings.size(); i++) {
                String line = strings.get(i).trim();
                if (line.startsWith("#") || line.startsWith("//")){
                    continue;
                }
                String[] split = line.split(",");
                // symbolId,type,baseCurrency,quoteCurrency,baseScaleK,quoteScaleK,takerFee,makerFee,marginBuy,marginSell
                CoreSymbolSpecification build = CoreSymbolSpecification.builder()
                        .symbolId(Integer.parseInt(split[0]))
                        .type(SymbolType.of(Integer.parseInt(split[1])))
                        .baseCurrency(Integer.parseInt(split[2]))
                        .quoteCurrency(Integer.parseInt(split[3]))
                        .baseScaleK(Long.parseLong(split[4]))
                        .quoteScaleK(Long.parseLong(split[5]))
                        .takerFee(Long.parseLong(split[6]))
                        .makerFee(Long.parseLong(split[7]))
                        .marginBuy(Long.parseLong(split[8]))
                        .marginSell(Long.parseLong(split[9]))
                        .build();
                symbolInfos.add(build);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return symbolInfos;
    }
}
