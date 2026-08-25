package com.dcf.model;

import com.dcf.data.model.HistoricalData;

import java.util.ArrayList;
import java.util.List;

/**
 * Piotroski F-Score 财务质量打分（9 项二值指标，满分 9 分）。
 *
 * <p>逐项定义（对比 t 年与 t-1 年）：
 * <ol>
 *   <li>ROA（净利润/总资产）&gt; 0</li>
 *   <li>经营现金流 &gt; 0</li>
 *   <li>ROA 同比改善</li>
 *   <li>经营现金流 &gt; 净利润（应计质量，盈余现金含量高）</li>
 *   <li>杠杆率（总负债/总资产）下降</li>
 *   <li>流动比率（流动资产/流动负债）上升</li>
 *   <li>未增发新股（股本不增加）</li>
 *   <li>毛利率（毛利/营收）上升</li>
 *   <li>资产周转率（营收/总资产）上升</li>
 * </ol>
 *
 * <p>任一输入缺失的项记为 null（不参与计分，并在展示中标记 N/A）；
 * 首年（无上年对比）计分不可用。
 */
public final class FScoreCalculator {

    /** 9 项指标中文名（与 {@link #items} 顺序一致）。 */
    public static final String[] ITEM_LABELS = {
            "ROA > 0", "经营现金流 > 0", "ROA 同比改善", "经营现金流 > 净利润",
            "杠杆率下降", "流动比率上升", "未增发新股", "毛利率上升", "资产周转率上升"
    };

    private FScoreCalculator() {
    }

    /** 单年 F-Score 结果。 */
    public record FScoreResult(
            int year,
            int score,           // 命中项数（仅统计有数据的项）
            int itemsAvailable,  // 有数据的项数
            Boolean[] items) {   // 9 项命中与否；null=数据不足
    }

    /**
     * 计算最近 {@code maxYears} 个可计分年份（从最早一年起算，需上一年数据做同比）。
     *
     * @param history  历史财务（含进阶科目）
     * @param maxYears 最多返回几年
     * @return 按年份升序的 F-Score 列表（数据不足时可能为空）
     */
    public static List<FScoreResult> calc(HistoricalData history, int maxYears) {
        List<FScoreResult> out = new ArrayList<>();
        int n = history.size();
        for (int i = 1; i < n; i++) { // 首年无同比
            out.add(calcYear(history, i));
        }
        if (out.size() > maxYears) {
            out = out.subList(out.size() - maxYears, out.size());
        }
        return out;
    }

    private static FScoreResult calcYear(HistoricalData h, int i) {
        Boolean[] items = new Boolean[9];
        double roa = ratio(netIncome(h, i), totalAssets(h, i));
        double roaPrev = ratio(netIncome(h, i - 1), totalAssets(h, i - 1));
        double ocf = h.ocf()[i];
        double ni = netIncome(h, i);
        double lev = ratio(totalLiabilities(h, i), totalAssets(h, i));
        double levPrev = ratio(totalLiabilities(h, i - 1), totalAssets(h, i - 1));
        double cur = ratio(currentAssets(h, i), currentLiabilities(h, i));
        double curPrev = ratio(currentAssets(h, i - 1), currentLiabilities(h, i - 1));
        double gm = ratio(grossProfit(h, i), h.revenue()[i]);
        double gmPrev = ratio(grossProfit(h, i - 1), h.revenue()[i - 1]);
        double turn = ratio(h.revenue()[i], totalAssets(h, i));
        double turnPrev = ratio(h.revenue()[i - 1], totalAssets(h, i - 1));
        double sc = h.extraAt(i, HistoricalData.EXTRA_SHARES_CAPITAL);
        double scPrev = h.extraAt(i - 1, HistoricalData.EXTRA_SHARES_CAPITAL);

        items[0] = pos(roa);
        items[1] = pos(ocf);
        items[2] = cmp(roa, roaPrev, true);
        items[3] = (!Double.isNaN(ocf) && !Double.isNaN(ni)) ? ocf > ni : null;
        items[4] = cmp(lev, levPrev, false);
        items[5] = cmp(cur, curPrev, true);
        items[6] = (!Double.isNaN(sc) && !Double.isNaN(scPrev)) ? sc <= scPrev : null;
        items[7] = cmp(gm, gmPrev, true);
        items[8] = cmp(turn, turnPrev, true);

        int score = 0;
        int available = 0;
        for (Boolean b : items) {
            if (b != null) {
                available++;
                if (b) {
                    score++;
                }
            }
        }
        return new FScoreResult(h.years()[i], score, available, items);
    }

    /** a > 0 为真；a 缺失为 null。 */
    private static Boolean pos(double a) {
        return Double.isNaN(a) ? null : a > 0;
    }

    /** 前后值比较；任一缺失为 null。better=true 表示上升更好，false 表示下降更好。 */
    private static Boolean cmp(double cur, double prev, boolean betterWhenUp) {
        if (Double.isNaN(cur) || Double.isNaN(prev)) {
            return null;
        }
        return betterWhenUp ? cur > prev : cur < prev;
    }

    /** 分子/分母比值；任一缺失返回 NaN。 */
    private static double ratio(double numerator, double denominator) {
        return (Double.isNaN(numerator) || Double.isNaN(denominator) || denominator == 0)
                ? Double.NaN : numerator / denominator;
    }

    private static double netIncome(HistoricalData h, int i) {
        return h.extraAt(i, HistoricalData.EXTRA_NET_INCOME);
    }

    private static double totalAssets(HistoricalData h, int i) {
        return h.extraAt(i, HistoricalData.EXTRA_TOTAL_ASSETS);
    }

    private static double totalLiabilities(HistoricalData h, int i) {
        return h.extraAt(i, HistoricalData.EXTRA_TOTAL_LIABILITIES);
    }

    private static double currentAssets(HistoricalData h, int i) {
        return h.extraAt(i, HistoricalData.EXTRA_CURRENT_ASSETS);
    }

    private static double currentLiabilities(HistoricalData h, int i) {
        return h.extraAt(i, HistoricalData.EXTRA_CURRENT_LIABILITIES);
    }

    private static double grossProfit(HistoricalData h, int i) {
        return h.extraAt(i, HistoricalData.EXTRA_GROSS_PROFIT);
    }
}
