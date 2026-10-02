package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.yaowanggu.trainer.data.CharMap
import com.yaowanggu.trainer.ui.TrainerViewModel

/**
 * 属性页（v0.1.11）：数值（寿元/灵气/武力）+ 进阶属性（生日/性别/所在地/门派/种族/境界/阶段/灵根）可写；
 * 上限随 (境界, 阶段) 派生显示；突破几率/渡劫死亡率为游戏侧计算值，不展示。
 */
@Composable
fun AttrScreen(vm: TrainerViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    if (state.persons.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("尚未定位角色数据。请回到「存档」页打开外观模块（nfile30）。")
        }
        return
    }
    val person = state.currentPerson ?: return
    // 关键：在 body（已订阅 state）里取值并下传；子 composable 内部直接读 StateFlow 不会被 Compose 跟踪。
    val pending = state.pending
    fun currentValue(slot: CharMap.CharSlot): Long? {
        pending["char:${person.recordIndex}:${slot.key}"]?.let { return it }
        return CharMap.valueOf(person, slot)
    }
    val alias = state.aliases[person.charId]
    val lifeMax = CharMap.lifeMaxOf(person)
    val qiMax = CharMap.qiMaxOf(person)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildString {
                        alias?.let { append("$it　") }
                        append("ID ${person.charId}")
                        if (state.markedCharId == person.charId) append(" ★")
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    CharMap.realmText(person),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item { PersonPickerCard(state = state, vm = vm) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("life", "qi", "power").forEach { key ->
                        val slot = CharMap.slot(key)!!
                        val current = currentValue(slot)
                        val suffix = when (key) {
                            "life" -> "/${lifeMax ?: "—"}"
                            "qi" -> qiMax?.let { "/$it" } ?: ""
                            else -> current?.let { "/$it" } ?: ""
                        }
                        val displayMax = if (key == "qi") qiMax ?: (slot.max / slot.scale) else slot.max
                        NumRow(
                            label = slot.name,
                            hint = slot.desc,
                            max = displayMax,
                            current = current,
                            suffix = suffix,
                            onStage = { v -> vm.stageCharEdit(key, v) },
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("进阶属性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    val effRealm = currentValue(CharMap.slot("realm")!!) ?: CharMap.realmOf(person)
                    EnumRow("性别", CharMap.slot("gender")!!.enumLabels, currentValue(CharMap.slot("gender")!!)) {
                        vm.stageCharEdit("gender", it)
                    }
                    EnumRow("境界", CharMap.slot("realm")!!.enumLabels, currentValue(CharMap.slot("realm")!!)) {
                        vm.stageCharEdit("realm", it)
                    }
                    EnumRow("阶段", CharMap.stageOptions(effRealm), currentValue(CharMap.slot("stage")!!)) {
                        vm.stageCharEdit("stage", it)
                    }
                    EnumRow("种族", CharMap.slot("race")!!.enumLabels, currentValue(CharMap.slot("race")!!)) {
                        vm.stageCharEdit("race", it)
                    }
                    NumRow(
                        label = "生日·月", hint = "1-12", max = 12,
                        current = currentValue(CharMap.slot("birthM")!!), suffix = "月",
                        onStage = { vm.stageCharEdit("birthM", it) },
                    )
                    NumRow(
                        label = "生日·日", hint = "1-31", max = 31,
                        current = currentValue(CharMap.slot("birthD")!!), suffix = "日",
                        onStage = { vm.stageCharEdit("birthD", it) },
                    )
                    NumRow(
                        label = "所在地", hint = CharMap.slot("location")!!.hint, max = 999,
                        current = currentValue(CharMap.slot("location")!!), suffix = "",
                        onStage = { vm.stageCharEdit("location", it) },
                    )
                    NumRow(
                        label = "门派", hint = CharMap.slot("sect")!!.hint, max = 999,
                        current = currentValue(CharMap.slot("sect")!!), suffix = "",
                        onStage = { vm.stageCharEdit("sect", it) },
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("灵根", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    val flagSlots = CharMap.rootFlagSlots
                    val effFlags = flagSlots.map { currentValue(it) ?: 0L }
                    val effType = currentValue(CharMap.slot("rootType")!!)
                    Text(
                        CharMap.rootsStringFrom(effFlags, effType),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        flagSlots.take(4).forEachIndexed { i, slot ->
                            RootChip(slot.name, (effFlags.getOrNull(i) ?: 0L) != 0L) { on ->
                                vm.stageCharEdit(slot.key, if (on) 1 else 0)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        flagSlots.drop(4).forEachIndexed { i, slot ->
                            RootChip(slot.name, (effFlags.getOrNull(i + 4) ?: 0L) != 0L) { on ->
                                vm.stageCharEdit(slot.key, if (on) 1 else 0)
                            }
                        }
                    }
                    EnumRow("总纲", CharMap.slot("rootType")!!.enumLabels, effType) { vm.stageCharEdit("rootType", it) }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.writeBack(context) }, enabled = state.pending.isNotEmpty() && !state.loading) {
                    Text("写回存档（${state.pending.size} 项修改）")
                }
                OutlinedButton(onClick = { vm.discardPending() }, enabled = state.pending.isNotEmpty()) { Text("放弃") }
            }
            if (state.backupPath != null) {
                Spacer(Modifier.height(4.dp))
                Text("备份：${state.backupPath}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 数值行：当前值 + ±10 + 输入写入。 */
@Composable
private fun NumRow(
    label: String,
    hint: String,
    max: Long,
    current: Long?,
    suffix: String,
    onStage: (Long) -> Unit,
) {
    var draft by remember(label, current) { mutableStateOf(current?.toString() ?: "") }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.width(64.dp), fontWeight = FontWeight.Medium)
            Text(
                "${current ?: "—"}$suffix",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                val v = ((current ?: 0) - 10).coerceIn(0, max)
                onStage(v); draft = v.toString()
            }) { Text("−10") }
            OutlinedButton(onClick = {
                val v = ((current ?: 0) + 10).coerceIn(0, max)
                onStage(v); draft = v.toString()
            }) { Text("+10") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.filter { c -> c.isDigit() }.take(9) },
                label = { Text("改成") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 72.dp),
            )
            Button(onClick = { draft.toLongOrNull()?.let { onStage(it.coerceIn(0, max)) } }) { Text("写入") }
        }
        if (hint.isNotEmpty()) {
            Text(hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 72.dp))
        }
    }
}

/** 枚举行：当前标签 + 下拉选择。选项为空（如凡人阶段）时禁用。 */
@Composable
private fun EnumRow(
    label: String,
    options: Map<Long, String>,
    current: Long?,
    onPick: (Long) -> Unit,
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(64.dp), fontWeight = FontWeight.Medium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty()) {
                Text(options[current] ?: current?.toString() ?: "—")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (v, lbl) ->
                    DropdownMenuItem(text = { Text(lbl) }, onClick = { expanded = false; onPick(v) })
                }
            }
        }
    }
}

@Composable
private fun RootChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(selected = checked, onClick = { onToggle(!checked) }, label = { Text(label.removeSuffix("灵根")) })
}
