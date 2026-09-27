package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.data.schema.CharSchema
import com.yaowanggu.trainer.ui.TrainerViewModel

/**
 * 属性页：角色 39 项字段。核心字段（寿元/生日/境界/灵根/灵气/武力…）置顶，
 * 社区表格标注“未知”的字段折叠在最后并注明风险。
 */
@Composable
fun AttrScreen(vm: TrainerViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    if (state.charRecord == null && state.rawCharOffset == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("未定位到角色属性记录。")
        }
        return
    }

    val core = CharSchema.fields.filter { f -> !f.name.startsWith("未知") && !f.note.contains("未知") }
    val unknown = CharSchema.fields.filter { f -> f.name.startsWith("未知") || f.note.contains("未知") }
    var chartSet by remember { mutableStateOf<String?>(null) }
    chartSet?.let { RefChartDialog(set = it, onClose = { chartSet = null }) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("角色属性（${CharSchema.count} 项）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.charRecord?.let { "位置 ${it.loc.describe()}（匹配度 ${it.score}）" }
                            ?: "文件偏移 0x%X".format(state.rawCharOffset ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(core) { f ->
            CharRow(
                vm = vm,
                fieldNo = f.index,
                label = f.name,
                enumLabels = f.enumLabels,
                max = f.max,
                onShowChart = if (f.index == 9) ({ chartSet = "ear" }) else null,
            )
        }

        item {
            Divider(Modifier.padding(vertical = 8.dp))
            Text("以下字段社区表格标注为“未知”，修改风险自负", color = MaterialTheme.colorScheme.error)
        }
        items(unknown) { f ->
            CharRow(vm, f.index, "${f.name} ⚠", f.enumLabels, f.max)
        }

        item {
            Divider(Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.writeBack(context) }, enabled = state.pending.isNotEmpty() && !state.loading) {
                    Text("写回存档（${state.pending.size} 项修改）")
                }
                OutlinedButton(onClick = { vm.discardPending() }, enabled = state.pending.isNotEmpty()) { Text("放弃") }
            }
        }
    }
}

@Composable
private fun CharRow(
    vm: TrainerViewModel,
    fieldNo: Int,
    label: String,
    enumLabels: Map<Int, String>,
    max: Int,
    onShowChart: (() -> Unit)? = null,
) {
    val current = vm.currentCharValue(fieldNo)
    var draft by remember(fieldNo, current) { mutableStateOf(current?.toString() ?: "") }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label（字段 $fieldNo）", fontWeight = FontWeight.Medium)
                if (enumLabels.isNotEmpty()) {
                    Text(
                        enumLabels.entries.joinToString("  ") { "${it.key}=${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.filter { c -> c.isDigit() } },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { draft.toLongOrNull()?.let { vm.stageCharEdit(fieldNo, it.coerceIn(0, max.toLong())) } },
            ) { Text("改") }
            if (onShowChart != null) {
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onShowChart) { Text("图") }
            }
        }
    }
}
