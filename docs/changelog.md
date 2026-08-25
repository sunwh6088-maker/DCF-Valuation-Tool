# 变更与 Bug 记录（v1.1.0 开发）

> 规范：与 docs/bug-log.md 相同 —— 每次修改/修 bug 记录 4 要素：原因 / 修改位置 / 测试方式 / 可能影响到的模块。
> 开发过程中发现的既有问题（非本次改动引入）继续记入 docs/bug-log.md。

---

## 变更 #1：WACC 完整化（P0-1，2026-08-25）

- **原因**：原折现率只支持「CAPM（ke）或手动指定」两种口径，完全忽略债务成本与资本结构。
  对有负债公司，ke 通常高于含税盾的 WACC，导致估值系统性偏低；也无法解释
  「手动 8%-12% 与 CAPM 5%-7% 差异」的来源。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/model/WaccCalculator.java`：kd=Rf+信用利差、债务权重 D/(D+E)、WACC=kd×(1-t)×wD+ke×wE
  - `src/main/java/com/dcf/web/ValuationContext.java`：`useCapm` → `discountMode`（wacc/capm/manual 三选一）+ creditSpread + WACC 明细字段
  - `src/main/java/com/dcf/service/ValuationService.java`：compute() 按三模式取折现率，并把 ke/kd/债务权重/WACC 写入上下文
  - `src/main/java/com/dcf/web/PageController.java`：saveParams 绑定 discountMode/creditSpread；check() 校验信用利差并按模式校验折现率
  - `src/main/resources/templates/params.html`：折现率区改为三选一 radio + 信用利差输入 + 实时显示 ke/kd/权重/WACC
  - `src/main/resources/templates/result.html`：假设表新增 ke/kd/债务权重/WACC 明细；口径标签三态
  - `src/main/java/com/dcf/service/ReportService.java`：Markdown 报告假设表新增 WACC 明细、折现率口径三态
  - `src/main/java/com/dcf/excel/ExcelExporter.java`：Excel「假设」Sheet 新增 5 行 WACC 组成
  - 新增 `src/test/java/com/dcf/model/WaccCalculatorTest.java`（9 个用例）
- **测试方式**：`mvn test` 全量 37 个用例通过（含新增 9 个）；手工验证：
  无债公司 WACC=ke；高杠杆公司 WACC<ke；税率↑→WACC↓（税盾）。
- **可能影响的模块**：DcfModel 折现率来源、ValuationService、params/result 页面、Excel 假设表、Markdown 报告；
  所有走 `/params` 提交的估值流程（A股自动/CSV/手动、美股手动）。

---

## Bug #1：params.html 改造事故——文件被截断（开发过程自纠）

- **状态**：已修复
- **原因**：初版用 PowerShell 行号数组拼接替换 HTML 块，`$lines.Length` 边界与 here-string 嵌套
  （外层单引号 here-string 被内层 `'@` 提前终止、双引号 here-string 中 `${...}` 被展开）组合出错，
  导致写入的 params.html 只剩 57 行（尾部 JS/表单丢失）。
- **修改位置**：`src/main/resources/templates/params.html`（git checkout 恢复后，改用 Python 脚本
  以完整字符串精确替换，并校验关键词 `discountMode/waccOut/creditSpread/</html>` 与残留 `useCapm/capmOut`）
- **测试方式**：替换脚本内置断言（old 块必须存在、new 关键词必须存在、旧关键词必须清零）；
  最终 `mvn test` 通过；页面由浏览器截图复核。
- **可能影响的模块**：参数页表单提交链路（折现率三选一、信用利差）；若未恢复会导致 /params 表单缺字段。
- **教训**：Windows PowerShell 改含 `${}` 的模板文件时，禁止用双引号 here-string；尽量用 Python 脚本做模板替换。

---

## Bug #2：WaccCalculatorTest 税盾断言方向写反（开发过程自纠）

- **状态**：已修复
- **原因**：测试断言「税率越高，税盾越小，WACC 应越高」逻辑错误——
  税盾 = 利息×税率，税率越高税盾越大、税后债务成本越低，WACC 应越低。
- **修改位置**：`src/test/java/com/dcf/model/WaccCalculatorTest.java#taxShieldLowersWaccWhenTaxRises`
  （原方法名 taxShieldIncreasesWaccWhenTaxRises）
