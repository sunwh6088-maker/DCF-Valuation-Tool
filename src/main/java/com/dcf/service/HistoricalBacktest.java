package com.dcf.service;

/**
 * 历史 DCF 回溯单年结果。
 *
 * @param year          数据年份
 * @param perShareValue 用截至该年数据回算的每股内在价值
 * @param price         该年年末收盘价（缺失为 NaN）
 * @param premium       折溢价 =（内在价值 - 股价）/ 股价（正=当年模型认为低估）
 */
public record HistoricalBacktest(int year, double perShareValue, double price, double premium) {
}
