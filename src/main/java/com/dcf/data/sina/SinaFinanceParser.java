package com.dcf.data.sina;

import com.dcf.data.model.HistoricalData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 新浪财报科目提取器：从三张报表中按关键词匹配出估值所需科目。
 *
 * <p>关键词采用"包含匹配"并统一归一化（全角转半角、去空格），
 * 以兼容新浪接口中科目名的细微差异（如 "加：经营活动..."）。
 */
public final class SinaFinanceParser {

    /** 匹配关键词定义：报表中文名片段。 */
    public static final String KEY_OCF = "经营活动产生的现金流量净额";
    public static final String KEY_CAPEX = "购建固定资产";
    public static final String KEY_REVENUE = "营业总收入";
    public static final String KEY_EBIT = "营业利润";
    public static final String KEY_PRETAX = "利润总额";
    public static final String KEY_TAX = "所得税费用";
    public static final String KEY_CASH = "货币资金";
    public static final String KEY_SHORT_DEBT = "短期借款";
    public static final String KEY_LONG_DEBT = "长期借款";
    public static final String KEY_BOND = "应付债券";
    public static final String KEY_CURRENT_DEBT = "一年内到期的非流动负债";
    public static final String KEY_LEASE = "租赁负债";
    public static final String KEY_MINORITY = "少数股东权益";

    private SinaFinanceParser() {
    }

    /**
     * 提取年报序列：只保留 12-31 报告期（年报），按年份升序。
     *
     * @param balanceSheet 资产负债表 {报告期: {科目: 值}}
     * @param profitSheet  利润表
     * @param cashFlow     现金流量表
     * @param maxYears     最多取最近 N 年
     * @return 对齐后的历史数据
     */
    public static HistoricalData extract(
            Map<String, Map<String, Double>> balanceSheet,
            Map<String, Map<String, Double>> profitSheet,
            Map<String, Map<String, Double>> cashFlow,
            int maxYears) {

        // 收集所有年报报告期（以 12-31 结尾），并按年份倒序取最近 N 年
        List<String> annualDates = new ArrayList<>();
        for (String date : cashFlow.keySet()) {
            // 兼容 "2024-12-31" 与 "20251231" 两种报告期格式
            if (date.endsWith("12-31") || date.endsWith("1231")) {
                annualDates.add(date);
            }
        }
        annualDates.sort(Comparator.reverseOrder());
        if (annualDates.size() > maxYears) {
            annualDates = annualDates.subList(0, maxYears);
        }
        annualDates.sort(Comparator.naturalOrder()); // 恢复升序

        int n = annualDates.size();
        int[] years = new int[n];
        double[] ocf = new double[n];
        double[] capex = new double[n];
        double[] revenue = new double[n];
        double[] ebit = new double[n];
        double[] pretax = new double[n];
        double[] tax = new double[n];

        for (int i = 0; i < n; i++) {
            String date = annualDates.get(i);
            years[i] = Integer.parseInt(date.substring(0, 4));
            ocf[i] = pick(cashFlow.get(date), KEY_OCF);
            capex[i] = pick(cashFlow.get(date), KEY_CAPEX);
            revenue[i] = pick(profitSheet.get(date), KEY_REVENUE);
            ebit[i] = pick(profitSheet.get(date), KEY_EBIT);
            pretax[i] = pick(profitSheet.get(date), KEY_PRETAX);
            tax[i] = pick(profitSheet.get(date), KEY_TAX);
        }
        return new HistoricalData(years, ocf, capex, revenue, ebit, pretax, tax);
    }

    /** 从科目表中按关键词匹配数值，缺失返回 NaN。 */
    private static double pick(Map<String, Double> items, String keyword) {
        if (items == null || items.isEmpty()) {
            return Double.NaN;
        }
        String kw = normalize(keyword);
        for (Map.Entry<String, Double> e : items.entrySet()) {
            if (normalize(e.getKey()).contains(kw)) {
                return e.getValue();
            }
        }
        return Double.NaN;
    }

    /** 归一化：NFKC 全角转半角、统一小写、去除所有空白。 */
    public static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(n.length());
        for (char c : n.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}