package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.data.RecordKind
import com.yaowanggu.trainer.data.RawMatch
import com.yaowanggu.trainer.shizuku.ShellBackend
import com.yaowanggu.trainer.shizuku.ShizukuRepository
import com.yaowanggu.trainer.util.AppLog
import com.yaowanggu.trainer.ui.TrainerViewModel
import com.yaowanggu.trainer.data.msgpack.MpValue
import kotlinx.coroutines.launch

/**
 * 诊断页：展示存档解析出的结构，方便人工核对字段映射。
 * 遇到无法识别的格式时，把这一页的内容发给开发者。
 */
@Composable
fun DiagScreen(vm: TrainerViewModel) {
    val state by vm.state.collectAsState()
    val st = state.structure
    val context = LocalContextCompat()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val probe = remember { mutableStateOf("") }
    val logTail = remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) vm.exportDiag(context, uri)
    }

    fun refreshProbe() {
        scope.launch {
            probe.value = ShizukuRepository.probeSaves(context)
            logTail.value = AppLog.snapshot().takeLast(60).joinToString("\n")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShizukuDiagCard(
                onRefresh = { refreshProbe() },
                onCopy = {
                    val d = ShizukuRepository.diagnose(context)
                    val text = buildString {
                        appendLine("=== 药王谷修改器 诊断信息 ===")
                        appendLine("binder=${d.binderAlive} permission=${d.permissionOk}")
                        appendLine("serverVersion=${d.serverVersion} serverUid=${d.serverUid}")
                        appendLine("selinux=${d.selinuxContext}")
                        appendLine("backend=${d.activeBackend}")
                        appendLine("disabled=${d.disabledReason}")
                        d.notes.forEach { appendLine("note: $it") }
                        appendLine("openedSlot=${state.slotPath ?: "(none)"} container=${st?.format ?: "-"} innerSize=${st?.innerBytes?.size ?: 0}")
                        appendLine("--- 存档探测 ---")
                        append(probe.value)
                        appendLine("--- 运行日志(最近60行) ---")
                        append(AppLog.snapshot().takeLast(60).joinToString("\n"))
                    }
                    clipboard.setText(AnnotatedString(text))
                },
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("存档探测", style = MaterialTheme.typography.titleMedium)
                    if (probe.value.isBlank()) {
                        Text("点「刷新状态」后再点「探测存档目录」。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(probe.value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("存档诊断", style = MaterialTheme.typography.titleMedium)
                    Text("文件：${state.slotPath ?: "（未打开）"}", style = MaterialTheme.typography.bodySmall)
                    if (st != null) {
                        Text("大小：${st.bytes.size} 字节 · 容器：${st.format} · 解压后：${st.innerBytes.size} 字节", style = MaterialTheme.typography.bodySmall)
                        Text("文件头 HEX：", style = MaterialTheme.typography.bodySmall)
                        Text(
                            st.bytes.take(48).joinToString(" ") { "%02X".format(it) },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (st.format != "msgpack") {
                            Text("解压后头部 HEX：", style = MaterialTheme.typography.bodySmall)
                            Text(
                                st.innerBytes.take(48).joinToString(" ") { "%02X".format(it) },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text("尚未打开存档。", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            exportLauncher.launch("yaowanggu-diag-$ts.zip")
                        }) { Text("导出诊断包(zip)") }
                        OutlinedButton(onClick = { refreshProbe() }) { Text("刷新探测/日志") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("模块扫描表", style = MaterialTheme.typography.titleMedium)
                    if (state.moduleInfo.isEmpty()) {
                        Text("先在「存档」页点「刷新存档列表」。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.slots.forEach { sl ->
                            val mi = state.moduleInfo[sl.path]
                            Text(
                                "nfile%-2d %8dB  %-22s 角色×%d".format(sl.slot, sl.size, mi?.container ?: "-", mi?.personCount ?: 0),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        if (st != null && st.persons.isNotEmpty()) {
            val p = st.persons.getOrNull(state.selectedPerson) ?: st.persons[0]
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("选中角色：ID ${p.charId}（记录 #${p.recordIndex}，长度 ${p.recordLen}）", style = MaterialTheme.typography.titleMedium)
                        Text("五官窗口 @${p.loc.startIndex}：${p.faceValues}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("提示值（疑似灵气/武力）：${p.hints}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("角色记录前缀查看器（用于字段定位）", style = MaterialTheme.typography.titleMedium)
                        Text("把此内容与游戏内数值（寿元/灵气/武力等）对照，告诉我哪个索引是什么字段。", style = MaterialTheme.typography.bodySmall)
                        val rootArr = st.tree as? MpValue.Arr
                        val rec = rootArr?.items?.getOrNull(p.recordIndex) as? MpValue.Arr
                        if (rec != null) {
                            val ints = rec.items.map { it.asIntOrNull() }
                            val faceStart = p.loc.startIndex
                            ints.chunked(8).forEachIndexed { ci, chunk ->
                                Text(
                                    chunk.mapIndexed { j, v -> "[${ci * 8 + j}]${if (ci * 8 + j == faceStart) "→脸" else ""}=${v ?: "·"}" }
                                        .joinToString(" "),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            Text("（无法读取记录）", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (st != null) {
            val recs = st.records
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("通用记录扫描（${recs.size}）", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            items(recs.take(20)) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${if (r.kind == RecordKind.FACE) "外观记录" else "角色记录"} · 匹配度 ${r.score}",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(r.loc.describe(), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "值：" + r.values.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            if (st.rawMatches.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("原始二进制扫描（前 10 条）", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                items(st.rawMatches.take(10)) { r: RawMatch ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${if (r.kind == RecordKind.FACE) "外观" else "角色"} @0x%X · 匹配度 ${r.score}".format(r.offset),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("运行日志（最近 60 行）", style = MaterialTheme.typography.titleMedium)
                        if (logTail.value.isBlank()) {
                            Text("点「刷新探测/日志」查看。", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(logTail.value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (st.tree != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("结构预览（前 60 行）", style = MaterialTheme.typography.titleMedium)
                            val lines = ArrayList<String>()
                            dumpTree(st.tree, 0, lines)
                            lines.take(60).forEach {
                                Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dumpTree(v: MpValue, depth: Int, out: ArrayList<String>) {
    val indent = "  ".repeat(depth)
    when (v) {
        is MpValue.Arr -> {
            out.add("${indent}Arr(${v.items.size})")
            v.items.take(40).forEach { dumpTree(it, depth + 1, out) }
        }
        is MpValue.Map -> {
            out.add("${indent}Map(${v.entries.size})")
            v.entries.entries.take(40).forEach { (k, vv) ->
                out.add("${indent}  ${preview(k)}:")
                dumpTree(vv, depth + 2, out)
            }
        }
        else -> out.add("$indent${preview(v)}")
    }
}

private fun preview(v: MpValue): String = when (v) {
    is MpValue.Nil -> "nil"
    is MpValue.Bool -> v.v.toString()
    is MpValue.Int -> v.v.toString()
    is MpValue.Float32 -> v.v.toString()
    is MpValue.Float64 -> v.v.toString()
    is MpValue.Str -> "\"${v.v.take(40)}\""
    is MpValue.Bin -> "bin(${v.v.size})"
    is MpValue.Ext -> "ext(${v.type},${v.data.size})"
    else -> v.toString()
}


/** Shizuku 链路自检卡：一眼看出是权限问题、版本问题还是通道问题。 */
@Composable
private fun ShizukuDiagCard(onRefresh: () -> Unit, onCopy: () -> Unit) {
    val context = LocalContextCompat()
    val alive = remember { mutableStateOf(ShizukuRepository.binderAlive()) }
    val perm = remember { mutableStateOf(ShizukuRepository.permissionGranted()) }
    val backendName = remember { mutableStateOf(ShizukuRepository.activeBackendName()) }
    val ver = remember { mutableStateOf(-1) }
    val uid = remember { mutableStateOf(-1) }
    val selinux = remember { mutableStateOf("") }
    val notes = remember { mutableStateOf<List<String>>(emptyList()) }
    val disabled = remember { mutableStateOf("") }

    // 定期刷一次（权限/后端状态都可能变化）
    LaunchedEffect(Unit) {
        while (true) {
            val d = ShizukuRepository.diagnose(context)
            alive.value = d.binderAlive
            perm.value = d.permissionOk
            backendName.value = d.activeBackend
            ver.value = d.serverVersion
            uid.value = d.serverUid
            selinux.value = d.selinuxContext
            notes.value = d.notes
            disabled.value = d.disabledReason
            kotlinx.coroutines.delay(1500)
        }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Shizuku 链路自检", style = MaterialTheme.typography.titleMedium)
            Text("binder：${if (alive.value) "已连接" else "未连接"}", style = MaterialTheme.typography.bodySmall)
            Text("权限：${if (perm.value) "已授权" else "未授权"}", style = MaterialTheme.typography.bodySmall)
            Text("服务端版本：${if (ver.value > 0) ver.value else "?"} · uid：${uid.value}", style = MaterialTheme.typography.bodySmall)
            if (backendName.value != "unknown") Text("当前通道：$backendName.value", style = MaterialTheme.typography.bodySmall)
            if (selinux.value.isNotBlank()) Text("SELinux：${selinux.value}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            notes.value.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
            if (disabled.value.isNotBlank() && disabled.value != "未记录") {
                Text("禁用通道错误：$disabled.value", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    ShizukuRepository.resetBackend()
                    onRefresh()
                }) { Text("重试") }
                OutlinedButton(onClick = onCopy) { Text("复制诊断信息") }
            }
        }
    }
}


@Composable
private fun LocalContextCompat() = LocalContext.current
