package com.dcf.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RateFetcher 单元测试：市场分发、CN 回退、来源标注。
 * （FRED 联网取值见 LiveApiSmokeTest#fetchFredUs10y，默认 @Disabled）
 */
class RateFetcherTest {

    @Test
    void cnIsNotAvailableWithDefaultFallback() {
        RateFetcher f = new RateFetcher();
        assertTrue(Double.isNaN(f.fetchCn10y()));
        assertTrue(Double.isNaN(f.fetch("CN")));
        assertEquals(0.017, RateFetcher.CN_DEFAULT_10Y, 1e-9);
    }

    @Test
    void sourceLabelsAreDescriptive() {
        assertTrue(RateFetcher.sourceLabel("US").contains("FRED"));
        assertTrue(RateFetcher.sourceLabel("CN").contains("手动输入"));
        assertTrue(RateFetcher.sourceLabel("cn").contains("手动输入"));
    }
}
