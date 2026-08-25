package com.dcf.model;

/**
 * 敏感性分析结果（不可变 record）。
 *
 * <p>二维矩阵：行 = 折现率，列 = 永续增长率（或 PE 模式下的退出市盈率），
 * 单元格 = 对应假设下的每股内在价值。
 * 非法组合（永续增长率 >= 折现率）的单元格为 NaN。
 *
 * @param values        values[r][c] 每股内在价值矩阵
 * @param discountRates 行标签：折现率序列
 * @param growthRates   列标签：永续增长率（或退出 PE）序列
 * @param xLabel        列轴名称（"永续增长率" / "退出PE"）
 */
public record SensitivityResult(double[][] values, double[] discountRates, double[] growthRates, String xLabel) {

    public SensitivityResult(double[][] values, double[] discountRates, double[] growthRates) {
        this(values, discountRates, growthRates, "永续增长率");
    }

    public int rows() {
        return values.length;
    }

    public int cols() {
        return values[0].length;
    }
}
