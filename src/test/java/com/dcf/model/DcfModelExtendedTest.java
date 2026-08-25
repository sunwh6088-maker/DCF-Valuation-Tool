package com.dcf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三阶段增长率路径 + PE 退出法终值 单元测试。
 */
class DcfModelExtendedTest {

    @Test
    void threeStageGrowthPathLengthAndValues() {
        double[] gs = DcfModel.forecastGrowthPathThreeStage(0.20, 0.10, 0.03, 3, 3, 2);
        assertEquals(8, gs.length);
        assertEquals(0.20, gs[0], 1e-12);
        assertEquals(0.20, gs[2], 1e-12);
        // 成长期过渡：0.20 -> 0.10，步长 (0.10-0.20)/3
        assertEquals(0.20 - 0.10 / 3, gs[3], 1e-12);
        assertEquals(0.10, gs[5], 1e-12);
        // 永续过渡：0.10 -> 0.03，步长 (0.03-0.10)/2
        assertEquals(0.10 - 0.07 / 2, gs[6], 1e-12);
        assertEquals(0.03, gs[7], 1e-12);
    }

    @Test
    void peExitTerminalValue() {
        // 10 年 FCF 各 100，r=10%，期末净利润 150，PE=15
        // TV = 15*150 = 2250；PV(TV) = 2250 / 1.1^10
        double[] fcf = new double[10];
        java.util.Arrays.fill(fcf, 100.0);
        ValuationResult res = DcfModel.dcfValuationPeExit(fcf, 0.10, 15.0, 150.0);
        assertEquals(2250.0, res.terminalValue(), 1e-9);
        double pvTerminal = 2250.0 / Math.pow(1.1, 10);
        assertEquals(pvTerminal, res.pvTerminal(), 1e-9);
        double pvFcf = 0;
        for (int i = 0; i < 10; i++) {
            pvFcf += 100.0 / Math.pow(1.1, i + 1);
        }
        assertEquals(pvFcf, res.pvFcf(), 1e-9);
        assertEquals(pvFcf + pvTerminal, res.enterpriseValue(), 1e-9);
    }

    @Test
    void fullPeExitValuation() {
        double[] growth = DcfModel.forecastGrowthPath(0.10, 0.03, 5, 5);
        ValuationResult res = DcfModel.fullValuationPeExit(
                100.0, growth, 0.10, 15.0, 500.0, 50.0, 100_000_000L, 0.0);
        assertEquals(15.0 * 500.0, res.terminalValue(), 1e-9);
        double expectedEquity = res.enterpriseValue() - 50.0;
        assertEquals(expectedEquity, res.equityValue(), 1e-9);
        assertEquals(expectedEquity / 100_000_000L, res.perShareValue(), 1e-9);
        assertArrayEquals(growth, res.growthPath(), 1e-12);
    }
}
