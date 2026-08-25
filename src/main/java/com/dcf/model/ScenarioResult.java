package com.dcf.model;

/**
 * 单情景估值结果摘要（三情景对比展示用）。
 *
 * @param scenario      情景
 * @param discountRate  该情景使用的折现率
 * @param perShareValue 每股内在价值
 * @param equityValue   股权价值
 */
public record ScenarioResult(Scenario scenario, double discountRate, double perShareValue, double equityValue) {
}
