package com.dcf.service;

import com.dcf.data.model.HistoricalData;
import com.dcf.model.DcfModel;
import com.dcf.model.FScoreCalculator;
import com.dcf.model.FcfCalculator;
import com.dcf.model.FinanceDetector;
import com.dcf.model.Indicators;
import com.dcf.model.Scenario;
import com.dcf.model.ScenarioResult;
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
 *   <li>现金流口径：简化 FCF（经营现金流 - 资本开支，默认）或 FCFF（EBIT 起步）；
 *       FCFF 数据不足时自动回退简化口径并给出提示</li>
 *   <li>估值模型：两阶段（默认）/ 零增长 / 三阶段（高增长→成长期→永续）</li>
 *   <li>终值方法：Gordon 永续增长（默认）或 PE 退出法（退出PE × 期末净利润预测）</li>
 *   <li>折现率默认 = WACC（CAPM ke 与债务成本加权），可在参数页手动覆盖</li>
 *   <li>敏感性矩阵：折现率 ±3%（步长 0.5%）；Gordon 模式 × 永续增长率 1.5%~3.5%，
 *       PE 模式 × 退出PE（以输入值为中心 ±5，步长 2.5）</li>
 *   <li>辅助：判断分级 / 回本年限 / 隐含年化回报 / Piotroski F-Score</li>
 * </ul>
 */
public class ValuationService {

    /** 有效税率兜底（历史数据缺失时）。 */
    public static final double DEFAULT_TAX_RATE = 0.25;

    /** 三情景下 PE 退出法的市盈率偏移（保守-3 / 乐观+3）。 */
    private static final double SCENARIO_PE_DELTA = 3.0;

    private com.dcf.data.DataService dataService;

    /** 注入数据服务（历史股价回溯用；不注入则跳过历史回溯）。 */
    public void setDataService(com.dcf.data.DataService dataService) {
        this.dataService = dataService;
    }

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

        // 1. 基准 FCF：按现金流口径（simple / fcff）
        double[] fcfSeries = switch (ctx.getFcfMode()) {
            case "fcff" -> FcfCalculator.fcffSeries(h, ctx.getTaxRate());
            default -> FcfCalculator.simpleSeries(h);
        };
        double baseFcf = FcfCalculator.baseValue(fcfSeries);
        ctx.setFcfModeUsed(ctx.getFcfMode());
        if ("fcff".equals(ctx.getFcfMode())) {
            int available = 0;
            for (double v : fcfSeries) {
                if (!Double.isNaN(v)) {
                    available++;
                }
            }
            if (available == 0 || Double.isNaN(baseFcf)) {
                ctx.setFcfModeUsed("simple");
                baseFcf = FcfCalculator.baseValue(FcfCalculator.simpleSeries(h));
                ctx.setFcffWarning("FCFF 所需数据（EBIT/折旧摊销/流动资产/流动负债）不足，已回退为简化 FCF 口径");
            } else {
                ctx.setFcffWarning("");
            }
        }
        if (Double.isNaN(baseFcf)) {
            throw new IllegalArgumentException("历史自由现金流数据缺失，请检查数据来源");
        }
        ctx.setBaseFcfValue(baseFcf);

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

        // 3. 模型参数（零增长：增长率全部强制为 0）
        boolean zeroGrowth = "zeroGrowth".equals(ctx.getModelType());
        double gFirst = zeroGrowth ? 0.0 : ctx.getGFirst();
        double gSecond = zeroGrowth ? 0.0 : ctx.getGSecond();
        double gTerminal = zeroGrowth ? 0.0 : ctx.getGTerminal();

        // 4. PE 退出法：净利润率（历史净利润率优先，缺失用 EBIT×(1-t) 近似）
        double netMargin = Double.NaN;
        String marginSource = "";
        if ("pe".equals(ctx.getTerminalMode())) {
            NetMarginEstimate est = estimateNetMargin(h, ctx.getTaxRate());
            netMargin = est.margin();
            marginSource = est.source();
        }

        // 5. 主结果 + 三情景
        double netDebt = ctx.getCompany().snapshot().netDebt();
        double minority = ctx.getCompany().snapshot().minorityInterest();
        double shares = ctx.getCompany().snapshot().sharesOutstanding();
        ctx.setNetMarginUsed(netMargin);
        ctx.setNetMarginSource(marginSource);

        List<ScenarioResult> scenarioResults = new ArrayList<>();
        for (Scenario sc : Scenario.values()) {
            double scGFirst = clamp(gFirst + sc.gFirstDelta(), -0.50, 0.50);
            double scGSecond = clamp(gSecond + sc.gFirstDelta(), -0.50, 0.50);
            double scGTerminal = clamp(gTerminal + sc.gTerminalDelta(), 0.0, 0.05);
            double scRate = clamp(discountRate + sc.discountDelta(), 0.001, 0.30);
            if (scRate <= scGTerminal) {
                scRate = scGTerminal + 0.005;
            }
            double scPe = ctx.getExitPe() + switch (sc) {
                case CONSERVATIVE -> -SCENARIO_PE_DELTA;
                case OPTIMISTIC -> SCENARIO_PE_DELTA;
                default -> 0.0;
            };
            scPe = Math.max(3.0, scPe);
            ValuationResult r = valueOnce(ctx, h, baseFcf, scGFirst, scGSecond, scGTerminal,
                    scRate, netDebt, minority, shares, scPe, netMargin);
            scenarioResults.add(new ScenarioResult(sc, scRate, r.perShareValue(), r.equityValue()));
        }
        ctx.setScenarioResults(scenarioResults);

