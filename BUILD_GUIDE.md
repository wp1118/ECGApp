# ECGApp APK 构建指南

**版权所有 © 2025 woP All Rights Reserved**

---

## 方法1：使用 Android Studio（推荐）

### 步骤
1. **下载并安装 Android Studio**
   - 官网: https://developer.android.com/studio

2. **打开项目**
   - 启动 Android Studio
   - 选择 "Open an existing Android Studio project"
   - 选择 `ECGApp` 文件夹

3. **等待同步**
   - 首次打开会自动下载Gradle和依赖
   - 等待同步完成（可能需要几分钟）

4. **构建APK**
   - 点击菜单栏 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - 或者点击工具栏的绿色运行按钮

5. **获取APK**
   - 构建完成后，点击右下角的通知
   - 选择 "Show in Explorer" 查看APK文件
   - 文件位置：`app/build/outputs/apk/debug/app-debug.apk`

---

## 方法2：使用 Docker（无需安装Android Studio）

### 前提条件
- 安装 Docker Desktop
  - Windows/Mac: https://www.docker.com/products/docker-desktop
  - Linux: `sudo apt install docker.io`

### 步骤

#### Linux/Mac
```bash
cd ECGApp
chmod +x build-docker.sh
./build-docker.sh
```

#### Windows
```cmd
cd ECGApp
build-docker.bat
```

构建完成后，APK文件位于 `app/build/outputs/apk/debug/app-debug.apk`

---

## 方法3：使用 GitHub Actions（云端构建）

### 步骤
1. **Fork 或创建 GitHub 仓库**
   - 将代码上传到GitHub仓库

2. **触发构建**
   - GitHub Actions会自动触发构建
   - 或者点击 Actions → Build APK → Run workflow

3. **下载APK**
   - 构建完成后，在 Actions 页面找到Artifacts
   - 下载 `ECGApp-APK` 文件
   - 解压后得到 `app-debug.apk`

---

## 方法4：使用命令行（需要Java和Android SDK）

### 前提条件
- JDK 17 或更高版本
- Android SDK
- 设置环境变量 `ANDROID_HOME`

### 步骤
```bash
cd ECGApp

# Linux/Mac
chmod +x gradlew
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

---

## APK 安装

构建成功后，将APK传输到安卓设备：

1. **通过USB**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **通过文件传输**
   - 将APK复制到手机存储
   - 在手机上点击安装
   - 可能需要允许"未知来源"安装

3. **通过邮件/微信**
   - 发送APK文件给自己
   - 在手机上接收并安装

---

## 常见问题

### Q: 构建失败，提示Gradle版本不兼容
**A:** 在 Android Studio 中点击 `File` → `Sync Project with Gradle Files`

### Q: Docker构建很慢
**A:** 首次拉取镜像需要几分钟，请耐心等待。后续构建会更快。

### Q: 安装时提示"解析包错误"
**A:** 
- 确保APK文件完整传输
- 检查安卓版本是否 >= Android 5.0 (API 21)
- 尝试重新构建

### Q: 应用闪退
**A:** 
- 检查是否授予存储权限
- 尝试清理应用数据后重新打开
- 查看logcat错误日志

---

## 技术信息

| 项目 | 说明 |
|-----|------|
| 应用名称 | 心电图绘制 - woP |
| 包名 | com.ecg.drawer |
| 最低Android版本 | Android 5.0 (API 21) |
| 目标Android版本 | Android 13 (API 33) |
| 开发语言 | Java |
| 版权所有 | woP © 2025 |

---

## 需要帮助？

如有构建问题，请检查：
1. 代码是否完整下载
2. 网络连接是否正常（需要下载依赖）
3. 磁盘空间是否充足（至少需要2GB）

**开发者: woP**
**版权所有 © 2025 woP All Rights Reserved**
