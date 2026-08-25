package com.dcf.service;

import com.dcf.data.model.HistoricalData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ReferenceCalculator 单元测试：有效税率参考、营收 CAGR 参考。 */
class ReferenceCalculatorTest {

    /** 构造 5 年历史数据（末 3 年有效税率均为 25%）。 */
    private static HistoricalData history(double[] revenue, double[] pretax, double[] tax) {
        int n = revenue.length;
        double[] ocf = new double[n], capex = new double[n], ebit = new double[n];
        int[] years = new int[n];
        for (int i = 0; i < n; i++) {
            years[i] = 2020 + i;
            ocf[i] = 100;
            capex[i] = 30;
            ebit[i] = pretax[i];
        }
        return new HistoricalData(years, ocf, capex, revenue, ebit, pretax, tax);
    }

    @Test
    void effectiveTaxRateUsesWeightedThreeYears() {
        // 近 3 年：tax 25+20+30=75，pretax 100+80+120=300 → 25%
        HistoricalData h = history(
                new double[]{100, 110, 121, 133, 146},
                new double[]{50, 60, 100, 80, 120},
                new double[]{12, 15, 25, 20, 30});
        assertEquals(0.25, ReferenceCalculator.effectiveTaxRate(h), 1e-9);
    }

    @Test
    void effectiveTaxRateUsesAllWhenLessThanThreeYears() {
        // 只有 2 年：tax 10+20=30，pretax 100+80=180 → 16.6667%
        HistoricalData h = history(
                new double[]{100, 110},
                new double[]{100, 80},
                new double[]{10, 20});
        assertEquals(0.1666666667, ReferenceCalculator.effectiveTaxRate(h), 1e-6);
    }

    @Test
    void effectiveTaxRateSkipsLossYears() {
        // 近 3 年有亏损年（-100）→ 该年不参与：tax 0+20+30=50，pretax 80+120=200 → 25%
        HistoricalData h = history(
                new double[]{100, 110, 121, 133, 146},
                new double[]{50, 60, -100, 80, 120},
                new double[]{12, 15, 0, 20, 30});
        assertEquals(0.25, ReferenceCalculator.effectiveTaxRate(h), 1e-9);
    }

    @Test
    void effectiveTaxRateReturnsNanWhenEmpty() {
        HistoricalData h = history(new double[]{}, new double[]{}, new double[]{});
        assertTrue(Double.isNaN(ReferenceCalculator.effectiveTaxRate(h)));
    }

    @Test
    void revenueCagrComputesCompoundGrowth() {
        // 100 → 110 → 121：两年 CAGR = 10%
        HistoricalData h = history(new double[]{100, 110, 121}, new double[]{50, 55, 60}, new double[]{12, 13, 15});
        assertEquals(0.10, ReferenceCalculator.revenueCagr(h), 1e-9);
    }

    @Test
    void revenueCagrReturnsNanWhenTooFewYears() {
        HistoricalData h = history(new double[]{100, 110}, new double[]{50, 55}, new double[]{12, 13});
        assertTrue(Double.isNaN(ReferenceCalculator.revenueCagr(h)));
    }

    @Test
    void revenueCagrReturnsNanWhenNonPositive() {
        HistoricalData h = history(new double[]{100, 0, 121}, new double[]{50, 55, 60}, new double[]{12, 13, 15});
        assertTrue(Double.isNaN(ReferenceCalculator.revenueCagr(h)));
    }
}
