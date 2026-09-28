# 本地开发环境

当前项目是纯 Android/Kotlin 应用，不再包含本地 OCR、翻译、TTS、llama.cpp 或 Git submodule。

## 需要的工具

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | platform 35、build-tools 35.0.0 |
| Gradle Wrapper | 8.10.2 |

`local.properties` 示例：

```properties
sdk.dir=C\:\\Users\\<用户名>\\android-sdk
```

## Windows 常见问题

### JDK AF_UNIX 临时目录

如果 Gradle 报：

```text
java.io.IOException: Unable to establish loopback connection
```

设置：

```powershell
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/jtmp"
```

### 中文项目路径

AGP 对中文路径可能报错。仓库已在 `gradle.properties` 中设置：

```properties
android.overridePathCheck=true
```

如果单元测试出现 `ClassNotFoundException`，可以从 ASCII 路径联接运行：

```powershell
New-Item -ItemType Junction -Path "C:\paicard" -Target "<中文项目目录>"
cd C:\paicard
```

### Gradle 下载慢

可以把 Gradle 8.10.2 发行包放入 wrapper 缓存，或使用腾讯镜像下载。

## 常用命令

```powershell
# 编译 Kotlin
.\gradlew.bat :app:compileDebugKotlin

# 运行全部 JVM 单测
.\gradlew.bat :app:testDebugUnitTest

# 构建 debug APK
.\gradlew.bat :app:assembleDebug
```

## CI

`.github/workflows/ci.yml` 会运行：

```text
testDebugUnitTest
assembleDebug
lint
```

Debug APK 会作为 artifact 上传。
