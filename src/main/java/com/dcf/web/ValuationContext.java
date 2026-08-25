package com.dcf.web;

import com.dcf.data.model.CompanyData;
import com.dcf.model.FScoreCalculator;
import com.dcf.model.ScenarioResult;
import com.dcf.model.SensitivityResult;
import com.dcf.model.ValuationResult;
import com.dcf.service.HistoricalBacktest;

import java.util.List;

/**
 * 估值会话上下文（存于 HttpSession）。
 *
 * <p>贯穿整个估值流程：数据 → 参数 → 结果。
 * 页面之间通过它传递状态，避免重复抓取数据。
 */
public class ValuationContext {

    /** 公司数据（自动抓取 / CSV / 手动组装）。 */
    private CompanyData company;

    /** 自动计算的 Beta（可能为 NaN，表示数据不足需手填）。 */
    private Double beta;

    // ---- 估值参数（params 页表单） ----
    private double rf = 0.017;            // 无风险利率（默认中国 10Y）
    private double betaInput = 1.0;       // Beta（自动填充或手动）
    private double erp = 0.055;           // 市场风险溢价
    private String discountMode = "wacc";   // 折现率来源：wacc=WACC加权 / capm=纯CAPM / manual=手动
    private double manualDiscountRate = 0.10; // 手动折现率
    private double creditSpread = 0.02;       // 债务成本信用利差（kd = Rf + 利差）
    private double taxRate = 0.25;        // 有效税率
    private double gFirst = 0.08;         // 高增长期增长率
    private int nFirst = 5;               // 高增长年数
    private int nTransition = 5;          // 过渡年数
    private double gTerminal = 0.025;     // 永续增长率
    private String modelType = "twoStage"; // 估值模型：twoStage=两阶段 / zeroGrowth=零增长 / threeStage=三阶段
    private double gSecond = 0.05;         // 三阶段：成长期增长率（阶段 2）
    private int nSecond = 5;               // 三阶段：成长期年数（阶段 2）
    private String fcfMode = "simple";     // 现金流口径：simple=经营现金流-资本开支 / fcff=EBIT起步
    private String terminalMode = "gordon";// 终值方法：gordon=永续增长 / pe=PE退出
    private double exitPe = 15.0;          // PE 退出法：退出市盈率

    // ---- 参考值（历史数据自动计算，仅供预填参考，不覆盖用户输入） ----
    private Double refTaxRate;   // 有效税率参考（近 3 年均值；null=不可用）
    private Double refGrowth;    // 历史营收 CAGR 参考（null=不可用）

    // ---- WACC 明细（计算结果） ----
    private double keValue = Double.NaN;         // 股权成本（CAPM）
    private double kdValue = Double.NaN;         // 债务成本（Rf + 信用利差）
    private double debtWeightValue = Double.NaN; // 债务权重 D/(D+E)
    private double waccValue = Double.NaN;       // 加权平均资本成本

    // ---- 计算结果 ----
    private ValuationResult result;
    private List<ScenarioResult> scenarioResults = List.of();
    private boolean financial;             // 金融股标记（财报结构特殊，DCF 参考性有限）
    private String verdict = "";            // 判断分级（明显低估/略有折价/基本合理/偏贵/明显高估）
    private double paybackYears = Double.NaN; // 回本年限（市值/年均FCF）
    private double impliedReturn = Double.NaN; // 隐含年化回报
    private List<HistoricalBacktest> backtestResults = List.of(); // 历史 DCF 回溯
    private String backtestError;             // 回溯不可用原因（可空）
    private SensitivityResult sensitivity;
    private double baseFcfValue = Double.NaN; // 实际使用的基准自由现金流
    private String fcfModeUsed = "simple";    // 实际生效的口径（FCFF 数据不足时回退 simple）
    private String fcffWarning = "";          // FCFF 数据不足提示（可空）
    private double terminalNetIncome = Double.NaN; // PE 退出法：期末净利润预测
    private double netMarginUsed = Double.NaN;     // PE 退出法：使用的净利润率
    private String netMarginSource = "";           // 净利润率来源说明
    private List<FScoreCalculator.FScoreResult> fScores = List.of(); // Piotroski F-Score
    private String errorMessage;

    // ---- 便捷访问 ----
    public String market() {
        return company == null ? "CN" : company.market();
    }

    public boolean hasCompany() {
        return company != null;
    }

    public CompanyData getCompany() { return company; }
    public void setCompany(CompanyData company) { this.company = company; }

    public Double getBeta() { return beta; }
    public void setBeta(Double beta) { this.beta = beta; }

    public double getRf() { return rf; }
    public void setRf(double rf) { this.rf = rf; }

    public double getBetaInput() { return betaInput; }
    public void setBetaInput(double betaInput) { this.betaInput = betaInput; }

    public double getErp() { return erp; }
    public void setErp(double erp) { this.erp = erp; }

