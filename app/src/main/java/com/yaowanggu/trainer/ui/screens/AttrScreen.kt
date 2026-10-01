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
import com.yaowanggu.trainer.data.CharMap
import com.yaowanggu.trainer.ui.TrainerViewModel

/**
 * 属性页：v0.1.10 校准字段（寿元前数 face−49、灵气 face−20 ×100 定点、武力 face−19）可写；
 * 寿元上限/灵气上限由境界派生（存档不存储），境界/突破几率/生日/灵根只读展示，与游戏面板逐 chip 对齐。
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
    // 关键：在 body（已订阅 state）里取值并下传；CharRow 内部直接读 StateFlow 不会被 Compose 跟踪，
    // 会导致切换角色后数值不刷新（v0.1.6 bug）。
    val pending = state.pending
    fun currentValue(slot: CharMap.CharSlot): Long? {
        pending["char:${person.recordIndex}:${slot.key}"]?.let { return it }
        return CharMap.valueOf(person, slot)
    }
    val values = CharMap.slots.associate { it.key to currentValue(it) }

    // 派生展示（只读）：上限不存储，由境界查表；未验证的境界显示 —
    val realm = CharMap.realmOf(person)
    val lifeMax = CharMap.lifeMaxFor(realm)
    val qiMax = CharMap.qiMaxFor(realm)
    val birth = CharMap.birthOf(person)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("角色属性（v0.1.10 校准）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "角色 ID ${person.charId} · 记录 #${person.recordIndex} · 寿元 face−49、灵气 face−20（×100 定点）、武力 face−19",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "数值应与游戏内属性面板一致；上限随境界派生（存档不存储）。如仍不一致请导出诊断包发我。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item { PersonPickerCard(state = state, vm = vm) }

        items(CharMap.slots) { slot ->
            val suffix = when (slot.key) {
                "life" -> "/${lifeMax ?: "—"}"
                "qi" -> qiMax?.let { "/$it" } ?: ""
                "power" -> values["power"]?.let { "/$it" } ?: ""
                else -> ""
            }
            val displayMax = when (slot.key) {
                "qi" -> qiMax ?: (slot.max / slot.scale)
                else -> slot.max
            }
            CharRow(
                vm = vm,
                slotKey = slot.key,
                label = slot.name,
                desc = slot.desc,
                max = displayMax,
                current = values[slot.key],
                suffix = suffix,
                editable = slot.editable,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("面板对照（只读·派生）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    InfoRow("境界", CharMap.realmText(person))
                    InfoRow("寿元上限", lifeMax?.toString() ?: "—（该境界未验证，不臆造）")
                    InfoRow("灵气上限", qiMax?.toString() ?: "—（该境界未验证，不臆造）")
                    InfoRow("突破几率", CharMap.breakthroughOf(person)?.let { "$it%" } ?: "—")
                    InfoRow("生日", if (birth.first != null) "${birth.first}月${birth.second ?: 0}日" else "—")
                    InfoRow("灵根", CharMap.rootsString(person))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "上限/境界/灵根等在存档中无独立存储字段（或仅随境界派生），故只读；强行写 face−62 等旧偏移游戏内无效。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("未映射字段", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "灵石/库存/贡献度等位于加密模块（nfile0–4），当前无法安全读写，暂不开放；" +
                            "后续版本如取得解密线索再扩展。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Divider(Modifier.padding(vertical = 8.dp))
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CharRow(
    vm: TrainerViewModel,
    slotKey: String,
    label: String,
    desc: String,
    max: Long,
    current: Long?,
    suffix: String = "",
    editable: Boolean = true,
) {
    var draft by remember(slotKey, current) { mutableStateOf(current?.toString() ?: "") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "游戏内：${current ?: "—"}$suffix",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (editable) {
                    OutlinedButton(onClick = {
                        val v = ((current ?: 0) - 10).coerceIn(0, max)
                        vm.stageCharEdit(slotKey, v)
                        draft = v.toString()
                    }) { Text("−10") }
                    OutlinedButton(onClick = {
                        val v = ((current ?: 0) + 10).coerceIn(0, max)
                        vm.stageCharEdit(slotKey, v)
                        draft = v.toString()
                    }) { Text("+10") }
                }
            }
            if (editable) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("改成") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        draft.toLongOrNull()?.let { vm.stageCharEdit(slotKey, it.coerceIn(0, max)) }
                    }) { Text("写入修改") }
                }
            }
        }
    }
}
