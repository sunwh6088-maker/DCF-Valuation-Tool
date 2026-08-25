package com.dcf.service;

import com.dcf.data.model.HistoricalData;
import com.dcf.model.DcfModel;
import com.dcf.model.ValuationResult;
import com.dcf.web.ValuationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 历史 DCF 回溯：用历史财务数据逐年回算「当年模型估值」，与当年实际股价对比。
 *
 * <p>用途：验证模型假设的合理性——若多年回算估值与股价长期大幅偏离，说明参数假设
 * 或口径可能存在问题（这是 halessi/DCF 项目最独特的功能）。
 *
 * <p>口径（全部透明）：
 * <ul>
 *   <li>基准 FCF = 该历史年份当年的自由现金流（经营现金流 - 资本开支）</li>
 *   <li>增长率 / 折现率 / 永续增长率 / 预测年数 = 沿用当前参数页设置</li>
 *   <li>净债务 / 少数股东权益 / 股本 = 当前快照值（历史值不可得，标注近似）</li>
 *   <li>股价 = 该年 12 月末收盘价（东财月线）</li>
 * </ul>
 */
public class HistoricalDcfService {

    /** 需要的历史前置年数（少于该年数时该年不参与回溯，避免样本过短）。 */
    public static final int MIN_HISTORY_YEARS = 4;

    /**
     * 逐年回溯估值。
     *
     * @param h         历史财务数据
     * @param yearPrices 年份 → 年末收盘价（可缺失，缺失年份仍返回估值但 price/premium 为 NaN）
     * @param ctx        当前估值参数（增长率/折现率/股本等）
     * @return 按年份升序的回溯结果列表
     */
    public static List<HistoricalBacktest> backtest(HistoricalData h,
                                                    Map<Integer, Double> yearPrices,
                                                    ValuationContext ctx) {
        List<HistoricalBacktest> out = new ArrayList<>();
        int n = h.size();
        for (int i = 0; i < n; i++) {
            int year = h.years()[i];
            double fcf = h.ocf()[i] - h.capex()[i];
            if (!Double.isFinite(fcf) || i < MIN_HISTORY_YEARS) {
                continue;
            }
            double rate = effectiveRate(ctx);
            double perShare = Double.NaN;
            try {
                ValuationResult r = DcfModel.fullValuation(
                        fcf, ctx.getGFirst(), ctx.getGTerminal(), rate,
                        ctx.getCompany().snapshot().netDebt(),
                        ctx.getCompany().snapshot().sharesOutstanding(),
                        ctx.getCompany().snapshot().minorityInterest(),
                        ctx.getNFirst(), ctx.getNTransition());
                perShare = r.perShareValue();
            } catch (IllegalArgumentException ignore) {
                // 极端参数下该年跳过（如 r <= g）
            }
            double price = yearPrices == null ? Double.NaN : yearPrices.getOrDefault(year, Double.NaN);
            double premium = (Double.isFinite(perShare) && Double.isFinite(price) && price > 0)
                    ? (perShare - price) / price : Double.NaN;
            out.add(new HistoricalBacktest(year, perShare, price, premium));
        }
        return out;
    }

    /** 与主流程一致的折现率：wacc / capm / manual。 */
    private static double effectiveRate(ValuationContext ctx) {
        return switch (ctx.getDiscountMode()) {
            case "capm" -> DcfModel.capmCostOfEquity(ctx.getRf(), ctx.getBetaInput(), ctx.getErp());
            case "manual" -> ctx.getManualDiscountRate();
            default -> ctx.getWaccValue();
        };
    }
}
