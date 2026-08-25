package com.dcf.model;

/**
 * 估值辅助指标：判断分级、回本年限、隐含年化回报。
 *
 * <p>口径（参考 ianzheng 十年 DCF skill）：
 * <ul>
 *   <li>分级按 内在价值/股价（ratio）：≥1.3 明显低估；1.1~1.3 略有折价；0.9~1.1 基本合理；
 *       0.7~0.9 偏贵；&lt;0.7 明显高估</li>
 *   <li>回本年限 = 当前市值 / 年均自由现金流（用预测基准 FCF，等价于"公司几年赚回市值"）</li>
 *   <li>隐含年化回报 = (内在价值/股价)^(1/年限) - 1，表示若股价在未来年限内回归内在价值的年化收益</li>
 * </ul>
 */
public final class Indicators {

    /** 判断分级。 */
    public enum Verdict {
        UNDERVALUED("明显低估"),
        CHEAP("略有折价"),
        FAIR("基本合理"),
        EXPENSIVE("偏贵"),
        OVERVALUED("明显高估");

        private final String label;

        Verdict(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private Indicators() {
    }

    /** 按 内在价值/股价 比率分级。 */
    public static Verdict verdict(double perShare, double price) {
        if (price <= 0) {
            return Verdict.FAIR;
        }
        double ratio = perShare / price;
        if (ratio >= 1.3) {
            return Verdict.UNDERVALUED;
        }
        if (ratio >= 1.1) {
            return Verdict.CHEAP;
        }
        if (ratio >= 0.9) {
            return Verdict.FAIR;
        }
        if (ratio >= 0.7) {
            return Verdict.EXPENSIVE;
        }
        return Verdict.OVERVALUED;
    }

    /** 回本年限 = 市值 / 年均 FCF；FCF 非正时返回 NaN（无意义）。 */
    public static double paybackYears(double marketCap, double annualFcf) {
        if (annualFcf <= 0 || marketCap <= 0) {
            return Double.NaN;
        }
        return marketCap / annualFcf;
    }

    /** 隐含年化回报 = (内在价值/股价)^(1/years) - 1。 */
    public static double impliedAnnualReturn(double perShare, double price, int years) {
        if (price <= 0 || perShare <= 0 || years <= 0) {
            return Double.NaN;
        }
        return Math.pow(perShare / price, 1.0 / years) - 1.0;
    }
}