- **测试方式**：`mvn test` 全量通过（修正后断言与实际公式一致）。
- **可能影响的模块**：仅测试代码；实现（WaccCalculator.wacc）本身正确，无需改动。

---

## 变更 #2：三情景并行（保守 / 中性 / 乐观，P0-2，2026-08-25）

- **原因**：单点估值给人「虚假精确」感；研报惯例是给估值区间。调研的 ianzheng 项目三情景对比是最受认可的亮点之一。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/model/Scenario.java`：枚举含中文标签与偏移量（增长率±2pp、折现率∓1.5pp、永续增长率±0.5pp）
  - 新增 `src/main/java/com/dcf/model/ScenarioResult.java`：情景估值摘要 record
  - 新增 `src/main/java/com/dcf/model/ScenarioValuer.java`：偏移+夹取+估值执行器（保证 r > g）
  - `src/main/java/com/dcf/service/ValuationService.java`：compute() 一次计算三情景存入上下文；主结果=中性情景
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 `List<ScenarioResult> scenarioResults`
  - `src/main/resources/templates/result.html`：顶部新增三情景对比卡片（含情景边框配色与折现率标注）
  - `src/main/java/com/dcf/excel/ExcelExporter.java`：新增「三情景」Sheet（7 个 Sheet）
  - `src/main/java/com/dcf/service/ReportService.java`：报告「估值结论」后新增三情景对比表
  - 新增 `src/test/java/com/dcf/model/ScenarioTest.java`（3 个用例）
- **测试方式**：`mvn test` 全量 40 个用例通过；ScenarioTest 验证偏移约定、单调性（保守≤中性≤乐观）、极端参数（r≤g）不崩溃。
- **可能影响的模块**：估值编排链路（所有市场/数据来源）、结果页、Excel、Markdown 报告；敏感性矩阵不受影响（仍以主折现率为中心）。

---

## 变更 #3：判断分级 + 回本年限 + 隐含年化回报（P1-6，2026-08-25）

- **原因**：安全边际一个数字不够直观；调研的 ianzheng 项目「分级 + 回本年限 + 隐含回报」对使用者更友好、更有行动参考价值。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/model/Indicators.java`：Verdict 分级（比率阈值 1.3/1.1/0.9/0.7）、回本年限=市值/年均FCF、隐含年化回报=(内在价值/股价)^(1/年限)-1
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 verdict / paybackYears / impliedReturn 字段
  - `src/main/java/com/dcf/service/ValuationService.java`：compute() 计算三个指标写入上下文
  - `src/main/resources/templates/result.html`：安全边际卡片 badge 改为分级标签，新增回本年限/隐含年化回报
  - `src/main/java/com/dcf/excel/ExcelExporter.java`：「估值」Sheet 新增 3 行
  - `src/main/java/com/dcf/service/ReportService.java`：结论表新增 3 行
  - 新增 `src/test/java/com/dcf/model/IndicatorsTest.java`（3 个用例）
- **测试方式**：`mvn test` 全量 43 个用例通过；边界测试覆盖 1.3/1.1/0.9/0.7 阈值与 NaN 兜底。
- **可能影响的模块**：结果页卡片区、Excel 估值 Sheet、Markdown 报告；不影响核心估值数字（纯展示层指标）。

---

## 变更 #4：无风险利率自动获取（P1-4，2026-08-25）

- **原因**：手动查 10 年期国债收益率易过时、易查错；调研的 bben1 项目从 FRED 自动拉取。
- **数据源验证结果**（2026-08-25 实测）：
  - 美股 US：FRED `DGS10`（10Y 美国国债，日频 CSV，免费无 Key）✅ 可用（实测 4.74%）
  - 中国 CN：东财 5 个 reportName、新浪 globalbd、中债登 downYearBzqx（返回空模板）、中国货币网接口全部验证失败/已停用；
    FRED 中国 10Y 系列（IRLTLT01CNM156N）已下架（404）→ CN 暂不自动获取，回退默认 1.7% 并提示手动输入
