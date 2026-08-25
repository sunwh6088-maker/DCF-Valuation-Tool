@echo off
chcp 65001 >nul
rem ============================================================
rem  DCF-Valuation-Tool 启动脚本（Windows）
rem  用法：
rem    run.bat            -> 开发模式（mvn spring-boot:run，自动编译）
rem    run.bat jar        -> 运行已打包 jar（java -jar，需先 mvn package）
rem    run.bat 8502       -> 开发模式 + 自定义端口（8501 被占用时使用）
rem    run.bat jar 8502   -> 运行 jar + 自定义端口
rem  代理（可选，仅 FRED 等境外数据源需要；别人使用无需任何代理配置）：
rem    set HTTPS_PROXY=http://127.0.0.1:7890
rem    run.bat
rem ============================================================
cd /d %~dp0

set "PROXY_HOST="
set "PROXY_PORT="
if defined HTTPS_PROXY (
    for /f "tokens=3,4 delims=:/" %%a in ("%HTTPS_PROXY%") do (
        set "PROXY_HOST=%%a"
        set "PROXY_PORT=%%b"
    )
)
if "%PROXY_HOST%"=="" if defined HTTP_PROXY (
    for /f "tokens=3,4 delims=:/" %%a in ("%HTTP_PROXY%") do (
        set "PROXY_HOST=%%a"
        set "PROXY_PORT=%%b"
    )
)
if not "%PROXY_HOST%"=="" echo [INFO] 使用代理: %PROXY_HOST%:%PROXY_PORT%
if "%PROXY_PORT%"=="" set "PROXY_PORT=80"

set "PROXY_ARGS="
if not "%PROXY_HOST%"=="" set "PROXY_ARGS=-Dhttps.proxyHost=%PROXY_HOST% -Dhttps.proxyPort=%PROXY_PORT% -Dhttp.proxyHost=%PROXY_HOST% -Dhttp.proxyPort=%PROXY_PORT%"

rem 可选端口：run.bat [jar] [port]
set "PORT_ARG="
if /i "%~1"=="jar" (
    if not "%~2"=="" set "PORT_ARG=--server.port=%~2"
) else (
    if not "%~1"=="" set "PORT_ARG=--server.port=%~1"
)
set "DEV_ARGS="
if not "%PORT_ARG%"=="" set "DEV_ARGS=-Dspring-boot.run.arguments=%PORT_ARG%"

set "JAR=target\dcf-valuation-tool-1.1.1.jar"

if /i "%~1"=="jar" goto :run_jar

if exist "%JAR%" (
    echo [INFO] 发现已打包 jar，直接运行（代码有更新时请先 mvn package）
    java %PROXY_ARGS% -jar "%JAR%" %PORT_ARG%
    goto :eof
)
echo [INFO] 开发模式启动（首次会自动编译）...
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=%PROXY_ARGS%" %DEV_ARGS%
goto :eof

:run_jar
if not exist "%JAR%" (
    echo [INFO] 未找到 jar，先打包（跳过测试）...
    mvn -q -DskipTests package
    if errorlevel 1 exit /b 1
)
java %PROXY_ARGS% -jar "%JAR%" %PORT_ARG%
goto :eof
