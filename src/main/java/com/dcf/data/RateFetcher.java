package com.dcf.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 无风险利率自动获取器。
 *
 * <ul>
 *   <li>美股（US）：FRED DGS10（10 年期美国国债，日频，免费无 Key）——
 *       https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS10</li>
 *   <li>中国（CN）：中债登「国债及其他债券收益率曲线」历史查询（日频，免费无登录）——
 *       https://yield.chinabond.com.cn/cbweb-pbc-web/pbc/historyQuery
 *       解析「中债国债收益率曲线」行的 10 年列；失败返回 {@link #NOT_AVAILABLE}，由页面提示手动输入。</li>
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

    private static final String CHINABOND_HISTORY_URL =
            "https://yield.chinabond.com.cn/cbweb-pbc-web/pbc/historyQuery";

    /** 中债登查询窗口上限（接口要求 end-start < 1 年），取最近 30 天足够。 */
    private static final int CN_QUERY_DAYS = 30;

    /** 「10年」列在曲线数据行中的下标：曲线名称/日期/3月/6月/1年/3年/5年/7年/10年/30年。 */
    private static final int CN_COL_10Y = 8;

    /** 「3年」列下标（信用利差用：票据AAA 3Y − 国债 3Y）。 */
    private static final int CN_COL_3Y = 5;

    /** 信用利差基准曲线：中短期票据收益率曲线(AAA)。 */
    private static final String CN_TARGET_NOTE = "中债中短期票据收益率曲线(AAA)";

    /** 目标曲线行首列匹配词（排除“中债商业银行普通债/中短期票据”等行）。 */
    private static final String CN_TARGET_CURVE = "中债国债收益率曲线";

    private static final Pattern TR_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern TD_PATTERN = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.DOTALL);

    private Double cachedUs10y;
    private Double cachedCn10y;
    private Double cachedCnSpread;
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

    /**
     * 获取中国 10 年期国债收益率（小数，如 0.0168）；失败返回 {@link #NOT_AVAILABLE}。
     * 数据源：中债登历史查询（免登录），解析「中债国债收益率曲线」最新一行的 10 年列。
     */
    public synchronized double fetchCn10y() {
        if (cachedCn10y != null && cacheTime != null
                && cacheTime.isAfter(LocalDateTime.now().minusHours(24))) {
            return cachedCn10y;
        }
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(CN_QUERY_DAYS);
            String url = CHINABOND_HISTORY_URL
                    + "?startDate=" + start + "&endDate=" + end
                    + "&gjqx=0&qxId=ycqx&locale=cn_ZH";
            String html = fetchText(url);
            double value = parseChinabond10y(html);
            cachedCn10y = value;
            cacheTime = LocalDateTime.now();
            return value;
        } catch (Exception e) {
            return NOT_AVAILABLE;
        }
    }

    /**
     * 获取中国信用利差参考（中短期票据 AAA 3 年 − 国债 3 年，小数如 0.0041）。
     * 同一张中债登历史查询表即包含两条曲线，按最新共同交易日对齐；失败返回 {@link #NOT_AVAILABLE}。
     */
    public synchronized double fetchCnCreditSpread() {
        if (cachedCnSpread != null && cacheTime != null
                && cacheTime.isAfter(LocalDateTime.now().minusHours(24))) {
            return cachedCnSpread;
        }
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(CN_QUERY_DAYS);
            String url = CHINABOND_HISTORY_URL
                    + "?startDate=" + start + "&endDate=" + end
                    + "&gjqx=0&qxId=ycqx&locale=cn_ZH";
            String html = fetchText(url);
            double value = parseChinabondCreditSpread(html);
            cachedCnSpread = value;
            cacheTime = LocalDateTime.now();
            return value;
        } catch (Exception e) {
            return NOT_AVAILABLE;
        }
    }

    /** 按市场获取：US→FRED；CN→中债登（失败回退默认手动）。 */
    public double fetch(String market) {
        return "US".equalsIgnoreCase(market) ? fetchUs10y() : fetchCn10y();
    }

    /**
     * 解析中债登 historyQuery 返回的 HTML 表格，取「中债国债收益率曲线」最新一行的 10 年列。
     * 包私有，便于单元测试。
     *
     * @param html UTF-8 编码的查询结果页面
     * @return 收益率（小数，如 0.016794）
     */
    static double parseChinabond10y(String html) {
        Matcher trMatcher = TR_PATTERN.matcher(html);
        while (trMatcher.find()) {
            List<String> cells = extractCells(trMatcher.group(1));
            if (cells.size() <= CN_COL_10Y || !cells.get(0).startsWith(CN_TARGET_CURVE)) {
                continue;
            }
            String value = cells.get(CN_COL_10Y);
            if (value.isEmpty()) {
                continue; // 最新一行 10 年可能暂无值，继续向前找上一交易日
            }
            return Double.parseDouble(value) / 100.0;
        }
        throw new IllegalStateException("中债登 HTML 中未找到目标曲线数据");
    }

    /**
     * 解析中债登 historyQuery HTML，计算信用利差 = 中短期票据AAA 3年 − 国债 3年。
     * 取两条曲线「最新共同交易日」的 3 年列（避免跨日错配）；包私有，便于单元测试。
     *
     * @param html UTF-8 编码的查询结果页面
     * @return 利差（小数，如 0.004103）
     */
    static double parseChinabondCreditSpread(String html) {
        java.util.TreeMap<String, Double> gov3y = new java.util.TreeMap<>();
        java.util.TreeMap<String, Double> note3y = new java.util.TreeMap<>();
        Matcher trMatcher = TR_PATTERN.matcher(html);
        while (trMatcher.find()) {
            List<String> cells = extractCells(trMatcher.group(1));
            if (cells.size() <= CN_COL_3Y || cells.size() < 2) {
                continue;
            }
            String name = cells.get(0);
            // 先匹配曲线名称再解析数值：表头行（曲线名称/日期/3月/.../30年）的第 5 列是文字「3年」，
            // 提前解析会 NumberFormatException
            if (!name.startsWith(CN_TARGET_CURVE) && !name.startsWith(CN_TARGET_NOTE)) {
                continue;
            }
            String date = cells.get(1);
            String value = cells.get(CN_COL_3Y);
            if (value.isEmpty()) {
                continue;
            }
            double v = Double.parseDouble(value) / 100.0;
            if (name.startsWith(CN_TARGET_CURVE)) {
                gov3y.put(date, v);
            } else {
                note3y.put(date, v);
            }
        }
        // 从最新日期向前找两条曲线共有的交易日
        for (String date : gov3y.descendingKeySet()) {
            Double note = note3y.get(date);
            if (note != null) {
                return note - gov3y.get(date);
            }
        }
        throw new IllegalStateException("中债登 HTML 中未找到可对齐的国债/票据AAA 3 年曲线");
    }

    /** 提取一行 <tr> 内的所有 <td>/<th> 单元格文本（去标签、去空白）。 */
    private static List<String> extractCells(String trBody) {
        List<String> cells = new ArrayList<>();
        Matcher tdMatcher = TD_PATTERN.matcher(trBody);
        while (tdMatcher.find()) {
            String text = tdMatcher.group(1)
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .trim();
            cells.add(text);
        }
        return cells;
    }

    /** 从 FRED CSV 中取最后一行非空值。 */
    private static double fetchFredLatest(String url) throws Exception {
        String csv = fetchText(url);
        double last = NOT_AVAILABLE;
        for (String line : csv.split("\r?\n")) {
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
        if (Double.isNaN(last)) {
            throw new IllegalStateException("FRED 数据为空");
        }
        return last;
    }

    /** 通用 GET 文本获取（15s 超时，UTF-8 解码）。 */
    private static String fetchText(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DCF-Valuation-Tool/1.1");
        if (url.startsWith(CHINABOND_HISTORY_URL)) {
            // 中债登证书由国内 CA 签发，不在 JDK 默认信任库（cacerts），需宽松 TLS 才能访问；
            // 仅用于读取公开国债收益率，不涉及用户敏感信息；FRED 仍走严格校验。
            HttpsURLConnection https = (HttpsURLConnection) conn;
            https.setSSLSocketFactory(looseSslContext().getSocketFactory());
            https.setHostnameVerifier(LOOSE_HOSTNAME_VERIFIER);
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    /** 仅校验主机名与内置名单一致的宽松校验器（只用于中债登）。 */
    private static final HostnameVerifier LOOSE_HOSTNAME_VERIFIER = (hostname, session) -> {
        try {
            return hostname.equalsIgnoreCase(URI.create(CHINABOND_HISTORY_URL).getHost());
        } catch (Exception e) {
            return false;
        }
    };

    /**
     * 宽松 TLS 上下文：信任任意证书链（仅中债登使用）。
     * 原因：中债登 SSL 证书链（国内 CA）不在 JDK cacerts 中，严格校验会 PKIX 失败；
     * 该接口仅返回公开的国债收益率行情，不传输任何敏感数据。
     */
    private static SSLContext looseSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());
        return sc;
    }

    /** 数据来源描述（报告/页面标注用）。 */
    public static String sourceLabel(String market) {
        return "US".equalsIgnoreCase(market)
                ? "FRED DGS10（10Y 美国国债，日频）"
                : "中债登（中债国债收益率曲线 10Y，日频）";
    }
}