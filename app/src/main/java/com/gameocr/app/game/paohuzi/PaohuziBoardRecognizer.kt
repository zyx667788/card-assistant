package com.gameocr.app.game.paohuzi

import com.gameocr.app.game.core.BoardRecognizer
import com.gameocr.app.game.core.BoardZone
import com.gameocr.app.game.core.BoardZoneRole
import com.gameocr.app.game.core.GameState
import com.gameocr.app.game.core.TileTextSpan

/**
 * 从一屏 OCR 文本还原跑胡子牌局。
 *
 * 识别策略（MVP 阶段不训练专用检测模型）：
 * 1. 按区域裁剪并解析出手牌、我的亮牌、桌面已出的牌；
 * 2. 「操作提示」区域里若恰好出现一种牌，就认为那是别人刚打出、等我决定的牌；
 * 3. 牌数明显不合理时返回 null 或写入备注，交给界面让用户核对修正。
 */
class PaohuziBoardRecognizer(
    private val parser: PaohuziOcrParser = PaohuziOcrParser(),
) : BoardRecognizer {

    override suspend fun recognize(
        spans: List<TileTextSpan>,
        zones: List<BoardZone>,
        imageWidth: Int,
        imageHeight: Int,
        screenshotJpeg: ByteArray?,
    ): GameState? {
        if (zones.isEmpty()) return null

        val hand = zones.withRole(BoardZoneRole.SELF_HAND)
            .flatMap { parser.tilesInZone(spans, it, imageWidth, imageHeight) }
        if (hand.size < MIN_PLAUSIBLE_HAND) return null

        val meldZoneTiles = zones.withRole(BoardZoneRole.SELF_MELDS)
            .flatMap { parser.tilesInZone(spans, it, imageWidth, imageHeight) }
        val melds = PaohuziMeldInference.infer(meldZoneTiles)

        val tableDiscards = zones.withRole(BoardZoneRole.TABLE_DISCARDS)
            .flatMap { parser.tilesInZone(spans, it, imageWidth, imageHeight) }

        val opponents = listOf(
            BoardZoneRole.OPPONENT_LEFT_MELDS to "左家",
            BoardZoneRole.OPPONENT_RIGHT_MELDS to "右家",
        ).mapNotNull { (role, label) ->
            val tiles = zones.withRole(role)
                .flatMap { parser.tilesInZone(spans, it, imageWidth, imageHeight) }
            if (tiles.isEmpty()) null else PaohuziOpponentView(label, PaohuziMeldInference.infer(tiles))
        }

        val actionText = zones.withRole(BoardZoneRole.ACTION_PROMPT)
            .map { parser.textInZone(spans, it, imageWidth, imageHeight) }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val remainingText = zones.withRole(BoardZoneRole.REMAINING_COUNT)
            .joinToString(" ") { parser.textInZone(spans, it, imageWidth, imageHeight) }

        val notes = buildNotes(
            handSize = hand.size,
            meldZoneTileCount = meldZoneTiles.size,
            melds = melds,
        )

        return PaohuziState(
            hand = hand,
            melds = melds,
            // 只有在提示文字里恰好出现一种牌时，才认定它一定是「刚打出的那张牌」。
            incomingTile = PaohuziTileCodec.parse(actionText).distinct().singleOrNull(),
            tableDiscards = tableDiscards,
            opponents = opponents,
            remainingTileCount = REMAINING_COUNT_PATTERN.find(remainingText)?.value?.toIntOrNull(),
            actionHint = actionText.takeIf { it.isNotBlank() },
            notes = notes,
        )
    }

    private fun buildNotes(
        handSize: Int,
        meldZoneTileCount: Int,
        melds: List<PaohuziMeld>,
    ): List<String> {
        val notes = mutableListOf<String>()
        if (handSize > MAX_PLAUSIBLE_HAND) {
            notes += "手牌识别到 $handSize 张，可能有多认的牌，请核对"
        }
        val consumed = melds.sumOf { it.tiles.size }
        if (meldZoneTileCount > consumed) {
            notes += "亮牌区有 ${meldZoneTileCount - consumed} 张牌没能组成完整的顺/坎/提，请核对"
        }
        return notes
    }

    private fun List<BoardZone>.withRole(role: BoardZoneRole): List<BoardZone> =
        filter { it.role == role }

    private companion object {
        /** 少于两张不可能构成任何牌局，直接判定识别失败。 */
        const val MIN_PLAUSIBLE_HAND = 2

        /** 远超庄家 21 张说明混入了别的内容。 */
        const val MAX_PLAUSIBLE_HAND = 21

        val REMAINING_COUNT_PATTERN = Regex("\\d+")
    }
}