- **修改位置**：
  - 新增 `src/main/java/com/dcf/data/RateFetcher.java`：FRED CSV 拉取 + 24h 缓存 + CN 回退 + 来源标注
  - `src/main/java/com/dcf/web/ApiController.java`：新增 `GET /api/rf?market=US|CN`
  - `src/main/resources/templates/params.html`：Rf 输入框新增「自动获取」按钮（US 填 FRED 值；CN 提示手动）
  - 新增 `src/test/java/com/dcf/data/RateFetcherTest.java`（2 个用例）；`LiveApiSmokeTest` 新增 FRED 冒烟用例（默认 @Disabled）
- **测试方式**：`mvn test` 全量 45 个用例通过；FRED 联网取值用 curl 实测 + 冒烟用例（可手动启用）。
- **可能影响的模块**：参数页 Rf 输入、CAPM/WACC 计算链路；CN 用户行为不变（手动输入）；US 用户可一键获取。
- **已知限制**：CN 自动获取待免费源恢复后补上（可在 RateFetcher.fetchCn10y 内扩展）。

---

## 变更 #5：金融股提示（P1-5，2026-08-25）

- **原因**：银行/保险/证券/信托等金融企业财报结构特殊，经营现金流口径 DCF 会失真（xuelixunhua 项目明确提示银行股不适用）。
- **实现决策**：东财行情行业字段（f127）实测返回 502/空（接口风控），改用**公司名称关键词检测**（银行/保险/证券/信托/期货/金融/租赁/消费金融），不依赖外部接口，稳定可靠。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/model/FinanceDetector.java`：关键词检测
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 isFinancial 标记
  - `src/main/java/com/dcf/service/ValuationService.java`：compute() 检测并写入上下文
  - `params.html` / `result.html`：金融股警告 banner
  - `ReportService.java`：报告头部风险注记
  - 新增 `src/test/java/com/dcf/model/FinanceDetectorTest.java`（2 个用例）
- **测试方式**：`mvn test` 全量 47 个用例通过；正例（招商银行/中信证券/中航信托等）、反例（贵州茅台/宁德时代/空值）。
- **可能影响的模块**：参数页/结果页展示（纯提示，不影响计算）；美股手动输入同样生效（名称必填）。
- **已知限制**：名称不含关键词的类金融公司（如"民生控股"）可能漏检；行业接口恢复后可升级为字段判断。

---

## 变更 #6：历史 DCF 回溯（P1-3，2026-08-25）

- **原因**：使用者无法验证"我的模型假设历史靠不靠谱"；这是调研的 halessi/DCF（496★）最独特的功能——回算过去 N 年估值并对比当年股价，画折溢价曲线。
- **修改位置**：
  - `src/main/java/com/dcf/data/eastmoney/EastMoneyClient.java`：新增 fetchYearEndPrices（月线 klt=103，按年取年末收盘）
  - `src/main/java/com/dcf/data/DataService.java`：新增 fetchYearEndPrices 入口
  - 新增 `src/main/java/com/dcf/service/HistoricalDcfService.java`：逐年用当年实际 FCF 回算估值（假设沿用当前参数，股本/净债务按当前值近似，样本<4 年跳过）
  - 新增 `src/main/java/com/dcf/service/HistoricalBacktest.java`：单年结果 record（年份/估值/股价/折溢价）
  - `src/main/java/com/dcf/service/ValuationService.java`：新增 setDataService 注入 + runBacktest（容错，失败不阻断主流程；美股提示无免费历史股价源）
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 backtestResults / backtestError
  - `src/main/java/com/dcf/web/PageController.java`：接线 dataService
  - `result.html`：新增历史回溯折线图（模型估值 vs 年末股价，ECharts）
  - `ReportService.java`：报告新增「七、历史 DCF 回溯」节
  - `ExcelExporter.java`：新增「历史回溯」Sheet（8 个 Sheet）
  - 新增 `src/test/java/com/dcf/service/HistoricalDcfServiceTest.java`（3 个用例）
- **测试方式**：`mvn test` 全量 50 个用例通过；固定数据手工核算折溢价公式、缺股价 NaN、样本不足跳过。
- **可能影响的模块**：结果页（新增图表）、Excel/报告（新增内容）；核心估值数字不受影响；历史股价接口失败时仅提示不报错。
- **已知限制**：历史股本/净债务按当前值近似（历史值无免费源）；美股不支持（无免费历史股价源）。
