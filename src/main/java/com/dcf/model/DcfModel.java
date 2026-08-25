package com.dcf.model;

import java.util.Arrays;

/**
 * 两阶段 DCF（自由现金流折现）估值模型核心计算。
 *
 * <p>模型口径（与 Python 原型版一致）：
 * <ul>
 *   <li>显式预测期 10 年：前 5 年固定高增长率，后 5 年线性过渡到永续增长率</li>
 *   <li>终值采用 Gordon 增长模型：TV = FCF_n * (1 + g) / (r - g)</li>
 *   <li>企业价值 EV = 显式期现值 + 终值现值</li>
 *   <li>股权价值 = EV - 净债务 - 少数股东权益（净债务可为负，即净现金）</li>
 *   <li>每股内在价值 = 股权价值 / 总股本</li>
 * </ul>
 *
 * <p>本类为纯计算逻辑：不依赖网络、文件、Spring 等外部设施，便于单元测试与复用。
 * 所有参数由调用方（UI 层）完成校验，此处仅做模型层面的必要防御（如 g &lt; r）。
 */
public final class DcfModel {

    private DcfModel() {
        // 工具类，禁止实例化
    }

    /**
     * CAPM 股权成本：ke = rf + beta * erp。
     *
     * @param rf   无风险利率（如 10 年期国债收益率，小数形式 0.02 表示 2%）
     * @param beta 贝塔系数（衡量个股相对市场的波动敏感度）
     * @param erp  股权风险溢价（市场风险溢价，小数形式）
     * @return 股权成本 ke（小数形式）
     */
    public static double capmCostOfEquity(double rf, double beta, double erp) {
        return rf + beta * erp;
    }

    /**
     * 生成显式预测期的逐年增长率序列。
     *
     * <p>前 {@code nFirst} 年固定为 {@code gFirst}（高增长期），
     * 之后 {@code nTransition} 年从 {@code gFirst} 线性过渡到 {@code gTerminal}（永续增长率），
     * 避免增长率跳崖导致现金流预测失真。
     *
     * @param gFirst      高增长期增长率（小数）
     * @param gTerminal   永续增长率（小数）
     * @param nFirst      高增长期年数
     * @param nTransition 过渡期年数
     * @return 长度为 nFirst + nTransition 的增长率数组
     */
    public static double[] forecastGrowthPath(double gFirst, double gTerminal, int nFirst, int nTransition) {
        double[] gs = new double[nFirst + nTransition];
        Arrays.fill(gs, 0, nFirst, gFirst);
        for (int i = 1; i <= nTransition; i++) {
            gs[nFirst + i - 1] = gFirst + (gTerminal - gFirst) * i / nTransition;
        }
        return gs;
    }

    /**
     * 从基准 FCF 出发，按增长率序列逐年生成自由现金流预测。
     *
     * <p>允许负 FCF：成长型公司个别年份为负属正常，按绝对额复利计算。
     *
     * @param baseFcf    基准 FCF（最近一年自由现金流）
     * @param growthPath 逐年增长率序列
     * @return 逐年 FCF 数组
     */
    public static double[] forecastFcf(double baseFcf, double[] growthPath) {
        double[] out = new double[growthPath.length];
        double cur = baseFcf;
        for (int i = 0; i < growthPath.length; i++) {
            cur = cur * (1.0 + growthPath[i]);
            out[i] = cur;
        }
        return out;
    }

    /** 折现因子：(1 + r)^-t。 */
    private static double discountFactor(double rate, int year) {
        return Math.pow(1.0 + rate, -year);
    }

    /**
     * 两阶段 DCF 核心估值：显式期折现 + 终值折现。
     *
     * @param fcf          显式期各年自由现金流数组
     * @param discountRate 折现率（WACC，小数）
     * @param gTerminal    永续增长率（小数）
     * @return 核心估值结果（equityValue / perShareValue 为 NaN，需用 {@link #fullValuation} 或手工拆分）
     * @throws IllegalArgumentException 当 gTerminal &gt;= discountRate 时（Gordon 公式无意义）
     */
    public static ValuationResult dcfValuation(double[] fcf, double discountRate, double gTerminal) {
        if (discountRate <= gTerminal) {
            throw new IllegalArgumentException(
                    String.format("折现率(%.2f%%)必须大于永续增长率(%.2f%%)", discountRate * 100, gTerminal * 100));
        }
        int n = fcf.length;
        double pvFcf = 0.0;
        for (int i = 0; i < n; i++) {
            pvFcf += fcf[i] * discountFactor(discountRate, i + 1);
        }
        double terminalValue = fcf[n - 1] * (1.0 + gTerminal) / (discountRate - gTerminal);
        double pvTerminal = terminalValue * discountFactor(discountRate, n);
        double ev = pvFcf + pvTerminal;
        double terminalRatio = ev != 0 ? pvTerminal / ev : Double.NaN;
        return new ValuationResult(pvFcf, terminalValue, pvTerminal, ev, terminalRatio,
                ValuationResult.NOT_SET, ValuationResult.NOT_SET, fcf, null);
    }

