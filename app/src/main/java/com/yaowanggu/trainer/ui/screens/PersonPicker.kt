package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.data.CharMap
import com.yaowanggu.trainer.ui.TrainerViewModel
import com.yaowanggu.trainer.ui.UiState

/** 角色选择器（五官页 / 属性页共用）。 */
@Composable
fun PersonPickerCard(state: UiState, vm: TrainerViewModel) {
    val persons = state.persons
    val sel = state.selectedPerson.coerceIn(0, persons.size - 1)
    val p = persons[sel]
    var jump by remember { mutableStateOf("") }
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("选择角色（共 ${persons.size} 个）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "ID 1 通常是玩家主角。若不确定，对照游戏内的灵气/武力等数值，选提示值最接近的记录；改错角色不会影响玩家。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.selectPerson(sel - 1) }, enabled = sel > 0) { Text("上一个") }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.markedCharId == p.charId) "角色 ID ${p.charId} ★主角" else "角色 ID ${p.charId}",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "寿元 ${CharMap.valueOf(p, CharMap.slot("life")!!) ?: "—"}/${CharMap.lifeMaxOf(p) ?: "—"}" +
                            " · 灵气 ${CharMap.valueOf(p, CharMap.slot("qi")!!) ?: "—"}${CharMap.qiMaxOf(p)?.let { "/$it" } ?: ""}" +
                            " · 武力 ${CharMap.valueOf(p, CharMap.slot("power")!!) ?: "—"}" +
                            " · ${CharMap.realmText(p)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("记录 #${p.recordIndex} · 长度 ${p.recordLen}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { vm.selectPerson(sel + 1) }, enabled = sel < persons.size - 1) { Text("下一个") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.markProtagonist(context) }) {
                    Text(if (state.markedCharId == p.charId) "已标记为主角 ★" else "标记为主角")
                }
            }
            if (state.markedCharId == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "尚未标记主角：当前默认显示 ID 1（可能是 NPC）。请切换到你的主角（对照上面寿元/灵气/武力与游戏面板）后点「标记为主角」，之后每次打开存档都会自动选中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jump,
                    onValueChange = { v -> jump = v.filter { it.isDigit() }.take(6) },
                    label = { Text("跳到角色 ID") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { jump.toIntOrNull()?.let { vm.selectPersonByCharId(it.toLong()) } }, enabled = jump.isNotEmpty()) {
                    Text("跳转")
                }
            }
        }
    }
}

