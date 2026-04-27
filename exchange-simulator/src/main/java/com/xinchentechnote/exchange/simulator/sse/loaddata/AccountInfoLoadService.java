package com.xinchentechnote.exchange.simulator.sse.loaddata;

import com.google.common.io.Files;
import exchange.core2.core.common.api.ApiAdjustUserBalance;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountInfoLoadService implements IDataLoadService<ApiAdjustUserBalance>{
    @Override
    public List<ApiAdjustUserBalance> loadData(String path) {
        List<ApiAdjustUserBalance> userBalances = new ArrayList<>();
        //从csv文件加载
        try {
            List<String> strings = Files.readLines(new File(path), StandardCharsets.UTF_8);
            for (int i = 1; i < strings.size(); i++) {
                String line = strings.get(i).trim();
                if (line.startsWith("#") || line.startsWith("//")){
                    continue;
                }
                String[] split = line.split(",");
                // uid,currency,amount,transactionId
                ApiAdjustUserBalance build = ApiAdjustUserBalance.builder()
                        .uid(Long.parseLong(split[0]))
                        .currency(Integer.parseInt(split[1]))
                        .amount(Long.parseLong(split[2]))
                        .build();
                userBalances.add(build);
            }
        }catch (IOException e){
            e.printStackTrace();
        }

        return userBalances;
    }
}
