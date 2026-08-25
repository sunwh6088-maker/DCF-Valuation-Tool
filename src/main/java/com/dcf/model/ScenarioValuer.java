package com.dcf.model;

/**
 * 三情景估值执行器：把用户参数按情景偏移后调用完整估值流水线。
 *
 * <p>偏移约定（与 {@link Scenario} 一致）：增长率±2pp、折现率∓1.5pp、永续增长率±0.5pp。
 * 折现率/永续增长率会夹取到合法区间，且保证 r &gt; g（Gordon 公式前提）。
 */
public final class ScenarioValuer {

    private ScenarioValuer() {
    }

    /** 默认显式期结构：前 5 年高增长 + 5 年线性过渡（与页面默认一致）。 */
    public static ScenarioResult value(Scenario sc, double baseFcf, double gFirst, double gTerminal,
                                       double baseRate, double netDebt, double minority, double shares) {
        return value(sc, baseFcf, gFirst, gTerminal, baseRate, netDebt, minority, shares, 5, 5);
    }

    /** 完整参数版：返回含折现率与估值摘要的 {@link ScenarioResult}。 */
    public static ScenarioResult value(Scenario sc, double baseFcf, double gFirst, double gTerminal,
                                       double baseRate, double netDebt, double minority, double shares,
                                       int nFirst, int nTransition) {
        double scGFirst = clamp(gFirst + sc.gFirstDelta(), -0.50, 0.50);
        double scGTerminal = clamp(gTerminal + sc.gTerminalDelta(), 0.0, 0.05);
        double scRate = clamp(baseRate + sc.discountDelta(), 0.001, 0.30);
        if (scRate <= scGTerminal) {
            // Gordon 公式要求 r > g：极端参数下强制拉开 0.5pp
            scRate = scGTerminal + 0.005;
        }
        ValuationResult r = DcfModel.fullValuation(baseFcf, scGFirst, scGTerminal, scRate,
                netDebt, shares, minority, nFirst, nTransition);
        return new ScenarioResult(sc, scRate, r.perShareValue(), r.equityValue());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
