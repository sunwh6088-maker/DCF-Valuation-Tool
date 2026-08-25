package com.dcf.model;

import com.dcf.data.model.HistoricalData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FcfCalculator 单元测试：简化 FCF 与 FCFF 口径、基准值回退。
 */
class FcfCalculatorTest {

    private static HistoricalData hist(double[] ocf, double[] capex, double[] ebit,
                                       double[] dep, double[] ca, double[] cl) {
        HistoricalData.ExtraFinancials extra = new HistoricalData.ExtraFinancials(
                null, dep, null, null, ca, cl, null, null);
        int n = ocf.length;
        int[] years = new int[n];
        for (int i = 0; i < n; i++) {
            years[i] = 2020 + i;
        }
        return new HistoricalData(years, ocf, capex, new double[n], ebit,
                new double[n], new double[n], extra);
    }

    @Test
    void simpleSeriesIsOcfMinusCapex() {
        HistoricalData h = hist(new double[]{100, 120}, new double[]{30, 40},
                new double[]{0, 0}, null, null, null);
        assertArrayEquals(new double[]{70, 80}, FcfCalculator.simpleSeries(h), 1e-9);
    }

    @Test
    void fcffSeriesHandComputed() {
        // 2021: EBIT=200, D&A=50, Capex=40, NWC=100-60=40
        // 2022: EBIT=220, D&A=55, Capex=45, NWC=120-70=50 -> dNwc=10
        // FCFF_2022 = 220*0.75 + 55 - 45 - 10 = 165
        HistoricalData h = hist(new double[]{0, 0}, new double[]{40, 45},
                new double[]{200, 220}, new double[]{50, 55},
                new double[]{100, 120}, new double[]{60, 70});
        double[] fcff = FcfCalculator.fcffSeries(h, 0.25);
        assertTrue(Double.isNaN(fcff[0]), "首年无 ΔNWC 应为 NaN");
        assertEquals(165.0, fcff[1], 1e-9);
    }

    @Test
    void fcffMissingDepreciationIsNaN() {
        HistoricalData h = hist(new double[]{0, 0}, new double[]{40, 45},
                new double[]{200, 220}, null, new double[]{100, 120}, new double[]{60, 70});
        double[] fcff = FcfCalculator.fcffSeries(h, 0.25);
        assertTrue(Double.isNaN(fcff[1]));
    }

    @Test
    void baseValuePrefersLatestThenMean() {
        assertEquals(80.0, FcfCalculator.baseValue(new double[]{70, 80}), 1e-9);
        // 最新值缺失 -> 回退近 3 年可用均值
        assertEquals(90.0, FcfCalculator.baseValue(new double[]{Double.NaN, 90.0, Double.NaN}), 1e-9);
        assertTrue(Double.isNaN(FcfCalculator.baseValue(new double[]{Double.NaN, Double.NaN})));
    }

    @Test
    void netWorkingCapital() {
        HistoricalData h = hist(new double[]{0}, new double[]{0}, new double[]{0},
                null, new double[]{100}, new double[]{60});
        assertEquals(40.0, FcfCalculator.netWorkingCapital(h, 0), 1e-9);
    }
}
