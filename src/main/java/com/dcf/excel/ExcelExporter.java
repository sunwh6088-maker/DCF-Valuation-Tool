package com.dcf.excel;

import com.dcf.data.model.HistoricalData;
import com.dcf.model.SensitivityResult;
import com.dcf.model.ValuationResult;
import com.dcf.web.ValuationContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

/**
 * Excel 报告导出器（Apache POI）。
 *
 * <p>导出 6 个 sheet：
 * <ol>
 *   <li>说明：模型口径与免责声明</li>
 *   <li>原始数据：历史财务 + 快照</li>
 *   <li>假设：估值参数</li>
 *   <li>预测：显式期增长率与 FCF</li>
 *   <li>估值：EV 拆分与每股结论</li>
 *   <li>敏感性：折现率 × 永续增长率矩阵</li>
 * </ol>
 */
public class ExcelExporter {

    /** 导出为 xlsx 字节流。 */
    public static byte[] export(ValuationContext ctx) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);

            // ---------- 1. 说明 ----------
            Sheet s1 = wb.createSheet("说明");
            row(s1, 0, "DCF 估值工具 - 估值报告", bold);
            row(s1, 1, "公司：" + ctx.getCompany().snapshot().name()
                    + "（" + ctx.getCompany().code() + "）");
            row(s1, 2, "数据来源：" + ctx.getCompany().source() + " | 生成日期：" + LocalDate.now());
            row(s1, 4, "模型口径：两阶段 DCF");
            row(s1, 5, "显式预测期 10 年（前 5 年固定增长率 + 后 5 年线性过渡到永续增长率）");
            row(s1, 6, "终值：Gordon 增长模型 TV = FCF_n × (1+g) / (r-g)");
            row(s1, 7, "股权价值 = 企业价值 - 净债务 - 少数股东权益；每股价值 = 股权价值 / 总股本");
            row(s1, 9, "免责声明：本报告由模型自动生成，仅供学习研究参考，不构成投资建议。");
            s1.setColumnWidth(0, 9000);

            // ---------- 2. 原始数据 ----------
            Sheet s2 = wb.createSheet("原始数据");
            String[] headers = {"年份", "经营现金流", "资本开支", "自由现金流", "营收", "EBIT", "税前利润", "所得税"};
            headerRow(s2, 0, headers, bold);
            HistoricalData h = ctx.getCompany().history();
            for (int i = 0; i < h.size(); i++) {
                row(s2, i + 1, h.years()[i], h.ocf()[i], h.capex()[i],
                        h.ocf()[i] - h.capex()[i], h.revenue()[i], h.ebit()[i],
                        h.pretaxIncome()[i], h.taxExpense()[i]);
            }
            row(s2, h.size() + 2, "快照", bold);
            row(s2, h.size() + 3, "当前股价", ctx.getCompany().snapshot().price());
            row(s2, h.size() + 4, "总股本", ctx.getCompany().snapshot().sharesOutstanding());
            row(s2, h.size() + 5, "货币资金", ctx.getCompany().snapshot().cash());
            row(s2, h.size() + 6, "有息负债", ctx.getCompany().snapshot().interestDebt());
            row(s2, h.size() + 7, "少数股东权益", ctx.getCompany().snapshot().minorityInterest());
            autoWidth(s2, headers.length);

            // ---------- 3. 假设 ----------
            Sheet s3 = wb.createSheet("假设");
            String[] aHeaders = {"参数", "数值", "说明"};
            headerRow(s3, 0, aHeaders, bold);
            double ke = ctx.getRf() + ctx.getBetaInput() * ctx.getErp();
            row(s3, 1, "无风险利率 Rf", pct(ctx.getRf()), "10 年期国债收益率");
            row(s3, 2, "Beta", ctx.getBetaInput(), "CAPM 系数");
            row(s3, 3, "市场风险溢价 ERP", pct(ctx.getErp()), "Damodaran 口径");
            row(s3, 4, "信用利差", pct(ctx.getCreditSpread()), "债务成本 = Rf + 利差");
            row(s3, 5, "股权成本 ke（CAPM）", pct(ctx.getKeValue()), "Rf + Beta × ERP");
            row(s3, 6, "债务成本 kd", pct(ctx.getKdValue()), "Rf + 信用利差");
            row(s3, 7, "债务权重 D/(D+E)", pct(ctx.getDebtWeightValue()), "有息负债 /（有息负债 + 市值）");
            row(s3, 8, "折现率（WACC）", pct(ctx.getWaccValue()),
                    "WACC = kd×(1-t)×wD + ke×wE");
            row(s3, 9, "折现率来源", ctx.getDiscountMode(),
                    "wacc=加权 / capm=纯CAPM / manual=手动");
            row(s3, 10, "有效税率", pct(ctx.getTaxRate()), "");
            row(s3, 11, "高增长期增长率", pct(ctx.getGFirst()), "前 " + ctx.getNFirst() + " 年");
            row(s3, 12, "高增长年数", ctx.getNFirst(), "");
            row(s3, 13, "过渡年数", ctx.getNTransition(), "线性过渡到永续增长率");
            row(s3, 14, "永续增长率 g", pct(ctx.getGTerminal()), "常取 2%-3%");
            autoWidth(s3, 3);

            // ---------- 4. 预测 ----------
            Sheet s4 = wb.createSheet("预测");
            String[] fHeaders = {"预测年", "增长率", "自由现金流"};
            headerRow(s4, 0, fHeaders, bold);
            ValuationResult res = ctx.getResult();
            for (int i = 0; i < res.fcfForecast().length; i++) {
                row(s4, i + 1, "第" + (i + 1) + "年", pct(res.growthPath()[i]), res.fcfForecast()[i]);
            }
            autoWidth(s4, 3);

            // ---------- 5. 估值 ----------
            Sheet s5 = wb.createSheet("估值");
            String[] vHeaders = {"项目", "金额/数值"};
            headerRow(s5, 0, vHeaders, bold);
            row(s5, 1, "显式期现值", res.pvFcf());
            row(s5, 2, "终值现值", res.pvTerminal());
            row(s5, 3, "终值占比", pct(res.terminalRatio()));
            row(s5, 4, "企业价值 EV", res.enterpriseValue());
            row(s5, 5, "净债务", ctx.getCompany().snapshot().netDebt());
            row(s5, 6, "少数股东权益", ctx.getCompany().snapshot().minorityInterest());
            row(s5, 7, "股权价值", res.equityValue());
            row(s5, 8, "总股本", ctx.getCompany().snapshot().sharesOutstanding());
            row(s5, 9, "每股内在价值", res.perShareValue());
            row(s5, 10, "当前股价", ctx.getCompany().snapshot().price());
            row(s5, 11, "安全边际",
                    (res.perShareValue() - ctx.getCompany().snapshot().price())
                            / ctx.getCompany().snapshot().price());
            autoWidth(s5, 2);

            // ---------- 6. 三情景 ----------
            Sheet s6a = wb.createSheet("三情景");
            String[] scHeaders = {"情景", "折现率", "每股内在价值", "股权价值"};
            headerRow(s6a, 0, scHeaders, bold);
            int scRow = 1;
            for (com.dcf.model.ScenarioResult sc : ctx.getScenarioResults()) {
                row(s6a, scRow++, sc.scenario().label() + "情景", pct(sc.discountRate()),
                        sc.perShareValue(), sc.equityValue());
            }
            autoWidth(s6a, 4);

            // ---------- 7. 敏感性 ----------
            Sheet s6 = wb.createSheet("敏感性");
            SensitivityResult sen = ctx.getSensitivity();
            Row hr = s6.createRow(0);
            hr.createCell(0).setCellValue("折现率 \\ 永续增长率");
            hr.getCell(0).setCellStyle(bold);
            for (int c = 0; c < sen.cols(); c++) {
                Cell cell = hr.createCell(c + 1);
                cell.setCellValue(pct(sen.growthRates()[c]));
                cell.setCellStyle(bold);
            }
            for (int r = 0; r < sen.rows(); r++) {
                Row row = s6.createRow(r + 1);
                Cell label = row.createCell(0);
                label.setCellValue(pct(sen.discountRates()[r]));
                label.setCellStyle(bold);
                for (int c = 0; c < sen.cols(); c++) {
                    double v = sen.values()[r][c];
                                        Cell cell = row.createCell(c + 1);
                    if (Double.isNaN(v)) {
                        cell.setCellValue("g≥r");
                    } else {
                        cell.setCellValue(Math.round(v * 100) / 100.0);
                    }
                }
            }
            s6.setColumnWidth(0, 7000);
            for (int c = 0; c < sen.cols(); c++) {
                s6.setColumnWidth(c + 1, 3500);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---------- 工具 ----------

    private static void headerRow(Sheet s, int r, String[] headers, CellStyle bold) {
        Row row = s.createRow(r);
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(bold);
        }
    }

    private static void row(Sheet s, int r, Object... values) {
        Row row = s.createRow(r);
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            Cell c = row.createCell(i);
            if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
            } else if (v != null) {
                c.setCellValue(v.toString());
            }
        }
    }

    private static void autoWidth(Sheet s, int cols) {
        for (int i = 0; i < cols; i++) {
            s.setColumnWidth(i, 4500);
        }
    }

    private static String pct(double v) {
        if (Double.isNaN(v)) {
            return "N/A";
        }
        return String.format("%.2f%%", v * 100);
    }
}