package com.gameocr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gameocr.app.capture.CaptureStartRequestActivity
import com.gameocr.app.service.CaptureService
import com.gameocr.app.service.CaptureServiceState

/**
 * 打牌助手主界面：服务开关 + 云端识别配置 + 使用说明。
 * 没有翻译，没有本地模型——全部走云端 VLM 看牌 + 文本 LLM 决策。
 */
@Composable
fun MainScreen(
    viewModel: CardAssistantViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val serviceRunning by CaptureServiceState.running.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "打牌助手",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (serviceRunning) "● 悬浮球服务运行中" else "○ 悬浮球服务未启动",
                style = MaterialTheme.typography.bodyLarge,
                color = if (serviceRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (serviceRunning) {
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    onClick = { context.startService(CaptureService.stopIntent(context)) }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("  停止悬浮球服务")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    onClick = {
                        context.startActivity(CaptureStartRequestActivity.newIntent(context))
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  启动悬浮球")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("牌局模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "不同模式用不同的提示词看牌、出主意；切换后点悬浮球即按新模式分析。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    viewModel.gameModules.forEach { module ->
                        val selected = settings.gameModuleId == module.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.update { s -> s.copy(gameModuleId = module.id) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { viewModel.update { s -> s.copy(gameModuleId = module.id) } },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = module.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                val summaryFirstLine = module.promptPolicy.rulesSummary
                                    .lineSequence()
                                    .map { it.trim() }
                                    .firstOrNull { it.isNotEmpty() }
                                if (!summaryFirstLine.isNullOrEmpty()) {
                                    Text(
                                        text = summaryFirstLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("云端识别配置", style = MaterialTheme.typography.titleMedium)
                    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
                    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
                    var model by remember(settings.model) { mutableStateOf(settings.model) }
                    var timeout by remember(settings.apiTimeoutSeconds) {
                        mutableStateOf(settings.apiTimeoutSeconds.toString())
                    }
                    var keyVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            viewModel.update { s -> s.copy(apiKey = it) }
                        },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-…") },
                        visualTransformation = if (keyVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            androidx.compose.material3.TextButton(onClick = { keyVisible = !keyVisible }) {
                                Text(if (keyVisible) "隐藏" else "显示")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            viewModel.update { s -> s.copy(baseUrl = it) }
                        },
                        label = { Text("Base URL（OpenAI 兼容）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            viewModel.update { s -> s.copy(model = it) }
                        },
                        label = { Text("视觉模型") },
                        placeholder = { Text("deepseek-flash") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { raw ->
                            timeout = raw
                            raw.toIntOrNull()?.let { v ->
                                viewModel.update { s -> s.copy(apiTimeoutSeconds = v.coerceIn(10, 300)) }
                            }
                        },
                        label = { Text("请求超时（秒）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "VLM 看整张截图识别牌局，文本模型在合法动作里做决策。" +
                            "默认 DeepSeek，也可填任何 OpenAI 兼容接口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("使用说明", style = MaterialTheme.typography.titleMedium)
                    listOf(
                        "1. 先在上面选好牌局模式（跑胡子 / 斗地主），再点「启动悬浮球」。",
                        "2. 按提示授予悬浮窗 / 截屏权限。",
                        "3. 打开对应牌局，点一下悬浮球即分析当前牌面。",
                        "4. 悬浮卡展示 VLM 识别的牌局与 AI 建议，可核对后自行决策。",
                        "5. 换一局时点悬浮卡上的「新开一局」，清空上局记忆。",
                    ).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "仅供学习娱乐，请遵守当地法律法规与平台规则",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
