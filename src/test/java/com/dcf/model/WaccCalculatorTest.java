package com.dcf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WaccCalculator 单元测试：验证 CAPM 股权成本、债务成本、权重与加权公式。
 */
class WaccCalculatorTest {

    private static final double EPS = 1e-9;

    @Test
    void costOfDebtIsRfPlusSpread() {
        assertEquals(0.037, WaccCalculator.costOfDebt(0.017, 0.02), EPS);
        assertEquals(0.017, WaccCalculator.costOfDebt(0.017, 0.0), EPS);
    }

    @Test
    void costOfDebtRejectsNegativeSpread() {
        assertThrows(IllegalArgumentException.class, () -> WaccCalculator.costOfDebt(0.017, -0.01));
    }

    @Test
    void debtWeightUsesDebtOverDebtPlusEquity() {
        assertEquals(0.5, WaccCalculator.debtWeight(100, 100), EPS);
        assertEquals(0.0, WaccCalculator.debtWeight(0, 100), EPS);
        // 负有息负债（异常数据）按 0 兜底
        assertEquals(0.0, WaccCalculator.debtWeight(-50, 100), EPS);
    }

    @Test
    void debtWeightRejectsNonPositiveMarketCap() {
        assertThrows(IllegalArgumentException.class, () -> WaccCalculator.debtWeight(100, 0));
        assertThrows(IllegalArgumentException.class, () -> WaccCalculator.debtWeight(100, -1));
    }

    @Test
    void noDebtCompanyWaccEqualsKe() {
        double ke = 0.08;
        double wacc = WaccCalculator.wacc(ke, 0.04, 0.0, 0.25);
        assertEquals(ke, wacc, EPS);
    }

    @Test
    void leveragedCompanyWaccBelowKe() {
        // ke=10%，kd=4%，债务权重 50%，税率 25% -> WACC = 4%*0.75*0.5 + 10%*0.5 = 6.5%
        double wacc = WaccCalculator.wacc(0.10, 0.04, 0.5, 0.25);
        assertEquals(0.065, wacc, EPS);
        assertTrue(wacc < 0.10);
    }

    @Test
    void taxShieldLowersWaccWhenTaxRises() {
        // 税盾 = 利息 × 税率：税率越高，税后债务成本越低，WACC 越低
        double waccLowTax = WaccCalculator.wacc(0.10, 0.04, 0.5, 0.0);
        double waccHighTax = WaccCalculator.wacc(0.10, 0.04, 0.5, 0.30);
        assertTrue(waccHighTax < waccLowTax, "税率越高，税盾越大，WACC 应越低");
    }

    @Test
    void fullPipelineMatchesManualFormula() {
        // rf=1.7%, beta=0.8, erp=5.5% -> ke=6.1%; kd=1.7%+2%=3.7%
        // D=100, E=400 -> wD=0.2; tax=25% -> WACC = 3.7%*0.75*0.2 + 6.1%*0.8 = 5.435%
        double wacc = WaccCalculator.wacc(0.017, 0.8, 0.055, 0.02, 100, 400, 0.25);
        assertEquals(0.05435, wacc, 1e-9);
    }

    @Test
    void debtWeightBoundaryValidation() {
        assertThrows(IllegalArgumentException.class, () -> WaccCalculator.wacc(0.1, 0.04, 1.5, 0.25));
        assertThrows(IllegalArgumentException.class, () -> WaccCalculator.wacc(0.1, 0.04, -0.1, 0.25));
    }
}
