package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.refs.ChartView
import com.yaowanggu.trainer.refs.RefCatalog

/**
 * 参考图弹窗：显示字段集合对应的社区教程图表，自动滚动到该章节。
 */
@Composable
fun RefChartDialog(set: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val anchors = RefCatalog.anchors(context, set)
    var idx by remember { mutableStateOf(0) }
    val fallback = remember(set) { RefCatalog.Anchor("male.jpg", 0, "$set 参考图") }
    val anchor = anchors.getOrNull(idx) ?: fallback

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(anchor.label) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (anchors.size > 1) {
                    LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        items(anchors) { a ->
                            AssistChip(
                                onClick = { idx = anchors.indexOf(a) },
                                label = { Text(a.label) },
                            )
                        }
                    }
                }
                Text(
                    "找到对应编号后照抄到左侧。图表来自社区 GG 教程整理。",
                    style = MaterialTheme.typography.bodySmall,
                )
                ChartView(
                    asset = "ref/charts/${anchor.chart}",
                    scrollToY = anchor.y,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
    )
}
