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

---

## 变更 #7：GitHub Actions CI + Dockerfile + 公网部署指南（P2-7 / P2-8，2026-08-25）

- **原因**：协作后 PR 无自动验证；非 Java 用户部署门槛高；`localhost` 地址仅本机可访问，需要公网部署路径说明。
- **修改位置**：
  - 新增 `.github/workflows/ci.yml`：JDK 21 + mvn test + package + 上传 jar artifact（push/PR 触发）
  - 新增 `Dockerfile`（多阶段：maven 构建 → temurin JRE 运行，EXPOSE 8501）+ `.dockerignore`
  - `README.md`：新增「Docker 部署（免装 JDK）」「部署到公网」两节（PaaS / 云服务器 / 内网穿透三方案，含数据源限流提示）
- **测试方式**：本地 `mvn test` 全量 50 个用例通过；push 后观察 GitHub Actions 状态（build-and-test 变绿）。
- **可能影响的模块**：工程流程（CI 不参与运行时代码）；Docker 镜像构建产物。

---

## 变更 #8：中国 10Y 自动获取恢复（中债登，2026-08-25 晚）

- **原因**：v1.1.0 发版前实测东财/新浪/中债登 downYearBzqx/中国货币网免费接口全部失效或需登录，CN 曾降级为「手动输入（默认 1.7%）」。
  发版后复查发现中债登**另一接口** `historyQuery`（国债及其他债券收益率曲线，网页版历史查询入口）恢复可用且免登录：
  实测 2026-08-25 返回 2026-08-24 数据，中国 10Y = 1.6794%。故把自动获取接回，同时保留手动兜底。
- **修改位置**：
  - `src/main/java/com/dcf/data/RateFetcher.java`：
    - `fetchCn10y()` 由「直接返回 NaN」改为调用中债登 `historyQuery` 并解析 HTML 表格（`parseChinabond10y`，取「中债国债收益率曲线」行 10 年列，最新行为空则向前取上一交易日）
    - 新增宽松 TLS 处理：中债登证书链由国内 CA 签发、不在 JDK cacerts，严格校验会 PKIX 失败；仅对该主机放宽（FRED 仍严格校验），并限定 HostnameVerifier
    - 新增 24h 缓存（与 US 共用 cacheTime）；`sourceLabel("CN")` 更新为「中债登（中债国债收益率曲线 10Y，日频）」
  - `src/main/java/com/dcf/web/ApiController.java`：`/api/rf` 注释更新（CN 由「不可用」改为「中债登历史查询」）
  - `src/main/resources/templates/params.html`：`fetchRf()` 移除「CN 直接提示手动输入」分支，CN/US 统一走 `/api/rf`；成功填充并显示来源，失败才提示手动输入；rfHint 文案同步更新
  - `src/test/java/com/dcf/data/RateFetcherTest.java`：重写为 5 个用例（解析最新行 / 空值回退上一交易日 / 找不到目标曲线抛异常 / 兜底常量 / 来源标注）
  - `src/test/java/com/dcf/data/LiveApiSmokeTest.java`：`fetchFredUs10y` 改为 `fetchRfUsAndCn`（US + CN 双源冒烟，默认 @Disabled）
  - `README.md`：数据来源节补充 10Y 来源；版本号 1.0.0 → 1.1.0；「部署到公网」节改写为面向访客的正式说明
- **测试方式**：
  - 单元：`mvn test` 全量 54 个用例通过（含 RateFetcherTest 5 个新用例，解析用真实页面结构精简样本）
  - 联网：`jshell` 直接调用 `RateFetcher.fetchCn10y()` 实测返回 0.016794（1.6794%，与 2026-08-24 中债登官网一致）
  - 手工：参数页点「自动获取」应填入 1.68 并显示来源；断网/接口变更时应提示手动输入
- **可能影响的模块**：参数页 Rf 输入（CN 由手动改自动）、CAPM/WACC 计算链路（Rf 自动取值）、报告/Excel 中 Rf 来源标注；FRED（US）逻辑不变。
- **已知限制**：
  - 中债登查询窗口上限 1 年，本实现取最近 30 天，日频足够
  - 中债登 SSL 证书链不在 JDK 默认信任库，需宽松 TLS（仅限该公开数据源，不传输敏感信息）
  - FRED（境外）在当前网络实测超时，失败时前端提示手动输入（兜底逻辑保留）

---

## 变更 #9：启动脚本完善（v1.1.1，2026-08-25 晚）

- **原因**：
  1. `run.bat` 中 jar 名仍写死 `dcf-valuation-tool-1.0.0.jar`（实际版本已到 1.1.x），且检测逻辑失效；
  2. FRED 等境外数据源在国内网络需代理，但代理地址是个人环境变量，**不能写死进脚本**（别人用不了自己的代理），
     需要脚本支持标准 `HTTPS_PROXY` / `HTTP_PROXY` 环境变量，未设置时行为与原来完全一致。
