package com.dcf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DcfModel 单元测试：数值对照 + 边界条件（平移自 Python 原型的 test_model.py）。
 */
class DcfModelTest {

    @Test
    void testCapm() {
        assertEquals(0.08, DcfModel.capmCostOfEquity(0.02, 1.0, 0.06), 1e-12);
        assertEquals(0.05, DcfModel.capmCostOfEquity(0.017, 0.6, 0.055), 1e-12);
    }

    @Test
    void testForecastGrowthPath() {
        double[] gs = DcfModel.forecastGrowthPath(0.10, 0.02, 5, 5);
        assertEquals(10, gs.length);
        assertEquals(0.10, gs[0], 1e-12);
        assertEquals(0.10, gs[4], 1e-12);
        // 过渡第 1 年：0.10 + (0.02-0.10)*1/5 = 0.084
        assertEquals(0.084, gs[5], 1e-12);
        assertEquals(0.02, gs[9], 1e-12);
    }

    @Test
    void testForecastFcfCompounding() {
        double[] fcf = DcfModel.forecastFcf(100.0, new double[]{0.10, 0.10, 0.10});
        assertArrayEquals(new double[]{110.0, 121.0, 133.1}, fcf, 1e-9);
    }

    @Test
    void testDcfValuationConstantFcf() {
        // 零增长、r=10% 时 EV 应等于 FCF/r = 1000（年金+终值分解恰好重合）
        double[] fcf = new double[10];
        java.util.Arrays.fill(fcf, 100.0);
        ValuationResult res = DcfModel.dcfValuation(fcf, 0.10, 0.0);
        assertEquals(1000.0, res.enterpriseValue(), 1e-6);
        assertTrue(res.terminalRatio() > 0 && res.terminalRatio() < 1);
    }

    @Test
    void testGmustBeLessThanR() {
        double[] fcf = new double[10];
        java.util.Arrays.fill(fcf, 100.0);
        assertThrows(IllegalArgumentException.class, () -> DcfModel.dcfValuation(fcf, 0.05, 0.05));
        assertThrows(IllegalArgumentException.class, () -> DcfModel.dcfValuation(fcf, 0.05, 0.06));
    }

    @Test
    void testNegativeFcfAllowed() {
        double[] fcf = {-100.0, 50.0, 80.0, 100.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0};
        ValuationResult res = DcfModel.dcfValuation(fcf, 0.10, 0.02);
        assertTrue(Double.isFinite(res.enterpriseValue()));
    }

    @Test
    void testEquityAndPerShare() {
        double equity = DcfModel.equityValue(1000.0, 200.0, 50.0);
        assertEquals(750.0, equity, 1e-9);
        assertEquals(7.5, DcfModel.perShareValue(equity, 100.0), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> DcfModel.perShareValue(equity, 0.0));
    }

    @Test
    void testFullValuation() {
        ValuationResult res = DcfModel.fullValuation(
                100.0, 0.08, 0.02, 0.10, -500.0, 1_256_000_000L, 0.3, 5, 5);
        assertEquals(10, res.fcfForecast().length);
        double expected = (res.enterpriseValue() + 500.0 - 0.3) / 1_256_000_000L;
        assertEquals(expected, res.perShareValue(), 1e-9);
        assertTrue(res.perShareValue() > 0);
    }

    @Test
    void testSensitivityMatrix() {
        SensitivityResult mat = DcfModel.sensitivityMatrix(
                100.0, 0.08,
                new double[]{0.08, 0.10, 0.12},
                new double[]{0.02, 0.03, 0.04},
                0.0, 100.0, 0.0, 5, 5);
        assertEquals(3, mat.rows());
        assertEquals(3, mat.cols());
        // 网格内所有组合 g < r，应全部可计算
        for (double[] row : mat.values()) {
            for (double v : row) {
                assertFalse(Double.isNaN(v));
            }
        }
        // 折现率越高、永续增长率越低，每股价值越低（同列下行递减、同行右列递增）
        assertTrue(mat.values()[2][0] < mat.values()[0][2]);
    }
}