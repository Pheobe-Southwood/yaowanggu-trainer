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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.ui.TrainerViewModel

@Composable
fun HomeScreen(vm: TrainerViewModel, onOpenFace: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("① Shizuku 状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            !state.binderAlive -> "未检测到 Shizuku 服务。请先安装并启动 Shizuku（无线调试授权），详见「教程」。"
                            !state.permissionOk -> "Shizuku 已连接，但未授权。点击下方按钮授权。"
                            else -> "Shizuku 已就绪${if (state.whoami.isNotBlank()) "（${state.whoami}）" else ""}"
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.refreshShizuku(context) }) { Text("刷新状态") }
                        if (state.binderAlive && !state.permissionOk) {
                            Button(onClick = { vm.requestShizukuPermission() }) { Text("授权 Shizuku") }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("② 游戏状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    when (state.gameRunning) {
                        null -> Text("未知。点击「刷新存档列表」检测。")
                        true -> Text("⚠️ 游戏正在运行！请先完全退出游戏（划掉后台），再读取/写入存档，否则修改会被覆盖。", color = MaterialTheme.colorScheme.error)
                        false -> Text("游戏未运行，可以安全读写存档。")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadSlots(context) }, enabled = state.permissionOk && !state.loading) {
                        Text("刷新存档列表")
                    }
                }
            }
        }

        if (state.loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("处理中…")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("③ 选择存档", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("默认排在最上面的是最近修改的存档（通常是玩家正在玩的进度）。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (state.slots.isEmpty() && state.permissionOk && !state.loading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        "未发现存档文件。确认：1) 真机上玩过该游戏并有过手动/自动存档；2) 目录 /sdcard/Android/data/com.hydrozoa.yyg/files 可访问。",
                        Modifier.padding(16.dp),
                    )
                }
            }
        }

        items(state.slots) { slot ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (slot.path == state.slotPath) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("槽位 ${slot.slot}", fontWeight = FontWeight.Bold)
                        Text("${slot.size} 字节 · 修改于 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(slot.mtimeSec * 1000))}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.openSlot(context, slot) }, enabled = state.permissionOk) { Text("打开") }
                    if (state.faceReady && slot.path == state.slotPath) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onOpenFace) { Text("去改脸") }
                    }
                }
            }
        }

        if (state.slotPath != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("当前存档", style = MaterialTheme.typography.titleMedium)
                        Text(state.slotLabel, style = MaterialTheme.typography.bodySmall)
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text(
                            if (state.faceReady) "已定位外观数据，可进入「五官」页修改。"
                            else "未能定位外观数据，请到「诊断」页查看结构并发给我。",
                        )
                    }
                }
            }
        }

        if (state.pending.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("有 ${state.pending.size} 项待写入的修改", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.writeBack(context) }, enabled = !state.loading) { Text("写回存档") }
                            OutlinedButton(onClick = { vm.discardPending() }) { Text("放弃修改") }
                        }
                    }
                }
            }
        }
    }
}
