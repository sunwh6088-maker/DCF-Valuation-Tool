# DCF-Valuation-Tool Java 版架构设计

## 技术栈（机器已具备 JDK 21 + Maven 3.9.9）
- JDK 21 LTS + Spring Boot 3.3.x + Thymeleaf + Bootstrap 5
- 图表：ECharts（前端）；Excel 导出：Apache POI；JSON：Jackson
- 测试：JUnit 5 + Maven Surefire

## 数据接口（已从 akshare 1.18.94 源码确认，Java 直接调用）

### 1. 新浪财报（三表合一接口）
- URL: https://quotes.sina.cn/cn/api/openapi.php/CompanyFinanceService.getFinanceReport2022
- 参数: paperCode=sh600519, source=fzb|llb|lrb, type=0, page=1, num=1000
- 返回: result.data.report_date[]（报告期列表）; result.data.report_list[日期].data[]（科目, 含 item_title/item_value/publish_date）
- 科目名（中文）：经营活动产生的现金流量净额 / 购建固定资产、无形资产和其他长期资产支付的现金 /
  营业总收入 / 营业利润 / 利润总额 / 所得税费用 / 货币资金 / 短期借款 / 长期借款 /
  应付债券 / 一年内到期的非流动负债 / 租赁负债 / 少数股东权益
- 一次请求返回全部报告期全部科目，无需分页

### 2. 东财个股快照（股本/股价/名称）
- URL: https://push2.eastmoney.com/api/qt/stock/get
- 参数: secid={1|0}.{code}, fields=f57,f58,f43,f84,f85 等
- f58=股票名称, f43=最新价, f84=总股本, f85=流通股本, f116=总市值
- market 规则: 6 开头=1(沪)，其余=0(深)

### 3. 东财历史K线（Beta 计算用）
- URL: https://push2his.eastmoney.com/api/qt/stock/kline/get
- 参数: secid, klt=102(周线), fqt=1(前复权), beg/end, fields2=f51,f53(日期/收盘)
- 个股: secid={market}.{code}；沪深300 指数: secid=1.000300（用指数代替个股做 Beta 基准）

### 4. Beta 计算口径（默认，可手动覆盖）
- 3 年周对数收益率回归：beta = cov(个股, 沪深300) / var(沪深300)，不做无风险调整
- 数据不足 30 个周样本时返回 null，UI 提示手动输入

## 目录结构（与实际代码一致）
src/main/java/com/dcf/
- model/        纯计算（DcfModel/CAPM/敏感性/结果对象），无 IO 依赖
- data/         HTTP 数据层（DataService、HttpUtil）
  - sina/       新浪财报抓取 + 解析（getFinanceReport2022）
  - eastmoney/  东财快照（股本/股价/市值）+ K 线（Beta 用）
  - csv/        理杏仁/模板 CSV 导入（年份排序、编码探测）
  - cache/      JSON 24 小时缓存
  - model/      数据模型（CompanyData/HistoricalData/SnapshotData）
- service/      估值编排（ValuationService）、Beta 计算、Markdown 报告
- excel/        POI 多 sheet 导出（ExcelExporter）
- web/          Thymeleaf 页面 + JSON API + HttpSession 上下文
src/main/resources/
- templates/    index / input-a / input-us / params / result
- static/       bootstrap/echarts 本地化 + custom css/js
src/test/java/                 JUnit 5 单元测试

## 页面流程
1. 首页选市场（A股/美股）
2. A股：选数据源（自动抓取 / 理杏仁CSV / 手动输入）；美股：按清单手动输入
3. 参数页：无风险利率/Beta/ERP/税率/增长率分段/折现率(可覆盖)/永续增长率
4. 结果页：每股内在价值 vs 股价、安全边际、敏感性热力图(ECharts)、终值占比
5. 下载：Markdown 报告 + Excel（说明/原始数据/假设/预测/估值/敏感性 6 sheet）

## Git 提交计划（每模块测试通过后提交）
- J1 chore: migrate to Java/Spring Boot skeleton (删 Python 文件, pom, 骨架)
- J2 feat: DCF model core in Java + tests
- J3 feat: data layer (sina/eastmoney/csv/cache) + tests
- J4 feat: web UI pages + validation
- J5 feat: excel export + markdown report
- J6 test: e2e with real data (600519) + README（已完成，v1.0.0 tag）

## 风险与对策
- 新浪/东财接口字段变更 → 解析层集中隔离，失败给出中文提示并引导手动输入
- Maven 依赖下载慢 → 使用阿里云镜像 settings.xml（本地 C:\Maven 已有）
- 中文乱码 → 全链路 UTF-8，CSV 读入 utf-8-sig 兼容 BOM
- 输入校验 → 后端统一校验（复用 Python 版 validation 逻辑平移），前端 JS 预校验