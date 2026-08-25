package com.dcf.model;

import com.dcf.data.model.HistoricalData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Piotroski F-Score 单元测试：手工构造两年数据，逐项核对 9 个指标。
 */
class FScoreCalculatorTest {

    /**
     * 构造 2 年数据（2021/2022），2022 年各项设计为全部命中：
     * ROA>0、CFO>0、ROA 改善、CFO>NI、杠杆下降、流动比率上升、未发新股、毛利率上升、周转率上升。
     */
    private static HistoricalData allPassData() {
        int[] years = {2021, 2022};
        double[] ocf = {80, 200};          // CFO > NI
        double[] capex = {20, 30};
        double[] revenue = {400, 500};
        double[] ebit = {100, 150};
        double[] netIncome = {60, 100};    // ROA 2021=60/500=0.12, 2022=100/600=0.167 -> 改善
        double[] totalAssets = {500, 600};
        double[] totalLiab = {250, 280};   // 杠杆 0.5 -> 0.4667 下降
        double[] curAssets = {200, 260};   // 流动比率 2.0 -> 2.167 上升
        double[] curLiab = {100, 120};
        double[] grossProfit = {200, 260}; // 毛利率 0.5 -> 0.52 上升
        double[] shares = {1000, 1000};    // 未增发
        // 周转率 0.8 -> 0.833 上升
        HistoricalData.ExtraFinancials extra = new HistoricalData.ExtraFinancials(
                netIncome, null, totalAssets, totalLiab, curAssets, curLiab, grossProfit, shares);
        return new HistoricalData(years, ocf, capex, revenue, ebit,
                new double[]{0, 0}, new double[]{0, 0}, extra);
    }

    @Test
    void allNineItemsPass() {
        List<FScoreCalculator.FScoreResult> scores = FScoreCalculator.calc(allPassData(), 3);
        assertEquals(1, scores.size(), "2 年数据只有 1 个可计分年份");
        FScoreCalculator.FScoreResult s = scores.get(0);
        assertEquals(2022, s.year());
        assertEquals(9, s.score());
        assertEquals(9, s.itemsAvailable());
        for (Boolean b : s.items()) {
            assertEquals(Boolean.TRUE, b);
        }
    }

    @Test
    void missingDataMarksNull() {
        int[] years = {2021, 2022};
        double[] ocf = {80, 200};
        double[] capex = {20, 30};
        double[] revenue = {400, 500};
        double[] ebit = {100, 150};
        // 只有净利润与总资产，其余缺失
        HistoricalData.ExtraFinancials extra = new HistoricalData.ExtraFinancials(
                new double[]{60, 100}, null, new double[]{500, 600}, null,
                null, null, null, null);
        HistoricalData h = new HistoricalData(years, ocf, capex, revenue, ebit,
                new double[]{0, 0}, new double[]{0, 0}, extra);
        List<FScoreCalculator.FScoreResult> scores = FScoreCalculator.calc(h, 3);
        assertEquals(1, scores.size());
        FScoreCalculator.FScoreResult s = scores.get(0);
        // ROA>0 ✓、CFO>0 ✓、ROA 改善 ✓、CFO>NI ✓、杠杆 null、流动比率 null、新股 null、毛利 null、
        // 周转率（营收/总资产）有数据且改善 ✓
        assertEquals(Boolean.TRUE, s.items()[0]);
        assertEquals(Boolean.TRUE, s.items()[1]);
        assertEquals(Boolean.TRUE, s.items()[2]);
        assertEquals(Boolean.TRUE, s.items()[3]);
        assertNull(s.items()[4]);
        assertNull(s.items()[5]);
        assertNull(s.items()[6]);
        assertNull(s.items()[7]);
        assertEquals(Boolean.TRUE, s.items()[8]);
        assertEquals(5, s.score());
        assertEquals(5, s.itemsAvailable());
    }

    @Test
    void maxYearsLimit() {
        int[] years = {2019, 2020, 2021, 2022};
        double[] ocf = {100, 110, 120, 130};
        double[] capex = {30, 30, 30, 30};
        double[] revenue = {400, 440, 480, 520};
        double[] ebit = {100, 110, 120, 130};
        double[] ni = {60, 66, 72, 78};
        double[] ta = {500, 550, 600, 650};
        HistoricalData.ExtraFinancials extra = new HistoricalData.ExtraFinancials(
                ni, null, ta, null, null, null, null, null);
        HistoricalData h = new HistoricalData(years, ocf, capex, revenue, ebit,
                new double[]{0, 0, 0, 0}, new double[]{0, 0, 0, 0}, extra);
        List<FScoreCalculator.FScoreResult> scores = FScoreCalculator.calc(h, 3);
        assertEquals(3, scores.size());
        assertEquals(2020, scores.get(0).year());
        assertEquals(2022, scores.get(2).year());
    }
}
