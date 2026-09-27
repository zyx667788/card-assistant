package com.gameocr.app.capture

internal fun shouldResizeProjection(
    currentWidth: Int,
    currentHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Boolean =
    targetWidth > 0 && targetHeight > 0 &&
        (currentWidth != targetWidth || currentHeight != targetHeight)
