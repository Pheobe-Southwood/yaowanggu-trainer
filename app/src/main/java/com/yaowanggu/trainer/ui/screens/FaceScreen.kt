package com.yaowanggu.trainer.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaowanggu.trainer.data.schema.FaceField
import com.yaowanggu.trainer.data.schema.FaceSchema
import com.yaowanggu.trainer.refs.RefCatalog
import com.yaowanggu.trainer.ui.TrainerViewModel

/** 外观字段 → 图鉴 asset 集合名 */
private val partRefSets = mapOf(
    2 to "face", 3 to "brow", 4 to "eye", 5 to "mouth",
    6 to "nose", 7 to "hair_front", 8 to "hair_back",
    19 to "hair_front", 20 to "hair_back",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScreen(vm: TrainerViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    if (!state.faceReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("尚未定位外观数据。请回到「存档」页打开一个存档，或到「诊断」页查看。")
        }
        return
    }

    val parts = FaceSchema.fields.filter {
        it.kind == FaceField.Kind.OPTION || it.kind == FaceField.Kind.MOLE || it.kind == FaceField.Kind.TRAIT
    }
    var chartSet by remember { mutableStateOf<String?>(null) }

    chartSet?.let { RefChartDialog(set = it, onClose = { chartSet = null }) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("修改五官外貌", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "数据来源：${state.structure?.format ?: "?"}" +
                            (state.faceRecord?.let { " · 记录位置 ${it.loc.describe()}" } ?: " · 文件偏移 0x%X".format(state.rawFaceOffset ?: 0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("修改完成后点底部「写回存档」，然后重开游戏生效。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(parts) { f ->
            val current = vm.currentFaceValue(f.index)
            PartRow(
                field = f,
                current = current,
                refSet = partRefSets[f.index],
                onChange = { vm.stageFaceEdit(f.index, it.toLong()) },
                onShowChart = { chartSet = it },
            )
        }

        item {
            Divider(Modifier.padding(vertical = 8.dp))
            Text("颜色（色相 0–360，饱和/明暗 0–400）", style = MaterialTheme.typography.titleMedium)
        }

        item {
            ColorRow(
                title = "发色",
                labels = listOf("色相", "饱和", "明暗"),
                indices = listOf(FaceSchema.hairHueIndex, FaceSchema.hairSatIndex, FaceSchema.hairValIndex),
                vm = vm,
                onShowChart = { chartSet = "hair_color" },
            )
        }
        item {
            ColorRow(
                title = "瞳色",
                labels = listOf("色相", "饱和", "明暗"),
                indices = listOf(FaceSchema.eyeHueIndex, FaceSchema.eyeSatIndex, FaceSchema.eyeValIndex),
                vm = vm,
                onShowChart = { chartSet = "eye_color" },
            )
        }
        item {
            ColorRow(
                title = "肤色",
                labels = listOf("饱和", "明暗"),
                indices = listOf(FaceSchema.skinSatIndex, FaceSchema.skinValIndex),
                vm = vm,
            )
        }

        item {
            Divider(Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.writeBack(context) }, enabled = state.pending.isNotEmpty() && !state.loading) {
                    Text("写回存档（${state.pending.size} 项修改）")
                }
                OutlinedButton(onClick = { vm.discardPending() }, enabled = state.pending.isNotEmpty()) {
                    Text("放弃")
                }
            }
            if (state.backupPath != null) {
                Spacer(Modifier.height(4.dp))
                Text("备份文件：${state.backupPath}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartRow(
    field: FaceField,
    current: Long?,
    refSet: String?,
    onChange: (Int) -> Unit,
    onShowChart: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val refItems = remember(refSet) { refSet?.let { RefCatalog.items(context, it) } ?: emptyList() }
    val currentRef = refItems.firstOrNull { it.index.toLong() == current }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${field.name}（字段 ${field.index}）", fontWeight = FontWeight.Medium)
                    currentRef?.let {
                        AssetImage(
                            asset = it.asset,
                            modifier = Modifier.padding(top = 4.dp).size(64.dp).clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    if (refSet != null) {
                        TextButton(onClick = { onShowChart(refSet) }, contentPadding = PaddingValues(4.dp)) {
                            Text("查看编号参考图", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = current?.toString() ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("编号") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().width(120.dp),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        (0..field.max).forEach { v ->
                            DropdownMenuItem(
                                text = { Text("$v") },
                                onClick = { onChange(v); expanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onChange(((current ?: 0L) - 1).coerceAtLeast(0).toInt()) }) { Text("−") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { onChange(((current ?: 0L) + 1).coerceAtMost(field.max.toLong()).toInt()) }) { Text("+") }
            }
        }
    }
}

@Composable
private fun AssetImage(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp: ImageBitmap? = remember(asset) {
        runCatching {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ColorRow(
    title: String,
    labels: List<String>,
    indices: List<Int>,
    vm: TrainerViewModel,
    onShowChart: (() -> Unit)? = null,
) {
    val values = indices.map { vm.currentFaceValue(it) }
    val s = vm.currentFaceValue(FaceSchema.hairSatIndex)?.toFloat() ?: 0f
    val v = vm.currentFaceValue(FaceSchema.hairValIndex)?.toFloat() ?: 0f
    val swatch = when (title) {
        "瞳色" -> android.graphics.Color.HSVToColor(floatArrayOf(
            vm.currentFaceValue(FaceSchema.eyeHueIndex)?.toFloat() ?: 0f,
            (vm.currentFaceValue(FaceSchema.eyeSatIndex)?.toFloat() ?: 0f) / 400f,
            (vm.currentFaceValue(FaceSchema.eyeValIndex)?.toFloat() ?: 0f) / 400f,
        ))
        "发色" -> android.graphics.Color.HSVToColor(floatArrayOf(
            vm.currentFaceValue(FaceSchema.hairHueIndex)?.toFloat() ?: 0f,
            (vm.currentFaceValue(FaceSchema.hairSatIndex)?.toFloat() ?: 0f) / 400f,
            (vm.currentFaceValue(FaceSchema.hairValIndex)?.toFloat() ?: 0f) / 400f,
        ))
        else -> android.graphics.Color.HSVToColor(floatArrayOf(30f, s / 400f, v / 400f))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (onShowChart != null) {
                    TextButton(onClick = onShowChart, contentPadding = PaddingValues(4.dp)) {
                        Text("参考图", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)).background(Color(swatch)))
            }
            Spacer(Modifier.height(6.dp))
            indices.forEachIndexed { i, idx ->
                val cur = values[i]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${labels[i]}（${idx}）", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.stageFaceEdit(idx, ((cur ?: 0L) - 10).coerceAtLeast(0)) }) { Text("−10") }
                    Spacer(Modifier.width(4.dp))
                    Text("${cur ?: "—"}", modifier = Modifier.width(48.dp))
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = { vm.stageFaceEdit(idx, ((cur ?: 0L) + 10)) }) { Text("+10") }
                }
            }
        }
    }
}
