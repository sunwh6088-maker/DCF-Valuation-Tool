@echo off
chcp 65001 >nul
rem ============================================================
rem  DCF-Valuation-Tool 启动脚本（Windows）
rem  用法：
rem    run.bat            -> 开发模式（自动编译，优先使用内置 mvnw.cmd，无需装 Maven）
rem    run.bat jar        -> 运行已打包 jar（java -jar，需先 mvn package）
rem    run.bat 8502       -> 开发模式 + 自定义端口（8501 被占用时使用）
rem    run.bat jar 8502   -> 运行 jar + 自定义端口
rem  代理（可选，仅 FRED 等境外数据源需要；别人使用无需任何代理配置）：
rem    set HTTPS_PROXY=http://127.0.0.1:7890
rem    run.bat
rem ============================================================
cd /d %~dp0

rem ---------- Java 检查（要求 JDK 21+，缺 Java 时给友好提示） ----------
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] 未检测到 Java。请先安装 JDK 21：https://adoptium.net/temurin/releases/
    pause
    exit /b 1
)
set "JAVA_VER="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%v"
set "JAVA_VER=%JAVA_VER:"=%"
set "JAVA_MAJOR=0"
if defined JAVA_VER for /f "tokens=1 delims=." %%m in ("%JAVA_VER%") do set "JAVA_MAJOR=%%m"
if %JAVA_MAJOR% LSS 21 (
    echo [ERROR] 需要 JDK 21 及以上版本，当前检测到 Java %JAVA_VER%
    echo         请安装 JDK 21：https://adoptium.net/temurin/releases/
    pause
    exit /b 1
)

rem ---------- Maven 选择：优先内置 wrapper（无需手动安装 Maven） ----------
set "MVN=mvn"
if exist "mvnw.cmd" set "MVN=call mvnw.cmd"

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

set "JAR=target\dcf-valuation-tool-1.1.3.jar"

if /i "%~1"=="jar" goto :run_jar

if exist "%JAR%" (
    echo [INFO] 发现已打包 jar，直接运行（代码有更新时请先 mvn package）
    java %PROXY_ARGS% -jar "%JAR%" %PORT_ARG%
    goto :eof
)
echo [INFO] 开发模式启动（首次会自动编译）...
%MVN% spring-boot:run "-Dspring-boot.run.jvmArguments=%PROXY_ARGS%" %DEV_ARGS%
goto :eof

:run_jar
if not exist "%JAR%" (
    echo [INFO] 未找到 jar，先打包（跳过测试）...
    %MVN% -q -DskipTests package
    if errorlevel 1 exit /b 1
)
java %PROXY_ARGS% -jar "%JAR%" %PORT_ARG%
goto :eof
