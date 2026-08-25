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
 * </ul>
 */
public record HistoricalData(
        int[] years,
        double[] ocf,
        double[] capex,
        double[] revenue,
        double[] ebit,
        double[] pretaxIncome,
        double[] taxExpense) {

    /** 逐年自由现金流 = 经营现金流 - 资本开支。 */
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
}