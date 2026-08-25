package com.dcf.model;

import java.util.List;

/**
 * 金融股检测（银行 / 保险 / 证券 / 信托 / 期货等）。
 *
 * <p>金融企业财报结构特殊：经营活动现金流不能反映真实盈利能力（存贷业务资金流庞大），
 * 直接用「经营现金流 - 资本开支」口径的 DCF 会失真，需提示用户谨慎使用。
 * 检测基于公司名称关键词（不依赖行业接口，避免数据源不稳定）。
 */
public final class FinanceDetector {

    private static final List<String> KEYWORDS = List.of(
            "银行", "保险", "证券", "信托", "期货", "金融", "租赁", "消费金融");

    private FinanceDetector() {
    }

    /** 名称包含金融关键词则返回 true。 */
    public static boolean isFinancial(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return false;
        }
        return KEYWORDS.stream().anyMatch(companyName::contains);
    }
}
