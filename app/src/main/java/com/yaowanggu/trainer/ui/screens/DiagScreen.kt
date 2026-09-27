package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.data.RecordKind
import com.yaowanggu.trainer.data.RawMatch
import com.yaowanggu.trainer.ui.TrainerViewModel
import com.yaowanggu.trainer.data.msgpack.MpValue

/**
 * 诊断页：展示存档解析出的结构，方便人工核对字段映射。
 * 遇到无法识别的格式时，把这一页的内容发给开发者。
 */
@Composable
fun DiagScreen(vm: TrainerViewModel) {
    val state by vm.state.collectAsState()
    val st = state.structure

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("存档诊断", style = MaterialTheme.typography.titleMedium)
                    Text("文件：${state.slotPath ?: "（未打开）"}", style = MaterialTheme.typography.bodySmall)
                    if (st != null) {
                        Text("大小：${st.bytes.size} 字节 · 识别格式：${st.format}", style = MaterialTheme.typography.bodySmall)
                        Text("头部 HEX：", style = MaterialTheme.typography.bodySmall)
                        Text(
                            st.bytes.take(48).joinToString(" ") { "%02X".format(it) },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("尚未打开存档。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (st != null) {
            val recs = st.records
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("识别到的记录（${recs.size}）", style = MaterialTheme.typography.titleMedium)
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
