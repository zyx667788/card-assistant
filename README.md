# 打牌助手

一个基于 Android 屏幕识别与 LLM 的牌局决策助手。应用读取当前牌桌画面，识别手牌和桌面信息，再由本地规则引擎确认合法动作，最后让模型在这些合法动作里给出建议。

首期玩法为湖南跑胡子，后续不同纸牌玩法按独立模块接入。

## 当前能力

- 悬浮球手动触发一次分析，不自动点击、不代打。
- 支持湖南跑胡子牌面、组合、胡牌、听牌和吃碰提判定。
- 支持手牌、亮牌、对手亮牌、桌面、操作提示、剩余牌数等区域标定。
- OCR 结果先还原成结构化牌局，再交给模型决策。
- 本地规则引擎先过滤非法动作，模型只在合法范围内给出建议。
- 结果悬浮卡展示识别结果、建议动作、理由和备选打法。
- 支持重新识别和开始新一局；当局建议历史会持久化保存。
- 牌类玩法通过模块接口扩展，不需要重写截图、OCR、悬浮窗和决策链路。

## 支持玩法

| 玩法 | 状态 | 说明 |
| --- | --- | --- |
| 湖南跑胡子 | 可用 MVP | 支持OCR辅助识别、规则校验和出牌/吃碰建议 |

## 使用方式

1. 启动 Android 应用的截屏服务。
2. 长按悬浮球，选择「牌局区域标定」，依次框选当前玩法的识别区域。
3. 长按悬浮球，切换到「打牌助手」。
4. 进入牌局，在需要决策时单击悬浮球。
5. 在结果卡里核对识别文本，并参考建议动作、理由和备选打法。
6. 开始新一局时点击「新开局」，清空当前模块的决策历史。

首次使用必须先完成区域标定。未标定区域时，助手会提示先标定，避免使用错误的默认坐标。

## 工作流

```text
悬浮球单击
  -> 截取当前屏幕
  -> OCR 识别文字与位置
  -> 裁切并解析牌局区域
  -> 生成结构化 GameState
  -> 本地规则引擎计算合法动作
  -> LLM 在合法动作内选择并说明理由
  -> 悬浮结果卡展示建议
```

## 模块结构

```text
game/core/        牌类模块协议、牌局状态、决策引擎接口
game/paohuzi/     湖南跑胡子：牌面、规则、识别、提示词
game/advice/      OpenAI 兼容接口的决策请求与解析
game/integration/ Android OCR 结果到领域层的桥接
game/session/     当局状态、历史持久化、分析编排
game/ui/          牌局建议悬浮卡
```

新增玩法时：

1. 新建 `game/<玩法>/` 包。
2. 实现 `GameModule`、`BoardRecognizer` 和 `PromptPolicy`。
3. 在 `di/GameModuleBindings.kt` 中注册模块。
4. 在 `app/src/main/assets/game/<玩法>/rules.md` 中放置可编辑的地区规则说明。

详细设计见 [docs/game-assistant/README.md](docs/game-assistant/README.md)。

## 构建

环境要求：

- JDK 17
- Android SDK 35
- Gradle Wrapper 8.10.2

```bash
git submodule update --init --recursive
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Windows 本地开发注意事项见 [docs/game-assistant/LOCAL-DEV.md](docs/game-assistant/LOCAL-DEV.md)。

当前仓库在 Windows 上会遇到 `llama-android` 的 Visual Studio/Vulkan 构建限制，完整 APK 由 GitHub Actions 构建。

## 设计边界

- 只读取屏幕并给出建议，不自动点击、不自动出牌。
- 规则引擎只固化跨地区成立的结构性规则。
- 起胡息数、计息方式、是否允许吃等地区差异放在规则的 Markdown 说明中，便于按地区调整。
- OCR 信息不足时优先提示用户核对，不编造不可见牌。
- 模型建议必须落在本地规则引擎确认的合法动作范围内。

## 规则与合规

不同地区跑胡子规则差异较大，应用不把地方规则写死在界面文案里。规则说明和提示词可以直接调整，结构判定由 `PaohuziRules` 负责。

本项目仅供学习、研究和本地牌局复盘使用。使用前请确认符合当地法律、游戏服务条款和平台规则。

## 来源与许可

本项目基于 [ciddwd/overlay-translator](https://github.com/ciddwd/overlay-translator) 的 Android 截屏、OCR、悬浮窗和模型调用基础设施继续开发。

代码采用 [Apache-2.0](LICENSE)。第三方组件和模型许可保留各自原协议。