- **修改位置**：
  - `run.bat`：重写（jar 名改为 1.1.1 并按需打包；新增 `HTTPS_PROXY`/`HTTP_PROXY` 解析 →
    `-Dhttps.proxyHost/-Dhttps.proxyPort/-Dhttp.proxyHost/-Dhttp.proxyPort` 透传给 JVM；
    开发模式经 `spring-boot.run.jvmArguments` 传给 fork 出的应用 JVM；无代理时零改动启动）
  - 新增 `run.sh`：Linux/macOS 等价脚本（sed 解析代理 URL，端口默认 80，未设置时零改动启动）
  - `README.md`：快速开始新增 `run.bat`/`run.sh` 用法与「代理（可选）」说明（明确别人使用无需配置）
  - `pom.xml`：版本 1.1.0 → 1.1.1
- **测试方式**：
  - `run.bat`：无代理双击/命令行启动正常；`set HTTPS_PROXY=http://127.0.0.1:7890` 后回显「使用代理」且
    实际 java 进程带 `-Dhttps.proxyHost=127.0.0.1` 参数（`run.bat jar` + 任务管理器/wmic 命令行核对）
  - `run.sh`：语法检查 `sh -n run.sh`；`HTTPS_PROXY` 解析用例人工核对（host/port 提取）
  - `mvn package` 成功产出 `dcf-valuation-tool-1.1.1.jar`；`mvn test` 全量 54 个用例通过
- **可能影响的模块**：仅启动方式（README/run.bat/run.sh）；运行时代码、数据抓取、估值计算均不受影响。
- **已知限制**：`HTTPS_PROXY` 不支持带用户名密码的格式（如 `http://user:pass@host:port`），极少数场景可用
  系统属性 `-Dhttps.proxyUser/-Dhttps.proxyPassword` 补充。
## 变更 #10：启动脚本支持自定义端口 + README 公开可用性修正（v1.1.1，2026-08-25 晚）

- **原因**：审查「本地可用但别人用不了」的可移植性风险时发现：
  1. `application.yml` 端口固定 8501，被占用时启动直接失败且无便捷覆盖方式（用户本地就存在 8501 被旧实例占用的情况）；
  2. README 的 PowerShell 代理示例命令被换行截断（`.\run.bat` 变成 `.` + `un.bat`），照抄必然执行失败；
  3. README 版本号/云服务器 jar 名仍为 v1.1.0，Excel Sheet 数写 6 个（实际 8 个：三情景/历史回溯未计入），分支说明未标注 python-prototype 为 WIP。
- **修改位置**：
  - `run.bat` / `run.sh`：新增可选端口参数（`run.bat 8502`、`run.bat jar 8502`、`./run.sh jar 8502`），并统一 run.bat 行尾为 CRLF（部分 cmd 版本对 LF-only 批处理的 `goto` 有兼容问题）
  - `README.md`：修复 PowerShell 示例命令断裂；版本号与 jar 名更新为 v1.1.1；Excel 描述改为 8 个 Sheet；快速开始补充端口占用时的三种换端口方式；分支段标注 python-prototype 为 WIP（勿直接运行）
  - `python-prototype` 分支（单独提交 `0d51f76`）：`run.bat` 增加 app.py 缺失检测与友好提示；README 从 `# First` 改为完整 WIP 说明（现状/计划/许可）
- **测试方式**：`mvn test` 全量 54 个用例通过（2 跳过）；冒烟：`cmd /c run.bat jar 8502` 启动后 `http://127.0.0.1:8502/` 返回 200（Tomcat 日志确认端口 8502），测试进程已清理；`run.sh` 无法在本机验证（无 sh），语法按 POSIX 规范手写复查
- **可能影响的模块**：启动流程（run.bat/run.sh）、README/分支说明（仅文档）；不影响 Java 业务代码与估值逻辑
## 变更 #11：Maven Wrapper + 脚本 Java 21 检查 + 可执行位/行尾修正（v1.1.1，2026-08-25 晚）

- **原因**：上一轮审查提出的两个可移植性建议落地：
  1. 别人克隆仓库后若未安装 Maven，`run.bat`/`run.sh` 的开发模式直接报「mvn 不是内部或外部命令」；
  2. 未装 Java 或版本低于 21 时，报错信息不友好（UnsupportedClassVersionError 等）；
  3. 仓库在 `core.autocrlf=true` 环境下，`run.sh`/`mvnw` 可能被检出为 CRLF，Linux/macOS 上脚本首行 `#!/usr/bin/env sh\r` 直接失败；且 `run.sh`、`mvnw` 缺少可执行位（`./run.sh` 会权限拒绝）。