    public String getDiscountMode() { return discountMode; }
    public void setDiscountMode(String discountMode) { this.discountMode = discountMode; }

    public double getManualDiscountRate() { return manualDiscountRate; }
    public void setManualDiscountRate(double manualDiscountRate) { this.manualDiscountRate = manualDiscountRate; }

    public double getCreditSpread() { return creditSpread; }

    public Double getRefTaxRate() { return refTaxRate; }
    public void setRefTaxRate(Double refTaxRate) { this.refTaxRate = refTaxRate; }

    public Double getRefGrowth() { return refGrowth; }
    public void setRefGrowth(Double refGrowth) { this.refGrowth = refGrowth; }
    public void setCreditSpread(double creditSpread) { this.creditSpread = creditSpread; }

    public double getKeValue() { return keValue; }
    public double getKdValue() { return kdValue; }
    public double getDebtWeightValue() { return debtWeightValue; }
    public double getWaccValue() { return waccValue; }

    /** 记录本次计算的 WACC 明细（结果页展示用）。 */
    public void setWaccDetails(double ke, double kd, double debtWeight, double wacc) {
        this.keValue = ke;
        this.kdValue = kd;
        this.debtWeightValue = debtWeight;
        this.waccValue = wacc;
    }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }

    public double getGFirst() { return gFirst; }
    public void setGFirst(double gFirst) { this.gFirst = gFirst; }

    public int getNFirst() { return nFirst; }
    public void setNFirst(int nFirst) { this.nFirst = nFirst; }

    public int getNTransition() { return nTransition; }
    public void setNTransition(int nTransition) { this.nTransition = nTransition; }

    public double getGTerminal() { return gTerminal; }
    public void setGTerminal(double gTerminal) { this.gTerminal = gTerminal; }

    public ValuationResult getResult() { return result; }
    public void setResult(ValuationResult result) { this.result = result; }

    public List<ScenarioResult> getScenarioResults() { return scenarioResults; }
    public void setScenarioResults(List<ScenarioResult> scenarioResults) { this.scenarioResults = scenarioResults; }

    public boolean isFinancial() { return financial; }
    public void setFinancial(boolean financial) { this.financial = financial; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public double getPaybackYears() { return paybackYears; }
    public void setPaybackYears(double paybackYears) { this.paybackYears = paybackYears; }

    public double getImpliedReturn() { return impliedReturn; }
    public void setImpliedReturn(double impliedReturn) { this.impliedReturn = impliedReturn; }

    public List<HistoricalBacktest> getBacktestResults() { return backtestResults; }
    public void setBacktestResults(List<HistoricalBacktest> backtestResults) { this.backtestResults = backtestResults; }

    public String getBacktestError() { return backtestError; }
    public void setBacktestError(String backtestError) { this.backtestError = backtestError; }

    public SensitivityResult getSensitivity() { return sensitivity; }
    public void setSensitivity(SensitivityResult sensitivity) { this.sensitivity = sensitivity; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public double getGSecond() { return gSecond; }
    public void setGSecond(double gSecond) { this.gSecond = gSecond; }

    public int getNSecond() { return nSecond; }
    public void setNSecond(int nSecond) { this.nSecond = nSecond; }

    public String getFcfMode() { return fcfMode; }
    public void setFcfMode(String fcfMode) { this.fcfMode = fcfMode; }

    public String getTerminalMode() { return terminalMode; }
    public void setTerminalMode(String terminalMode) { this.terminalMode = terminalMode; }

    public double getExitPe() { return exitPe; }
    public void setExitPe(double exitPe) { this.exitPe = exitPe; }

    public double getBaseFcfValue() { return baseFcfValue; }
    public void setBaseFcfValue(double baseFcfValue) { this.baseFcfValue = baseFcfValue; }

    public String getFcfModeUsed() { return fcfModeUsed; }
    public void setFcfModeUsed(String fcfModeUsed) { this.fcfModeUsed = fcfModeUsed; }

    public String getFcffWarning() { return fcffWarning; }
    public void setFcffWarning(String fcffWarning) { this.fcffWarning = fcffWarning; }

    public double getTerminalNetIncome() { return terminalNetIncome; }
    public void setTerminalNetIncome(double terminalNetIncome) { this.terminalNetIncome = terminalNetIncome; }

    public double getNetMarginUsed() { return netMarginUsed; }
    public void setNetMarginUsed(double netMarginUsed) { this.netMarginUsed = netMarginUsed; }

    public String getNetMarginSource() { return netMarginSource; }
    public void setNetMarginSource(String netMarginSource) { this.netMarginSource = netMarginSource; }

    public List<FScoreCalculator.FScoreResult> getFScores() { return fScores; }
    public void setFScores(List<FScoreCalculator.FScoreResult> fScores) { this.fScores = fScores; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}