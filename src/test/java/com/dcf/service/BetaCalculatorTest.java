package com.dcf.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BetaCalculator 单元测试：构造已知收益率序列验证协方差/方差口径。
 */
class BetaCalculatorTest {

    @Test
    void testBetaOfDoubledIndexReturnsTwo() {
        // 构造 40 个价格点：指数线性递增；个股对数收益 = 2 × 指数对数收益
        java.util.List<Double> index = new java.util.ArrayList<>();
        java.util.List<Double> stock = new java.util.ArrayList<>();
        double p = 100.0, q = 100.0;
        for (int i = 0; i < 40; i++) {
            index.add(p);
            stock.add(q);
            double ret = Math.log((p + 1.0) / p); // 指数收益
            p = p + 1.0;
            q = q * Math.exp(2 * ret);           // 个股收益 = 2×指数收益
        }
        double beta = BetaCalculator.calculate(stock, index);
        assertEquals(2.0, beta, 1e-6);
    }

    @Test
    void testBetaPerfectlyCorrelatedOneToOne() {
        // 个股 = 指数 × 2（常数倍），对数收益完全相同 → beta = 1
        java.util.List<Double> index = new java.util.ArrayList<>();
        double p = 100.0;
        for (int i = 0; i < 40; i++) {
            index.add(p);
            p = p * 1.01; // 恒定 1% 收益率
        }
        java.util.List<Double> stock = index.stream().map(x -> x * 2.0).toList();
        double beta = BetaCalculator.calculate(stock, index);
        assertEquals(1.0, beta, 1e-9);
    }

    @Test
    void testBetaZeroWhenStockFlat() {
        // 个股价格恒定 → 收益全 0 → beta = 0
        List<Double> index = List.of(100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0,
                108.0, 109.0, 110.0, 111.0, 112.0, 113.0, 114.0, 115.0, 116.0, 117.0, 118.0,
                119.0, 120.0, 121.0, 122.0, 123.0, 124.0, 125.0, 126.0, 127.0, 128.0, 129.0, 130.0,
                131.0, 132.0, 133.0, 134.0, 135.0);
        List<Double> stock = java.util.stream.IntStream.range(0, index.size())
                .mapToObj(i -> 100.0).toList();
        double beta = BetaCalculator.calculate(stock, index);
        assertEquals(0.0, beta, 1e-9);
    }

    @Test
    void testInsufficientSamplesReturnsNaN() {
        List<Double> shortList = List.of(100.0, 101.0, 102.0);
        assertTrue(Double.isNaN(BetaCalculator.calculate(shortList, shortList)));
    }

    @Test
    void testMismatchedLengthsReturnsNaN() {
        List<Double> a = List.of(100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0,
                108.0, 109.0, 110.0, 111.0, 112.0, 113.0, 114.0, 115.0, 116.0, 117.0, 118.0,
                119.0, 120.0, 121.0, 122.0, 123.0, 124.0, 125.0, 126.0, 127.0, 128.0, 129.0, 130.0,
                131.0, 132.0, 133.0, 134.0, 135.0);
        List<Double> b = a.subList(0, 10);
        assertTrue(Double.isNaN(BetaCalculator.calculate(a, b)));
    }
}