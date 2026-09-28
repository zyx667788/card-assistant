# 打牌助手设计说明

本目录描述当前基于云端 VLM 和文本 LLM 的牌局决策助手。

## 当前架构

```text
CaptureService
  -> 截取整屏
  -> GameTurnCoordinator 压缩为 JPEG
  -> GameModule.recognizer
  -> 云端 VLM 返回牌局状态
  -> 跑胡子 PaohuziRules / 斗地主 DoudizhuRules 分别计算合法动作
  -> AdviceEngine 请求文本 LLM
  -> GameAdviceOverlay 展示结果
```

识别不依赖本地 OCR，也不要求用户标定区域。VLM 直接看整张截图。

## 模块协议

`GameModule` 由三部分组成：

- `id` / `displayName`
- `recognizer`：实现 `BoardRecognizer`，把整屏 JPEG 转成 `GameState`
- `promptPolicy`：把 `GameState` 转成系统提示词和用户提示词

两个模块都先用本地规则引擎算好合法动作，再让模型在合法范围内选择：
跑胡子用 `PaohuziRules`，斗地主用 `DoudizhuRules`。

## 跑胡子

跑胡子识别流程：

1. 云端 VLM 接收截图和看牌提示词。
2. VLM 输出结构化 JSON。
3. `VlmBoardParser` 校验并转换 JSON。
4. `PaohuziRules` 计算胡牌、吃、碰、提和可出牌。
5. 决策提示词只允许模型从合法动作中选择。

关键文件：

- `PaohuziVlmBoardRecognizer`
- `VlmBoardParser`
- `PaohuziRules`
- `PaohuziPromptPolicy`

## 斗地主

斗地主识别流程与跑胡子同构：

1. VLM 按看牌提示词逐张输出带花色的牌（`黑桃7` / `7s`），并给出身份、上一手、剩余张数。
2. `DoudizhuVlmBoardParser` 转成 `DoudizhuState`。
3. `DoudizhuBoardValidator` 用 54 张牌的硬约束复核：同一张牌不能出现两次、每个点数最多
   4 张、手牌与已出牌不能重合。矛盾结果直接判为识别失败，要求重新截图。
4. `DoudizhuRules` 计算当前可出的合法牌型（含炸弹/火箭与需压过的上一手）。
5. 决策提示词只允许模型从合法动作里选，避免自创牌型或点错张数。

关键文件：

- `DoudizhuVlmBoardRecognizer`
- `DoudizhuVlmBoardParser`
- `DoudizhuBoardValidator`
- `DoudizhuRules`
- `DoudizhuPromptPolicy`

提示词全在 `assets/game/doudizhu/`：

- `assets/game/doudizhu/eyes.md`：看牌提示词
- `assets/game/doudizhu/advisor.md`：决策提示词
- `assets/game/doudizhu/rules.md`：规则说明

## 模型配置

VLM 和文本决策共用：

- Base URL
- API Key
- Model

模型必须支持图片输入。请求使用 OpenAI 兼容的 `chat/completions` 格式。

## 会话历史

- 每个模块保留最近 12 条建议。
- 历史保存在 Settings/DataStore。
- 服务重启后仍可继续读取。
- 「新开局」清空当前模块历史。

## 合规

应用只读取屏幕并给出建议，不自动点击、不自动出牌。截图会发送到用户配置的云端模型服务。