        // 主结果 = 中性情景（BASE，参数与用户输入完全一致）
        ValuationResult result = valueOnce(ctx, h, baseFcf, gFirst, gSecond, gTerminal,
                discountRate, netDebt, minority, shares, ctx.getExitPe(), netMargin);
        ctx.setResult(result);

        // 6. 金融股标记（财报结构特殊提示）
        ctx.setFinancial(FinanceDetector.isFinancial(ctx.getCompany().snapshot().name()));

        // 7. 辅助指标：判断分级 / 回本年限 / 隐含年化回报
        double price = ctx.getCompany().snapshot().price();
        ctx.setVerdict(Indicators.verdict(result.perShareValue(), price).label());
        ctx.setPaybackYears(Indicators.paybackYears(price * shares, baseFcf));
        int forecastYears = ctx.getNFirst() + ctx.getNTransition()
                + ("threeStage".equals(ctx.getModelType()) ? ctx.getNSecond() : 0);
        ctx.setImpliedReturn(Indicators.impliedAnnualReturn(
                result.perShareValue(), price, forecastYears));

        // 8. Piotroski F-Score（数据不足时为空列表，不阻断）
        ctx.setFScores(FScoreCalculator.calc(h, 3));

        // 9. 历史 DCF 回溯（容错：失败不阻断主流程）
        runBacktest(ctx, h);

