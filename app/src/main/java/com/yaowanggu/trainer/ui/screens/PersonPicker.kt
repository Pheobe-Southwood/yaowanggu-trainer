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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.ui.TrainerViewModel
import com.yaowanggu.trainer.ui.UiState

/** 角色选择器（五官页 / 属性页共用）。 */
@Composable
fun PersonPickerCard(state: UiState, vm: TrainerViewModel) {
    val persons = state.persons
    val sel = state.selectedPerson.coerceIn(0, persons.size - 1)
    val p = persons[sel]
    var jump by remember { mutableStateOf("") }
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
                    Text("角色 ID ${p.charId}", fontWeight = FontWeight.Bold)
                    Text(
                        "记录 #${p.recordIndex} · 长度 ${p.recordLen}" +
                            if (p.hints.isNotEmpty()) " · 提示值 ${p.hints.joinToString(" / ")}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { vm.selectPerson(sel + 1) }, enabled = sel < persons.size - 1) { Text("下一个") }
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

