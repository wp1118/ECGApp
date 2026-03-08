@echo off
chcp 65001 > nul
echo ========================================
echo ECGApp APK Docker 构建脚本
echo 版权: woP 2025
echo ========================================

REM 检查Docker是否安装
docker --version > nul 2>&1
if errorlevel 1 (
    echo 错误: Docker未安装，请先安装Docker
    pause
    exit /b 1
)

echo 正在拉取Android构建环境镜像...
docker pull mingc/android-build-box:latest

echo 正在构建APK...
docker run --rm ^
    -v "%cd%:/project" ^
    -w /project ^
    mingc/android-build-box:latest ^
    bash -c "chmod +x gradlew && ./gradlew assembleDebug"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo 构建成功!
    echo APK文件位置: app/build/outputs/apk/debug/app-debug.apk
    echo ========================================
) else (
    echo.
    echo ========================================
    echo 构建失败，请检查错误信息
    echo ========================================
)

pause
