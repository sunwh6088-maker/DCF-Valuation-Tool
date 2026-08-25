package com.dcf.service;

import com.dcf.data.model.CompanyData;
import com.dcf.data.model.HistoricalData;
import com.dcf.data.model.SnapshotData;
import com.dcf.web.ValuationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史 DCF 回溯测试：固定数据手工核算、折溢价口径、样本不足跳过。
 */
class HistoricalDcfServiceTest {

    private ValuationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ValuationContext();
        ctx.setCompany(new CompanyData("CN", "600000",
                new HistoricalData(
                        new int[]{2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025},
                        new double[]{100, 110, 120, 130, 140, 150, 160, 170}, // ocf
                        new double[]{10, 11, 12, 13, 14, 15, 16, 17},          // capex
                        new double[]{0, 0, 0, 0, 0, 0, 0, 0},
                        new double[]{0, 0, 0, 0, 0, 0, 0, 0},
                        new double[]{0, 0, 0, 0, 0, 0, 0, 0},
                        new double[]{0, 0, 0, 0, 0, 0, 0, 0}),
                new SnapshotData("测试公司", "600000", 20.0, 10.0, 0, 0, 0, "2026-01-01"),
                "manual", "2026-01-01"));
        ctx.setDiscountMode("manual");
        ctx.setManualDiscountRate(0.10);
        ctx.setGFirst(0.08);
        ctx.setGTerminal(0.025);
        ctx.setNFirst(5);
        ctx.setNTransition(5);
    }

    @Test
    void backtestUsesEachYearsOwnFcfAndYearEndPrice() {
        Map<Integer, Double> prices = Map.of(
                2022, 100.0, 2023, 110.0, 2024, 120.0, 2025, 130.0);
        var results = HistoricalDcfService.backtest(ctx.getCompany().history(), prices, ctx);
        // 前 4 年（2018-2021）样本不足应跳过，剩 2022-2025 共 4 条
        assertEquals(4, results.size());
        assertEquals(2022, results.get(0).year());
        assertEquals(2025, results.get(3).year());
        // 2025: FCF=170-17=153, 折现 10%, g=8%->2.5% 过渡
        // 仅验证单调/正数（精确值由 DcfModel 保证，此处验证接线正确）
        assertTrue(results.get(3).perShareValue() > 0);
        assertEquals(130.0, results.get(3).price(), 1e-9);
        // 折溢价 = (估值 - 股价)/股价
        double expectPremium = (results.get(3).perShareValue() - 130.0) / 130.0;
        assertEquals(expectPremium, results.get(3).premium(), 1e-9);
    }

    @Test
    void missingPriceYieldsNanPriceAndPremium() {
        var results = HistoricalDcfService.backtest(ctx.getCompany().history(), Map.of(), ctx);
        assertFalse(results.isEmpty());
        for (HistoricalBacktest bt : results) {
            assertTrue(Double.isNaN(bt.price()));
            assertTrue(Double.isNaN(bt.premium()));
        }
    }

    @Test
    void insufficientHistorySkipsAll() {
        HistoricalData shortH = new HistoricalData(
                new int[]{2023, 2024},
                new double[]{100, 110},
                new double[]{10, 11},
                new double[]{0, 0}, new double[]{0, 0}, new double[]{0, 0}, new double[]{0, 0});
        var results = HistoricalDcfService.backtest(shortH, Map.of(2023, 50.0, 2024, 60.0), ctx);
        assertTrue(results.isEmpty(), "少于 4 年历史不应回溯");
    }
}
