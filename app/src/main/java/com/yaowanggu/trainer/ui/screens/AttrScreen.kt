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
 * 属性页：仅暴露真机校准过的字段（寿元当前/上限、灵气、武力，见 [CharMap]）。
 * 面板上的灵玉/境界/突破几率/灵根等本轮未唯一定位 → 不显示不可改，防止写坏存档。
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("角色属性（已校准字段）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "角色 ID ${person.charId} · 记录 #${person.recordIndex} · 灵气/武力 face-20/-19，寿元 face-63/-62（真机校准）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("数值应与游戏内属性面板一致；如不一致请导出诊断包发我。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { PersonPickerCard(state = state, vm = vm) }

        items(CharMap.slots) { slot ->
            CharRow(vm = vm, slotKey = slot.key, label = slot.name, max = slot.max)
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("未映射字段", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "灵玉（灵石）、境界、突破几率、灵根、出生年月等尚未在存档中唯一定位，本轮不开放修改以避免写坏存档；" +
                            "后续版本用双包差分分析补齐。",
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
private fun CharRow(
    vm: TrainerViewModel,
    slotKey: String,
    label: String,
    max: Long,
) {
    val current = vm.currentCharValue(slotKey)
    var draft by remember(slotKey, current) { mutableStateOf(current?.toString() ?: "") }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text("当前：${current ?: "—"}", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                val v = ((current ?: 0) - 10).coerceIn(0, max)
                vm.stageCharEdit(slotKey, v)
                draft = v.toString()
            }) { Text("−10") }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.filter { c -> c.isDigit() }.take(9) },
                singleLine = true,
                modifier = Modifier.width(110.dp),
            )
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = {
                val v = ((current ?: 0) + 10).coerceIn(0, max)
                vm.stageCharEdit(slotKey, v)
                draft = v.toString()
            }) { Text("+10") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = {
                draft.toLongOrNull()?.let { vm.stageCharEdit(slotKey, it.coerceIn(0, max)) }
            }) { Text("改") }
        }
    }
}
