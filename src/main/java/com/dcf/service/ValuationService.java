package com.dcf.service;

import com.dcf.data.model.HistoricalData;
import com.dcf.model.DcfModel;
import com.dcf.model.FinanceDetector;
import com.dcf.model.Indicators;
import com.dcf.model.Scenario;
import com.dcf.model.ScenarioResult;
import com.dcf.model.ScenarioValuer;
import com.dcf.model.SensitivityResult;
import com.dcf.model.ValuationResult;
import com.dcf.model.WaccCalculator;
import com.dcf.web.ValuationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 估值编排服务：把会话参数接入模型计算，并补充业务口径。
 *
 * <p>业务口径：
 * <ul>
 *   <li>基准 FCF = 最近一年自由现金流（经营现金流 - 资本开支）；若最近一年为 NaN
 *       则回退到近 3 年可用的均值</li>
 *   <li>折现率默认 = CAPM（rf + beta * erp），可在参数页手动覆盖（8%-12% 区间）</li>
 *   <li>敏感性矩阵：折现率以 CAPM 值为中心 ±3%（步长 0.5%），
 *       永续增长率 1.5% ~ 3.5%（步长 0.25%），网格可后续扩展</li>
 *   <li>安全边际 =（每股内在价值 - 当前股价）/ 当前股价</li>
 * </ul>
 */
public class ValuationService {

    /** 有效税率兜底（历史数据缺失时）。 */
    public static final double DEFAULT_TAX_RATE = 0.25;

    /**
     * 执行完整估值并写回上下文。
     *
     * @param ctx 会话上下文（含公司数据与参数）
     * @throws IllegalArgumentException 参数非法时
     */
    public void compute(ValuationContext ctx) {
        if (!ctx.hasCompany()) {
            throw new IllegalStateException("请先完成数据录入");
        }
        HistoricalData h = ctx.getCompany().history();

        // 1. 基准 FCF（最近一年优先，回退近 3 年均值）
        double baseFcf = baseFcf(h);

        // 2. 折现率：WACC 加权（推荐） / 纯 CAPM / 手动，三选一
        double ke = DcfModel.capmCostOfEquity(ctx.getRf(), ctx.getBetaInput(), ctx.getErp());
        double interestDebt = ctx.getCompany().snapshot().interestDebt();
        double marketCap = ctx.getCompany().snapshot().price() * ctx.getCompany().snapshot().sharesOutstanding();
        double kd = WaccCalculator.costOfDebt(ctx.getRf(), ctx.getCreditSpread());
        double debtWeight = WaccCalculator.debtWeight(interestDebt, marketCap);
        double wacc = WaccCalculator.wacc(ke, kd, debtWeight, ctx.getTaxRate());
        ctx.setWaccDetails(ke, kd, debtWeight, wacc);
        double discountRate = switch (ctx.getDiscountMode()) {
            case "capm" -> ke;
            case "manual" -> ctx.getManualDiscountRate();
            default -> wacc;
        };
        if (discountRate <= 0) {
            throw new IllegalArgumentException("折现率必须大于 0");
        }

        // 3. 完整估值（三情景：保守 / 中性 / 乐观）
        double netDebt = ctx.getCompany().snapshot().netDebt();
        double minority = ctx.getCompany().snapshot().minorityInterest();
        double shares = ctx.getCompany().snapshot().sharesOutstanding();
        List<ScenarioResult> scenarioResults = new ArrayList<>();
        for (Scenario sc : Scenario.values()) {
            scenarioResults.add(ScenarioValuer.value(sc, baseFcf, ctx.getGFirst(), ctx.getGTerminal(),
                    discountRate, netDebt, minority, shares,
                    ctx.getNFirst(), ctx.getNTransition()));
        }
        ctx.setScenarioResults(scenarioResults);

        // 主结果 = 中性情景（BASE，参数与用户输入完全一致）
        ValuationResult result = DcfModel.fullValuation(
                baseFcf, ctx.getGFirst(), ctx.getGTerminal(),
                discountRate, netDebt, shares, minority,
                ctx.getNFirst(), ctx.getNTransition());
        ctx.setResult(result);

        // 3.4 金融股标记（财报结构特殊提示）
        ctx.setFinancial(FinanceDetector.isFinancial(ctx.getCompany().snapshot().name()));

        // 3.5 辅助指标：判断分级 / 回本年限 / 隐含年化回报
        double price = ctx.getCompany().snapshot().price();
        ctx.setVerdict(Indicators.verdict(result.perShareValue(), price).label());
        ctx.setPaybackYears(Indicators.paybackYears(price * shares, baseFcf));
        ctx.setImpliedReturn(Indicators.impliedAnnualReturn(
                result.perShareValue(), price, ctx.getNFirst() + ctx.getNTransition()));

        // 4. 敏感性矩阵（CAPM 值 ±3%）
        double rLow = Math.max(0.005, Math.round((discountRate - 0.03) * 200) / 200.0);
        double rHigh = discountRate + 0.03;
        double[] rRates = grid(rLow, rHigh, 0.005);
        double[] gRates = grid(0.015, 0.035, 0.0025);
        SensitivityResult sens = DcfModel.sensitivityMatrix(
                baseFcf, ctx.getGFirst(), rRates, gRates,
                netDebt, shares, minority, ctx.getNFirst(), ctx.getNTransition());
        ctx.setSensitivity(sens);
    }

    /** 基准 FCF：最近一年优先，回退近 3 年均值；全部缺失抛异常。 */
    public static double baseFcf(HistoricalData h) {
        double[] fcf = h.fcfSeries();
        for (int i = fcf.length - 1; i >= 0; i--) {
            if (Double.isFinite(fcf[i])) {
                return fcf[i];
            }
        }
        throw new IllegalArgumentException("历史自由现金流数据缺失，请检查数据来源");
    }

    /** 生成等差数列（含端点）。 */
    private static double[] grid(double lo, double hi, double step) {
        int n = (int) Math.round((hi - lo) / step) + 1;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = Math.round((lo + i * step) * 10000.0) / 10000.0;
        }
        return out;
    }
}