package com.dcf.model;

import com.dcf.data.model.HistoricalData;

/**
 * 自由现金流口径计算器。
 *
 * <ul>
 *   <li><b>简化口径</b>：FCF = 经营现金流 − 资本开支（默认，数据要求低）</li>
 *   <li><b>FCFF 口径</b>：FCFF = EBIT×(1−t) + 折旧摊销 − 资本开支 − 营运资本增加
 *       （EBIT 起步，更贴近公司自由现金流定义，需进阶科目数据）</li>
 * </ul>
 */
public final class FcfCalculator {

    private FcfCalculator() {
    }

    /** 简化口径逐年 FCF。 */
    public static double[] simpleSeries(HistoricalData h) {
        return h.fcfSeries();
    }

    /** 净营运资本 = 流动资产 − 流动负债；数据缺失返回 NaN。 */
    public static double netWorkingCapital(HistoricalData h, int index) {
        double ca = h.extraAt(index, HistoricalData.EXTRA_CURRENT_ASSETS);
        double cl = h.extraAt(index, HistoricalData.EXTRA_CURRENT_LIABILITIES);
        return (Double.isNaN(ca) || Double.isNaN(cl)) ? Double.NaN : ca - cl;
    }

    /**
     * FCFF 口径逐年序列：EBIT×(1−t) + D&A − Capex − ΔNWC。
     * 首年无 ΔNWC（需上一年数据），返回 NaN；任一输入缺失该年返回 NaN。
     */
    public static double[] fcffSeries(HistoricalData h, double taxRate) {
        int n = h.size();
        double[] out = new double[n];
        double prevNwc = Double.NaN;
        for (int i = 0; i < n; i++) {
            double curNwc = netWorkingCapital(h, i);
            double dNwc = (Double.isNaN(prevNwc) || Double.isNaN(curNwc)) ? Double.NaN : curNwc - prevNwc;
            prevNwc = curNwc;
            double ebit = h.ebit()[i];
            double dep = h.extraAt(i, HistoricalData.EXTRA_DEPRECIATION);
            if (Double.isNaN(ebit) || Double.isNaN(dep) || Double.isNaN(h.capex()[i]) || Double.isNaN(dNwc)) {
                out[i] = Double.NaN;
            } else {
                out[i] = ebit * (1.0 - taxRate) + dep - h.capex()[i] - dNwc;
            }
        }
        return out;
    }

    /**
     * 基准值：最近一年优先；缺失时回退近 3 年可用均值；全缺失返回 NaN。
     */
    public static double baseValue(double[] series) {
        int n = series.length;
        if (n == 0) {
            return Double.NaN;
        }
        if (!Double.isNaN(series[n - 1])) {
            return series[n - 1];
        }
        double sum = 0.0;
        int cnt = 0;
        for (int i = Math.max(0, n - 3); i < n; i++) {
            if (!Double.isNaN(series[i])) {
                sum += series[i];
                cnt++;
            }
        }
        return cnt == 0 ? Double.NaN : sum / cnt;
    }
}
