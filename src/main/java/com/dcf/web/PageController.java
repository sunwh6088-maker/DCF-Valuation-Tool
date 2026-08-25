package com.dcf.web;

import com.dcf.data.DataService;
import com.dcf.data.csv.CsvImporter;
import com.dcf.data.model.CompanyData;
import com.dcf.data.model.HistoricalData;
import com.dcf.data.model.SnapshotData;
import com.dcf.service.ValuationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面路由与表单处理控制器。
 *
 * <p>流程：首页选市场 → 输入数据（A股三路/美股手动）→ 参数页 → 结果页。
 * 中间状态保存在 HttpSession（{@link ValuationContext}）。
 */
@Controller
public class PageController {

    private static final String CTX = "valuationContext";
    private static final Path CACHE_DIR = Path.of("data/cache");

    private final DataService dataService = new DataService(CACHE_DIR);
    private final ValuationService valuationService = new ValuationService();

    private ValuationContext ctx(HttpSession session) {
        ValuationContext c = (ValuationContext) session.getAttribute(CTX);
        if (c == null) {
            c = new ValuationContext();
            session.setAttribute(CTX, c);
        }
        return c;
    }

    // ---------- 首页 ----------

    @GetMapping("/")
    public String index() {
        return "index";
    }

    // ---------- 输入页 ----------

    @GetMapping("/input/a")
    public String inputA(Model model, HttpSession session) {
        model.addAttribute("market", "CN");
        return "input-a";
    }

    @GetMapping("/input/us")
    public String inputUs(Model model, HttpSession session) {
        model.addAttribute("market", "US");
        return "input-us";
    }

    /** A股：自动抓取。 */
    @PostMapping("/input/a/auto")
    public String autoA(@RequestParam String code,
                        @RequestParam(defaultValue = "10") int years,
                        HttpSession session, Model model) {
        try {
            CompanyData data = dataService.fetchAShare(code, years);
            ValuationContext c = ctx(session);
            c.setCompany(data);
            c.setErrorMessage(null);
            return "redirect:/params";
        } catch (Exception e) {
            model.addAttribute("error", friendly(e));
            return "input-a";
        }
    }

