package com.dcf.model;

/**
 * 敏感性分析结果（不可变 record）。
 *
 * <p>二维矩阵：行 = 折现率，列 = 永续增长率，单元格 = 对应假设下的每股内在价值。
 * 非法组合（永续增长率 >= 折现率）的单元格为 NaN。
 *
 * @param values       values[r][c] 每股内在价值矩阵
 * @param discountRates 行标签：折现率序列
 * @param growthRates   列标签：永续增长率序列
 */
public record SensitivityResult(double[][] values, double[] discountRates, double[] growthRates) {

    public int rows() {
        return values.length;
    }

    public int cols() {
        return values[0].length;
    }
}