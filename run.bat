@echo off
cd /d %~dp0
if not exist target\dcf-valuation-tool-1.0.0.jar (
    echo [INFO] 首次启动请先编译: mvn package -DskipTests
)
mvn spring-boot:run