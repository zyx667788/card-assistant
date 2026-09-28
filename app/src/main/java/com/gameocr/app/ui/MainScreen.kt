package com.gameocr.app.ui

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gameocr.app.R
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
    val logs by viewModel.logs.collectAsState()
    var showLogs by remember { mutableStateOf(false) }

    // 日志页复用手写状态切换，不引入导航依赖。
    if (showLogs) {
        BackHandler { showLogs = false }
        LogScreen(
            entries = logs,
            onClear = viewModel::clearLogs,
            onClose = { showLogs = false },
        )
        return
    }

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
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    if (serviceRunning) R.string.assistant_service_running
                    else R.string.assistant_service_stopped
                ),
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
                    Text("  " + stringResource(R.string.assistant_stop_service))
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    onClick = {
                        context.startActivity(CaptureStartRequestActivity.newIntent(context))
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  " + stringResource(R.string.assistant_start_service))
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
                    Text(
                        stringResource(R.string.assistant_game_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.assistant_game_mode_desc),
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
                    Text(
                        stringResource(R.string.assistant_cloud_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // 本地状态一旦被用户编辑过就不再回灌：DataStore 写盘有延迟，若每次都按
                    // 存储值重建状态，刚敲进去的字会被上一次落盘的结果覆盖。
                    var apiKey by remember { mutableStateOf(settings.apiKey) }
                    var apiKeyEdited by remember { mutableStateOf(false) }
                    LaunchedEffect(settings.apiKey, apiKeyEdited) {
                        if (!apiKeyEdited) apiKey = settings.apiKey
                    }
                    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
                    var baseUrlEdited by remember { mutableStateOf(false) }
                    LaunchedEffect(settings.baseUrl, baseUrlEdited) {
                        if (!baseUrlEdited) baseUrl = settings.baseUrl
                    }
                    var model by remember { mutableStateOf(settings.model) }
                    var modelEdited by remember { mutableStateOf(false) }
                    LaunchedEffect(settings.model, modelEdited) {
                        if (!modelEdited) model = settings.model
                    }
                    var timeout by remember { mutableStateOf(settings.apiTimeoutSeconds.toString()) }
                    var timeoutEdited by remember { mutableStateOf(false) }
                    LaunchedEffect(settings.apiTimeoutSeconds, timeoutEdited) {
                        if (!timeoutEdited) timeout = settings.apiTimeoutSeconds.toString()
                    }
                    var keyVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKeyEdited = true
                            apiKey = it
                            viewModel.update { s -> s.copy(apiKey = it) }
                        },
                        label = { Text(stringResource(R.string.assistant_api_key_label)) },
                        placeholder = { Text("sk-…") },
                        visualTransformation = if (keyVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            androidx.compose.material3.TextButton(onClick = { keyVisible = !keyVisible }) {
                                Text(
                                    stringResource(
                                        if (keyVisible) R.string.assistant_hide_secret
                                        else R.string.assistant_show_secret
                                    )
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrlEdited = true
                            baseUrl = it
                            viewModel.update { s -> s.copy(baseUrl = it) }
                        },
                        label = { Text(stringResource(R.string.assistant_base_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            modelEdited = true
                            model = it
                            viewModel.update { s -> s.copy(model = it) }
                        },
                        label = { Text(stringResource(R.string.assistant_vision_model_label)) },
                        placeholder = { Text("deepseek-flash") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { raw ->
                            timeoutEdited = true
                            timeout = raw
                            raw.toIntOrNull()?.let { v ->
                                viewModel.update { s -> s.copy(apiTimeoutSeconds = v.coerceIn(10, 300)) }
                            }
                        },
                        label = { Text(stringResource(R.string.assistant_timeout_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.assistant_cloud_desc),
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
                    Text(
                        stringResource(R.string.assistant_usage_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    listOf(
                        stringResource(R.string.assistant_usage_step_1),
                        stringResource(R.string.assistant_usage_step_2),
                        stringResource(R.string.assistant_usage_step_3),
                        stringResource(R.string.assistant_usage_step_4),
                        stringResource(R.string.assistant_usage_step_5),
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.log_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.log_summary, logs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showLogs = true },
                    ) {
                        Text(stringResource(R.string.log_open))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.assistant_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