- **修改位置**：
  - 新增 `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`（maven-wrapper-plugin `only-script` 模式，不提交 wrapper jar；`distributionUrl` 指向 Maven 3.9.9，properties 内含国内清华镜像备选注释）
  - `run.bat` / `run.sh`：开头新增 JDK 21 检测（缺失/低版本给出 adoptium 下载链接并退出）；开发模式优先使用 `mvnw.cmd`/`mvnw`，无 wrapper 时回退 `mvn`
  - 新增 `.gitattributes`：`mvnw`、`run.sh` 强制 `eol=lf`（防 CRLF 破坏 shell 脚本）
  - `git update-index --chmod=+x mvnw run.sh`（修正可执行位）
  - `README.md`：前置要求改为「仅需 JDK 21」；快速开始与测试命令改为 `./mvnw`，并说明脚本自动检查 Java 版本
- **测试方式**：
  - `mvnw.cmd -v`：首次运行自动下载 Maven 3.9.9 成功（输出 Apache Maven 3.9.9 + Java 21.0.12）
  - 冒烟 1（无 Java）：`cmd /c "set PATH=C:\Windows\System32&& run.bat"` → 输出 `[ERROR] 未检测到 Java`，退出码 1
  - 冒烟 2（完整链路）：`cmd /c run.bat 8502` → 经 mvnw 自动编译并启动，`http://127.0.0.1:8502/` 返回 200，Tomcat 日志确认端口 8502，测试进程已清理
  - `mvnw.cmd test` 全量测试（见提交时输出）
- **可能影响的模块**：启动流程（run.bat/run.sh/新增 wrapper）、README；不影响 Java 业务代码；`run.sh` 新增的 awk 解析仅在本机无 sh 环境下无法实测，已按 POSIX 语法人工复查
## 变更 #12：版本 bump v1.1.1 → v1.1.2（2026-08-25 晚）

- **原因**：v1.1.1 发布后补充了 Maven Wrapper、脚本 Java 检查、行尾/可执行位修正（变更 #10/#11），
  为便于用户区分发行版，bump 版本号并重新打包发布。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.1 → 1.1.2
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.2.jar`
  - `README.md`：版本号与 jar 名同步为 1.1.2
- **测试方式**：`mvnw test` 全量 54 个用例通过；`mvnw -DskipTests package` 产出 1.1.2 jar；
  冒烟：`cmd /c run.bat jar 8502` 启动后 `http://127.0.0.1:8502/` 返回 200，测试进程已清理
- **可能影响的模块**：打包产物文件名（旧 1.1.1 jar 不再被脚本引用）、Release 资产；无业务逻辑变化
## 变更 #13：数据层扩展——进阶科目（净利润/折旧摊销/资产负债/股本）（2026-08-25 晚）

- **原因**：FCFF 口径、PE 退出法、Piotroski F-Score 需要比「OCF/Capex/营收/EBIT」更多的财务科目；
  原 `HistoricalData` 只有 7 个字段，无法支撑这些功能。
- **修改位置**：
  - `src/main/java/com/dcf/data/model/HistoricalData.java`：新增 `ExtraFinancials` 记录（netIncome/depreciation/totalAssets/totalLiabilities/currentAssets/currentLiabilities/grossProfit/sharesCapital），保留 7 参兼容构造器，新增 `extraAt()` 便捷访问
  - `src/main/java/com/dcf/data/sina/SinaFinanceParser.java`：新增 8 个科目关键词；净利润用「精确匹配优先」（避免命中归母净利润）；折旧摊销为现金流量表内所有含「折旧/摊销」科目求和；毛利 = 营收 − 营业成本
  - `src/main/java/com/dcf/data/csv/CsvImporter.java`：模板新增 8 个选填列 + 理杏仁关键词映射（净利润/折旧摊销/总资产/总负债/流动资产/流动负债/营业成本/股本）
  - `src/main/java/com/dcf/web/PageController.java`：手动输入新增 8 个选填 `@RequestParam`，随年份排序对齐
  - `src/main/resources/templates/input-a.html`、`input-us.html`、`static/js/common.js`：手动输入表新增 8 个选填列（带说明文案）
- **测试方式**：`mvnw test` 全量通过（含既有 CsvImporterTest/SinaFinanceParserTest 回归）；冒烟用手动输入带进阶科目数据走通 /input/manual → /params → /result
- **可能影响的模块**：所有数据入口（自动抓取/CSV/手动）、`HistoricalData` 构造调用点；老数据（extra=null）完全向后兼容

---

## 变更 #14：FCFF 备选现金流口径（EBIT 起步）（2026-08-25 晚）

