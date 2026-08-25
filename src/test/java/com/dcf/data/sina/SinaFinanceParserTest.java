package com.dcf.data.sina;

import com.dcf.data.model.HistoricalData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SinaFinanceParser 单元测试：模拟接口 JSON 结构验证提取逻辑。
 */
class SinaFinanceParserTest {

    /** 构造模拟的三表数据。 */
    private Map<String, Map<String, Double>> sheet(String datePrefix, double cash, double capex) {
        return Map.of(datePrefix + "-12-31", Map.of(
                "经营活动产生的现金流量净额", cash,
                "购建固定资产、无形资产和其他长期资产支付的现金", capex));
    }

    @Test
    void testExtractAnnualOnly() {
        Map<String, Map<String, Double>> cashFlow = Map.of(
                "2024-12-31", Map.of("经营活动产生的现金流量净额", 100.0, "购建固定资产、无形资产和其他长期资产支付的现金", 40.0),
                "2024-06-30", Map.of("经营活动产生的现金流量净额", 50.0, "购建固定资产、无形资产和其他长期资产支付的现金", 20.0), // 中报应被过滤
                "2023-12-31", Map.of("经营活动产生的现金流量净额", 90.0, "购建固定资产、无形资产和其他长期资产支付的现金", 35.0));
        Map<String, Map<String, Double>> profit = Map.of(
                "2024-12-31", Map.of("营业总收入", 500.0, "营业利润", 200.0, "利润总额", 180.0, "所得税费用", 45.0),
                "2023-12-31", Map.of("营业总收入", 450.0, "营业利润", 180.0, "利润总额", 160.0, "所得税费用", 40.0));
        Map<String, Map<String, Double>> balance = Map.of();

        HistoricalData h = SinaFinanceParser.extract(balance, profit, cashFlow, 10);
        assertEquals(2, h.size());
        assertArrayEquals(new int[]{2023, 2024}, h.years());
        assertArrayEquals(new double[]{90, 100}, h.ocf(), 1e-9);
        assertArrayEquals(new double[]{35, 40}, h.capex(), 1e-9);
        assertArrayEquals(new double[]{450, 500}, h.revenue(), 1e-9);
        assertArrayEquals(new double[]{40, 45}, h.taxExpense(), 1e-9);
    }

    @Test
    void testMaxYearsLimit() {
        Map<String, Map<String, Double>> cashFlow = new java.util.LinkedHashMap<>();
        for (int y = 2015; y <= 2024; y++) {
            cashFlow.put(y + "-12-31", Map.of("经营活动产生的现金流量净额", (double) y));
        }
        HistoricalData h = SinaFinanceParser.extract(Map.of(), Map.of(), cashFlow, 5);
        assertEquals(5, h.size());
        // 应取最近 5 年：2020-2024
        assertArrayEquals(new int[]{2020, 2021, 2022, 2023, 2024}, h.years());
    }

    @Test
    void testMissingItemYieldsNaN() {
        Map<String, Map<String, Double>> cashFlow = Map.of(
                "2024-12-31", Map.of("经营活动产生的现金流量净额", 100.0)); // 缺 capex
        HistoricalData h = SinaFinanceParser.extract(Map.of(), Map.of(), cashFlow, 10);
        assertTrue(Double.isNaN(h.capex()[0]));
    }

    @Test
    void testNormalizeRemovesFullwidthAndSpace() {
        assertEquals("经营活动产生的现金流量净额", SinaFinanceParser.normalize("经营活动产生的现金流量净额 "));
        assertEquals("abc", SinaFinanceParser.normalize("ＡＢＣ"));
    }

    @Test
    void testParseJsonStructure() {
        // 模拟接口 JSON（report_date + report_list）
        String json = """
                {"result":{"data":{
                  "report_date":[{"date_value":"2024-12-31"},{"date_value":"2023-12-31"}],
                  "report_list":{
                    "2024-12-31":{"data":[{"item_title":"经营活动产生的现金流量净额","item_value":"100.5"}]},
                    "2023-12-31":{"data":[{"item_title":"经营活动产生的现金流量净额","item_value":"90.5"}]}
                  }
                }}}
                """;
        Map<String, Map<String, Double>> parsed = SinaFinanceClient.parse(json);
        assertEquals(2, parsed.size());
        assertEquals(100.5, parsed.get("2024-12-31").get("经营活动产生的现金流量净额"), 1e-9);
    }
}