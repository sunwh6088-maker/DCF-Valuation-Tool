package com.dcf.data.csv;

import com.dcf.data.model.HistoricalData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CsvImporter 单元测试：模板解析、理杏仁列名兼容、年份/数值校验。
 */
class CsvImporterTest {

    @Test
    void testParseTemplateCsv() {
        String csv = """
                年份,经营现金流,资本开支,营收,EBIT,税前利润,所得税费用
                2024,100,40,500,200,180,45
                2023,90,35,450,180,160,40
                2022,80,30,400,160,140,35
                """;
        HistoricalData h = CsvImporter.parseText(csv);
        assertEquals(3, h.size());
        assertArrayEquals(new int[]{2022, 2023, 2024}, h.years());
        assertArrayEquals(new double[]{80, 90, 100}, h.ocf(), 1e-9);
        assertArrayEquals(new double[]{30, 35, 40}, h.capex(), 1e-9);
        assertArrayEquals(new double[]{400, 450, 500}, h.revenue(), 1e-9);
        assertArrayEquals(new double[]{160, 180, 200}, h.ebit(), 1e-9);
        double[] fcf = h.fcfSeries();
        assertArrayEquals(new double[]{50, 55, 60}, fcf, 1e-9);
    }

    @Test
    void testParseLixingerStyleColumns() {
        // 理杏仁导出风格：中文科目名 + 报告期
        String csv = """
                报告期,经营活动产生的现金流量净额,购建固定资产、无形资产和其他长期资产支付的现金,营业总收入,营业利润,利润总额,所得税费用
                2022-12-31,80,30,400,160,140,35
                2023-12-31,90,35,450,180,160,40
                2024-12-31,100,40,500,200,180,45
                """;
        HistoricalData h = CsvImporter.parseText(csv);
        assertEquals(3, h.size());
        assertArrayEquals(new int[]{2022, 2023, 2024}, h.years());
        assertArrayEquals(new double[]{80, 90, 100}, h.ocf(), 1e-9);
        assertArrayEquals(new double[]{30, 35, 40}, h.capex(), 1e-9);
        assertArrayEquals(new double[]{400, 450, 500}, h.revenue(), 1e-9);
    }

    @Test
    void testMissingOptionalColumnsYieldNaN() {
        String csv = """
                年份,经营现金流,资本开支
                2024,100,40
                2023,90,35
                """;
        HistoricalData h = CsvImporter.parseText(csv);
        assertTrue(Double.isNaN(h.revenue()[0]));
        assertTrue(Double.isNaN(h.taxExpense()[0]));
    }

    @Test
    void testMissingRequiredColumnFails() {
        String csv = """
                年份,营收,资本开支
                2024,100,40
                """;
        assertThrows(IllegalArgumentException.class, () -> CsvImporter.parseText(csv));
    }

    @Test
    void testCommentAndBlankLinesSkipped() {
        String csv = """
                # 注释行
                年份,经营现金流,资本开支

                2024,100,40
                """;
        HistoricalData h = CsvImporter.parseText(csv);
        assertEquals(1, h.size());
        assertEquals(2024, h.years()[0]);
    }

    @Test
    void testBadNumberFails() {
        String csv = """
                年份,经营现金流,资本开支
                2024,abc,40
                """;
        assertThrows(IllegalArgumentException.class, () -> CsvImporter.parseText(csv));
    }

    @Test
    void testDescendingRowsAreSortedAscendingWithAlignment() {
        // 理杏仁导出风格：最新年份在前（倒序），排序后各字段必须与年份保持对齐
        String csv = """
                报告期,经营活动产生的现金流量净额,购建固定资产、无形资产和其他长期资产支付的现金,营业总收入
                2024-12-31,100,40,500
                2022-12-31,80,30,400
                2023-12-31,90,35,450
                """;
        HistoricalData h = CsvImporter.parseText(csv);
        assertEquals(3, h.size());
        assertArrayEquals(new int[]{2022, 2023, 2024}, h.years());
        assertArrayEquals(new double[]{80, 90, 100}, h.ocf(), 1e-9);
        assertArrayEquals(new double[]{30, 35, 40}, h.capex(), 1e-9);
        assertArrayEquals(new double[]{400, 450, 500}, h.revenue(), 1e-9);
        assertArrayEquals(new double[]{50, 55, 60}, h.fcfSeries(), 1e-9);
    }
    @Test
    void testTemplateGenerationRoundTrip() {
        String template = CsvImporter.generateTemplate();
        HistoricalData h = CsvImporter.parseText(template);
        assertEquals(3, h.size());
        assertTrue(h.ocf()[0] > 0);
    }
}