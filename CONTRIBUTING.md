# 贡献指南

感谢你愿意参与 DCF-Valuation-Tool 的开发！本项目欢迎任何形式的贡献：报告问题、改进文档、提交代码、翻译等。

## 如何报告问题（Issues）

提交 Issue 前请先搜索是否已有人报告过类似问题。请尽量包含：

- 环境信息：操作系统、JDK 版本、Maven 版本
- 复现步骤：如何触发该问题
- 实际结果与预期结果
- 相关的报错信息（日志、截图）

## 如何提交代码

本项目采用标准的 GitHub Fork + Pull Request 流程：

1. Fork 本仓库到你的账号
2. Clone 到本地并创建功能分支：`git checkout -b feat/your-feature`
3. 修改代码，并确保：
   - 通过现有测试：`mvn test`（Java 版）；Python 版见 `python-prototype` 分支
   - 新增功能有对应的单元测试
   - 遵循现有代码风格
4. 提交并推送到你的 Fork：`git push origin feat/your-feature`
5. 在 GitHub 上发起 Pull Request，描述清楚改了什么、为什么改

## 提交信息规范

使用 Conventional Commits 风格：

- `feat:` 新功能
- `fix:` 修复 bug
- `docs:` 文档
- `test:` 测试
- `refactor:` 重构
- `chore:` 杂项（依赖、配置等）

示例：`feat: add sensitivity heatmap export`

## 开发环境

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
Spring Boot run app.py
```

## 代码规范

- 模型计算逻辑与 UI 分离（`model/` 目录只放纯计算函数）
- 所有用户输入必须经过类型与范围校验
- 注释和文档使用中文，变量和函数命名使用英文