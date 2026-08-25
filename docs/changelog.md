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
