package com.dcf.data.model;

/**
 * 估值基准日快照数据（当前时点）。
 *
 * <p>净债务 = 有息负债 - 现金及等价物，可为负（净现金）。
 * 有息负债 = 短期借款 + 长期借款 + 应付债券 + 一年内到期的非流动负债 + 租赁负债。
 */
public record SnapshotData(
        String name,
        String code,
        double price,
        double sharesOutstanding,
        double cash,
        double interestDebt,
        double minorityInterest,
        String asOf) {

    /** 净债务 = 有息负债 - 货币资金（可为负）。 */
    public double netDebt() {
        return interestDebt - cash;
    }
}