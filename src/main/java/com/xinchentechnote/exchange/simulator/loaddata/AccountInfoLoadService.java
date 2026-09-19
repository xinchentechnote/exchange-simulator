package com.xinchentechnote.exchange.simulator.loaddata;

import com.google.common.io.Files;
import exchange.core2.core.common.api.ApiAdjustUserBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AccountInfoLoadService implements IDataLoadService<ApiAdjustUserBalance> {

    private static final int COLUMNS = 3;

    @Override
    public List<ApiAdjustUserBalance> loadData(String path) {
        File file = new File(path);
        if (!file.exists()) {
            //基础数据缺失时必须终止启动，否则市场会以空数据静默运行
            throw new IllegalStateException("Account data file not found: " + path);
        }
        List<ApiAdjustUserBalance> userBalances = new ArrayList<>();
        try {
            List<String> strings = Files.readLines(file, StandardCharsets.UTF_8);
            for (int i = 1; i < strings.size(); i++) {
                String line = strings.get(i).trim();
                if (line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                String[] split = line.split(",");
                if (split.length < COLUMNS) {
                    throw new IllegalStateException("Invalid account csv line " + (i + 1) + " in " + path
                            + ": expect " + COLUMNS + " columns, got " + split.length);
                }
                try {
                    // uid,currency,amount
                    ApiAdjustUserBalance build = ApiAdjustUserBalance.builder()
                            .uid(Long.parseLong(split[0]))
                            .currency(Integer.parseInt(split[1]))
                            .amount(Long.parseLong(split[2]))
                            .build();
                    userBalances.add(build);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Failed to parse account csv line " + (i + 1) + " in " + path + ": " + line, e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read account data file: " + path, e);
        }
        log.info("Loaded {} account balances from {}", userBalances.size(), path);
        return userBalances;
    }
}
