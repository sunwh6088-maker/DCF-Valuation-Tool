package com.dcf.model;

/**
 * WACC（加权平均资本成本）计算器。
 *
 * <p>口径：WACC = kd × (1 - t) × D/(D+E) + ke × E/(D+E)
 * <ul>
 *   <li>ke：股权成本 = CAPM（Rf + β × ERP），复用 {@link DcfModel#capmCostOfEquity}</li>
 *   <li>kd：债务成本 = 无风险利率 + 信用利差（默认 2%，可手动覆盖）</li>
 *   <li>D：有息负债（来自财报快照）；E：股权市值 = 股价 × 总股本</li>
 *   <li>t：有效税率</li>
 * </ul>
 *
 * <p>与"纯 CAPM 折现"的区别：有负债公司的 WACC 通常低于 ke（债务便宜且有税盾），
 * 用 ke 折现会系统性低估企业价值；本计算器让两种口径可以对比。
 */
public final class WaccCalculator {

    /** 默认信用利差（无评级信息时的保守取值）。 */
    public static final double DEFAULT_CREDIT_SPREAD = 0.02;

    private WaccCalculator() {
    }

    /** 债务成本 kd = Rf + 信用利差。 */
    public static double costOfDebt(double rf, double creditSpread) {
        if (creditSpread < 0) {
            throw new IllegalArgumentException("信用利差不能为负");
        }
        return rf + creditSpread;
    }

    /** 债务权重 = D / (D+E)。有息负债为负时按 0 处理（异常数据兜底）。 */
    public static double debtWeight(double interestDebt, double marketCap) {
        if (marketCap <= 0) {
            throw new IllegalArgumentException("股权市值必须大于 0，无法计算 WACC 权重");
        }
        double d = Math.max(0, interestDebt);
        return d / (d + marketCap);
    }

    /** 股权权重 = 1 - 债务权重。 */
    public static double equityWeight(double interestDebt, double marketCap) {
        return 1 - debtWeight(interestDebt, marketCap);
    }

    /**
     * 加权平均资本成本：WACC = kd × (1-t) × wD + ke × wE。
     *
     * @param ke         股权成本（CAPM）
     * @param kd         债务成本（税后口径在公式内处理）
     * @param debtWeight 债务权重（0~1）
     * @param taxRate    有效税率
     */
    public static double wacc(double ke, double kd, double debtWeight, double taxRate) {
        if (debtWeight < 0 || debtWeight > 1) {
            throw new IllegalArgumentException("债务权重必须在 0~1 之间");
        }
        double wacc = kd * (1 - taxRate) * debtWeight + ke * (1 - debtWeight);
        // 权重为 1 时因浮点误差可能产生 -0.0，规整为 0 便于展示
        return Math.abs(wacc) < 1e-12 ? 0.0 : wacc;
    }

    /**
     * 一键计算 WACC（完整输入）。
     *
     * @param rf            无风险利率
     * @param beta          Beta
     * @param erp           市场风险溢价
     * @param creditSpread  信用利差
     * @param interestDebt  有息负债
     * @param marketCap     股权市值（股价 × 总股本）
     * @param taxRate       有效税率
     * @return WACC
     */
    public static double wacc(double rf, double beta, double erp, double creditSpread,
                              double interestDebt, double marketCap, double taxRate) {
        double ke = DcfModel.capmCostOfEquity(rf, beta, erp);
        double kd = costOfDebt(rf, creditSpread);
        double wd = debtWeight(interestDebt, marketCap);
        return wacc(ke, kd, wd, taxRate);
    }
}