- **原因**：简化 FCF（OCF−Capex）未含税盾与营运资本变动，对重资产/营运资金占用大的公司偏差明显；方案暂缓项落地。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/model/FcfCalculator.java`：`fcffSeries()` = EBIT×(1−t) + D&A − Capex − ΔNWC（首年 NaN）；`baseValue()` 最近一年优先、回退近 3 年均值
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 `fcfMode`/`fcfModeUsed`/`fcffWarning`/`baseFcfValue`
  - `src/main/java/com/dcf/service/ValuationService.java`：按口径计算基准 FCF，FCFF 数据不足自动回退简化口径并提示
  - `src/main/resources/templates/params.html`：现金流口径 radio；`result.html` 假设表显示口径与实际生效值
  - `ExcelExporter.java`/`ReportService.java`：说明页/报告口径描述同步
- **测试方式**：新增 `FcfCalculatorTest`（5 用例：简化口径、FCFF 手工核算、缺折旧→NaN、基准值回退、NWC）；冒烟三阶段+FCFF 组合走通
- **可能影响的模块**：基准 FCF → 全部估值结果；参数页/结果页/Excel/报告展示

---

## 变更 #15：多模型切换（零增长 / 三阶段）（2026-08-25 晚）

- **原因**：两阶段模型对低增长成熟公司（零增长更合适）与长赛道公司（需要更细的增长分段）不够灵活；方案暂缓项落地。
- **修改位置**：
  - `src/main/java/com/dcf/model/DcfModel.java`：新增 `forecastGrowthPathThreeStage(g1,g2,g3,n1,n2,nTransition)`（高增长恒定 → 线性过渡到成长期 → 线性过渡到永续）
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 `modelType`/`gSecond`/`nSecond`
  - `src/main/java/com/dcf/service/ValuationService.java`：统一估值管线 `valueOnce()`（主结果与三情景共用同一口径），零增长强制 g=0，三阶段走新路径
  - `PageController.saveParams`：新增参数绑定与校验（模型白名单、年数合计 ≤20）
  - `params.html`：模型 radio + 三阶段参数显隐联动；`result.html`/Excel/报告：模型标签与成长期参数
- **测试方式**：新增 `DcfModelExtendedTest`（三阶段增长率路径逐点核对）；三阶段冒烟（3+3+2 年路径正确折现）
- **可能影响的模块**：估值管线（主结果/三情景/敏感性）、参数页、结果页、Excel、报告

---

## 变更 #16：PE 退出法终值（2026-08-25 晚）

- **原因**：Gordon 终值对永续增长假设极敏感；PE 退出法是机构常用交叉验证口径；方案暂缓项落地。
- **修改位置**：
  - `src/main/java/com/dcf/model/DcfModel.java`：新增 `dcfValuationPeExit()`/`fullValuationPeExit()`（TV = 退出PE × 期末净利润）
  - `src/main/java/com/dcf/service/ValuationService.java`：终值方法分发；净利润率估算（历史净利润率 → 近3年均值 → EBIT×(1−t)/营收 近似，均标注来源）；期末净利润 = 基准营收沿增长率路径复利 × 净利润率；敏感性矩阵列轴切换为「退出PE」（±5，步长 2.5）
  - `src/main/java/com/dcf/model/SensitivityResult.java`：新增 `xLabel`（列轴名称，默认"永续增长率"）
  - `ValuationContext`：`terminalMode`/`exitPe`/`terminalNetIncome`/`netMarginUsed`/`netMarginSource`
  - `params.html`/`result.html`/`ExcelExporter`/`ReportService`：终值方法选择、退出PE 输入、敏感性标题/轴标签联动
- **测试方式**：`DcfModelExtendedTest`（PE 终值手工核算 TV=PE×NI、PV 折现、完整估值）；冒烟 PE 模式敏感性轴显示「退出PE」
- **可能影响的模块**：终值计算与敏感性矩阵（Gordon/PE 两套轴）、结果页热力图、Excel 敏感性 sheet、报告

---

## 变更 #17：Piotroski F-Score 财务质量打分（2026-08-25 晚）

- **原因**：DCF 假设现金流质量可持续，但缺乏对财务质量的客观检验；方案暂缓项落地（与 DCF 互补的财务健康快检）。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/model/FScoreCalculator.java`：9 项二值指标（ROA/现金流/ROA改善/应计质量/杠杆/流动比率/新股/毛利率/周转率），数据不足项记 null 不参与计分
  - `src/main/java/com/dcf/web/ValuationContext.java`：`fScores` 列表（最近 3 个可计分年份）
  - `src/main/java/com/dcf/service/ValuationService.java`：compute() 中计算 F-Score（失败不影响主流程）
  - `result.html`：F-Score 卡片（总分 + 9 项明细表）；`ExcelExporter` 新增「F-Score」sheet（第 9 个）；`ReportService` 新增 F-Score 章节
- **测试方式**：新增 `FScoreCalculatorTest`（3 用例：9 项全命中、缺数据→null 降级、年份截断）；冒烟结果页/报告/Excel 均出现 F-Score
- **可能影响的模块**：结果页/Excel/报告（新增内容，不影响估值数字）；数据不足时显示为空
## 变更 #18：版本 bump v1.1.2 → v1.1.3（2026-08-25 晚）

