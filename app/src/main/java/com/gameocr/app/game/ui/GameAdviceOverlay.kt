package com.gameocr.app.game.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.gameocr.app.R
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.game.core.AdviceAction
import com.gameocr.app.game.core.GameAdvice
import com.gameocr.app.game.session.GameTurnOutcome
import com.gameocr.app.overlay.DraggableOverlayWindow
import kotlinx.coroutines.CoroutineScope

/** 牌局识别核对与决策结果的独立悬浮卡。 */
class GameAdviceOverlay(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ioScope: CoroutineScope,
) {
    private val window = DraggableOverlayWindow(
        context,
        settingsRepository,
        ioScope,
        persistSharedSettings = false,
    ).apply {
        widthDp = 360
        heightDp = 440
        theme = com.gameocr.app.data.OverlayTheme.CLASSIC_DARK
        alpha = 0.96f
    }

    fun isShown(): Boolean = window.isShown()

    fun dismiss() = window.hide()

    fun setHiddenForCapture(hidden: Boolean) {
        window.setHiddenForCapture(hidden)
    }

    fun show(
        outcome: GameTurnOutcome,
        onRerun: () -> Unit,
        onNewGame: () -> Unit,
    ) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }
        when (outcome) {
            is GameTurnOutcome.AdviceReady -> content.addView(
                buildReadyContent(outcome.state.toPromptText(), outcome.advice)
            )
            is GameTurnOutcome.RecognitionFailed -> content.addView(
                buildMessageContent(
                    context.getString(R.string.advice_error_recognition),
                    outcome.message,
                    isError = true,
                )
            )
            is GameTurnOutcome.AdviceFailed -> {
                content.addView(buildRecognizedContent(outcome.state.toPromptText()))
                content.addView(
                    buildMessageContent(
                        context.getString(R.string.advice_error_advice),
                        outcome.message,
                        isError = true,
                    )
                )
            }
            is GameTurnOutcome.Error -> content.addView(
                buildMessageContent(
                    context.getString(R.string.advice_error_analyze),
                    outcome.message,
                    isError = true,
                )
            )
        }
        content.addView(buildActions(onRerun, onNewGame))
        window.show(content)
    }

    private fun buildReadyContent(stateText: String, advice: GameAdvice): View {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val actionText = when (advice.action) {
            AdviceAction.PLAY -> context.getString(R.string.advice_action_play)
            AdviceAction.CHOW -> context.getString(R.string.advice_action_chow)
            AdviceAction.PUNG -> context.getString(R.string.advice_action_pung)
            AdviceAction.KONG -> context.getString(R.string.advice_action_kong)
            AdviceAction.WIN -> context.getString(R.string.advice_action_win)
            AdviceAction.PASS -> context.getString(R.string.advice_action_pass)
            AdviceAction.UNKNOWN -> context.getString(R.string.advice_action_unknown)
        } + advice.targetTile?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()

        root.addView(label(context.getString(R.string.advice_section_title), 13f, 0xFF9E9E9E.toInt()))
        root.addView(text(actionText, 28f, 0xFFFFD166.toInt(), bold = true))
        if (advice.reason.isNotBlank()) {
            root.addView(text(advice.reason, 15f, Color.WHITE))
        }
        if (advice.alternatives.isNotEmpty()) {
            root.addView(spacer(dp(8)))
            root.addView(
                label(context.getString(R.string.advice_section_alternatives), 13f, 0xFF9E9E9E.toInt())
            )
            advice.alternatives.forEach { root.addView(text("• $it", 14f, 0xFFE0E0E0.toInt())) }
        }
        root.addView(spacer(dp(12)))
        root.addView(buildRecognizedContent(stateText))
        if (advice.rawText != null && advice.action == AdviceAction.UNKNOWN) {
            root.addView(spacer(dp(8)))
            root.addView(text(advice.rawText, 12f, 0xFFBDBDBD.toInt()))
        }
        return root
    }

    private fun buildRecognizedContent(stateText: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(0x332D9CDB)
        }
        setPadding(dp(10), dp(9), dp(10), dp(9))
        addView(
            label(
                context.getString(R.string.advice_section_recognized),
                13f,
                0xFF90CAF9.toInt(),
                bold = true,
            )
        )
        addView(spacer(dp(4)))
        addView(text(stateText.trim(), 13f, 0xFFE0E0E0.toInt()))
    }

    private fun buildMessageContent(title: String, message: String, isError: Boolean): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(label(title, 14f, if (isError) 0xFFFF8A80.toInt() else Color.WHITE, bold = true))
            addView(text(message, 14f, 0xFFFFCDD2.toInt()))
        }

    private fun buildActions(onRerun: () -> Unit, onNewGame: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
            addView(
                actionButton(context.getString(R.string.advice_action_rerun)) { dismiss(); onRerun() }
            )
            addView(
                actionButton(context.getString(R.string.advice_action_new_game)) { dismiss(); onNewGame() }
            )
        }

    private fun actionButton(labelText: String, action: () -> Unit): Button =
        Button(context).apply {
            text = labelText
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(7).toFloat()
                setColor(0xFF263238.toInt())
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8) }
        }

    private fun label(value: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        text(value, sizeSp, color, bold)

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.12f)
    }

    private fun spacer(height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()
}
