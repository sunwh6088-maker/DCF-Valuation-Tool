package com.dcf.data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 简易 HTTP 工具（JDK 内置 HttpClient 封装）。
 *
 * <p>统一设置：10 秒连接超时、浏览器 UA、UTF-8。
 * 所有数据源请求均走此工具，便于集中调整代理与超时策略。
 */
public final class HttpUtil {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private HttpUtil() {
    }

    /**
     * GET 请求返回 UTF-8 文本。
     *
     * @param url 完整 URL（含查询参数）
     * @return 响应体文本
     * @throws RuntimeException 网络错误或非 200 响应
     */
    public static String get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA)
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + url);
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("请求被中断: " + url, e);
        } catch (Exception e) {
            throw new RuntimeException("网络请求失败: " + url + "（" + e.getMessage() + "）", e);
        }
    }

    /** 拼装查询参数。 */
    public static String buildQuery(String base, java.util.Map<String, String> params) {
        StringBuilder sb = new StringBuilder(base).append('?');
        params.forEach((k, v) -> {
            if (sb.length() > base.length() + 1) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}