- **原因**：v1.1.2 发布后新增了 4 项功能（FCFF 口径、多模型、PE 退出法、F-Score，见变更 #13-17），
  本地重新打包的 1.1.2 jar 已含新代码但与已发布的 v1.1.2 Release 不一致；bump 版本号重新发版。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.2 → 1.1.3
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.3.jar`
  - `README.md`：版本号与 jar 名同步为 1.1.3
- **测试方式**：`mvnw test` 全量 65 个用例通过；`mvnw package` 产出 1.1.3 jar；
  冒烟 `run.bat jar 8502`：自动抓取茅台 → 提交参数 → 结果页/Excel/报告正常，截图与茅台示例重新生成
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化

---

## 变更 #19：首页版本号动态化（2026-08-25）

- **原因**：首页导航栏版本号自 v1.0 起写死在模板（"Java 版 v1.0"），版本 bump 到 1.1.x 后页面仍显示 v1.0，用户误以为在用旧版本；且存在"本地 jar 与 Release 不一致"同类隐患（页面与代码版本脱节）。
- **修改位置**：
  - `src/main/resources/application.properties`（新增）：`dcf.version=@project.version@`，Maven resource filtering 自动注入 pom 版本号，bump 版本时无需再改页面
  - `src/main/java/com/dcf/web/PageController.java`：注入 `@Value("${dcf.version}")`，首页 index() 向模板传 version
  - `src/main/resources/templates/index.html`：版本号改为 Thymeleaf 动态输出（`th:text`），无 JS/降级时仍显示兜底文案
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 重新打包后启动 8504 冒烟，首页显示 "Java 版 v1.1.3"（实测确认 filtering 生效）
- **可能影响的模块**：仅首页导航栏显示文案；不影响估值流程/其他页面/导出

---

## 变更 #20：清理历史遗留的写死文案与文档脱节（2026-08-25）

- **原因**：变更 #19 修复首页版本号写死后，全仓排查同类问题，发现 4 处"写死/文档与实际脱节"：
  1. README 目录结构只列 `docs/bug-log.md`，漏掉当前主记录 `docs/changelog.md`；
  2. 参数页无风险利率提示写死具体数值（CN≈1.7% / US≈4.3%），利率是动态数据，数字会过时；
  3. 首页副标题写死"预测 10 年"，三阶段模型支持自定义预测年数（合计 ≤20）；
  4. README 首段写死"两阶段 + Gordon"，现已支持零增长/三阶段模型与 PE 退出法。
- **修改位置**：
  - `README.md`：首段模型描述更新；目录结构补充 changelog.md（主记录）并注明 bug-log.md 为早期记录
  - `src/main/resources/templates/params.html`：rfHint 改为通用提示（自动获取 + 参考区间，不写死具体值）
  - `src/main/resources/templates/index.html`：副标题改为"预测期折现（默认 10 年，可分段调整）"
- **测试方式**：模板修改后重新 `mvnw package`；启动 8504 冒烟，首页/参数页文案正常显示，`rg` 确认无残留写死值
- **可能影响的模块**：仅页面文案与 README 文档；不影响估值逻辑/导出/接口

---

## 变更 #21：版本 bump v1.1.3 → v1.1.4（2026-08-25）

- **原因**：v1.1.3 发布后修复了两处问题（变更 #19 首页版本号动态化、#20 清理写死文案与文档脱节），本地 jar 与已发布的 v1.1.3 Release 内容不一致；bump 版本号重新发版，保证 jar 与 Release 一一对应。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.3 → 1.1.4
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.4.jar`
  - `README.md`：jar 名与版本号同步为 1.1.4
  - 首页版本号无需再改（变更 #19 已动态化，自动跟随 pom）
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.4 jar；启动冒烟，首页显示 "Java 版 v1.1.4"
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化

---

## 变更 #22：港股代码输入友好提示（2026-08-25）

- **原因**：用户输入港股代码（如腾讯 H00700）时，前端 `pattern="[0-9]{6}"` 直接拦截只报笼统的"格式错误"，未说明港股不在支持范围，误导用户以为代码格式输错。
- **修改位置**：
  - `src/main/java/com/dcf/data/DataService.java`：`normalizeCode()` 对 `Hxxxxx`/`HKxxxxx` 格式抛出明确中文提示（"港股暂不支持自动抓取，请改用美股手动输入入口"），其他非法格式提示同步补充说明
  - `src/main/resources/templates/input-a.html`：3 处代码输入框加 `title` 提示；自动抓取说明文案更新（明确 A 股范围 + 港股指引）；新增 JS 在 `pattern` 不匹配时 `setCustomValidity` 显示友好中文提示
  - 新增 `src/test/java/com/dcf/data/DataServiceTest.java`：normalizeCode 全角/前缀归一化、港股 H00700/HK00700 友好报错、其他非法格式用例
