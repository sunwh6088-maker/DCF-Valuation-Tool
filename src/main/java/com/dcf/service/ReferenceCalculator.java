package com.dcf.service;

import com.dcf.data.model.HistoricalData;

/**
 * 估值参数参考值计算（用历史财务数据给预测假设提供「锚点」）。
 *
 * <p>注意：有效税率 / 增长率本质是投资者的预测假设，不存在「自动获取」的权威源；
 * 这里只根据公司自身历史数据给出参考值，由用户确认或覆盖（页面提供「参考历史」按钮）。
 * 所有方法返回 {@link Double#NaN} 表示数据不足，由页面提示手动输入。
 */
public final class ReferenceCalculator {

    private ReferenceCalculator() {
    }

    /**
     * 有效税率参考：近 3 年「所得税费用合计 ÷ 利润总额合计」（加权口径，比简单平均更稳）。
     *
     * @return 税率（小数，如 0.25）；数据不足返回 NaN
     */
    public static double effectiveTaxRate(HistoricalData h) {
        if (h == null || h.size() == 0) {
            return Double.NaN;
        }
        int n = Math.min(3, h.size());
        double tax = 0, pretax = 0;
        for (int i = h.size() - n; i < h.size(); i++) {
            double t = h.taxExpense()[i];
            double p = h.pretaxIncome()[i];
            if (!Double.isNaN(t) && !Double.isNaN(p) && t >= 0 && p > 0) {
                tax += t;
                pretax += p;
            }
        }
        if (pretax <= 0 || tax <= 0) {
            return Double.NaN;
        }
        double rate = tax / pretax;
        return (rate > 0 && rate < 1) ? rate : Double.NaN;
    }

    /**
     * 高增长期增长率参考：历史营收 CAGR（复利），要求至少 3 年且首末均为正。
     *
     * @return 增长率（小数，如 0.10）；数据不足返回 NaN
     */
    public static double revenueCagr(HistoricalData h) {
        if (h == null || h.size() < 3) {
            return Double.NaN;
        }
        double[] rev = h.revenue();
        double first = rev[0];
        double last = rev[rev.length - 1];
        for (double r : rev) {
            if (!(r > 0)) {
                return Double.NaN; // 任一年营收非正（含 NaN）→ 无法计算复利
            }
        }
        double cagr = Math.pow(last / first, 1.0 / (rev.length - 1)) - 1;
        // 异常值保护：营收 10 年涨 100 倍以上视为数据异常
        return (cagr > -0.9 && cagr < 5) ? cagr : Double.NaN;
    }
}
