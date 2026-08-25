package com.dcf.model;

/**
 * 估值结果对象（不可变 record）。
 *
 * <p>包含两阶段 DCF 的全部中间结果与最终结论：
 * <ul>
 *   <li>pvFcf：显式预测期各年 FCF 的现值之和</li>
 *   <li>terminalValue：终值（Gordon 模型，未折现）</li>
 *   <li>pvTerminal：终值现值</li>
 *   <li>enterpriseValue：企业价值 EV = pvFcf + pvTerminal</li>
 *   <li>terminalRatio：终值现值占企业价值的比例（过高说明估值依赖假设，需警惕）</li>
 *   <li>equityValue：股权价值 = EV - 净债务 - 少数股东权益</li>
 *   <li>perShareValue：每股内在价值 = 股权价值 / 总股本</li>
 *   <li>fcfForecast：显式期逐年 FCF 预测值</li>
 *   <li>growthPath：显式期逐年增长率假设</li>
 * </ul>
 *
 * @param pvFcf          显式期现值
 * @param terminalValue  终值（未折现）
 * @param pvTerminal     终值现值
 * @param enterpriseValue 企业价值
 * @param terminalRatio  终值占比（0~1）
 * @param equityValue    股权价值
 * @param perShareValue  每股内在价值
 * @param fcfForecast    显式期 FCF 序列（可空）
 * @param growthPath     显式期增长率序列（可空）
 */
public record ValuationResult(
        double pvFcf,
        double terminalValue,
        double pvTerminal,
        double enterpriseValue,
        double terminalRatio,
        double equityValue,
        double perShareValue,
        double[] fcfForecast,
        double[] growthPath) {

    /** 仅核心估值（不含股权拆分）时使用的占位值。 */
    public static final double NOT_SET = Double.NaN;
}