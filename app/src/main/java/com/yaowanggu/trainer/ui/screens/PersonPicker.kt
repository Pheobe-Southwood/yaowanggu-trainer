package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.yaowanggu.trainer.data.PersonQuery
import com.yaowanggu.trainer.ui.TrainerViewModel
import com.yaowanggu.trainer.ui.UiState

/** 角色选择器（五官页 / 属性页共用）：搜索（别名/ID/武力）+ 命名 + 主角标记。 */
@Composable
fun PersonPickerCard(state: UiState, vm: TrainerViewModel) {
    val persons = state.persons
    val sel = state.selectedPerson.coerceIn(0, persons.size - 1)
    val p = persons[sel]
    var query by remember { mutableStateOf("") }
    var renameOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun aliasOf(id: Long): String? = state.aliases[id]
    fun powerOf(idx: Int): Long? = CharMap.valueOf(persons[idx], CharMap.slot("power")!!)
    fun titleOf(idx: Int): String {
        val pp = persons[idx]
        val a = aliasOf(pp.charId)
        val star = if (state.markedCharId == pp.charId) " ★" else ""
        return if (a != null) "$a　ID ${pp.charId}$star" else "ID ${pp.charId}$star"
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "角色（${persons.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(20) },
                    label = { Text("搜索：别名 / ID / 武力") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (query.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.selectPerson(sel - 1) }, enabled = sel > 0) { Text("上一个") }
                    Column(Modifier.weight(1f)) {
                        Text(titleOf(sel), fontWeight = FontWeight.Bold)
                        Text(
                            "寿元 ${CharMap.valueOf(p, CharMap.slot("life")!!) ?: "—"}/${CharMap.lifeMaxOf(p) ?: "—"}" +
                                " · 灵气 ${CharMap.valueOf(p, CharMap.slot("qi")!!) ?: "—"}${CharMap.qiMaxOf(p)?.let { "/$it" } ?: ""}" +
                                " · 武力 ${CharMap.valueOf(p, CharMap.slot("power")!!) ?: "—"}" +
                                " · ${CharMap.realmText(p)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = { vm.selectPerson(sel + 1) }, enabled = sel < persons.size - 1) { Text("下一个") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.markProtagonist(context) }) {
                        Text(if (state.markedCharId == p.charId) "已标记主角 ★" else "标记为主角")
                    }
                    OutlinedButton(onClick = {
                        renameDraft = aliasOf(p.charId) ?: ""
                        renameOpen = true
                    }) { Text("命名") }
                }
            } else {
                val hits = persons.indices.filter { i ->
                    PersonQuery.matches(persons[i].charId, aliasOf(persons[i].charId), powerOf(i), query)
                }
                Text("匹配 ${hits.size} 个", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hits.take(60).forEach { i ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { vm.selectPerson(i) }) {
                            Text(
                                "${titleOf(i)} · 武力 ${powerOf(i) ?: "—"} · ${CharMap.realmText(persons[i])}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("命名 ${titleOf(sel)}") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(12) },
                    label = { Text("别名（留空清除）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAlias(context, p.charId, renameDraft)
                    renameOpen = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("取消") } },
        )
    }
}