- **测试方式**：`mvnw test -Dtest=DataServiceTest` 通过（新增 3 用例）；全量测试通过；冒烟 POST `/input/a/auto` code=H00700 返回友好错误提示而非 500
- **可能影响的模块**：A 股输入页（提示文案，不影响正常 A 股流程）；代码校验链路（错误消息更明确）；不涉及估值逻辑

---

## 变更 #23：版本 bump v1.1.4 → v1.1.5（2026-08-25）

- **原因**：v1.1.4 发布后修复了港股代码输入提示（变更 #22），本地 jar 与已发布 v1.1.4 Release 内容不一致；bump 版本号重新发版，保证 jar 与 Release 一一对应。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.4 → 1.1.5
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.5.jar`
  - `README.md`：jar 名与版本号同步为 1.1.5
  - 首页版本号无需再改（变更 #19 已动态化，自动跟随 pom）
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.5 jar；启动冒烟，首页显示 "Java 版 v1.1.5"，H00700 友好提示与 600519 正常抓取均验证
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化

---

## 变更 #24：敏感性热力图坐标轴单位标签被裁剪（2026-08-25）

- **原因**：ECharts 图表容器未设固定高度（默认 0 高导致依赖默认高度），且热力图 grid 边距
  （left:70 / bottom:60）过窄，y 轴名称「折现率」、x 轴名称「永续增长率/退出PE」按默认
  nameLocation（轴端点）渲染时被容器边界裁掉，只露出一点点。
- **修改位置**：`src/main/resources/templates/result.html`
  - 热力图 `#sensitivity-chart`、FCF 图 `#fcf-chart` 加固定高度 `style="height:460px"`；
    历史回溯图 `#backtest-chart` 加 `height:420px`
  - 热力图 grid 边距改为 `{ left: 90, right: 30, top: 30, bottom: 85 }`，给轴名称留足空间
  - xAxis：`nameLocation:'middle', nameGap:32`，axisLabel `margin:10, interval:0`，
    增长率标签从 `toFixed(2)` 改为 `toFixed(1)`（缩短文本，避免拥挤）
  - yAxis：`nameLocation:'middle', nameGap:45`，axisLabel `margin:10`
  - visualMap 从 `bottom:0` 微调为 `bottom:5`，避免压住 x 轴名称
- **测试方式**：打包后在 8501 启动，headless Edge + CDP 走「600519 自动抓取（10 年）+ FCFF/PE 退出法参数」流程，
  截图复核：y 轴「折现率」与 x 轴「永续增长率」标签完整可见、不再出界；热力图/FCF 图 canvas 均为 484×460 正常渲染；
  回溯图因东财历史股价免费接口网络失败未渲染（已知限制，与本改动无关）
- **可能影响的模块**：结果页三个 ECharts 图表（热力图、FCF 图、历史回溯图）的布局；
  不影响估值计算、Excel/报告导出、参数页

---

## 变更 #25：版本 bump v1.1.5 → v1.1.6（2026-08-25）

- **原因**：变更 #24（热力图坐标轴裁剪修复）改动了打包内容，本地 jar 与已发布 v1.1.5 Release 不一致；bump 版本号重新发版，保证 jar 与 Release 一一对应。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.5 → 1.1.6
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.6.jar`
  - `README.md`：jar 名与版本号同步为 1.1.6
  - 首页版本号动态化（变更 #19），自动跟随 pom，无需手动改
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.6 jar；启动冒烟 600519 全流程 + 结果页截图复核热力图
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化


---

## Bug #8：Beta 自动计算接口 500（Map.of 不允许 null 值）——详见 docs/bug-log.md Bug #7

- **原因 / 修改位置 / 测试方式 / 可能影响的模块**：完整 4 要素记录在 `docs/bug-log.md` 的 Bug #7。
  简版：`ApiController.beta()`/`rf()` 用 `Map.of` 构造响应，null 值（Beta 计算失败/无风险利率获取失败）触发 NPE 500；
  已改用 HashMap，失败时返回 200 + 具体 error 文案。前端无需改动。


---

## 变更 #26：版本 bump v1.1.6 → v1.1.7（2026-08-25）

- **原因**：v1.1.6 发布后修复了 Beta/无风险利率接口 500（Bug #7/#8），本地 jar 与已发布 v1.1.6 Release 内容不一致；bump 版本号重新发版，保证 jar 与 Release 一一对应。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.6 → 1.1.7
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.7.jar`
  - `README.md`：jar 名与版本号同步为 1.1.7
  - 首页版本号动态化（变更 #19），自动跟随 pom，无需手动改
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.7 jar；启动冒烟 `/api/beta?code=600519` 200 + beta 值、`/api/rf?market=US` 200 + 来源提示（失败路径不再 500）
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化


---

## 变更 #27：东财 K 线连接不稳定（HTTP/2 显式指定 + 失败自动重试）（2026-08-25）

