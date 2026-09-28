# 打牌助手设计说明

本目录描述当前基于云端 VLM 和文本 LLM 的牌局决策助手。

## 当前架构

```text
CaptureService
  -> 截取整屏
  -> GameTurnCoordinator 压缩为 JPEG
  -> GameModule.recognizer
  -> 云端 VLM 返回牌局状态
  -> 跑胡子：PaohuziRules 计算合法动作
  -> AdviceEngine 请求文本 LLM
  -> GameAdviceOverlay 展示结果
```

识别不依赖本地 OCR，也不要求用户标定区域。VLM 直接看整张截图。

## 模块协议

`GameModule` 由三部分组成：

- `id` / `displayName`
- `recognizer`：实现 `BoardRecognizer`，把整屏 JPEG 转成 `GameState`
- `promptPolicy`：把 `GameState` 转成系统提示词和用户提示词

跑胡子模块额外使用本地 `PaohuziRules`。斗地主目前是纯提示词模式。

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

斗地主使用 `VlmPromptGameModule`：

- `assets/game/doudizhu/eyes.md`：看牌提示词
- `assets/game/doudizhu/advisor.md`：决策提示词
- `assets/game/doudizhu/rules.md`：规则说明

斗地主没有本地牌型校验，属于提示词模式。模型必须严格依据画面信息输出，信息不足时应选择保守打法或说明缺失信息。

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
