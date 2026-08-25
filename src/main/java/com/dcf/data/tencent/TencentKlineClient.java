package com.dcf.data.tencent;

import com.dcf.data.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.TreeMap;

/**
 * 腾讯财经 K 线客户端（东财备用数据源）。
 *
 * <p>东财 push2his 免费接口偶发整体不可用（2026-08-25 实测连 .NET 直连都失败），
 * 本客户端用腾讯 {@code web.ifzq.gtimg.cn} 前复权周线兜底，供 Beta 计算使用。
 *
 * <p>返回 {@code Map<日期yyyy-MM-dd, 收盘价>}（TreeMap 按日期升序）。
 * 响应行格式：[日期, 开盘, 收盘, 最高, 最低, 成交量]，收盘价在 index=2。
 */
public final class TencentKlineClient {

    private static final String KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get";
    /** 请求的周线条数（320 条约 6 年，足够近 3 年 Beta 计算）。 */
    private static final int COUNT = 320;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TencentKlineClient() {
    }

    /**
     * 拉取前复权周线收盘价（升序，过滤起始日期之前的数据）。
     *
     * @param symbol    完整证券标识（如 sh600519 / sh000300）
     * @param startDate 起始日期（yyyyMMdd，含）
     * @return 日期(yyyy-MM-dd) → 收盘价
     */
    public static Map<String, Double> fetchWeeklyCloses(String symbol, String startDate) {
        String url = HttpUtil.buildQuery(KLINE_URL,
                Map.of("param", symbol + ",week,,," + COUNT + ",qfq"));
        String body = HttpUtil.get(url);
        try {
            JsonNode data = MAPPER.readTree(body).path("data").path(symbol);
            JsonNode rows = data.path("qfqweek");
            if (rows.isMissingNode() || !rows.isArray()) {
                rows = data.path("week"); // 个别标的可能无前复权周线，退回不复权
            }
            if (rows.isMissingNode() || !rows.isArray()) {
                throw new RuntimeException("腾讯K线无周线数据: " + symbol);
            }
            Map<String, Double> closes = new TreeMap<>();
            for (JsonNode row : rows) {
                String date = row.get(0).asText();
                if (date.replace("-", "").compareTo(startDate) < 0) {
                    continue;
                }
                closes.put(date, row.get(2).asDouble());
            }
            return closes;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析腾讯K线失败: " + e.getMessage(), e);
        }
    }
}
