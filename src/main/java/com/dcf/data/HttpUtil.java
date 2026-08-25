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

    /** 最多尝试次数（含首次）。东财等免费源连接偶发被服务器立即断开，重试可显著提高成功率。 */
    private static final int MAX_ATTEMPTS = 3;
    /** 重试间隔（毫秒）。 */
    private static final long RETRY_INTERVAL_MS = 400;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            // 实测（2026-08-25）：东财 push2his 在 HTTP/2 下稳定（3/3），
            // ALPN 降级到 HTTP/1.1 后连接常被服务器立即关闭（"header parser received no bytes"）。
            .version(HttpClient.Version.HTTP_2)
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
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .GET()
                .build();
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
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
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("请求被中断: " + url, ie);
                    }
                }
            }
        }
        throw new RuntimeException("网络请求失败: " + url + "（" + last.getMessage() + "，已重试 " + (MAX_ATTEMPTS - 1) + " 次）", last);
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