# 打牌助手

一个基于 Android 截屏与云端视觉模型的牌局决策助手。应用把当前牌桌截图发送给支持视觉的 OpenAI 兼容模型，识别牌局状态；跑胡子会继续经过本地规则引擎校验合法动作，最后再由文本模型给出建议。

当前支持：

- 湖南跑胡子：VLM 整屏识别 + 本地规则校验 + LLM 决策
- 斗地主：纯提示词模式，VLM 看牌 + LLM 决策

## 工作方式

```text
点击悬浮球
  -> 截取当前屏幕
  -> 压缩为 JPEG
  -> 云端 VLM 识别牌局
  -> 构建结构化牌局状态
  -> 跑胡子：本地规则引擎计算合法动作
  -> 文本 LLM 在可见信息范围内给出建议
  -> 悬浮卡展示识别结果与建议
```

应用不自动点击、不自动出牌、不代打。

## 使用

1. 启动应用并授予悬浮窗权限。
2. 在首页填写 OpenAI 兼容的 `Base URL`、`API Key` 和视觉模型名称。
3. 启动截屏服务。
4. 进入牌局，点击悬浮球分析一次。
5. 在结果卡核对 VLM 识别出的牌局和建议。
6. 开始下一局时点击「新开局」，清空当前模块的决策历史。

## 模型配置

VLM 看牌和文本决策默认复用同一套 OpenAI 兼容配置：

- `Base URL`
- `API Key`
- `Model`

所选模型必须支持图片输入。纯文本模型可以完成决策请求，但不能完成整屏看牌。

## 模块结构

```text
game/core/        牌类模块、牌局状态、决策引擎接口
game/paohuzi/     湖南跑胡子：VLM 解析、牌面、规则和提示词
assets/game/doudizhu/ 斗地主看牌、决策和规则提示词
game/advice/      OpenAI 兼容决策请求和响应解析
game/session/     会话历史、持久化存储和分析编排
game/ui/          牌局建议悬浮卡
game/vlm/         通用纯提示词 VLM 模块
```

新增玩法时：

1. 新建 `game/<玩法>/` 包。
2. 实现 `GameModule`、`BoardRecognizer` 和 `PromptPolicy`。
3. 在 `di/GameModuleBindings.kt` 中注册模块。
4. 在 `app/src/main/assets/game/<玩法>/` 放置看牌、决策和规则提示词。

详细设计见 [docs/game-assistant/README.md](docs/game-assistant/README.md)。

## 构建

环境要求：

- JDK 17
- Android SDK 35
- Gradle Wrapper 8.10.2

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Windows 环境说明见 [docs/game-assistant/LOCAL-DEV.md](docs/game-assistant/LOCAL-DEV.md)。

## 设计边界

- 只读取屏幕并给出建议，不自动操作游戏。
- 跑胡子先在本地计算合法动作，模型只能在合法范围内选择。
- VLM 看不清或信息不足时必须明确说明，不能编造牌面。
- 斗地主目前是纯提示词模式，不包含本地规则引擎。
- 截图会发送到用户配置的云端模型服务。

## 合规

本项目仅供学习、研究和本地牌局复盘使用。使用前请确认符合当地法律、游戏服务条款和平台规则。

## 许可

代码采用 [Apache-2.0](LICENSE)。第三方组件和模型许可保留各自原协议。
