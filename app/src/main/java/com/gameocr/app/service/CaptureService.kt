package com.gameocr.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.gameocr.app.R
import com.gameocr.app.capture.AccessibilityScreenshotter
import com.gameocr.app.capture.CaptureBackend
import com.gameocr.app.capture.MediaProjectionScreenshotter
import com.gameocr.app.capture.Screenshotter
import com.gameocr.app.capture.ShizukuScreenshotter
import com.gameocr.app.data.FloatingMenu
import com.gameocr.app.data.FloatingSkill
import com.gameocr.app.data.LogRepository
import com.gameocr.app.data.Settings
import com.gameocr.app.data.SettingsRepository
import com.gameocr.app.game.session.GameSessionManager
import com.gameocr.app.game.session.GameTurnCoordinator
import com.gameocr.app.game.session.GameTurnOutcome
import com.gameocr.app.game.ui.GameAdviceOverlay
import com.gameocr.app.overlay.FloatingButtonManager
import com.gameocr.app.overlay.CardHintOverlay
import com.gameocr.app.shizuku.ShizukuCapabilities
import com.gameocr.app.trigger.GameOcrAccessibilityService
import com.gameocr.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 打牌助手的前台截屏服务（瘦身版）。
 *
 * 只做四件事：
 * 1. 以 Shizuku / 无障碍 / MediaProjection 任一后端建立截屏链路；
 * 2. 显示悬浮球（单击 = 分析当前牌局；弧菜单只有分析 / 设置 / 返回主应用）；
 * 3. 截一帧 -> 云端 VLM 看牌 -> 本地规则引擎算合法动作 -> 文本 LLM 给建议；
 * 4. 用悬浮卡展示可核对的建议。
 *
 * 不再有翻译 / OCR / TTS / 词典等任何分支。
 */
