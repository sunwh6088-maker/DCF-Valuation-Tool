package com.dcf.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RateFetcher 单元测试：中债登 HTML 解析、兜底常量、来源标注。
 * （FRED/中债登联网取值见 LiveApiSmokeTest，默认 @Disabled）
 */
class RateFetcherTest {

    /** 模拟中债登 historyQuery 返回的表格结构（真实页面精简）。 */
    private static final String LATEST_ROW = "<tr><td>中债国债收益率曲线</td><td>2026-08-24</td><td>1.2007</td><td>1.2040</td>"
            + "<td>1.2020</td><td>1.2528</td><td>1.3851</td><td>1.5088</td><td>1.6794</td><td>2.1220</td></tr>";

    private static final String SAMPLE_HTML = ""
            + "<html><body><table>"
            + "<tr><th>曲线名称</th><th>日期</th><th>3月</th><th>6月</th><th>1年</th><th>3年</th><th>5年</th><th>7年</th><th>10年</th><th>30年</th></tr>"
            + LATEST_ROW
            + "<tr><td>中债商业银行普通债收益率曲线(AAA)</td><td>2026-08-24</td><td>1.3939</td><td>1.4409</td><td>1.4792</td><td>1.5476</td><td>1.5903</td><td>1.7587</td><td>1.9498</td><td>2.3125</td></tr>"
            + "<tr><td>中债国债收益率曲线</td><td>2026-08-21</td><td>1.1703</td><td>1.1716</td><td>1.2028</td><td>1.2549</td><td>1.3921</td><td>1.5223</td><td>1.6839</td><td>2.1320</td></tr>"
            + "</table></body></html>";

    /** 含「中短期票据收益率曲线(AAA)」行的 HTML（信用利差解析用）。 */
    private static final String SAMPLE_HTML_WITH_NOTE = ""
            + "<html><body><table>"
            + "<tr><th>曲线名称</th><th>日期</th><th>3月</th><th>6月</th><th>1年</th><th>3年</th><th>5年</th><th>7年</th><th>10年</th><th>30年</th></tr>"
            + "<tr><td>中债国债收益率曲线</td><td>2026-08-24</td><td>1.1819</td><td>1.2011</td><td>1.2030</td><td>1.2533</td><td>1.3872</td><td>1.5148</td><td>1.6815</td><td>2.1290</td></tr>"
            + "<tr><td>中债中短期票据收益率曲线(AAA)</td><td>2026-08-24</td><td>1.4785</td><td>1.4994</td><td>1.5243</td><td>1.6631</td><td>1.7406</td><td>1.8629</td><td>2.0825</td><td></td></tr>"
            + "<tr><td>中债国债收益率曲线</td><td>2026-08-21</td><td>1.1703</td><td>1.1716</td><td>1.2028</td><td>1.2549</td><td>1.3921</td><td>1.5223</td><td>1.6839</td><td>2.1320</td></tr>"
            + "<tr><td>中债中短期票据收益率曲线(AA+)</td><td>2026-08-24</td><td>1.5785</td><td>1.5994</td><td>1.6243</td><td>1.8631</td><td>1.9406</td><td>2.0629</td><td>2.2825</td><td></td></tr>"
            + "</table></body></html>";

    @Test
    void parseChinabond10yPicksLatestRow() {
        // 取最新一行（2026-08-24）10 年列：1.6794% → 0.016794
        assertEquals(0.016794, RateFetcher.parseChinabond10y(SAMPLE_HTML), 1e-9);
    }

    @Test
    void parseChinabond10ySkipsEmptyLatestValue() {
        // 最新一行 10 年列留空 → 向前取上一交易日（2026-08-21：1.6839%）
        String latestRowEmpty = LATEST_ROW.replace(">1.6794<", "><");
        String html = SAMPLE_HTML.replace(LATEST_ROW, latestRowEmpty);
        assertEquals(0.016839, RateFetcher.parseChinabond10y(html), 1e-9);
    }

    @Test
    void parseChinabond10yThrowsWhenNoTargetCurve() {
        String html = SAMPLE_HTML.replace("中债国债收益率曲线", "中债国开债收益率曲线");
        assertThrows(IllegalStateException.class, () -> RateFetcher.parseChinabond10y(html));
    }

    @Test
    void cnDefaultFallbackConstant() {
        assertEquals(0.017, RateFetcher.CN_DEFAULT_10Y, 1e-9);
    }

    @Test
    void sourceLabelsAreDescriptive() {
        assertTrue(RateFetcher.sourceLabel("US").contains("FRED"));
        assertTrue(RateFetcher.sourceLabel("CN").contains("中债登"));
        assertTrue(RateFetcher.sourceLabel("cn").contains("中债登"));
    }

    @Test
    void parseChinabondCreditSpreadPicksLatestCommonDate() {
        // 2026-08-24：票据AAA 3年 1.6631 − 国债 3年 1.2533 = 0.4098% → 0.004098
        assertEquals(0.004098, RateFetcher.parseChinabondCreditSpread(SAMPLE_HTML_WITH_NOTE), 1e-9);
    }

    @Test
    void parseChinabondCreditSpreadIgnoresOtherNoteGrades() {
        // 只取 AAA 行：票据AA+ 3年 1.8631 不应参与计算
        assertEquals(0.004098, RateFetcher.parseChinabondCreditSpread(SAMPLE_HTML_WITH_NOTE), 1e-9);
    }

    @Test
    void parseChinabondCreditSpreadThrowsWhenNoAlignedDate() {
        // 票据只有 08-21、国债只有 08-24/08-25：无共同交易日 → 抛异常
        // （原 HTML 含一行国债 08-21，需一并改走，否则会与票据 08-21 对齐）
        String html = SAMPLE_HTML_WITH_NOTE
                .replace(">2026-08-24</td><td>1.4785", ">2026-08-21</td><td>1.4785")
                .replace(">2026-08-24</td><td>1.5785", ">2026-08-21</td><td>1.5785")
                .replace(">2026-08-21</td><td>1.1703", ">2026-08-25</td><td>1.1703");
        assertThrows(IllegalStateException.class,
                () -> RateFetcher.parseChinabondCreditSpread(html));
    }
}