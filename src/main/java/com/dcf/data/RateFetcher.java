package com.dcf.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 无风险利率自动获取器。
 *
 * <ul>
 *   <li>美股（US）：FRED DGS10（10 年期美国国债，日频，免费无 Key）——
 *       https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS10</li>
 *   <li>中国（CN）：截至 v1.1 实测东财/新浪/中债登/中国货币网免费接口均不可用
 *       （东财 reportName 已下线、新浪 globalbd 返回空、中债登 xlsx 需登录），
 *       故 CN 返回 {@link #NOT_AVAILABLE}，由页面提示手动输入默认值。</li>
 * </ul>
 *
 * <p>结果缓存 24 小时，避免频繁请求外部接口。
 */
public class RateFetcher {

    /** 自动获取失败标记。 */
    public static final double NOT_AVAILABLE = Double.NaN;

    /** 中国 10Y 国债默认兜底值（页面提示使用）。 */
    public static final double CN_DEFAULT_10Y = 0.017;

    private static final String FRED_DGS10_URL =
            "https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS10";

    private Double cachedUs10y;
    private LocalDateTime cacheTime;

    /** 获取美国 10 年期国债收益率（小数，如 0.0474）；失败返回 {@link #NOT_AVAILABLE}。 */
    public synchronized double fetchUs10y() {
        if (cachedUs10y != null && cacheTime != null
                && cacheTime.isAfter(LocalDateTime.now().minusHours(24))) {
            return cachedUs10y;
        }
        try {
            double value = fetchFredLatest(FRED_DGS10_URL);
            cachedUs10y = value;
            cacheTime = LocalDateTime.now();
            return value;
        } catch (Exception e) {
            return NOT_AVAILABLE;
        }
    }

    /** 获取中国 10 年期国债收益率；当前免费源不可用，返回 {@link #NOT_AVAILABLE}。 */
    public double fetchCn10y() {
        return NOT_AVAILABLE;
    }

    /** 按市场获取：US→FRED；CN→不可用（回退默认）。 */
    public double fetch(String market) {
        return "US".equalsIgnoreCase(market) ? fetchUs10y() : fetchCn10y();
    }

    /** 从 FRED CSV 中取最后一行非空值。 */
    private static double fetchFredLatest(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "DCF-Valuation-Tool/1.1");
        double last = NOT_AVAILABLE;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("DATE")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    try {
                        last = Double.parseDouble(parts[1].trim()) / 100.0;
                    } catch (NumberFormatException ignore) {
                        // 跳过缺失值行（如 "."）
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        if (Double.isNaN(last)) {
            throw new IllegalStateException("FRED 数据为空");
        }
        return last;
    }

    /** 数据来源描述（报告/页面标注用）。 */
    public static String sourceLabel(String market) {
        return "US".equalsIgnoreCase(market)
                ? "FRED DGS10（10Y 美国国债，日频）"
                : "未获取（免费接口不可用，请手动输入，默认 1.7%）";
    }
}
