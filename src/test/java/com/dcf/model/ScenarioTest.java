package com.dcf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三情景测试：偏移量约定、估值单调性（保守 ≤ 中性 ≤ 乐观）、极端参数不崩溃。
 */
class ScenarioTest {

    @Test
    void deltasFollowBusinessConvention() {
        assertEquals(-0.02, Scenario.CONSERVATIVE.gFirstDelta(), 1e-9);
        assertEquals(+0.015, Scenario.CONSERVATIVE.discountDelta(), 1e-9);
        assertEquals(-0.005, Scenario.CONSERVATIVE.gTerminalDelta(), 1e-9);
        assertEquals(0.0, Scenario.BASE.gFirstDelta(), 1e-9);
        assertEquals(+0.02, Scenario.OPTIMISTIC.gFirstDelta(), 1e-9);
        assertEquals(-0.015, Scenario.OPTIMISTIC.discountDelta(), 1e-9);
    }

    @Test
    void perShareValueIsMonotonicAcrossScenarios() {
        double baseFcf = 100.0;
        double gFirst = 0.08;
        double gTerminal = 0.025;
        double rate = 0.09;
        double netDebt = 0;
        double minority = 0;
        double shares = 10;

        double conservative = ScenarioValuer.value(Scenario.CONSERVATIVE, baseFcf, gFirst, gTerminal, rate, netDebt, minority, shares).perShareValue();
        double base = ScenarioValuer.value(Scenario.BASE, baseFcf, gFirst, gTerminal, rate, netDebt, minority, shares).perShareValue();
        double optimistic = ScenarioValuer.value(Scenario.OPTIMISTIC, baseFcf, gFirst, gTerminal, rate, netDebt, minority, shares).perShareValue();

        assertTrue(conservative <= base, "保守情景应不高于中性：" + conservative + " vs " + base);
        assertTrue(base <= optimistic, "中性情景应不高于乐观：" + base + " vs " + optimistic);
        // 单调性不是严格递增即可，但通常差异明显
        assertTrue(optimistic - conservative > 1.0, "情景差异应明显（当前 " + (optimistic - conservative) + "）");
    }

    @Test
    void extremeParametersDoNotCrash() {
        // 高永续增长率 + 低折现率：r 会被强制拉开到 g + 0.5pp，不应抛异常
        double v = ScenarioValuer.value(Scenario.CONSERVATIVE, 100.0, 0.08, 0.045, 0.05, 0, 0, 10).perShareValue();
        assertTrue(Double.isFinite(v));
        assertTrue(v > 0);
    }
}
