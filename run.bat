@echo off
cd /d %~dp0
if not exist .venv\Scripts\python.exe (
    echo [ERROR] 未找到虚拟环境，请先运行: python -m venv .venv
    pause
    exit /b 1
)
.venv\Scripts\python.exe -m streamlit run app.py
