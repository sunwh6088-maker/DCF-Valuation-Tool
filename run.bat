@echo off
chcp 65001 >nul
cd /d %~dp0
if not exist app.py (
    echo [ERROR] 本分支（python-prototype）仍在开发中（WIP）：
    echo         目前只提交了核心计算模块 dcf/，UI 层 app.py 尚未完成，暂时无法运行。
    echo         请切换到 main 分支使用 Java 版：git checkout main，然后按 README 运行 run.bat
    pause
    exit /b 1
)
if not exist .venv\Scripts\python.exe (
    echo [ERROR] 未找到虚拟环境，请先运行: python -m venv .venv
    pause
    exit /b 1
)
.venv\Scripts\python.exe -m streamlit run app.py
