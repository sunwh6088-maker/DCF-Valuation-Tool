package com.dcf.data;

import com.dcf.data.model.CompanyData;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实接口冒烟测试（依赖网络，验证通过后保留为 @Disabled 文档用例）。
 */
class LiveApiSmokeTest {

    @Disabled("真实接口冒烟测试，需网络；手动去掉 @Disabled 后运行 mvn test -Dtest=LiveApiSmokeTest")
    @Test
    void fetchMoutai() {
        DataService svc = new DataService(Path.of("data/cache"));
        CompanyData data = svc.fetchAShare("600519", 10);
        System.out.println("name=" + data.snapshot().name()
                + " price=" + data.snapshot().price()
                + " shares=" + data.snapshot().sharesOutstanding()
                + " years=" + data.history().size());
        for (int i = 0; i < data.history().size(); i++) {
            System.out.println(data.history().years()[i]
                    + " ocf=" + data.history().ocf()[i]
                    + " capex=" + data.history().capex()[i]
                    + " fcf=" + (data.history().ocf()[i] - data.history().capex()[i]));
        }
        double beta = svc.fetchBeta("600519");
        System.out.println("beta=" + beta);
        assertTrue(data.history().size() >= 5, "应至少抓取 5 年历史数据");
        assertTrue(data.snapshot().price() > 0);
        assertTrue(data.snapshot().sharesOutstanding() > 0);
    }

    @Disabled("真实接口冒烟测试，需网络；手动去掉 @Disabled 后运行 mvn test -Dtest=LiveApiSmokeTest")
    @Test
    void fetchRfUsAndCn() {
        RateFetcher fetcher = new RateFetcher();
        double us = fetcher.fetchUs10y();
        System.out.println("US 10Y = " + us);
        assertTrue(us > 0.01 && us < 0.08, "美国 10Y 应在 1%-8% 区间，实际 " + us);
        double cn = fetcher.fetchCn10y();
        System.out.println("CN 10Y = " + cn);
        assertTrue(cn > 0.005 && cn < 0.05, "中国 10Y 应在 0.5%-5% 区间，实际 " + cn);
    }
}