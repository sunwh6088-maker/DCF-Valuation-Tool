package com.dcf.data.model;

/**
 * 汇总公司数据：历史财务 + 当前快照 + 元信息。
 *
 * @param market  市场："CN"（A股）或 "US"（美股）
 * @param code    股票代码
 * @param history 历史财务序列
 * @param snapshot 当前快照
 * @param source  数据来源（auto / csv / manual）
 * @param fetchedAt 抓取时间（ISO 字符串）
 */
public record CompanyData(
        String market,
        String code,
        HistoricalData history,
        SnapshotData snapshot,
        String source,
        String fetchedAt) {
}