        // 10. 敏感性矩阵：Gordon 模式 = 折现率 × 永续增长率；PE 模式 = 折现率 × 退出PE
        double rLow = Math.max(0.005, Math.round((discountRate - 0.03) * 200) / 200.0);
        double rHigh = discountRate + 0.03;
        double[] rRates = grid(rLow, rHigh, 0.005);
        SensitivityResult sens;
        if ("pe".equals(ctx.getTerminalMode())) {
            double peLow = Math.max(5.0, ctx.getExitPe() - 5.0);
            double peHigh = ctx.getExitPe() + 5.0;
            double[] peRates = grid(peLow, peHigh, 2.5);
            sens = sensitivityPe(baseFcf, gFirst, gSecond, gTerminal, rRates, peRates,
                    netDebt, shares, minority, netMargin, ctx);
        } else {
            double[] gRates = grid(0.015, 0.035, 0.0025);
            sens = sensitivityGordon(baseFcf, gFirst, gSecond, gTerminal, rRates, gRates,
                    netDebt, shares, minority, ctx);
        }
        ctx.setSensitivity(sens);
    }

    // ---------- 统一估值管线 ----------

    /**
     * 按当前模型/终值设置执行一次完整估值（主结果与三情景共用，保证口径一致）。
     */
    private ValuationResult valueOnce(ValuationContext ctx, HistoricalData h, double baseFcf,
                                      double gFirst, double gSecond, double gTerminal,
                                      double rate, double netDebt, double minority, double shares,
                                      double exitPe, double netMargin) {
        double[] growth;
        if ("threeStage".equals(ctx.getModelType())) {
            growth = DcfModel.forecastGrowthPathThreeStage(
                    gFirst, gSecond, gTerminal, ctx.getNFirst(), ctx.getNSecond(), ctx.getNTransition());
        } else {
            growth = DcfModel.forecastGrowthPath(gFirst, gTerminal, ctx.getNFirst(), ctx.getNTransition());
        }
        double[] fcf = DcfModel.forecastFcf(baseFcf, growth);
        ValuationResult core;
        if ("pe".equals(ctx.getTerminalMode())) {
            double terminalNetIncome = forecastTerminalNetIncome(h, growth, netMargin);
            core = DcfModel.dcfValuationPeExit(fcf, rate, exitPe, terminalNetIncome);
            if (Double.isNaN(ctx.getTerminalNetIncome())) {
                ctx.setTerminalNetIncome(terminalNetIncome);
            }
        } else {
            core = DcfModel.dcfValuation(fcf, rate, gTerminal);
        }
        double equity = DcfModel.equityValue(core.enterpriseValue(), netDebt, minority);
        double perShare = DcfModel.perShareValue(equity, shares);
        return new ValuationResult(core.pvFcf(), core.terminalValue(), core.pvTerminal(),
                core.enterpriseValue(), core.terminalRatio(), equity, perShare, fcf, growth);
    }

    /** 期末净利润预测 = 基准营收 × 沿增长率路径复利到期末 × 净利润率。 */
    private double forecastTerminalNetIncome(HistoricalData h, double[] growth, double netMargin) {
        double baseRevenue = FcfCalculator.baseValue(h.revenue());
        double revenueEnd = baseRevenue;
        for (double g : growth) {
            revenueEnd *= (1.0 + g);
        }
        return revenueEnd * netMargin;
    }

    /** 净利润率估算结果（margin + 来源说明）。 */
    private record NetMarginEstimate(double margin, String source) {
    }

    /** 净利润率估算：历史净利润/营收（最近一年优先，回退近3年均值）→ EBIT×(1-t)/营收 近似。 */
    private NetMarginEstimate estimateNetMargin(HistoricalData h, double taxRate) {
        double latest = Double.NaN;
        for (int i = h.size() - 1; i >= 0; i--) {
            double ni = h.extraAt(i, HistoricalData.EXTRA_NET_INCOME);
            double rev = h.revenue()[i];
            if (!Double.isNaN(ni) && !Double.isNaN(rev) && rev != 0) {
                latest = ni / rev;
                break;
            }
        }
        double mean = 0.0;
        int cnt = 0;
        for (int i = Math.max(0, h.size() - 3); i < h.size(); i++) {
            double ni = h.extraAt(i, HistoricalData.EXTRA_NET_INCOME);
            double rev = h.revenue()[i];
            if (!Double.isNaN(ni) && !Double.isNaN(rev) && rev != 0) {
                mean += ni / rev;
                cnt++;
            }
        }
        if (!Double.isNaN(latest)) {
            return new NetMarginEstimate(latest, "历史净利润率（最近一年）");
        }
        if (cnt > 0) {
            return new NetMarginEstimate(mean / cnt, "历史净利润率（近 3 年均值）");
        }
        for (int i = h.size() - 1; i >= 0; i--) {
            double ebit = h.ebit()[i];
            double rev = h.revenue()[i];
            if (!Double.isNaN(ebit) && !Double.isNaN(rev) && rev != 0) {
                return new NetMarginEstimate(ebit * (1.0 - taxRate) / rev,
                        "EBIT×(1−税率)/营收 近似（无净利润数据）");
            }
        }
        throw new IllegalArgumentException("PE 退出法需要营收与净利润（或 EBIT）数据，请补充历史财务");
    }

    // ---------- 敏感性 ----------

    private SensitivityResult sensitivityGordon(double baseFcf, double gFirst, double gSecond,
                                                double gTerminal, double[] rRates, double[] gRates,
                                                double netDebt, double shares, double minority,
                                                ValuationContext ctx) {
        double[][] values = new double[rRates.length][gRates.length];
        for (int r = 0; r < rRates.length; r++) {
            for (int c = 0; c < gRates.length; c++) {
                try {
                    ValuationResult res = valueOnce(ctx, ctx.getCompany().history(), baseFcf,
                            gFirst, gSecond, gRates[c], rRates[r], netDebt, minority, shares,
                            ctx.getExitPe(), Double.NaN);
                    values[r][c] = res.perShareValue();
                } catch (IllegalArgumentException e) {
                    values[r][c] = Double.NaN;
                }
            }
        }
        return new SensitivityResult(values, rRates.clone(), gRates.clone(), "永续增长率");
    }

    private SensitivityResult sensitivityPe(double baseFcf, double gFirst, double gSecond,
                                            double gTerminal, double[] rRates, double[] peRates,
                                            double netDebt, double shares, double minority,
                                            double netMargin, ValuationContext ctx) {
        double[][] values = new double[rRates.length][peRates.length];
        for (int r = 0; r < rRates.length; r++) {
            for (int c = 0; c < peRates.length; c++) {
                try {
                    ValuationResult res = valueOnce(ctx, ctx.getCompany().history(), baseFcf,
                            gFirst, gSecond, gTerminal, rRates[r], netDebt, minority, shares,
                            peRates[c], netMargin);
                    values[r][c] = res.perShareValue();
                } catch (IllegalArgumentException e) {
                    values[r][c] = Double.NaN;
                }
            }
        }
        return new SensitivityResult(values, rRates.clone(), peRates.clone(), "退出PE");
    }

    // ---------- 工具 ----------

    /** 历史 DCF 回溯：A 股拉东财年末价；美股/失败时置错误提示（不阻断估值）。 */
    private void runBacktest(ValuationContext ctx, HistoricalData h) {
        ctx.setBacktestResults(java.util.List.of());
        ctx.setBacktestError(null);
        if (dataService == null) {
            ctx.setBacktestError("未配置数据服务，历史回溯不可用");
            return;
        }
        if (!"CN".equals(ctx.market())) {
            ctx.setBacktestError("美股暂无免费历史股价源，历史回溯仅支持 A 股");
            return;
        }
        try {
            int startYear = h.years()[0];
            int endYear = h.years()[h.size() - 1];
            java.util.Map<Integer, Double> prices =
                    dataService.fetchYearEndPrices(ctx.getCompany().code(), startYear, endYear);
            ctx.setBacktestResults(HistoricalDcfService.backtest(h, prices, ctx));
        } catch (Exception e) {
            ctx.setBacktestError("历史股价获取失败：" + e.getMessage());
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
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
