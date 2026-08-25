package com.dcf.service;

import com.dcf.data.model.HistoricalData;
import com.dcf.model.SensitivityResult;
import com.dcf.model.ValuationResult;
import com.dcf.web.ValuationContext;

import java.time.LocalDate;

/**
 * Markdown 估值报告生成器。
 *
 * <p>报告结构：结论 → 模型口径 → 历史财务 → 假设 → 现金流预测 → 估值拆分 → 敏感性 → 免责。
 * 全部逻辑透明可复核，数值与页面/Excel 同源。
 */
public class ReportService {

    /** 生成 Markdown 报告文本。 */
    public static String generate(ValuationContext ctx) {
        var c = ctx.getCompany();
        var snap = c.snapshot();
        var h = c.history();
        ValuationResult res = ctx.getResult();
        SensitivityResult sen = ctx.getSensitivity();
        double price = snap.price();
        double margin = (res.perShareValue() - price) / price;
        double ke = ctx.getRf() + ctx.getBetaInput() * ctx.getErp();
        double rate = switch (ctx.getDiscountMode()) {
            case "capm" -> ke;
            case "manual" -> ctx.getManualDiscountRate();
            default -> ctx.getWaccValue();
        };

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(snap.name()).append("（").append(c.code()).append("）DCF 估值报告\n\n");
        sb.append("> 生成日期：").append(LocalDate.now())
          .append(" ｜ 数据来源：").append(c.source())
          .append(" ｜ 数据截止：").append(snap.asOf()).append("\n\n");

        // 一、结论
        sb.append("## 一、估值结论\n\n");
        sb.append(String.format("| 指标 | 数值 |\n|---|---|\n"));
        sb.append(String.format("| 每股内在价值 | **%.2f** |\n", res.perShareValue()));
        sb.append(String.format("| 当前股价 | %.2f |\n", price));
        sb.append(String.format("| 安全边际 | **%.1f%%** |\n", margin * 100));
        sb.append(String.format("| 结论 | %s |\n", margin >= 0 ? "**低估**，值得关注" : "**高估**，谨慎对待"));
        sb.append(String.format("| 判断分级 | %s |\n", ctx.getVerdict()));
        sb.append(String.format("| 回本年限 | %.1f 年 |\n", ctx.getPaybackYears()));
        sb.append(String.format("| 隐含年化回报 | %.1f%% |\n", ctx.getImpliedReturn() * 100));
        sb.append("\n> 本结论基于下方全部假设，请结合敏感性分析判断稳健性。\n\n");
        sb.append("### 三情景对比\n\n");
        sb.append("| 情景 | 折现率 | 每股内在价值 | 股权价值 |\n|---|---|---|---|\n");
        for (com.dcf.model.ScenarioResult sc : ctx.getScenarioResults()) {
            sb.append(String.format("| %s情景 | %.2f%% | %,.2f | %,.0f |\n",
                    sc.scenario().label(), sc.discountRate() * 100, sc.perShareValue(), sc.equityValue()));
        }
        sb.append("\n");

        if (ctx.isFinancial()) {
            sb.append("> ⚠️ 本标的为金融企业（银行/保险/证券/信托等），经营现金流口径 DCF 参考性有限，"
                    + "建议结合 PB/PE 相对估值交叉验证。\n\n");
        }

        // 二、模型口径
        sb.append("## 二、模型口径\n\n");
        sb.append("- 自由现金流 FCF = 经营活动现金流 − 资本开支\n");
        sb.append("- 两阶段 DCF：显式预测期 10 年（前 5 年固定增长率，后 5 年线性过渡到永续增长率）\n");
        sb.append("- 终值：Gordon 模型 TV = FCFₙ×(1+g)/(r−g)\n");
        sb.append("- 企业价值 EV = 显式期现值 + 终值现值\n");
        sb.append("- 股权价值 = EV − 净债务 − 少数股东权益；每股价值 = 股权价值 / 总股本\n");
        sb.append("- 折现率：").append(switch (ctx.getDiscountMode()) {
            case "capm" -> "CAPM（ke = rf + β×ERP）";
            case "manual" -> "手动指定";
            default -> "WACC 加权（kd×(1-t)×D/(D+E) + ke×E/(D+E)）";
        }).append("\n\n");

        // 三、历史财务
        sb.append("## 三、历史财务与自由现金流（单位：元）\n\n");
        sb.append("| 年份 | 经营现金流 | 资本开支 | 自由现金流 | 营收 | EBIT |\n|---|---|---|---|---|---|\n");
        for (int i = 0; i < h.size(); i++) {
            sb.append(String.format("| %d | %,.0f | %,.0f | %,.0f | %,.0f | %,.0f |\n",
                    h.years()[i], h.ocf()[i], h.capex()[i], h.ocf()[i] - h.capex()[i],
                    h.revenue()[i], h.ebit()[i]));
        }
        sb.append(String.format("\n快照：股价 %.2f ｜ 总股本 %,.0f ｜ 净债务 %,.0f ｜ 少数股东权益 %,.0f\n\n",
                price, snap.sharesOutstanding(), snap.netDebt(), snap.minorityInterest()));

        // 四、假设
        sb.append("## 四、估值假设\n\n");
        sb.append("| 参数 | 数值 |\n|---|---|\n");
        sb.append(String.format("| 无风险利率 Rf | %.2f%% |\n", ctx.getRf() * 100));
        sb.append(String.format("| Beta | %.3f |\n", ctx.getBetaInput()));
        sb.append(String.format("| 市场风险溢价 ERP | %.1f%% |\n", ctx.getErp() * 100));
        sb.append(String.format("| 折现率 | %.2f%%（%s） |\n", rate * 100, switch (ctx.getDiscountMode()) {
            case "capm" -> "CAPM";
            case "manual" -> "手动";
            default -> "WACC";
        }));
        sb.append(String.format("| 股权成本 ke | %.2f%% |\n", ctx.getKeValue() * 100));
        sb.append(String.format("| 债务成本 kd | %.2f%% |\n", ctx.getKdValue() * 100));
        sb.append(String.format("| 债务权重 D/(D+E) | %.1f%% |\n", ctx.getDebtWeightValue() * 100));
        sb.append(String.format("| 有效税率 | %.1f%% |\n", ctx.getTaxRate() * 100));
        sb.append(String.format("| 高增长期增长率 | %.2f%% × %d 年 |\n", ctx.getGFirst() * 100, ctx.getNFirst()));
        sb.append(String.format("| 过渡期 | %d 年线性过渡 |\n", ctx.getNTransition()));
        sb.append(String.format("| 永续增长率 g | %.2f%% |\n", ctx.getGTerminal() * 100));
        sb.append("\n");

        // 五、现金流预测
        sb.append("## 五、自由现金流预测\n\n");
        sb.append("| 预测年 | 增长率 | 自由现金流（元） |\n|---|---|---|\n");
        for (int i = 0; i < res.fcfForecast().length; i++) {
            sb.append(String.format("| 第%d年 | %.2f%% | %,.0f |\n",
                    i + 1, res.growthPath()[i] * 100, res.fcfForecast()[i]));
        }
        sb.append("\n");

        // 六、估值拆分
        sb.append("## 六、估值拆分（单位：元）\n\n");
        sb.append("| 项目 | 金额 |\n|---|---|\n");
        sb.append(String.format("| 显式期现值 | %,.0f |\n", res.pvFcf()));
        sb.append(String.format("| 终值现值 | %,.0f |\n", res.pvTerminal()));
        sb.append(String.format("| **终值占比** | **%.1f%%** %s |\n", res.terminalRatio() * 100,
                res.terminalRatio() > 0.8 ? "⚠️ 高于 80%，估值对永续假设敏感" : ""));
        sb.append(String.format("| 企业价值 EV | %,.0f |\n", res.enterpriseValue()));
        sb.append(String.format("| − 净债务 | %,.0f |\n", snap.netDebt()));
        sb.append(String.format("| − 少数股东权益 | %,.0f |\n", snap.minorityInterest()));
        sb.append(String.format("| 股权价值 | %,.0f |\n", res.equityValue()));
        sb.append(String.format("| 每股内在价值 | **%.2f** |\n", res.perShareValue()));
        sb.append("\n");

        // 七、敏感性
        sb.append("## 七、敏感性分析（每股内在价值，折现率 × 永续增长率）\n\n");
        sb.append("| 折现率 \\ 永续增长率 |");
        for (double g : sen.growthRates()) {
            sb.append(String.format(" %.2f%% |", g * 100));
        }
        sb.append("\n|---|");
        for (int col = 0; col < sen.cols(); col++) {
            sb.append("---|");
        }
        sb.append("\n");
        for (int r = 0; r < sen.rows(); r++) {
            sb.append(String.format("| %.1f%% |", sen.discountRates()[r] * 100));
            for (int col = 0; col < sen.cols(); col++) {
                double v = sen.values()[r][col];
                sb.append(Double.isNaN(v) ? " g≥r |" : String.format(" %.2f |", v));
            }
            sb.append("\n");
        }
        sb.append("\n");

        // 八、免责
        sb.append("## 八、风险提示与免责声明\n\n");
        sb.append("1. 模型结果对增长率与折现率假设高度敏感，请结合敏感性矩阵评估区间；\n");
        sb.append("2. 历史现金流口径为简化口径（OCF − Capex），未调整营运资本变动与利息收支；\n");
        sb.append("3. 本报告由程序自动生成，仅供学习研究，不构成任何投资建议。\n");
        return sb.toString();
    }
}