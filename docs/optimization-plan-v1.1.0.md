# v1.1.0 优化方案（基于开源项目调研）

> 调研对象：halessi/DCF（496★）、bben1 自动估值（52★）、xuelixunhua/stock_DCF（55★）、ianzheng 十年DCF skill、potatossium/dcf_model（52★）
> 原则：所有优化保持"参数透明、可手动覆盖"；每次修改/修 bug 按 docs/bug-log.md 规范追加 4 要素记录（原因/位置/测试/影响模块）。

## 一、改动清单（按优先级）

### P0-1 WACC 完整化（债务成本 + 资本结构加权）
- **原因**：当前折现率 = CAPM 股权成本（ke），完全忽略债务。ke 通常高于含税盾的 WACC，导致**有负债公司估值系统性偏低**，且无法解释"为什么给 8%-12% 还是 CAPM 5%-7%"的差异来源。
- **修改位置**：
  - src/main/java/com/dcf/model/ 新增 WaccCalculator.java：ke=CAPM；kd=信用评级法（利息覆盖率→信用利差）或财务费用/平均有息负债；WACC = kd×D/(D+E)×(1-t) + ke×E/(D+E)
  - src/main/java/com/dcf/data/eastmoney/ 增补有息负债、货币资金、财务费用字段
  - src/main/resources/templates/params.html 新增"资本结构"区（WACC / ke / kd / D、E 自动值全部可手动覆盖）
  - src/main/resources/templates/result.html 展示 WACC 明细与两种口径估值对比
- **测试方式**：新增 WaccCalculatorTest（无债公司 WACC=ke；高杠杆公司 WACC<ke；税盾生效；边界：D=0、利息=0）；全量 mvn test 回归。
- **可能影响的模块**：DcfModel 折现率来源、ValuationService、params/result 页面、Excel 假设表/结果表、Markdown 报告。

### P0-2 三情景并行（保守 / 中性 / 乐观）
- **原因**：单点估值给人"虚假精确"感，研报惯例是给区间；ianzheng 项目三情景对比是最大亮点。
- **修改位置**：
  - src/main/java/com/dcf/model/ 新增 Scenario.java 枚举 + 默认偏移（增长率±2pp、折现率±1.5pp、永续增长率±0.5pp，可自定义）
  - src/main/java/com/dcf/service/ValuationService.java：compute() 一次算三个情景
  - src/main/resources/templates/result.html 顶部三卡片展示估值区间
  - Excel 新增"三情景"Sheet；Markdown 报告加区间结论
- **测试方式**：ScenarioTest 验证单调性（保守 ≤ 中性 ≤ 乐观）、偏移边界合法；回归全量测试。
- **可能影响的模块**：ValuationService、result 页面、ExcelExporter、ReportService。

### P1-3 历史 DCF 回溯（模型估值 vs 实际股价）
- **原因**：用户无法验证"我的模型历史靠不靠谱"；这是 halessi（496★）最独特的功能——用历史数据回算当年估值，对比当年股价，画折溢价曲线。
- **修改位置**：
  - src/main/java/com/dcf/service/ 新增 HistoricalDcfService.java：逐年用截至该年的财务数据回算 FCF 与每股估值（复用现有历史数组；历史股价从东财 K 线扩展）
  - src/main/resources/templates/result.html 新增 ECharts 折线"历史估值 vs 实际股价（折溢价%）"
  - Markdown 报告加"历史回溯"一节
- **测试方式**：固定数据单元测试（手工核算 1-2 年）；LiveApiSmokeTest 验证茅台历史回溯 5 年。
- **可能影响的模块**：data/eastmoney（历史股价）、service 新增、result 页面、报告/Excel。

### P1-4 无风险利率自动获取
- **原因**：手动查 10 年期国债收益率易过时、易查错；bben1 项目从 FRED 自动拉取。
- **修改位置**：
  - src/main/java/com/dcf/data/ 新增 RateFetcher.java：A 股拉中债/东财 10Y 国债收益率；美股拉 FRED DGS10；失败回退默认值并提示
  - params.html 显示"自动获取值（可覆盖）"；结果页标注 Rf 来源与日期
  - 缓存 24 小时，避免频繁请求
- **测试方式**：LiveApiSmokeTest 联网验证两端取值；离线回退测试。
- **可能影响的模块**：params 页面、ValuationContext、CAPM 计算链路。

### P1-5 金融股提示（银行 / 保险 / 券商）
- **原因**：金融企业财报结构特殊，经营现金流失真，DCF 不适用（xuelixunhua 项目明确提示）。
- **修改位置**：data/eastmoney 增补行业字段；input-a / params 页检测到金融行业显示警告 banner；报告加风险注记。
- **测试方式**：行业分类单元测试；手动验证银行股（如 600036）触发提示。
- **可能影响的模块**：input-a/params 页面、ReportService。

### P1-6 结果判断分级 + 回本年限 + 隐含年化回报
- **原因**：安全边际一个数字不够直观；ianzheng 的"明显低估/合理/高估 + 回本年限 + 隐含回报"更实用。
- **修改位置**：
  - src/main/java/com/dcf/model/ValuationResult.java 增补：verdict 分级（≤0.7 明显低估 / ≤1.1 合理 / ≥1.3 明显高估）、回本年限（市值/年均FCF）、隐含年化回报
  - esult.html 卡片区展示；Excel 结果 Sheet 增列
- **测试方式**：VerdictTest 边界值 0.7 / 1.1 / 1.3。
- **可能影响的模块**：ValuationResult、result 页面、Excel、报告。

### P2-7 GitHub Actions CI（工程化）
- **原因**：协作后 PR 无自动验证；主流开源项目标配。
- **修改位置**：新增 .github/workflows/ci.yml（JDK 21 + mvn test + package + 上传 artifact）。
- **测试方式**：push 后观察 Actions 变绿。
- **可能影响的模块**：工程流程，无运行时代码。

### P2-8 Dockerfile（可选，放最后）
- **原因**：非 Java 用户部署门槛高。
- **修改位置**：根目录 Dockerfile + README 一节。
- **可能影响的模块**：无运行时代码。

## 二、暂缓（本期不做）
- FCFF 备选口径（EBIT 起步）：模型复杂化，P0 之后评估
- 多模型切换（零增长 / 三阶段）：与两阶段重复度高
- PE 退出法（段永平式）：需要大量新输入，二期考虑
- F-score 财务质量打分：与 DCF 互补但范围大

## 三、实施顺序（每步一次提交，可单独回滚）
1. P0-1 WACC 完整化
2. P0-2 三情景并行
3. P1-6 判断分级（小改动先落地）
4. P1-4 Rf 自动获取
5. P1-5 金融股提示
6. P1-3 历史 DCF 回溯（工作量最大）
7. P2-7 CI
8. 全量回归 → 打 tag v1.1.0 → 发布 Release v1.1.0（新 jar + 新截图）

## 四、版本显示机制（回答"提交后是否直接显示更新版本"）
- **git push 后**：GitHub 仓库页（代码、README、目录）**立即显示最新**，无需任何额外操作。
- **但 Release 不会自动更新**：Release 是发布时刻的快照（v1.0.0 的 jar 永远指向当时的代码）。
- **结论**：开发阶段看 main 分支提交即可；对外发布靠"新 tag + 新 Release"，本方案全部完成后发 v1.1.0，别人下载到的才是更新后的版本。