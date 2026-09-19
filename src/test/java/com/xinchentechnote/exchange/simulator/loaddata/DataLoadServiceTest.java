package com.xinchentechnote.exchange.simulator.loaddata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import exchange.core2.core.common.SymbolType;
import exchange.core2.core.common.api.ApiAdjustUserBalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataLoadServiceTest {

    @TempDir
    Path tempDir;

    private Path writeCsv(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void shouldLoadSymbolsAndSkipComments() throws IOException {
        Path csv = writeCsv("symbol.csv", String.join("\n",
                "symbolId,type,baseCurrency,quoteCurrency,baseScaleK,quoteScaleK,takerFee,makerFee,marginBuy,marginSell",
                "// commented",
                "600000,0,840,840,100,1,3,2,0,0"));

        List<exchange.core2.core.common.CoreSymbolSpecification> symbols = new SymbolInfoLoadService().loadData(csv.toString());
        assertEquals(1, symbols.size());
        assertEquals(600000, symbols.get(0).symbolId);
        assertEquals(SymbolType.CURRENCY_EXCHANGE_PAIR, symbols.get(0).type);
    }

    @Test
    void missingSymbolFileShouldFailFast() {
        File missing = tempDir.resolve("missing.csv").toFile();
        assertThrows(IllegalStateException.class,
                () -> new SymbolInfoLoadService().loadData(missing.getPath()));
    }

    @Test
    void badSymbolRowShouldFailFast() throws IOException {
        Path csv = writeCsv("bad_symbol.csv", String.join("\n",
                "symbolId,type,baseCurrency,quoteCurrency,baseScaleK,quoteScaleK,takerFee,makerFee,marginBuy,marginSell",
                "600000,0,840")); // 列数不足
        assertThrows(IllegalStateException.class,
                () -> new SymbolInfoLoadService().loadData(csv.toString()));
    }

    @Test
    void shouldLoadAccounts() throws IOException {
        Path csv = writeCsv("account.csv", String.join("\n",
                "uid,currency,amount",
                "10001,840,1000000",
                "10002,840,500000"));

        List<ApiAdjustUserBalance> accounts = new AccountInfoLoadService().loadData(csv.toString());
        assertEquals(2, accounts.size());
        assertEquals(10001L, accounts.get(0).uid);
        assertEquals(1000000L, accounts.get(0).amount);
    }

    @Test
    void missingAccountFileShouldFailFast() {
        File missing = tempDir.resolve("missing_account.csv").toFile();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AccountInfoLoadService().loadData(missing.getPath()));
        assertTrue(ex.getMessage().contains("not found"));
    }
}
