package com.dcf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 辅助指标测试：分级边界、回本年限、隐含年化回报。
 */
class IndicatorsTest {

    @Test
    void verdictBoundaries() {
        assertEquals(Indicators.Verdict.UNDERVALUED, Indicators.verdict(13, 10));   // 1.30
        assertEquals(Indicators.Verdict.UNDERVALUED, Indicators.verdict(13.5, 10)); // >1.3
        assertEquals(Indicators.Verdict.CHEAP, Indicators.verdict(12, 10));         // 1.2
        assertEquals(Indicators.Verdict.FAIR, Indicators.verdict(10, 10));          // 1.0
        assertEquals(Indicators.Verdict.FAIR, Indicators.verdict(9.5, 10));         // 0.95
        assertEquals(Indicators.Verdict.EXPENSIVE, Indicators.verdict(8, 10));      // 0.8
        assertEquals(Indicators.Verdict.OVERVALUED, Indicators.verdict(6, 10));     // 0.6
        assertEquals(Indicators.Verdict.FAIR, Indicators.verdict(10, 0));           // 股价非法兜底
    }

    @Test
    void paybackYearsIsMarketCapOverFcf() {
        assertEquals(10.0, Indicators.paybackYears(1000, 100), 1e-9);
        assertTrue(Double.isNaN(Indicators.paybackYears(1000, 0)));
        assertTrue(Double.isNaN(Indicators.paybackYears(1000, -50)));
        assertTrue(Double.isNaN(Indicators.paybackYears(0, 100)));
    }

    @Test
    void impliedAnnualReturnIsCompounded() {
        // 内在价值 2 倍于股价、10 年回归 -> 年化约 7.18%
        double r = Indicators.impliedAnnualReturn(200, 100, 10);
        assertEquals(Math.pow(2, 0.1) - 1, r, 1e-9);
        assertTrue(r > 0.07 && r < 0.08);
        assertTrue(Double.isNaN(Indicators.impliedAnnualReturn(100, 0, 10)));
    }
}
