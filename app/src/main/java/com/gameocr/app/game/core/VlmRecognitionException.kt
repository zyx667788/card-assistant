package com.gameocr.app.game.core

/** VLM 识别失败时的异常，由调用方转成面向用户的错误提示。 */
class VlmRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)
