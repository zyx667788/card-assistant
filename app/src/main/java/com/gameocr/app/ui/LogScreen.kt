package com.gameocr.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gameocr.app.R
import com.gameocr.app.data.LogFormatter
import com.gameocr.app.data.LogRepository

/**
 * 运行日志页：按级别筛选、复制全文、导出 txt、清空。
 *
 * 列表按时间倒序（最新在最上面），最多显示仓库缓冲里的 200 条。
 */
@Composable
fun LogScreen(
    entries: List<LogRepository.Entry>,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var minimumLevel by remember { mutableStateOf<LogRepository.Level?>(null) }

    val visible = remember(entries, minimumLevel) {
        LogFormatter.filter(entries, minimumLevel).asReversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.log_close),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = {
                            clipboard.setText(AnnotatedString(LogFormatter.format(entries)))
                            Toast.makeText(context, R.string.log_copied, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.log_copy),
                        )
                    }
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = {
                            val intent = LogExporter.shareIntent(
                                context = context,
                                entries = entries,
                                subject = context.getString(R.string.log_title),
                            )
                            if (intent != null) {
                                context.startActivity(
                                    Intent.createChooser(intent, context.getString(R.string.log_share)),
                                )
                            } else {
                                Toast.makeText(context, R.string.log_export_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.log_share),
                        )
                    }
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = {
                            onClear()
                            Toast.makeText(context, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.log_clear),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LevelFilterRow(
                selected = minimumLevel,
                count = visible.size,
                onSelect = { minimumLevel = it },
            )
            HorizontalDivider()
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.log_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { entry -> LogRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LevelFilterRow(
    selected: LogRepository.Level?,
    count: Int,
    onSelect: (LogRepository.Level?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.log_filter_all)) },
        )
        FilterChip(
            selected = selected == LogRepository.Level.WARN,
            onClick = { onSelect(LogRepository.Level.WARN) },
            label = { Text(stringResource(R.string.log_filter_warn)) },
        )
        FilterChip(
            selected = selected == LogRepository.Level.ERROR,
            onClick = { onSelect(LogRepository.Level.ERROR) },
            label = { Text(stringResource(R.string.log_filter_error)) },
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.log_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogRow(entry: LogRepository.Entry) {
    val levelColor = when (entry.level) {
        LogRepository.Level.INFO -> MaterialTheme.colorScheme.primary
        LogRepository.Level.WARN -> Color(0xFFB26A00)
        LogRepository.Level.ERROR -> MaterialTheme.colorScheme.error
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = LogFormatter.formatClock(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = levelLabel(entry.level),
                style = MaterialTheme.typography.labelSmall,
                color = levelColor,
            )
            Text(
                text = categoryLabel(entry.category),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.elapsedMs?.let { elapsed ->
                Text(
                    text = stringResource(R.string.log_elapsed, elapsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun levelLabel(level: LogRepository.Level): String = stringResource(
    when (level) {
        LogRepository.Level.INFO -> R.string.log_level_info
        LogRepository.Level.WARN -> R.string.log_level_warn
        LogRepository.Level.ERROR -> R.string.log_level_error
    },
)

@Composable
private fun categoryLabel(category: LogRepository.Category): String = stringResource(
    when (category) {
        LogRepository.Category.CAPTURE -> R.string.log_category_capture
        LogRepository.Category.RECOGNITION -> R.string.log_category_recognition
        LogRepository.Category.ADVICE -> R.string.log_category_advice
        LogRepository.Category.CRASH -> R.string.log_category_crash
    },
)
