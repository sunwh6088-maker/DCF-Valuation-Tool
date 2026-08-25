package com.dcf.web;

import com.dcf.data.DataService;
import com.dcf.data.RateFetcher;
import com.dcf.data.csv.CsvImporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

/**
 * JSON API 控制器：Beta 自动计算、CSV 模板下载、报告下载。
 */
@RestController
public class ApiController {

    private final DataService dataService = new DataService(Path.of("data/cache"));
    private final RateFetcher rateFetcher = new RateFetcher();

    /**
     * 自动计算 Beta（个股周线 vs 沪深300，近 3 年）。
     * 参数页加载时由前端 JS 调用并填充输入框。
     */
    @GetMapping("/api/beta")
    public Map<String, Object> beta(@RequestParam String code) {
        try {
            double b = dataService.fetchBeta(code);
            return Map.of("beta", Double.isNaN(b) ? null : b);
        } catch (Exception e) {
            return Map.of("beta", null, "error", e.getMessage());
        }
    }

    /**
     * 自动获取无风险利率（10 年期国债收益率）。
     * US：FRED DGS10；CN：免费接口不可用返回 null（由页面提示手动输入）。
     */
    @GetMapping("/api/rf")
    public Map<String, Object> rf(@RequestParam String market) {
        double v = rateFetcher.fetch(market);
        return Map.of(
                "rate", Double.isNaN(v) ? null : v,
                "source", RateFetcher.sourceLabel(market));
    }

    /**
     * 下载标准 CSV 模板（UTF-8 带 BOM，Excel 打开不乱码）。
     */
    @GetMapping("/api/template")
    public void template(HttpServletResponse response) throws Exception {
        byte[] content = CsvImporter.generateTemplate().getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=dcf_history_template.csv");
        // UTF-8 BOM，兼容 Excel 直接打开
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    /**
     * 下载 Excel 估值报告（6 sheet）。
     */
    @GetMapping("/download/excel")
    public void excel(jakarta.servlet.http.HttpServletRequest request,
                      jakarta.servlet.http.HttpSession session,
                      HttpServletResponse response) throws Exception {
        ValuationContext ctx = (ValuationContext) session.getAttribute("valuationContext");
        if (ctx == null || ctx.getResult() == null) {
            response.sendError(400, "请先完成估值");
            return;
        }
        byte[] bytes = com.dcf.excel.ExcelExporter.export(ctx);
        String name = "DCF估值_" + safe(ctx.getCompany().snapshot().name()) + "_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    /**
     * 下载 Markdown 估值报告。
     */
    @GetMapping("/download/report")
    public void report(jakarta.servlet.http.HttpSession session,
                       HttpServletResponse response) throws Exception {
        ValuationContext ctx = (ValuationContext) session.getAttribute("valuationContext");
        if (ctx == null || ctx.getResult() == null) {
            response.sendError(400, "请先完成估值");
            return;
        }
        byte[] bytes = com.dcf.service.ReportService.generate(ctx).getBytes(StandardCharsets.UTF_8);
        String name = "DCF估值报告_" + safe(ctx.getCompany().snapshot().name()) + "_" + LocalDate.now() + ".md";
        response.setContentType("text/markdown; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    /** 文件名安全化（去特殊字符）。 */
    private String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}