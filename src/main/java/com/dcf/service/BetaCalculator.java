package com.dcf.service;

import java.util.List;

/**
 * Beta 系数计算：个股周收益率对基准指数（沪深300）周收益率的回归斜率。
 *
 * <p>口径（默认，可手动覆盖）：
 * <ul>
 *   <li>使用最近 3 年周线（约 156 个样本），前复权收盘价</li>
 *   <li>收益率 = 对数收益率 ln(Pt / Pt-1)，不做无风险利率调整</li>
 *   <li>beta = cov(个股收益, 指数收益) / var(指数收益)</li>
 *   <li>样本数 &lt; 30 时无法稳定估计，返回 NaN 由 UI 提示手动输入</li>
 * </ul>
 */
public final class BetaCalculator {

    /** 最少样本数（周），低于此值视为数据不足。 */
    public static final int MIN_SAMPLES = 30;

    private BetaCalculator() {
    }

    /**
     * 计算 Beta。
     *
     * @param stockCloses 个股收盘价序列（升序、等长）
     * @param indexCloses 指数收盘价序列（升序、等长）
     * @return beta；数据不足或序列非法时返回 NaN
     */
    public static double calculate(List<Double> stockCloses, List<Double> indexCloses) {
        if (stockCloses == null || indexCloses == null
                || stockCloses.size() != indexCloses.size()
                || stockCloses.size() < MIN_SAMPLES + 1) {
            return Double.NaN;
        }
        int n = stockCloses.size() - 1; // 收益率样本数
        double[] sr = new double[n];
        double[] ir = new double[n];
        double sumS = 0, sumI = 0;
        for (int i = 0; i < n; i++) {
            double ps = stockCloses.get(i), ps1 = stockCloses.get(i + 1);
            double pi = indexCloses.get(i), pi1 = indexCloses.get(i + 1);
            if (ps <= 0 || ps1 <= 0 || pi <= 0 || pi1 <= 0) {
                return Double.NaN;
            }
            sr[i] = Math.log(ps1 / ps);
            ir[i] = Math.log(pi1 / pi);
            sumS += sr[i];
            sumI += ir[i];
        }
        double meanS = sumS / n;
        double meanI = sumI / n;
        double cov = 0, var = 0;
        for (int i = 0; i < n; i++) {
            cov += (sr[i] - meanS) * (ir[i] - meanI);
            var += (ir[i] - meanI) * (ir[i] - meanI);
        }
        if (var <= 0) {
            return Double.NaN;
        }
        return cov / var;
    }
}