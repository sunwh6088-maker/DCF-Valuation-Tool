package com.dcf.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 金融股检测测试。 */
class FinanceDetectorTest {

    @Test
    void detectsFinancialNames() {
        assertTrue(FinanceDetector.isFinancial("招商银行"));
        assertTrue(FinanceDetector.isFinancial("中国平安保险"));
        assertTrue(FinanceDetector.isFinancial("中信证券"));
        assertTrue(FinanceDetector.isFinancial("中航信托"));
        assertTrue(FinanceDetector.isFinancial("南华期货"));
        assertTrue(FinanceDetector.isFinancial("中银金融租赁"));
    }

    @Test
    void ignoresNonFinancialNames() {
        assertFalse(FinanceDetector.isFinancial("贵州茅台"));
        assertFalse(FinanceDetector.isFinancial("宁德时代"));
        assertFalse(FinanceDetector.isFinancial(""));
        assertFalse(FinanceDetector.isFinancial(null));
    }
}