- **原因**：用户反馈 Beta 自动计算仍失败。接口日志报 `HTTP/1.1 header parser received no bytes`（连接建立后未收到任何响应字节）。
  用 JDK HttpClient 实测（3 次/协议）：**HTTP/2 下 3/3 成功，ALPN 降级 HTTP/1.1 后仅 1/3 成功**——
  东财 push2his 边缘节点/中间网络对 HTTP/1.1 连接间歇性立即断开；应用默认 HTTP_2 但协商失败会自动降级，
  降级后即触发失败；且常驻进程连接池复用被服务端主动断开的旧连接也会加剧该现象。
- **修改位置**：`src/main/java/com/dcf/data/HttpUtil.java`
  - `HttpClient` 显式 `.version(HttpClient.Version.HTTP_2)`，避免协商降级到不稳定的 HTTP/1.1
  - `get()` 增加失败自动重试：最多 3 次尝试、间隔 400ms；重试期间连接池自动剔除失效连接；
    最终失败时错误信息注明"已重试 2 次"，便于用户判断是否为持续网络故障
  - 该工具为所有数据源共用（新浪财报、东财 K 线、中债登 10Y、FRED），重试逻辑一并受益
- **测试方式**：`mvnw test` 全量通过；重启后连续实测 `/api/beta?code=600519` 5 次全部 200 且有 beta 值；
  对照实验（临时 Java 程序）HTTP_2 3/3 vs HTTP_1_1 1/3
- **可能影响的模块**：Beta 自动计算、A 股自动抓取、无风险利率自动获取（共用 HttpUtil 的全部数据源请求）；
  失败兜底行为不变（仍提示手动输入），仅降低偶发失败概率；不涉及估值计算


---

## 变更 #28：Beta 自动计算增加腾讯备用数据源（东财故障自动切换）（2026-08-25）

- **原因**：v1.1.7 修复后用户反馈 Beta 仍计算失败。实测发现东财 push2his 免费接口**整体不可用**（不仅 JDK 客户端，连 .NET 直连都失败，"An error occurred while sending the request"），
  HTTP/2 + 重试（变更 #27）只能缓解客户端侧问题，无法解决数据源本身故障。同期实测备用源可用性：新浪 K 线为加密格式（解析成本高）、**腾讯 web.ifzq.gtimg.cn 返回标准 JSON 前复权周线（2020-06 至今，约 320 条）**，适合直接兜底。
- **修改位置**：
  - 新增 `src/main/java/com/dcf/data/tencent/TencentKlineClient.java`：拉取前复权周线（`param=sh600519,week,,,320,qfq`），解析 `data.<symbol>.qfqweek`（行格式 [日期, 开盘, 收盘, 最高, 最低, 成交量]，收盘价 index=2），按起始日期过滤后返回 TreeMap（日期升序）
  - `src/main/java/com/dcf/data/DataService.java`：`fetchBeta()` 改为主备切换——先东财（个股+沪深300 周线），异常或返回 NaN 时自动切换腾讯源（个股 `6→sh/其他→sz` 前缀 + 沪深300 固定 `sh000300`，按共同日期对齐后回归计算）；两源都失败才抛异常
  - 前端无需改动（接口结构不变，仍返回 beta 或具体错误）
- **测试方式**：`mvnw test` 全量通过；东财不可用期间实测 `/api/beta?code=600519` 3/3 成功（beta≈0.9849）、`code=000858` 2/2 成功（beta≈1.2923），确认全部走腾讯兜底路径（东财直连持续 FAIL 对照）
- **可能影响的模块**：Beta 自动计算（参数页「自动算」按钮）；数据源故障时从"计算失败"变为"自动降级成功"；不涉及估值计算、A 股财务抓取（仍走新浪）、无风险利率获取
- **已知边界**：腾讯源仅覆盖 A 股（Beta 场景本来就是 A 股专属）；若腾讯也故障，错误信息会如实提示


---

## 变更 #29：版本 bump v1.1.7 → v1.1.8（2026-08-25）

