package com.gameocr.app.game.core

import kotlinx.serialization.Serializable

/**
 * 牌局识别方式。
 *
 * OCR 路径依赖用户预先标定各区域，再对区域做文字识别；
 * VLM 路径把整张截图直接发给云端视觉模型，由模型自己看全局、出结构化牌局，
 * 不需要标定区域。
 */
@Serializable
enum class GameRecognizerKind(val displayName: String) {
    OCR("本地 OCR"),
    VLM("云端 VLM"),
}
