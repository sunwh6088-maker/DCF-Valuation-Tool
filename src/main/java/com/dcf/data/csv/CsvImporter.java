package com.dcf.data.csv;

import com.dcf.data.model.HistoricalData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史财务 CSV 导入器。
 *
 * <p>支持两类格式：
 * <ol>
 *   <li><b>标准模板</b>（推荐）：{@link #generateTemplate()} 生成的模板，列名固定</li>
 *   <li><b>理杏仁导出</b>：列名为中文报表科目（如"经营活动产生的现金流量净额"），
 *       通过关键词模糊匹配自动识别，兼容列名细微差异</li>
 * </ol>
 *
 * <p>CSV 编码自动探测：优先 UTF-8（兼容 BOM），失败回退 GBK。
 */
public final class CsvImporter {

    /** 模板列名（第一行表头）。 */
    public static final String[] TEMPLATE_HEADERS = {
            "年份", "经营现金流", "资本开支", "营收", "EBIT", "税前利润", "所得税费用"
    };

    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    private CsvImporter() {
    }

    /**
     * 生成标准 CSV 模板内容（含说明注释行，以 # 开头）。
     */
    public static String generateTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("# DCF 历史财务数据模板（每年一行，金额单位：元；EBIT 可填营业利润）\n");
        sb.append("# 至少 3 年，建议 5-10 年；留空或填 - 表示缺失\n");
        sb.append(String.join(",", TEMPLATE_HEADERS)).append('\n');
        sb.append("2024,10000000000,5000000000,150000000000,90000000000,80000000000,20000000000\n");
        sb.append("2023,9000000000,4500000000,140000000000,85000000000,75000000000,19000000000\n");
        sb.append("2022,8500000000,4000000000,130000000000,80000000000,70000000000,18000000000\n");
        return sb.toString();
    }

    /**
     * 解析 CSV 文件为历史财务数据。
     *
     * @param file CSV 文件路径
     * @return 历史财务序列（缺失科目为 NaN）
     * @throws IllegalArgumentException 文件格式或必填列缺失
     */
    public static HistoricalData parse(Path file) {
        String text = readWithEncodingDetect(file);
        return parseText(text);
    }

    /** 解析 CSV 文本（供测试与文件共用）。 */
    public static HistoricalData parseText(String text) {
        List<String[]> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue; // 跳过空行与注释行
            }
            rows.add(splitCsvLine(trimmed));
        }
        if (rows.size() < 2) {
            throw new IllegalArgumentException("CSV 至少需要表头和 1 行数据");
        }

        // 识别列：表头关键词 → 字段索引
        Map<String, Integer> colIndex = mapColumns(rows.get(0));
        int idxYear = require(colIndex, "年份");
        int idxOcf = require(colIndex, "经营现金流");
        int idxCapex = require(colIndex, "资本开支");

        int n = rows.size() - 1;
        int[] years = new int[n];
        double[] ocf = new double[n];
        double[] capex = new double[n];
        double[] revenue = fill(colIndex.get("营收"), n);
        double[] ebit = fill(colIndex.get("EBIT"), n);
        double[] pretax = fill(colIndex.get("税前利润"), n);
        double[] tax = fill(colIndex.get("所得税费用"), n);

        for (int i = 0; i < n; i++) {
            String[] row = rows.get(i + 1);
            years[i] = parseYear(row[idxYear]);
            ocf[i] = parseNumber(row[idxOcf], "经营现金流");
            capex[i] = parseNumber(row[idxCapex], "资本开支");
            revenue[i] = optNumber(row, colIndex.get("营收"));
            ebit[i] = optNumber(row, colIndex.get("EBIT"));
            pretax[i] = optNumber(row, colIndex.get("税前利润"));
            tax[i] = optNumber(row, colIndex.get("所得税费用"));
        }
        sortByYear(years, ocf, capex, revenue, ebit, pretax, tax);
        return new HistoricalData(years, ocf, capex, revenue, ebit, pretax, tax);
    }

    /** 按年份升序重排所有财务数组（兼容理杏仁等倒序导出）。 */
    private static void sortByYear(int[] years, double[]... arrays) {
        Integer[] order = new Integer[years.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> years[i]));
        int[] sortedYears = new int[years.length];
        double[][] sortedArrays = new double[arrays.length][years.length];
        for (int i = 0; i < order.length; i++) {
            int from = order[i];
            sortedYears[i] = years[from];
            for (int a = 0; a < arrays.length; a++) {
                sortedArrays[a][i] = arrays[a][from];
            }
        }
        System.arraycopy(sortedYears, 0, years, 0, years.length);
        for (int a = 0; a < arrays.length; a++) {
            System.arraycopy(sortedArrays[a], 0, arrays[a], 0, years.length);
        }
    }

    /** 简单 CSV 行切分（支持引号包裹的字段）。 */
    static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                fields.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString().trim());
        return fields.toArray(new String[0]);
    }

    /** 表头映射：归一化表头后按关键词识别字段。 */
    private static Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String h = normalize(header[i]);
            if (h.contains("年份") || h.contains("报告期") || h.contains("日期")) {
                map.putIfAbsent("年份", i);
            } else if (h.contains("经营") && h.contains("现金流")) {
                map.putIfAbsent("经营现金流", i);
            } else if (h.contains("购建固定资产") || h.contains("资本开支") || h.contains("capex")) {
                map.putIfAbsent("资本开支", i);
            } else if (h.contains("营业总收入") || h.contains("营业收入") || h.contains("营收")) {
                map.putIfAbsent("营收", i);
            } else if (h.contains("营业利润") || h.equals("ebit")) {
                map.putIfAbsent("EBIT", i);
            } else if (h.contains("利润总额") || h.contains("税前")) {
                map.putIfAbsent("税前利润", i);
            } else if (h.contains("所得税")) {
                map.putIfAbsent("所得税费用", i);
            }
        }
        return map;
    }

    private static int require(Map<String, Integer> map, String key) {
        Integer i = map.get(key);
        if (i == null) {
            throw new IllegalArgumentException(
                    "CSV 缺少必需列：" + key + "。请使用模板或检查列名（理杏仁导出需含中文科目名）");
        }
        return i;
    }

    private static double[] fill(Integer idx, int n) {
        double[] arr = new double[n];
        java.util.Arrays.fill(arr, Double.NaN);
        return idx == null ? arr : arr;
    }

    private static int parseYear(String raw) {
        Matcher m = YEAR_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new IllegalArgumentException("无法解析年份：" + raw);
        }
        return Integer.parseInt(m.group(1));
    }

    private static double parseNumber(String raw, String name) {
        if (raw.isEmpty() || "-".equals(raw)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(normalize(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 列存在无法解析的数值：" + raw);
        }
    }

    private static double optNumber(String[] row, Integer idx) {
        return idx == null || idx >= row.length ? Double.NaN : parseNumber(row[idx], "可选列");
    }

    /** 归一化：全角转半角（NFKC 等价）、去空白、去逗号。 */
    static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
        return n.replaceAll("[,\\s，]", "").toLowerCase(Locale.ROOT);
    }

    /** 读取文件：UTF-8（去 BOM）优先，失败回退 GBK。 */
    private static String readWithEncodingDetect(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String utf8 = new String(bytes, StandardCharsets.UTF_8);
            if (utf8.startsWith("\uFEFF")) {
                utf8 = utf8.substring(1);
            }
            // 若包含常见中文且不是乱码特征（\uFFFD），视为 UTF-8
            if (utf8.indexOf('\uFFFD') < 0) {
                return utf8;
            }
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        } catch (IOException e) {
            throw new IllegalArgumentException("读取 CSV 文件失败: " + e.getMessage(), e);
        }
    }
}