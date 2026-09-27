# 本地开发环境

本项目是在 Windows 上开发、并在 GitHub Actions 上出包的。下面记录本机搭建时踩到的坑，
换机器时可以照着重来。

## 需要的工具

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| JDK | 17 | `llama-android` 模块要求 Java 17 工具链，且必须带 `jlink`（JetBrains Runtime 缺 jlink，不能用） |
| Android SDK | platform `android-35`、build-tools `35.0.0` | NDK 与 CMake 会在首次构建时自动下载 |
| Gradle | 8.10.2 | 仓库自带 wrapper |

`local.properties` 指向本机 SDK：

```properties
sdk.dir=C\:\\Users\\<用户名>\\android-sdk
```

## 三个必须知道的坑

### 1. JDK 的 AF_UNIX 管道在中英混排的短路径下会失败

症状：任何 Gradle 命令都报

```
java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
```

原因是 `%TEMP%` 为 8.3 短路径（形如 `C:\Users\DIBIAO~1\AppData\Local\Temp`）时，
JDK 在 Windows 上为 `Selector` 创建匿名 AF_UNIX 套接字会失败。`Selector.open()` 是
Gradle 等一切 JVM 工具的基础，所以表现为「所有 Java 工具都起不来」。

规避方式（三选一，任选其一即可）：

```powershell
# 方式一：让 JDK 把自动绑定套接字放到普通路径
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/jtmp"
```

- 方式二：把环境变量 `TEMP` / `TMP` 指向一个非短路径目录（例如 `C:\jtmp`）。
- 方式三：启用 Windows 的 8.3 短名以外的普通用户目录（换用长用户名）。

### 2. 项目路径不能含中文（中文路径会让单元测试加载不到类）

AGP 默认直接拒绝非 ASCII 路径。本仓库已在 `gradle.properties` 里打开
`android.overridePathCheck=true`，让编译可以继续。

但**单元测试仍然会失败**：编译产物存在，测试工作进程却报
`ClassNotFoundException: <你的测试类>`。解决办法是给项目建一个 ASCII 路径的目录联接，
从联接路径构建：

```powershell
New-Item -ItemType Junction -Path "C:\paicard" -Target "<中文项目目录>"
cd C:\paicard
.\gradlew.bat :app:testDebugUnitTest --tests com.gameocr.app.game.paohuzi.* --tests com.gameocr.app.game.advice.*
```

CI 上路径是 ASCII 的，不受这两条影响。

### 4. Windows 上打 APK 需要 Visual Studio Build Tools

`llama-android` 在 Windows 主机会调用 Visual Studio 的定位工具找 glslc（Vulkan 着色器编译器）。
本机没装 Visual Studio 时会报：

```
CMake Error: Visual Studio locator not found:
  C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe
```

处理方式：本地只做 `compileDebugKotlin` 和单元测试，出 APK 交给 GitHub Actions
（CI 跑在 Ubuntu 上，不会触发这条 Windows 分支）；如果需要本地打包，就安装 Visual Studio
Build Tools（含 C++ 生成工具）。

### 3. 首次构建会下载较多依赖

Gradle 官方分发源在国内可能被限速甚至卡住（表现为守护进程起来了但字节数不涨）。
可以改用镜像下载后放进 wrapper 缓存：

```powershell
curl.exe -L -o "$env:TEMP\gradle-8.10.2-bin.zip" `
  https://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip
# 解压到 %USERPROFILE%\.gradle\wrapper\dists\gradle-8.10.2-bin\<hash>\
```

## 常用命令

```powershell
# 编译 Kotlin（不打包 native）
.\gradlew.bat :app:compileDebugKotlin

# 跑牌局模块的单元测试
.\gradlew.bat :app:testDebugUnitTest --tests com.gameocr.app.game.paohuzi.* --tests com.gameocr.app.game.advice.*

# 出 debug APK（会编译 llama.cpp 原生库，较慢）
.\gradlew.bat :app:assembleDebug
```

`third_party/llama.cpp` 是 git submodule，克隆后需要：

```powershell
git submodule update --init --recursive
```
## 本轮已完成的验证

- `:app:compileDebugKotlin` 通过。
- `Paohuzi` + `GameAdvice` 单测通过。
- 悬浮菜单顺序/模式槽位单测通过。
- APK 仍由 GitHub Actions 构建；本地 Windows 不跑 `assembleDebug`。

