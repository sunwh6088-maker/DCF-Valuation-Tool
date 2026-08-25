package com.dcf.data;

import com.dcf.data.cache.DataCache;
import com.dcf.data.eastmoney.EastMoneyClient;
import com.dcf.data.model.CompanyData;
import com.dcf.data.model.HistoricalData;
import com.dcf.data.model.SnapshotData;
import com.dcf.data.sina.SinaFinanceClient;
import com.dcf.data.sina.SinaFinanceParser;
import com.dcf.service.BetaCalculator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 数据服务：A 股数据获取编排（自动抓取 + 缓存）。
 *
 * <p>流程：
 * <ol>
 *   <li>校验股票代码（6 位数字）</li>
 *   <li>命中缓存（24h 内）直接返回</li>
 *   <li>新浪财经拉取资产负债表 / 利润表 / 现金流量表</li>
 *   <li>按年报提取最近 N 年历史财务（科目关键词匹配）</li>
 *   <li>东方财富拉取快照（名称 / 股价 / 总股本）</li>
 *   <li>组装 CompanyData 并写入缓存</li>
 * </ol>
 *
 * <p>Beta 不包含在 CompanyData 中（属于估值参数而非公司快照），
 * 通过 {@link #fetchBeta(String)} 单独获取。
 */
public class DataService {

    private final SinaFinanceClient sinaClient = new SinaFinanceClient();
    private final DataCache cache;

    public DataService(Path cacheDir) {
        this.cache = new DataCache(cacheDir);
    }

    /**
     * 自动抓取 A 股公司数据。
     *
     * @param code   6 位股票代码（支持 sh/sz 前缀与全角字符，内部归一化）
     * @param years  历史年数（默认 10）
     * @return 公司数据
     * @throws IllegalArgumentException 代码格式错误
     */
    public CompanyData fetchAShare(String code, int years) {
        String normalized = normalizeCode(code);
        CompanyData cached = cache.load(normalized);
        if (cached != null) {
            return cached;
        }
        String paperCode = (normalized.startsWith("6") ? "sh" : "sz") + normalized;
        String market = normalized.startsWith("6") ? "1." : "0.";

        Map<String, Map<String, Double>> balance = sinaClient.fetch(paperCode, SinaFinanceClient.SOURCE_BALANCE);
        Map<String, Map<String, Double>> profit = sinaClient.fetch(paperCode, SinaFinanceClient.SOURCE_PROFIT);
        Map<String, Map<String, Double>> cashFlow = sinaClient.fetch(paperCode, SinaFinanceClient.SOURCE_CASH_FLOW);

        HistoricalData history = SinaFinanceParser.extract(balance, profit, cashFlow, years);
        if (history.size() == 0) {
            throw new RuntimeException("未获取到任何年报数据，请稍后重试或改用 CSV/手动输入");
        }

        EastMoneyClient.Quote quote = EastMoneyClient.fetchQuote(normalized);
        SnapshotData snapshot = new SnapshotData(
                quote.name(),
                normalized,
                quote.price(),
                quote.totalShares(),
                pick(balance, "货币资金"),
                interestDebt(balance),
                pick(balance, "少数股东权益"),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        CompanyData data = new CompanyData("CN", normalized, history, snapshot,
                "auto", LocalDateTime.now().toString());
        cache.save(data);
        return data;
    }

    /**
     * 计算个股 Beta（个股周线 vs 沪深300 周线，近 3 年）。
     *
     * @param code 6 位股票代码
     * @return beta；数据不足返回 NaN
     */
    public double fetchBeta(String code) {
        String normalized = normalizeCode(code);
        String start = EastMoneyClient.defaultStartDate();
        String end = EastMoneyClient.today();
        List<Double> stock = EastMoneyClient.fetchWeeklyCloses(EastMoneyClient.secid(normalized), start, end);
        List<Double> index = EastMoneyClient.fetchWeeklyCloses(EastMoneyClient.CSI300_SECID, start, end);
        return BetaCalculator.calculate(stock, index);
    }

    /**
     * 拉取逐年年末收盘价（历史 DCF 回溯用）。
     *
     * @param code      6 位股票代码
     * @param startYear 起始年份
     * @param endYear   结束年份
     * @return 年份 → 年末收盘价；失败抛异常（由调用方容错）
     */
    public Map<Integer, Double> fetchYearEndPrices(String code, int startYear, int endYear) {
        String normalized = normalizeCode(code);
        return EastMoneyClient.fetchYearEndPrices(EastMoneyClient.secid(normalized),
                startYear + "0101", endYear + "1231");
    }

    /** 快照科目取值（最新报告期）。 */
    private double pick(Map<String, Map<String, Double>> balance, String keyword) {
        // 资产负债表按报告期倒序取最新
        List<String> dates = balance.keySet().stream().sorted(java.util.Comparator.reverseOrder()).toList();
        for (String d : dates) {
            Map<String, Double> items = balance.get(d);
            if (items == null) {
                continue;
            }
            String kw = SinaFinanceParser.normalize(keyword);
            for (Map.Entry<String, Double> e : items.entrySet()) {
                if (SinaFinanceParser.normalize(e.getKey()).contains(kw)) {
                    return e.getValue();
                }
            }
        }
        return Double.NaN;
    }

    /** 有息负债 = 短借 + 长借 + 应付债券 + 一年内到期非流动负债 + 租赁负债。 */
    private double interestDebt(Map<String, Map<String, Double>> balance) {
        double d = 0;
        d += nanToZero(pick(balance, "短期借款"));
        d += nanToZero(pick(balance, "长期借款"));
        d += nanToZero(pick(balance, "应付债券"));
        d += nanToZero(pick(balance, "一年内到期的非流动负债"));
        d += nanToZero(pick(balance, "租赁负债"));
        return d;
    }

    private static double nanToZero(double v) {
        return Double.isNaN(v) ? 0.0 : v;
    }

    /** 代码归一化：去前缀/后缀/全角转半角，校验 6 位数字。 */
    public static String normalizeCode(String code) {
        String c = java.text.Normalizer.normalize(code == null ? "" : code, java.text.Normalizer.Form.NFKC)
                .toUpperCase()
                .replace("SH", "").replace("SZ", "")
                .replace(".", "").replace(" ", "");
        if (!c.matches("\\d{6}")) {
            throw new IllegalArgumentException("A股代码必须是 6 位数字（如 600519），当前输入：" + code);
        }
        return c;
    }

    /** 缓存目录便捷访问（供清理等操作）。 */
    public DataCache cache() {
        return cache;
    }
}