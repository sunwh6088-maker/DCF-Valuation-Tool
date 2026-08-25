package com.dcf.data.eastmoney;

import com.dcf.data.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 东方财富接口客户端（A 股）。
 *
 * <p>提供两类数据：
 * <ul>
 *   <li>个股快照：名称、最新价、总股本（push2.eastmoney.com/api/qt/stock/get）</li>
 *   <li>历史 K 线：个股/指数周线（push2his.eastmoney.com/api/qt/stock/kline/get，Beta 计算用）</li>
 * </ul>
 */
public class EastMoneyClient {

    private static final String QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 沪深300 指数 secid（Beta 基准）。 */
    public static final String CSI300_SECID = "1.000300";

    /**
     * 计算东财 secid：6 开头（沪市）=1，其余（深市）=0。
     */
    public static String secid(String code) {
        return (code.startsWith("6") ? "1." : "0.") + code;
    }

    /**
     * 拉取个股快照。
     *
     * @param code 6 位股票代码
     * @return 快照 record：名称 f58 / 最新价 f43 / 总股本 f84
     */
    public static Quote fetchQuote(String code) {
        String url = HttpUtil.buildQuery(QUOTE_URL, Map.of(
                "fltt", "2", "invt", "2",
                "fields", "f57,f58,f43,f84,f85,f116",
                "secid", secid(code)));
        String body = HttpUtil.get(url);
        try {
            JsonNode data = MAPPER.readTree(body).path("data");
            if (data.isMissingNode()) {
                throw new RuntimeException("未获取到个股信息，请检查代码是否正确（" + code + "）");
            }
            return new Quote(
                    data.path("f58").asText(),
                    data.path("f43").asDouble(),
                    data.path("f84").asDouble(),
                    data.path("f85").asDouble(),
                    data.path("f116").asDouble());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析东财快照失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拉取周 K 线收盘价序列（升序）。
     *
     * @param secid    证券标识（如 1.600519 / 1.000300）
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 周收盘价列表
     */
    public static List<Double> fetchWeeklyCloses(String secid, String startDate, String endDate) {
        String url = HttpUtil.buildQuery(KLINE_URL, Map.of(
                "fields1", "f1,f2,f3,f4,f5,f6",
                "fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f116",
                "ut", "7eea3edcaed734bea9cbfc24409ed989",
                "klt", "102",   // 102 = 周线
                "fqt", "1",     // 前复权
                "secid", secid,
                "beg", startDate,
                "end", endDate));
        String body = HttpUtil.get(url);
        try {
            JsonNode data = MAPPER.readTree(body).path("data");
            List<Double> closes = new ArrayList<>();
            for (JsonNode k : data.path("klines")) {
                // 每行格式：日期,开,收,高,低,成交量,...
                closes.add(Double.parseDouble(k.asText().split(",")[2]));
            }
            return closes;
        } catch (Exception e) {
            throw new RuntimeException("解析东财K线失败: " + e.getMessage(), e);
        }
    }

    /** 默认 3 年区间起始日期（用于 Beta 计算）。 */
    public static String defaultStartDate() {
        return LocalDate.now().minusYears(3).minusMonths(1).toString().replace("-", "");
    }

    /** 默认结束日期 = 今天。 */
    public static String today() {
        return LocalDate.now().toString().replace("-", "");
    }

    /** 东财个股快照记录：名称、最新价、总股本、流通股本、总市值。 */
    public record Quote(String name, double price, double totalShares,
                        double floatShares, double marketCap) {
    }
}