    /** A股：CSV 导入 + 快照补充。 */
    @PostMapping("/input/a/csv")
    public String csvA(@RequestParam MultipartFile file,
                       @RequestParam String name, @RequestParam String code,
                       @RequestParam double price, @RequestParam double shares,
                       @RequestParam(defaultValue = "0") double cash,
                       @RequestParam(defaultValue = "0") double interestDebt,
                       @RequestParam(defaultValue = "0") double minority,
                       HttpSession session, Model model) {
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("请选择 CSV 文件");
            }
            Path tmp = Files.createTempFile("dcf-import", ".csv");
            file.transferTo(tmp.toAbsolutePath());
            HistoricalData history = CsvImporter.parse(tmp);
            Files.deleteIfExists(tmp);
            SnapshotData snapshot = new SnapshotData(name, code, price, shares,
                    cash, interestDebt, minority, LocalDateTime.now().toLocalDate().toString());
            ValuationContext c = ctx(session);
            c.setCompany(new CompanyData("CN", code, history, snapshot, "csv", LocalDateTime.now().toString()));
            c.setErrorMessage(null);
            return "redirect:/params";
        } catch (Exception e) {
            model.addAttribute("error", friendly(e));
            return "input-a";
        }
    }

    /** A股/美股：手动输入。 */
    @PostMapping("/input/manual")
    public String manual(@RequestParam String market,
                         @RequestParam String name, @RequestParam String code,
                         @RequestParam double price, @RequestParam double shares,
                         @RequestParam(defaultValue = "0") double cash,
                         @RequestParam(defaultValue = "0") double interestDebt,
                         @RequestParam(defaultValue = "0") double minority,
                         @RequestParam String[] years,
                         @RequestParam String[] ocf,
                         @RequestParam String[] capex,
                         @RequestParam(required = false) String[] revenue,
                         @RequestParam(required = false) String[] ebit,
                         @RequestParam(required = false) String[] pretax,
                         @RequestParam(required = false) String[] tax,
                         HttpSession session, Model model) {
        try {
            HistoricalData history = buildHistory(years, ocf, capex, revenue, ebit, pretax, tax);
            SnapshotData snapshot = new SnapshotData(name, code, price, shares,
                    cash, interestDebt, minority, LocalDateTime.now().toLocalDate().toString());
            ValuationContext c = ctx(session);
            c.setCompany(new CompanyData(market, code, history, snapshot, "manual", LocalDateTime.now().toString()));
            c.setErrorMessage(null);
            return "redirect:/params";
        } catch (Exception e) {
            model.addAttribute("error", friendly(e));
            return "CN".equals(market) ? "input-a" : "input-us";
        }
    }

    // ---------- 参数页 ----------

    @GetMapping("/params")
    public String params(HttpSession session, Model model) {
        ValuationContext c = ctx(session);
        if (!c.hasCompany()) {
            return "redirect:/";
        }
        model.addAttribute("ctx", c);
        return "params";
    }

    @PostMapping("/params")
    public String saveParams(@RequestParam double rf,
                             @RequestParam double beta,
                             @RequestParam double erp,
                             @RequestParam(defaultValue = "true") boolean useCapm,
                             @RequestParam(defaultValue = "0.10") double manualRate,
                             @RequestParam(defaultValue = "0.25") double taxRate,
                             @RequestParam double gFirst,
                             @RequestParam(defaultValue = "0.025") double gTerminal,
                             @RequestParam(defaultValue = "5") int nFirst,
                             @RequestParam(defaultValue = "5") int nTransition,
                             HttpSession session, Model model) {
        ValuationContext c = ctx(session);
        try {
            // 范围校验（后端兜底，前端已预校验）
            check(c, rf, beta, erp, manualRate, taxRate, gFirst, gTerminal, nFirst, nTransition);
            c.setRf(rf);
            c.setBetaInput(beta);
            c.setErp(erp);
            c.setUseCapm(useCapm);
            c.setManualDiscountRate(manualRate);
            c.setTaxRate(taxRate);
            c.setGFirst(gFirst);
            c.setGTerminal(gTerminal);
            c.setNFirst(nFirst);
            c.setNTransition(nTransition);
            valuationService.compute(c);
            return "redirect:/result";
        } catch (Exception e) {
            c.setErrorMessage(friendly(e));
            model.addAttribute("ctx", c);
            return "params";
        }
    }

    // ---------- 结果页 ----------

    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        ValuationContext c = ctx(session);
        if (!c.hasCompany() || c.getResult() == null) {
            return "redirect:/";
        }
        model.addAttribute("ctx", c);
        return "result";
    }

    @GetMapping("/reset")
    public String reset(HttpSession session) {
        session.removeAttribute(CTX);
        return "redirect:/";
    }

    // ---------- 工具方法 ----------

    /** 手动输入组装历史数据（含年份连续性校验）。 */
    private HistoricalData buildHistory(String[] years, String[] ocf, String[] capex,
                                        String[] revenue, String[] ebit, String[] pretax,
                                        String[] tax) {
        int n = years.length;
        int[] ys = new int[n];
        double[] oc = new double[n], ca = new double[n];
        double[] re = fill(revenue, n), eb = fill(ebit, n);
        double[] pr = fill(pretax, n), tx = fill(tax, n);
        for (int i = 0; i < n; i++) {
            ys[i] = Integer.parseInt(years[i].trim());
            oc[i] = parse("经营现金流", ocf[i]);
            ca[i] = parse("资本开支", capex[i]);
        }
        // 允许任意顺序输入（如最新年份在前），内部按年份升序重排
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> ys[i]));
        int[] sortedY = new int[n];
        double[][] arrays = {oc, ca, re, eb, pr, tx};
        double[][] sortedA = new double[arrays.length][n];
        for (int i = 0; i < n; i++) {
            int from = order[i];
            sortedY[i] = ys[from];
            for (int a = 0; a < arrays.length; a++) {
                sortedA[a][i] = arrays[a][from];
            }
        }
        System.arraycopy(sortedY, 0, ys, 0, n);
        for (int a = 0; a < arrays.length; a++) {
            System.arraycopy(sortedA[a], 0, arrays[a], 0, n);
        }
        // 年份必须连续（去重后校验）
        for (int i = 1; i < n; i++) {
            if (ys[i] == ys[i - 1]) {
                throw new IllegalArgumentException("年份重复：" + ys[i]);
            }
            if (ys[i] != ys[i - 1] + 1) {
                throw new IllegalArgumentException("年份必须连续：" + ys[i - 1] + " -> " + ys[i]);
            }
        }
        return new HistoricalData(ys, oc, ca, re, eb, pr, tx);
    }

    private double[] fill(String[] src, int n) {
        double[] arr = new double[n];
        java.util.Arrays.fill(arr, Double.NaN);
        if (src == null) {
            return arr;
        }
        for (int i = 0; i < Math.min(n, src.length); i++) {
            if (src[i] != null && !src[i].trim().isEmpty() && !"-".equals(src[i].trim())) {
                arr[i] = parse("可选列", src[i]);
            }
        }
        return arr;
    }

    private double parse(String field, String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " 存在无法解析的数值：" + raw);
        }
    }

    /** 参数范围校验（与 dcf/validation.py 口径一致）。 */
    private void check(ValuationContext c, double rf, double beta, double erp,
                       double manualRate, double taxRate, double gFirst, double gTerminal,
                       int nFirst, int nTransition) {
        require(rf, 0.0, 0.10, "无风险利率");
        require(beta, 0.0, 3.0, "Beta");
        require(erp, 0.0, 0.15, "市场风险溢价");
        require(manualRate, 0.001, 0.30, "手动折现率");
        require(taxRate, 0.0, 0.50, "税率");
        require(gFirst, -0.50, 0.50, "高增长期增长率");
        require(gTerminal, 0.0, 0.05, "永续增长率");
        if (nFirst < 1 || nTransition < 1 || nFirst + nTransition > 20) {
            throw new IllegalArgumentException("预测年数设置非法（1-20 年）");
        }
        double ke = com.dcf.model.DcfModel.capmCostOfEquity(rf, beta, erp);
        double rate = c.isUseCapm() ? ke : manualRate;
        if (rate <= gTerminal) {
            throw new IllegalArgumentException("折现率必须大于永续增长率，当前折现率 "
                    + String.format("%.2f%%", rate * 100) + "，永续增长率 "
                    + String.format("%.2f%%", gTerminal * 100));
        }
    }

    private void require(double v, double lo, double hi, String name) {
        if (Double.isNaN(v) || v < lo || v > hi) {
            throw new IllegalArgumentException(name + " 超出允许范围（"
                    + String.format("%.2f%% ~ %.2f%%", lo * 100, hi * 100) + "）");
        }
    }

    /** 用户友好错误消息（截断堆栈）。 */
    private String friendly(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }
}