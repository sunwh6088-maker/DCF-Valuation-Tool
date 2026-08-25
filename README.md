# DCF-Valuation-Tool — Python 版（WIP，尚未完成）

> ⚠️ **开发中分支，请勿直接运行**：本分支只完成了核心计算模块 `dcf/`（模型与参数校验），
> UI 层（Streamlit `app.py`）尚未实现，`run.bat` 暂时无法启动。
>
> 当前可用版本是 `main` 分支的 **Java 版**（Spring Boot + Thymeleaf，功能完整）：
> ```bash
> git checkout main
> run.bat        # Windows
> ./run.sh       # Linux/macOS
> ```

## 本分支现状

- ✅ `dcf/model.py`：两阶段 DCF 模型（前 5 年固定增长 + 后 5 年线性过渡至永续增长）
- ✅ `dcf/config.py`、`dcf/validation.py`：参数校验（折现率 > 永续增长率等）
- ✅ `tests/`：JUnit 风格单元测试（`pytest tests/`）
- ❌ Streamlit UI（`app.py`）、数据抓取（akshare）、Excel/Markdown 导出 —— 待实现

## 开发计划

Python 版将按 Java 版的架构与口径重做，两版本并行维护：
A 股自动抓取（akshare）+ 理杏仁 CSV 兜底 + 美股手动输入 + 两阶段 DCF + CAPM 折现率 + 敏感性热力图。

## 许可

MIT（与 main 分支相同）。
