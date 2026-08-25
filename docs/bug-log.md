# Bug 修复记录

> 规范：每次修 bug 记录 4 要素 —— 原因 / 修改位置 / 测试方式 / 可能影响到的模块。
> 格式：按日期追加，修复后更新状态。

---

## Bug #1：CSV 导入后年份未升序 + "营收"列未识别（测试失败）

- **状态**：已修复（2026-08-25 12:10）
- **原因**（三个子问题）：
  1. 模板/理杏仁导出的 CSV 数据行常为倒序（最新年份在前），解析器按行序填充数组，未排序；
  2. 首次修复时文本替换未包含原方法结束括号，引入多余 `}`（已清理）；
  3. 表头"营收"未被 `mapColumns` 识别（只匹配"营业总收入/营业收入"），导致 revenue 列解析为 NaN；
  4. 测试断言数据笔误：模板 2022 行 EBIT=160，断言误写成 140（140 是税前利润）。
- **修改位置**：
  - `src/main/java/com/dcf/data/csv/CsvImporter.java`：新增 `sortByYear()` 并接入 `parseText()`；`mapColumns()` 增加 `营收` 关键词
  - `src/test/java/com/dcf/data/csv/CsvImporterTest.java`：修正 ebit 断言值
- **测试方式**：
  - `mvn test`（全量 26 个用例）；关键用例 `CsvImporterTest#testParseTemplateCsv`、`testParseLixingerStyleColumns`
- **可能影响的模块**：
  - CSV 导入链路（理杏仁兜底、美股/手动模板）→ 历史 FCF 序列 → DCF 估值结果
  - 不影响自动抓取（SinaFinanceParser 内部已排序）与模型层
- **教训**：替换代码用整段匹配时注意边界括号；测试数据与断言必须人工核算一遍再提交。
---

## Bug #2：新浪财报报告期格式不匹配，年报全被过滤（接口实测发现）

- **状态**：已修复（2026-08-25 12:20）
- **原因**：新浪 getFinanceReport2022 接口返回的报告期为 8 位数字（如 20251231），
  而过滤逻辑按带横杠格式（endsWith("12-31")）判断年报，导致 annualDates 为空。
- **修改位置**：`src/main/java/com/dcf/data/sina/SinaFinanceParser.java#extract()`，
  判断改为同时兼容 `12-31` 与 `1231` 两种后缀
- **测试方式**：`mvn test -Dtest=LiveApiSmokeTest`（真实网络抓取茅台 600519，
  验证 10 年历史数据 2016-2025 全部落地）；单元用例 `SinaFinanceParserTest#testExtractAnnualOnly`
- **可能影响的模块**：A 股自动抓取链路（历史财务 → FCF → DCF 估值）；CSV/手动不受影响
- **验证结果**：茅台 2024 年 FCF = 经营现金流 924.6 亿 − 资本开支 46.8 亿 = 877.8 亿（与年报一致）

## Bug #3：东财 K 线 fields2 字段不足导致解析越界（接口实测发现）

- **状态**：已修复（2026-08-25 12:22）
- **原因**：请求只传 f51,f53 两个字段，但东财 klines 行按固定列返回，
  split 后不足 3 列，取收盘价下标 [2] 越界（Index 2 out of bounds for length 2）。
- **修改位置**：`src/main/java/com/dcf/data/eastmoney/EastMoneyClient.java#fetchWeeklyCloses()`，
  fields2 补全为 f51-f61,f116（与 akshare 一致）
- **测试方式**：`mvn test -Dtest=LiveApiSmokeTest`，验证茅台与沪深300 周线均可取收盘价，
  Beta=0.983（3 年周线口径）
- **可能影响的模块**：Beta 自动计算（参数页 CAPM）；个股/指数历史行情展示
---

## Bug #4：手动输入年份倒序被拒绝（表单校验与用户习惯冲突）

- **状态**：已修复（2026-08-25 13:40）
- **原因**：用户习惯按最新年份在前填写（与 CSV 模板一致），
  但 buildHistory 要求严格递增，导致倒序输入直接报"年份必须严格递增且不重复"。
- **修改位置**：`src/main/java/com/dcf/web/PageController.java#buildHistory()`，
  改为任意顺序输入 → 内部按年份升序重排 → 再校验去重与连续性
- **测试方式**：`curl POST /input/manual` 模拟美股倒序输入（2024,2023,2022），
  验证 redirect 到 /params 且公司数据正确展示；单元层由 CsvImporter 排序用例覆盖同类逻辑
- **可能影响的模块**：A股/美股手动输入链路（数据组装 → 参数页 → 估值）
---

## Bug #5：参数页按「百分比」展示与提交，后端按「小数」校验，浏览器直接提交必报错

- **状态**：已修复（2026-08-25 14:00，J6 冒烟测试发现）
- **原因**：params.html 所有百分比输入框（Rf/ERP/手动折现率/税率/两段增长率）按百分比展示并原样提交
  （如 Rf 填 1.70），而后端 `PageController#require(rf, 0.0, 0.10)` 按小数口径校验，
  前端 `validateParams()` 只校验「折现率>永续增长率」不查范围 → 浏览器真实操作 100% 被拒。
- **修改位置**：`src/main/resources/templates/params.html`：表单提交前（submit 监听器）
  将 rf/erp/manualRate/taxRate/gFirst/gTerminal 六个字段除以 100 再提交；校验逻辑保留在换算前执行
- **测试方式**：
  - curl 模拟浏览器换算后的小数提交（rf=0.017, erp=0.055, gFirst=0.08, gTerminal=0.03, taxRate=0.25），
    全链路 auto→params→result→excel/report 均 200
  - 修复前以百分比值提交 → 400/报错「无风险利率 超出允许范围」，修复后浏览器路径（JS 换算）正常
- **可能影响的模块**：参数页提交 → 估值计算 → 结果页 → Excel/Markdown 导出（所有市场路径）
- **教训**：单位约定（% vs 小数）必须在前后端边界显式换算并加注释；冒烟测试应模拟浏览器真实提交值，
  而不是直接传后端口径的值