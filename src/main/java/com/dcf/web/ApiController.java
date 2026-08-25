package com.dcf.web;

import com.dcf.data.DataService;
import com.dcf.data.csv.CsvImporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * JSON API 控制器：Beta 自动计算、CSV 模板下载、报告下载。
 */
@RestController
public class ApiController {

    private final DataService dataService = new DataService(Path.of("data/cache"));

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
}