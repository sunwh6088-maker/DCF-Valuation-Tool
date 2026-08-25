package com.dcf.data.sina;

import com.dcf.data.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新浪财经财报接口客户端（A 股）。
 *
 * <p>接口一次返回全部报告期、全部科目（三表共用一个接口，source 区分）：
 * <ul>
 *   <li>fzb：资产负债表</li>
 *   <li>lrb：利润表</li>
 *   <li>llb：现金流量表</li>
 * </ul>
 *
 * <p>接口地址（源自 akshare 源码确认）：
 * https://quotes.sina.cn/cn/api/openapi.php/CompanyFinanceService.getFinanceReport2022
 */
public class SinaFinanceClient {

    private static final String URL =
            "https://quotes.sina.cn/cn/api/openapi.php/CompanyFinanceService.getFinanceReport2022";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 报告源标识：资产负债表。 */
    public static final String SOURCE_BALANCE = "fzb";
    /** 报告源标识：利润表。 */
    public static final String SOURCE_PROFIT = "lrb";
    /** 报告源标识：现金流量表。 */
    public static final String SOURCE_CASH_FLOW = "llb";

    /**
     * 拉取指定报表的全部报告期数据。
     *
     * @param paperCode 带市场前缀的股票代码（如 sh600519 / sz000858）
     * @param source    {@link #SOURCE_BALANCE} / {@link #SOURCE_PROFIT} / {@link #SOURCE_CASH_FLOW}
     * @return 报告期字符串（如 "2024-12-31"）→ 科目名 → 数值
     */
    public Map<String, Map<String, Double>> fetch(String paperCode, String source) {
        String url = HttpUtil.buildQuery(URL, Map.of(
                "paperCode", paperCode,
                "source", source,
                "type", "0",
                "page", "1",
                "num", "1000"));
        String body = HttpUtil.get(url);
        return parse(body);
    }

    /**
     * 解析新浪财报 JSON 为 {报告期: {科目名: 数值}}。
     *
     * <p>结构：result.data.report_date[]（date_value 字段，如 "2024-12-31"）
     * 与 result.data.report_list[报告期].data[]（item_title / item_value）。
     *
     * @param json 接口响应体
     * @return 解析结果（LinkedHashMap 保持报告期顺序）
     */
    public static Map<String, Map<String, Double>> parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode data = root.path("result").path("data");
            Map<String, Map<String, Double>> out = new LinkedHashMap<>();
            for (JsonNode rd : data.path("report_date")) {
                String date = rd.path("date_value").asText();
                JsonNode report = data.path("report_list").path(date).path("data");
                Map<String, Double> items = new LinkedHashMap<>();
                for (JsonNode item : report) {
                    String title = item.path("item_title").asText().trim();
                    String raw = item.path("item_value").asText().trim();
                    if (title.isEmpty() || raw.isEmpty() || "-".equals(raw)) {
                        continue;
                    }
                    try {
                        items.put(title, Double.parseDouble(raw.replace(",", "")));
                    } catch (NumberFormatException ignored) {
                        // 非数值科目（如备注类）跳过
                    }
                }
                out.put(date, items);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("解析新浪财报数据失败: " + e.getMessage(), e);
        }
    }
}