@AndroidEntryPoint
class CaptureService : Service() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.gameocr.app.data.AppLocalePrefs.live(newBase))
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shizukuCapabilities: ShizukuCapabilities
    @Inject lateinit var logRepository: LogRepository
    @Inject lateinit var gameTurnCoordinator: GameTurnCoordinator
    @Inject lateinit var gameSessionManager: GameSessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureLock = Mutex()
    private val screenshotLock = Mutex()

    private var screenshotter: Screenshotter? = null
    private var captureStopRequested = false
    private var captureUiStartupJob: Job? = null
    private var captureProbeJob: Job? = null
    private var projection: MediaProjection? = null
    private var floatingButton: FloatingButtonManager? = null
    private var gameAdviceOverlay: GameAdviceOverlay? = null
    private var gameAdviceJob: Job? = null
    private var hintOverlay: CardHintOverlay? = null
    private var settingsCollectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingButton?.onConfigurationChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> {
                captureStopRequested = true
                stopSelf()
            }
            ACTION_TRIGGER_ONCE -> triggerGameAdviceAnalysis()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (CaptureServiceState.running.value) {
            Timber.i("[capture-start] ignore duplicate start for the running session")
            return
        }
        captureStopRequested = false
        try {
            initializeCapture(intent)
        } catch (error: Exception) {
            failCaptureStart(error)
        }
    }

    private fun failCaptureStart(error: Exception? = null) {
        if (captureStopRequested) return
        logRepository.error(LogRepository.Category.CAPTURE, getString(R.string.log_msg_capture_failed), error)
        captureStopRequested = true
        stopSelf()
    }

    private fun initializeCapture(intent: Intent) {
        // 如果已经启动过，先 cleanup 旧资源避免悬浮窗叠加 / 截屏链路泄漏。
        cleanupCapture()

        // Execute the coordinator's decision. A disconnected backend must NEVER turn into projection.
        val backend = intent.getStringExtra(EXTRA_CAPTURE_BACKEND)?.let {
            runCatching { CaptureBackend.valueOf(it) }.getOrNull()
        } ?: CaptureBackend.MEDIA_PROJECTION
        val useShizuku = backend == CaptureBackend.SHIZUKU
        val useAccessibility = backend == CaptureBackend.ACCESSIBILITY
        if ((useShizuku && shizukuCapabilities.availability(this) != ShizukuCapabilities.Availability.READY) ||
            (useAccessibility && !GameOcrAccessibilityService.isScreenshotReady())) {
            Timber.w("[capture-start] selected backend no longer available: %s", backend)
            failCaptureStart()
            return
        }

        // 前台服务：Android 14+ 必须显式传非零 type，否则 InvalidForegroundServiceTypeException。
        // MediaProjection 路径全程走 MEDIA_PROJECTION；Shizuku / 无障碍路径在 34+ 走 SPECIAL_USE。
        val fgType = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
            !useShizuku && !useAccessibility ->
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            // SPECIAL_USE 是 API 34 才加入的 type；29..33 上传平台不认识的 type 位
            // 不符合契约，Shizuku / 无障碍路径在这些版本上不声明 type。
            else -> 0
        }
        // Android 14+ HyperOS/MIUI 上常见 race：CaptureStartRequestActivity onActivityResult
        // 收到 RESULT_OK 后立即 startForegroundService，此时 `android:project_media` app-op
        // grant 尚未异步落地，startForeground 抛 SecurityException 闪退。
        // workaround：捕获异常后 postDelayed 重试一次，给 op 200ms 落地时间；仍失败再 stopSelf。
        if (!startForegroundCompat(fgType, intent)) {
            return
        }

        if (useShizuku) {
            screenshotter = ShizukuScreenshotter()
            Timber.i("CaptureService started with Shizuku path")
        } else if (useAccessibility) {
            screenshotter = AccessibilityScreenshotter()
            Timber.i("CaptureService started with Accessibility path")
        } else {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (data == null) {
                Timber.w("MediaProjection result data is null")
                failCaptureStart()
                return
            }
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mpm.getMediaProjection(resultCode, data)
            val mp = projection
            if (mp == null) {
                Timber.w("getMediaProjection returned null")
                failCaptureStart()
                return
            }
            screenshotter = MediaProjectionScreenshotter(this, mp)
            Timber.i("CaptureService started with MediaProjection path")
        }

        hintOverlay = CardHintOverlay(this)
        gameAdviceOverlay = GameAdviceOverlay(this, settingsRepository, scope)
        floatingButton = FloatingButtonManager(
            this,
            // 打牌助手：主球单击就是分析当前牌局，不再有技能切换。
            onSingleTap = { triggerGameAdviceAnalysis() },
            onDoubleTap = { triggerGameAdviceAnalysis() },
            onSwitchToLoop = {},
            settingsRepository = settingsRepository,
            ioScope = scope
        ).also {
            it.onMenuOpenSettings = {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
            it.onMenuOpenMainActivity = {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
            // 弧菜单里的「打牌助手」按钮：直接触发一次分析。
            it.onSwitchSkill = { triggerGameAdviceAnalysis() }
        }
        // 异步读 settings 应用大小 + 还原上次松手位置后再 show，避免阻塞 startForeground 流程
        captureUiStartupJob = scope.launch {
            try {
                val s = settingsRepository.get()
                withContext(Dispatchers.Main.immediate) {
                    if (captureStopRequested) return@withContext
                    floatingButton?.sizeDp = s.floatingButtonSizeDp
                    floatingButton?.initialX = s.floatingButtonX
                    floatingButton?.initialY = s.floatingButtonY
                    floatingButton?.snapToEdgeEnabled = s.floatingButtonSnapToEdge
                    floatingButton?.autoDockEnabled = s.floatingButtonAutoDock
                    floatingButton?.dockEdgeInsetPx =
                        (s.floatingButtonDockInsetDp * resources.displayMetrics.density).toInt()
                    floatingButton?.menuItemOrder = FloatingMenu.GAME_ASSISTANT_ORDER
                    floatingButton?.arcMenuPageSize = FloatingMenu.DEFAULT_PAGE_SIZE
                    floatingButton?.skill = FloatingSkill.GAME_ASSISTANT
                    floatingButton?.applyOpacity(s.floatingButtonAlpha)
                    floatingButton?.show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) { failCaptureStart(error) }
            }
        }

        // Settings flow 是悬浮窗外观的唯一权威来源，首次 emit 同样需要应用初始状态。
        settingsCollectJob?.cancel()
        settingsCollectJob = scope.launch {
            settingsRepository.settings.collect { s -> applyCardAssistantConfig(s) }
        }

        CaptureServiceState.setRunning(true)

        // Keep the existing Shizuku trial capture and its failure hint.
        if (useShizuku) {
            captureProbeJob = scope.launch {
                try {
                    val shotter = screenshotter ?: return@launch
                    val test = shotter.capture()
                    try {
                        withContext(Dispatchers.Main.immediate) {
                            if (captureStopRequested) return@withContext
                            if (test == null) {
                                Timber.w("Shizuku dry-run failed; stopping service")
                                logRepository.error(LogRepository.Category.CAPTURE,
                                    getString(R.string.log_msg_shizuku_dry_run_failed))
                                hintOverlay?.showErrorHint(getString(R.string.toast_shizuku_dry_run_failed), durationMs = 8000L)
                                delay(8500L)
                                if (!captureStopRequested) stopSelf()
                            }
                        }
                    } finally {
                        test?.recycle()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    withContext(Dispatchers.Main.immediate) { failCaptureStart(error) }
                }
            }
        }
    }

    /**
     * 把 Settings 的悬浮球外观同步到已显示的球上。
     * 设置页保存后立即生效，不用重启服务。
     */
    private suspend fun applyCardAssistantConfig(settings: Settings) {
        val dockEdgeInsetPx = (settings.floatingButtonDockInsetDp * resources.displayMetrics.density).toInt()
        withContext(Dispatchers.Main) {
            floatingButton?.let {
                it.applyOpacity(settings.floatingButtonAlpha)
                if (it.sizeDp != settings.floatingButtonSizeDp) {
                    it.sizeDp = settings.floatingButtonSizeDp
                    it.applyResize()
                }
                it.applySnapPreference(settings.floatingButtonSnapToEdge)
                it.autoDockEnabled = settings.floatingButtonAutoDock
                it.dockEdgeInsetPx = dockEdgeInsetPx
                it.menuItemOrder = FloatingMenu.GAME_ASSISTANT_ORDER
                if (it.skill != FloatingSkill.GAME_ASSISTANT) {
                    it.skill = FloatingSkill.GAME_ASSISTANT
                    it.applySkillIcon()
                }
            }
        }
    }

    /**
     * 包裹 [ServiceCompat.startForeground]，处理 Android 14+ HyperOS/MIUI 上的 `android:project_media`
     * app-op race：CaptureStartRequestActivity 拿到 RESULT_OK 后 op grant 是异步的，立刻 startForeground
     * 可能在 op 还没落地时抛 `SecurityException`。
     *
     * @return true 代表 startForeground 同步成功，调用方可继续后续初始化；false 代表已进入异步
     *  重试模式，调用方应立刻 return（避免在前台未确立时初始化截屏 / 悬浮窗）。
     */
    private fun startForegroundCompat(fgType: Int, originalIntent: Intent): Boolean {
        val tryStart = {
            ServiceCompat.startForeground(
                this,
                CaptureNotification.NOTIF_ID,
                CaptureNotification.build(this),
                fgType
            )
        }
        return try {
            tryStart()
            true
        } catch (se: SecurityException) {
            Timber.w(se, "startForeground SecurityException; retry in 200ms")
            mainScope.launch {
                delay(200L)
                if (captureStopRequested) return@launch
                try {
                    tryStart()
                    Timber.i("startForeground retry succeeded; rerunning handleStart")
                    handleStart(originalIntent)
                } catch (e2: Exception) {
                    Timber.e(e2, "startForeground retry also failed")
                    logRepository.error(
                        LogRepository.Category.CAPTURE,
                        "startForeground SecurityException after retry: ${e2.message}",
                        e2
                    )
                    failCaptureStart(e2)
                }
            }
            false
        }
    }

    /** 单击悬浮球：截一帧，跑 VLM 牌局识别 + 本地规则 + LLM 决策，最后显示可核对悬浮卡。 */
    private fun triggerGameAdviceAnalysis() {
        if (gameAdviceJob?.isActive == true) return
        if (!captureLock.tryLock()) {
            mainScope.launch {
                hintOverlay?.showErrorHint(getString(R.string.assistant_hint_busy), 2500L)
            }
            return
        }
        gameAdviceJob = scope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                mainScope.async { hintOverlay?.showLoadingHint() }.await()
                prepareCleanCaptureFrame()
                val shotter = screenshotter
                val captureStartedAt = android.os.SystemClock.elapsedRealtime()
                bitmap = if (shotter == null) null else screenshotLock.withLock { shotter.capture() }
                restoreCaptureChrome(showLoading = true)
                val captureElapsedMs = android.os.SystemClock.elapsedRealtime() - captureStartedAt
                val frame = bitmap
                if (frame == null) {
                    logRepository.error(
                        category = LogRepository.Category.CAPTURE,
                        message = getString(R.string.log_msg_capture_failed),
                        elapsedMs = captureElapsedMs,
                    )
                    withContext(Dispatchers.Main) {
                        hintOverlay?.dismissLoading()
                        hintOverlay?.showErrorHint(getString(R.string.assistant_hint_capture_failed))
                    }
                    return@launch
                }
                logRepository.info(
                    category = LogRepository.Category.CAPTURE,
                    message = "截屏成功 ${frame.width}x${frame.height}",
                    elapsedMs = captureElapsedMs,
                )
                val settings = settingsRepository.get()
                val outcome = gameTurnCoordinator.analyze(frame, settings)
                withContext(Dispatchers.Main) {
                    hintOverlay?.dismissLoading()
                    gameAdviceOverlay?.show(
                        outcome = outcome,
                        onRerun = { triggerGameAdviceAnalysis() },
                        onNewGame = {
                            gameAdviceJob = scope.launch {
                                gameSessionManager.reset(settings.gameModuleId)
                                withContext(Dispatchers.Main) {
                                    hintOverlay?.showInfoHint(getString(R.string.game_session_reset))
                                }
                            }
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    hintOverlay?.dismissLoading()
                    hintOverlay?.showErrorHint(shortError(error), durationMs = 6000L)
                }
            } finally {
                bitmap?.let { if (!it.isRecycled) it.recycle() }
                captureLock.unlock()
            }
        }
    }

    /** 截图前藏起悬浮球和建议卡，保证截到的是干净牌面。 */
    private suspend fun prepareCleanCaptureFrame() {
        mainScope.launch {
            hintOverlay?.clearForCapture()
            gameAdviceOverlay?.setHiddenForCapture(true)
            floatingButton?.hide()
        }.join()
        delay(CAPTURE_CHROME_SETTLE_MS)
    }

    private fun restoreCaptureChrome(showLoading: Boolean) {
        mainScope.launch {
            gameAdviceOverlay?.setHiddenForCapture(false)
            floatingButton?.show()
            if (showLoading) hintOverlay?.showLoadingHint()
        }
    }

    private fun shortError(t: Throwable): String {
        val raw = t.message?.trim().orEmpty()
        return when {
            raw.isEmpty() -> getString(R.string.assistant_error_format, t.javaClass.simpleName)
            raw.length <= 120 -> raw
            else -> raw.take(117) + "…"
        }
    }

    /** 释放截屏相关资源（不停 Service），用于 handleStart 重入时去重 + onDestroy 兜底。 */
    private fun cleanupCapture() {
        captureUiStartupJob?.cancel()
        captureUiStartupJob = null
        captureProbeJob?.cancel()
        captureProbeJob = null
        gameAdviceJob?.cancel()
        gameAdviceJob = null
        gameAdviceOverlay?.dismiss()
        gameAdviceOverlay = null
        settingsCollectJob?.cancel()
        settingsCollectJob = null
        hintOverlay?.clear()
        hintOverlay = null
        floatingButton?.hide()
        floatingButton = null
        screenshotter?.release()
        screenshotter = null
        projection?.stop()
        projection = null
    }

    override fun onDestroy() {
        captureStopRequested = true
        cleanupCapture()
        scope.cancel()
        mainScope.cancel()
        CaptureServiceState.setRunning(false)
        Timber.i("CaptureService destroyed")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.gameocr.app.action.START"
        const val ACTION_STOP = "com.gameocr.app.action.STOP"
        const val ACTION_TRIGGER_ONCE = "com.gameocr.app.action.TRIGGER_ONCE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_CAPTURE_BACKEND = "extra_capture_backend"
        private const val CAPTURE_CHROME_SETTLE_MS = 80L

        fun stopIntent(context: Context): Intent =
            Intent(context, CaptureService::class.java).apply { action = ACTION_STOP }
    }
}