- **原因**：v1.1.7 发布后新增了腾讯备用数据源（变更 #28），本地 jar 与已发布 v1.1.7 Release 内容不一致；bump 版本号重新发版，保证 jar 与 Release 一一对应。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.7 → 1.1.8
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.8.jar`
  - `README.md`：jar 名与版本号同步为 1.1.8
  - 首页版本号动态化（变更 #19），自动跟随 pom，无需手动改
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.8 jar；东财不可用期间 `/api/beta` 600519/000858 实测成功（腾讯兜底）
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化


---

## 变更 #30：参数参考值增强——信用利差自动获取 + 有效税率/营收 CAGR 参考 + ERP/永续提示（2026-08-25）

- **原因**：用户询问 ERP、信用利差、高增长期增长率/年数、永续增长率、有效税率能否自动获取。分析结论：
  - 信用利差：中债登同一个免费接口（10Y 国债在用）就返回「国债/商业银行普通债AAA/中短期票据AAA」多条曲线，可算利差（票据 AAA 3Y − 国债 3Y）——**可自动获取**
  - 有效税率：历史数据已含「利润总额 + 所得税费用」（新浪/CSV 均支持）——**可自动计算参考值**
  - 高增长期增长率：无权威源，但可用历史营收 CAGR 给**参考值一键填入**
  - ERP：Damodaran 页面国内不可达、A 股无权威免费接口——**给市场化参考区间提示**
  - 永续增长率/高增长年数：本质是投资者假设——**补充惯例说明**（2%-3%、默认 5 年），不假装自动获取
- **修改位置**：
  - `src/main/java/com/dcf/data/RateFetcher.java`：新增 `fetchCnCreditSpread()` + `parseChinabondCreditSpread()`（解析同表「中债中短期票据收益率曲线(AAA)」与国债 3 年列，按**最新共同交易日**对齐；先匹配曲线名再解析数值，避免表头行「3年」文字导致 NumberFormatException——开发过程自纠）
  - `src/main/java/com/dcf/web/ApiController.java`：新增 `GET /api/creditSpread`（HashMap 构造，遵循 Bug #7 教训）
  - 新增 `src/main/java/com/dcf/service/ReferenceCalculator.java`：`effectiveTaxRate()`（近 3 年所得税合计÷利润总额合计，亏损年过滤）、`revenueCagr()`（营收复利 CAGR，任一年非正→NaN）
  - `src/main/java/com/dcf/web/ValuationContext.java`：新增 `refTaxRate` / `refGrowth`（Double，null=不可用）
  - `src/main/java/com/dcf/web/PageController.java`：`GET /params` 时按当前历史数据计算参考值（不覆盖用户已填值）
  - `src/main/resources/templates/params.html`：信用利差加「自动获取」按钮（US 提示手动输入）；高增长期增长率/有效税率加「参考历史」按钮与参考值提示（不可用时自动隐藏）；ERP 提示按市场动态（CN 5.5%-6.5% / US 4.5%-5%）；永续增长率提示补充「长期名义 GDP 增速预期」
  - 测试：`RateFetcherTest` +3（最新共同日期/忽略 AA+ 行/无对齐日期抛异常）、新增 `ReferenceCalculatorTest`（7 用例）
- **测试方式**：`mvnw test` 全量通过（85 用例）；实测 `/api/creditSpread` → 200 + spread≈0.004098（与手工测算一致）；600519 自动抓取后参数页显示 CAGR≈17.5%、有效税率≈25.4% 参考值与两个「参考历史」按钮，截图复核布局
- **可能影响的模块**：参数页（折现率区信用利差、预测假设区增长率/税率）；`/api/creditSpread` 新接口；RateFetcher 中债登解析（10Y 逻辑未动，回归测试覆盖）；不涉及估值计算核心逻辑与导出

---

## 变更 #31：版本 bump v1.1.8 → v1.1.9（2026-08-25）

- **原因**：变更 #30（参数参考值增强）改动了打包内容，本地 jar 与已发布 v1.1.8 Release 不一致；bump 版本号重新发版。
- **修改位置**：`pom.xml`、`run.bat`/`run.sh`（JAR 变量）、`README.md`（jar 名与版本号）；首页版本号动态化无需改
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.9 jar；启动冒烟 `/api/creditSpread` + 参数页参考值显示
- **可能影响的模块**：打包产物文件名、Release 资产；无业务逻辑变化

---

## 变更 #32：版本 bump v1.1.9 → v1.1.10（2026-08-25）

- **原因**：发版前冒烟验证发现 Bug #8（无会话访问 /params 时 302 重定向 URL 带 `;jsessionid`，welcome 页兜底渲染丢版本号）；修复后本地 jar 内容变化，bump 版本号重新发版。
- **修改位置**：
  - `pom.xml`：`<version>` 1.1.9 → 1.1.10
  - `run.bat` / `run.sh`：`JAR` 变量改为 `dcf-valuation-tool-1.1.10.jar`
  - `README.md`：jar 名与版本号同步为 1.1.10
- **测试方式**：`mvnw test` 全量通过；`mvnw package` 产出 1.1.10 jar；启动后 `curl -L /params` 重定向 URL 不再含 `;jsessionid`，首页显示 `v1.1.10`
- **可能影响的模块**：打包产物文件名、Release 资产；Session 跟踪策略改为仅 Cookie（影响所有页面跳转，回归覆盖首页/输入页/参数页跳转）

---

## 变更 #33：参数参考值增强功能说明（并入 v1.1.10 Release 说明）

- 信用利差自动获取（中债登同表计算，CN）、有效税率/营收 CAGR「参考历史」一键填入、ERP/永续增长率提示完善——详见变更 #30。
