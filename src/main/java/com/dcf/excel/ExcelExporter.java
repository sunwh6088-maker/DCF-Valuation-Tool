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
 * <p>导出 9 个 sheet：
 * <ol>
 *   <li>说明：模型口径与免责声明</li>
 *   <li>原始数据：历史财务（含进阶科目）+ 快照</li>
 *   <li>假设：估值参数（模型/口径/终值方法）</li>
 *   <li>预测：显式期增长率与 FCF</li>
 *   <li>估值：EV 拆分与每股结论</li>
 *   <li>三情景：保守 / 中性 / 乐观</li>
 *   <li>历史回溯：模型估值 vs 年末股价</li>
 *   <li>F-Score：Piotroski 财务质量打分</li>
 *   <li>敏感性：折现率 × 永续增长率（或退出 PE）矩阵</li>
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
            row(s1, 4, "模型口径：" + modelLabel(ctx));
            row(s1, 5, "现金流口径：" + ("fcff".equals(ctx.getFcfModeUsed()) ? "FCFF（EBIT 起步）" : "简化 FCF（经营现金流-资本开支）"));
            row(s1, 6, "终值方法：" + ("pe".equals(ctx.getTerminalMode()) ? "PE 退出法（退出PE × 期末净利润）" : "Gordon 增长 TV = FCF_n × (1+g) / (r-g)"));
            row(s1, 7, "股权价值 = 企业价值 - 净债务 - 少数股东权益；每股价值 = 股权价值 / 总股本");
            row(s1, 9, "免责声明：本报告由模型自动生成，仅供学习研究参考，不构成投资建议。");
            s1.setColumnWidth(0, 9000);

            // ---------- 2. 原始数据 ----------
            Sheet s2 = wb.createSheet("原始数据");
            String[] headers = {"年份", "经营现金流", "资本开支", "自由现金流", "营收", "EBIT", "税前利润", "所得税",
                    "净利润", "折旧摊销", "资产总计", "负债合计", "流动资产", "流动负债", "毛利", "股本"};
            headerRow(s2, 0, headers, bold);
            HistoricalData h = ctx.getCompany().history();
            for (int i = 0; i < h.size(); i++) {
                row(s2, i + 1, h.years()[i], h.ocf()[i], h.capex()[i],
                        h.ocf()[i] - h.capex()[i], h.revenue()[i], h.ebit()[i],
                        h.pretaxIncome()[i], h.taxExpense()[i],
                        h.extraAt(i, HistoricalData.EXTRA_NET_INCOME),
                        h.extraAt(i, HistoricalData.EXTRA_DEPRECIATION),
                        h.extraAt(i, HistoricalData.EXTRA_TOTAL_ASSETS),
                        h.extraAt(i, HistoricalData.EXTRA_TOTAL_LIABILITIES),
                        h.extraAt(i, HistoricalData.EXTRA_CURRENT_ASSETS),
                        h.extraAt(i, HistoricalData.EXTRA_CURRENT_LIABILITIES),
                        h.extraAt(i, HistoricalData.EXTRA_GROSS_PROFIT),
                        h.extraAt(i, HistoricalData.EXTRA_SHARES_CAPITAL));
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
            row(s3, 15, "估值模型", modelLabel(ctx), "");
            if ("threeStage".equals(ctx.getModelType())) {
                row(s3, 16, "成长期增长率 g2", pct(ctx.getGSecond()), "三阶段：高增长之后的中速期");
                row(s3, 17, "成长期年数", ctx.getNSecond(), "");
            }
            row(s3, 18, "现金流口径", "fcff".equals(ctx.getFcfModeUsed()) ? "FCFF（EBIT 起步）" : "简化 FCF", "");
            row(s3, 19, "终值方法", "pe".equals(ctx.getTerminalMode()) ? "PE 退出法" : "Gordon 永续增长", "");
            if ("pe".equals(ctx.getTerminalMode())) {
                row(s3, 20, "退出市盈率", ctx.getExitPe(), "");
                row(s3, 21, "期末净利润预测", ctx.getTerminalNetIncome(), "净利润率来源：" + ctx.getNetMarginSource());
            }
            if (!ctx.getFcffWarning().isEmpty()) {
                row(s3, 22, "口径提示", ctx.getFcffWarning(), "");
            }
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

            row(s5, 5, "判断分级", ctx.getVerdict());
            row(s5, 6, "回本年限（年）", ctx.getPaybackYears());
            row(s5, 7, "隐含年化回报", pct(ctx.getImpliedReturn()));

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

            // ---------- 7. 历史回溯 ----------
            Sheet s6b = wb.createSheet("历史回溯");
            String[] btHeaders = {"年份", "模型估值", "年末股价", "折溢价"};
            headerRow(s6b, 0, btHeaders, bold);
            int btRow = 1;
            for (com.dcf.service.HistoricalBacktest bt : ctx.getBacktestResults()) {
                row(s6b, btRow++, bt.year(), bt.perShareValue(), bt.price(), pct(bt.premium()));
            }
            autoWidth(s6b, 4);

            // ---------- 7.5 F-Score ----------
            Sheet s6c = wb.createSheet("F-Score");
            String[] fsHeaders = {"年份", "总分", "ROA>0", "现金流>0", "ROA改善", "现金流>净利润",
                    "杠杆下降", "流动比率上升", "未增发新股", "毛利率上升", "周转率上升"};
            headerRow(s6c, 0, fsHeaders, bold);
            int fsRow = 1;
            for (com.dcf.model.FScoreCalculator.FScoreResult fs : ctx.getFScores()) {
                String[] cells = new String[fs.items().length];
                for (int i = 0; i < cells.length; i++) {
                    Boolean b = fs.items()[i];
                    cells[i] = b == null ? "—" : (b ? "✓" : "✗");
                }
                row(s6c, fsRow++, fs.year(), fs.score() + "/" + fs.itemsAvailable(), (Object[]) cells);
            }
            autoWidth(s6c, fsHeaders.length);

            // ---------- 8. 敏感性 ----------
            Sheet s6 = wb.createSheet("敏感性");
            SensitivityResult sen = ctx.getSensitivity();
            Row hr = s6.createRow(0);
            hr.createCell(0).setCellValue("折现率 \\ " + sen.xLabel());
            hr.getCell(0).setCellStyle(bold);
            boolean isPeAxis = "退出PE".equals(sen.xLabel());
            for (int c = 0; c < sen.cols(); c++) {
                Cell cell = hr.createCell(c + 1);
                if (isPeAxis) {
                    cell.setCellValue(sen.growthRates()[c]);
                } else {
                    cell.setCellValue(pct(sen.growthRates()[c]));
                }
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
                        cell.setCellValue("N/A");
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

    private static String modelLabel(ValuationContext ctx) {
        return switch (ctx.getModelType()) {
            case "zeroGrowth" -> "零增长（g=0）";
            case "threeStage" -> "三阶段（高增长→成长期→永续）";
            default -> "两阶段（高增长→过渡→永续）";
        };
    }

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