# 打牌助手设计说明

本目录描述基于 Android 截屏、OCR、悬浮窗和模型调用能力构建的牌局决策助手。首期玩法为湖南跑胡子。

## 当前实现

- 悬浮球的独立「打牌助手」模式：单击截取一帧并分析。
- 「牌局区域标定」：按玩法定义依次框选手牌、亮牌、对手亮牌、桌面、操作提示和剩余牌数。
- OCR 文本先转换为纯数据文本块，再按区域裁切并还原牌局状态。
- 本地规则引擎先计算合法动作，模型只在合法动作范围内给出建议。
- 结果悬浮卡展示识别结果、建议动作、理由和备选打法。
- 支持重新识别、新开局和当局建议历史持久化。
- 未完成区域标定时拒绝分析，避免使用错误坐标给出误导建议。

## 分层

```text
game/core/        GameModule、BoardZone、GameState、AdviceEngine 等协议
game/paohuzi/     跑胡子的牌面、规则、识别器和提示词
game/advice/      OpenAI 兼容接口的请求与响应解析
game/integration/ Android OCR 文本块到领域层的桥接
game/session/     会话历史、持久化存储和分析编排
game/ui/          牌局建议悬浮卡
```

`game/core` 不依赖 Android。区域、文本块和牌局状态都是纯 Kotlin 类型，可以在普通 JVM 单元测试中验证。

## 模块接口

实现 `GameModule` 即可接入新玩法：

- `defaultZones`：区域角色的默认布局；首次标定只作为步骤元数据，不使用默认坐标直接分析。
- `recognizer`：接收 OCR 文本块、区域、图像宽高，还原 `GameState`。
- `promptPolicy`：把 `GameState` 渲染成系统提示词和用户提示词。

新增玩法：

1. 新建 `game/<玩法>/` 包。
2. 实现 `GameModule`、`BoardRecognizer` 和 `PromptPolicy`。
3. 在 `di/GameModuleBindings.kt` 中注册 `@IntoSet` 模块。
4. 在 `app/src/main/assets/game/<玩法>/rules.md` 中放置可编辑的地区规则。

## 跑胡子结构

| 文件 | 职责 |
| --- | --- |
| `PaohuziTile.kt` | 10 个点数、大小两门、80 张牌模型 |
| `PaohuziTileCodec.kt` | 牌面文字与牌对象互转，包含 OCR 形近字归一化 |
| `PaohuziMeld.kt` | 顺子、坎、提及其推断 |
| `PaohuziRules.kt` | 牌型分解、胡牌、听牌、吃碰提和可出牌判断 |
| `PaohuziState.kt` | 结构化牌局状态与模型可读文本 |
| `PaohuziOcrParser.kt` | 区域裁切、行聚合和阅读顺序 |
| `PaohuziBoardRecognizer.kt` | 组装 OCR 结果并产出 `PaohuziState` |
| `PaohuziPromptPolicy.kt` | 系统提示词、用户提示词和输出格式约束 |

## 规则边界

代码只固化跨地区成立的结构性规则：

- 顺子只在同一门内成立。
- 三张相同是坎，四张相同是提。
- 胡牌由若干顺子、坎、提与一对将组成。

不同地区的起胡息数、计息方式、是否允许吃等规则不写死在代码中，放在 `rules.md` 和规则配置里。模型只能从本地规则引擎确认的合法动作中选择。

## 会话与持久化

- 每个模块保留最近 12 条建议历史。
- 历史写入 Settings/DataStore，服务重启后仍可继续使用。
- 「新开局」只清空当前模块的历史。
- 区域坐标以每台设备的实际标定结果为准。

## 合规

应用只读取屏幕内容并给出建议，不自动点击、不自动出牌，也不实现代打行为。使用前需要确认符合当地法律、游戏服务条款和平台规则。
