package com.example.claims.exposure;

import com.example.claims.exposure.ExposureController.CurrencyTotal;
import com.example.claims.exposure.ExposureViewRepository.ExposureLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExposureTotalsTest {

    @Test
    void sumsWithinACurrencyButNeverAcrossCurrencies() {
        List<CurrencyTotal> totals = ExposureController.totalsByCurrency(List.of(
                new ExposureLine("SGD", "SG", "MOTOR", 2, 1, new BigDecimal("1000.00")),
                new ExposureLine("SGD", "SG", "PROPERTY", 1, 0, new BigDecimal("500.50")),
                new ExposureLine("AUD", "AU", "PROPERTY", 3, 0, new BigDecimal("9000.00"))));

        assertThat(totals).containsExactly(
                new CurrencyTotal("AUD", 3, 0, new BigDecimal("9000.00")),
                new CurrencyTotal("SGD", 3, 1, new BigDecimal("1500.50")));
    }

    @Test
    void noOpenClaimsMeansNoTotals() {
        assertThat(ExposureController.totalsByCurrency(List.of())).isEmpty();
    }
}
