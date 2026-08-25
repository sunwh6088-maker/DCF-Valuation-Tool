package com.dcf.data.model;

/**
 * 历史财务数据（逐年序列）。
 *
 * <p>数组按年份升序对齐（相同 index 即同一年份）。
 * 数据口径：
 * <ul>
 *   <li>ocf：经营活动产生的现金流量净额</li>
 *   <li>capex：资本开支（购建固定资产、无形资产和其他长期资产支付的现金，正数）</li>
 *   <li>revenue：营业总收入</li>
 *   <li>ebit：营业利润（近似 EBIT 口径，报告中标注）</li>
 *   <li>pretaxIncome：利润总额（税前利润）</li>
 *   <li>taxExpense：所得税费用</li>
 *   <li>extra：进阶科目（净利润/折旧摊销/资产负债等，FCFF、PE 退出、F-Score 用；可为空）</li>
 * </ul>
 */
public record HistoricalData(
        int[] years,
        double[] ocf,
        double[] capex,
        double[] revenue,
        double[] ebit,
        double[] pretaxIncome,
        double[] taxExpense,
        ExtraFinancials extra) {

    /** 兼容旧调用：不提供进阶科目。 */
    public HistoricalData(int[] years, double[] ocf, double[] capex, double[] revenue,
                          double[] ebit, double[] pretaxIncome, double[] taxExpense) {
        this(years, ocf, capex, revenue, ebit, pretaxIncome, taxExpense, null);
    }

    /** 逐年自由现金流 = 经营现金流 - 资本开支（简化口径）。 */
    public double[] fcfSeries() {
        double[] fcf = new double[ocf.length];
        for (int i = 0; i < ocf.length; i++) {
            fcf[i] = ocf[i] - capex[i];
        }
        return fcf;
    }

    public int size() {
        return years.length;
    }

    /** 取某年进阶科目值；extra 缺失或该年缺失返回 NaN。 */
    public double extraAt(int index, int fieldIndex) {
        if (extra == null || extra.arrays()[fieldIndex] == null) {
            return Double.NaN;
        }
        double[] arr = extra.arrays()[fieldIndex];
        return index < arr.length ? arr[index] : Double.NaN;
    }

    /** 进阶科目字段索引（与 {@link ExtraFinancials#arrays()} 顺序一致）。 */
    public static final int EXTRA_NET_INCOME = 0;
    public static final int EXTRA_DEPRECIATION = 1;
    public static final int EXTRA_TOTAL_ASSETS = 2;
    public static final int EXTRA_TOTAL_LIABILITIES = 3;
    public static final int EXTRA_CURRENT_ASSETS = 4;
    public static final int EXTRA_CURRENT_LIABILITIES = 5;
    public static final int EXTRA_GROSS_PROFIT = 6;
    public static final int EXTRA_SHARES_CAPITAL = 7;

    /**
     * 进阶财务科目（逐年序列，任一可为 null 表示未提供）。
     * <ul>
     *   <li>netIncome：净利润（合并口径）</li>
     *   <li>depreciation：折旧与摊销合计（固定资产折旧+无形资产摊销+长期待摊费用摊销等）</li>
     *   <li>totalAssets：资产总计</li>
     *   <li>totalLiabilities：负债合计</li>
     *   <li>currentAssets：流动资产合计</li>
     *   <li>currentLiabilities：流动负债合计</li>
     *   <li>grossProfit：毛利（营收-营业成本）</li>
     *   <li>sharesCapital：股本（实收资本，用于 F-Score 新股检测）</li>
     * </ul>
     */
    public record ExtraFinancials(
            double[] netIncome,
            double[] depreciation,
            double[] totalAssets,
            double[] totalLiabilities,
            double[] currentAssets,
            double[] currentLiabilities,
            double[] grossProfit,
            double[] sharesCapital) {

        public double[][] arrays() {
            return new double[][]{netIncome, depreciation, totalAssets, totalLiabilities,
                    currentAssets, currentLiabilities, grossProfit, sharesCapital};
        }
    }
}
