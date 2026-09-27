package com.gameocr.app.game.paohuzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlmBoardParserTest {

private fun payload(
hand: List<String> = listOf("一", "二", "三", "四", "五"),
) = VlmBoardPayload(hand = hand)

@Test
fun `parses hand tiles in order`() {
val state = VlmBoardParser.parse(payload())!!
assertEquals(
listOf(
PaohuziTile(1, TileCase.SMALL),
PaohuziTile(2, TileCase.SMALL),
PaohuziTile(3, TileCase.SMALL),
PaohuziTile(4, TileCase.SMALL),
PaohuziTile(5, TileCase.SMALL),
),
state.hand,
)
}

@Test
fun `normalizes lookalike characters from vlm output`() {
val state = VlmBoardParser.parse(payload(hand = listOf("壹", "叁", "陸")))!!
assertEquals(
listOf(
PaohuziTile(1, TileCase.BIG),
PaohuziTile(3, TileCase.BIG),
PaohuziTile(6, TileCase.BIG),
),
state.hand,
)
}

@Test
fun `returns null when hand is too small`() {
assertNull(VlmBoardParser.parse(payload(hand = listOf("一"))))
assertNull(VlmBoardParser.parse(payload(hand = emptyList())))
}

@Test
fun `infers meld kinds from groups instead of trusting labels`() {
val state = VlmBoardParser.parse(
payload().copy(
selfMelds = listOf(
listOf("五", "五", "五"),
listOf("壹", "贰", "叁"),
listOf("六", "六", "六", "六"),
),
),
)!!
assertEquals(3, state.melds.size)
assertEquals(PaohuziMeldKind.PUNG, state.melds[0].kind)
assertEquals(PaohuziMeldKind.CHOW, state.melds[1].kind)
assertEquals(PaohuziMeldKind.KONG, state.melds[2].kind)
}

@Test
fun `parses incoming tile only when it is a single tile`() {
val withIncoming = VlmBoardParser.parse(payload().copy(incomingTile = "六"))!!
assertEquals(PaohuziTile(6, TileCase.SMALL), withIncoming.incomingTile)

val ambiguous = VlmBoardParser.parse(payload().copy(incomingTile = "六七"))!!
assertNull(ambiguous.incomingTile)

val none = VlmBoardParser.parse(payload().copy(incomingTile = null))!!
assertNull(none.incomingTile)
}

@Test
fun `parses opponents and skips empty seats`() {
val state = VlmBoardParser.parse(
payload().copy(
opponentLeftMelds = listOf(listOf("壹", "壹", "壹")),
opponentRightMelds = emptyList(),
),
)!!
assertEquals(1, state.opponents.size)
assertEquals("左家", state.opponents[0].seatLabel)
assertEquals(PaohuziMeldKind.PUNG, state.opponents[0].melds[0].kind)
}

@Test
fun `carries global observation into visual context`() {
val state = VlmBoardParser.parse(
payload().copy(globalObservation = "左家攻势很猛，已亮出三组"),
)!!
assertEquals("左家攻势很猛，已亮出三组", state.visualContext)
assertTrue(state.toPromptText().contains("左家攻势很猛，已亮出三组"))
}

@Test
fun `warns when hand is implausibly large`() {
val state = VlmBoardParser.parse(payload(hand = List(25) { "一"}))!!
assertEquals(25, state.hand.size)
assertTrue(state.notes.any { it.contains("25 张")})
}
}