    /**
     * 股权价值 = 企业价值 - 净债务 - 少数股东权益。
     *
     * <p>净债务 = 有息负债 - 现金及等价物。净债务为负表示公司账上净现金（如茅台），
     * 会提升股权价值，此调整不可省略。
     *
     * @param ev               企业价值
     * @param netDebt          净债务（可为负）
     * @param minorityInterest 少数股东权益（无则传 0）
     * @return 股权价值
     */
    public static double equityValue(double ev, double netDebt, double minorityInterest) {
        return ev - netDebt - minorityInterest;
    }

    /**
     * 每股内在价值 = 股权价值 / 总股本。
     *
     * @param equityValue      股权价值
     * @param sharesOutstanding 总股本（稀释后）
     * @return 每股内在价值
     * @throws IllegalArgumentException 当总股本 &lt;= 0 时
     */
    public static double perShareValue(double equityValue, double sharesOutstanding) {
        if (sharesOutstanding <= 0) {
            throw new IllegalArgumentException("总股本必须大于 0");
        }
        return equityValue / sharesOutstanding;
    }

    /**
     * 完整估值流水线：预测 → 折现 → 股权拆分 → 每股价值，一次返回全部中间结果。
     *
     * @param baseFcf           基准 FCF
     * @param gFirst            高增长期增长率
     * @param gTerminal         永续增长率
     * @param discountRate      折现率（WACC）
     * @param netDebt           净债务（可为负）
     * @param sharesOutstanding 总股本
     * @param minorityInterest  少数股东权益（无则传 0）
     * @param nFirst            高增长期年数
     * @param nTransition       过渡期年数
     * @return 完整估值结果
     */
    public static ValuationResult fullValuation(double baseFcf, double gFirst, double gTerminal,
                                                double discountRate, double netDebt,
                                                double sharesOutstanding, double minorityInterest,
                                                int nFirst, int nTransition) {
        double[] growthPath = forecastGrowthPath(gFirst, gTerminal, nFirst, nTransition);
        double[] fcf = forecastFcf(baseFcf, growthPath);
        ValuationResult core = dcfValuation(fcf, discountRate, gTerminal);
        double equity = equityValue(core.enterpriseValue(), netDebt, minorityInterest);
        double perShare = perShareValue(equity, sharesOutstanding);
        return new ValuationResult(core.pvFcf(), core.terminalValue(), core.pvTerminal(),
                core.enterpriseValue(), core.terminalRatio(), equity, perShare, fcf, growthPath);
    }

    /**
     * 折现率 × 永续增长率二维敏感性分析。
     *
     * <p>对每个 (r, g) 组合重新生成过渡期增长率与 FCF 序列并估值，
     * 结果为每股内在价值矩阵。g &gt;= r 的非法组合填入 NaN。
     *
     * @param baseFcf           基准 FCF
     * @param gFirst            高增长期增长率
     * @param discountRates     折现率序列（行）
     * @param growthRates       永续增长率序列（列）
     * @param netDebt           净债务
     * @param sharesOutstanding 总股本
     * @param minorityInterest  少数股东权益
     * @param nFirst            高增长期年数
     * @param nTransition       过渡期年数
     * @return 敏感性矩阵结果
     */
    public static SensitivityResult sensitivityMatrix(double baseFcf, double gFirst,
                                                      double[] discountRates, double[] growthRates,
                                                      double netDebt, double sharesOutstanding,
                                                      double minorityInterest,
                                                      int nFirst, int nTransition) {
        double[][] values = new double[discountRates.length][growthRates.length];
        for (int r = 0; r < discountRates.length; r++) {
            for (int c = 0; c < growthRates.length; c++) {
                try {
                    ValuationResult res = fullValuation(baseFcf, gFirst, growthRates[c],
                            discountRates[r], netDebt, sharesOutstanding, minorityInterest, nFirst, nTransition);
                    values[r][c] = res.perShareValue();
                } catch (IllegalArgumentException e) {
                    values[r][c] = Double.NaN;
                }
            }
        }
        return new SensitivityResult(values, discountRates.clone(), growthRates.clone());
    }
}