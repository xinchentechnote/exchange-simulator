package com.xinchentechnote.exchange.simulator.loaddata;

import com.google.common.io.Files;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.SymbolType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SymbolInfoLoadService implements IDataLoadService<CoreSymbolSpecification> {

    private static final int COLUMNS = 10;

    @Override
    public List<CoreSymbolSpecification> loadData(String path) {
        File file = new File(path);
        if (!file.exists()) {
            //基础数据缺失时必须终止启动，否则市场会以空数据静默运行
            throw new IllegalStateException("Symbol data file not found: " + path);
        }
        List<CoreSymbolSpecification> symbolInfos = new ArrayList<>();
        try {
            List<String> strings = Files.readLines(file, StandardCharsets.UTF_8);
            for (int i = 1; i < strings.size(); i++) {
                String line = strings.get(i).trim();
                if (line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                String[] split = line.split(",");
                if (split.length < COLUMNS) {
                    throw new IllegalStateException("Invalid symbol csv line " + (i + 1) + " in " + path
                            + ": expect " + COLUMNS + " columns, got " + split.length);
                }
                try {
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
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Failed to parse symbol csv line " + (i + 1) + " in " + path + ": " + line, e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read symbol data file: " + path, e);
        }
        log.info("Loaded {} symbols from {}", symbolInfos.size(), path);
        return symbolInfos;
    }
}
