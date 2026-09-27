package com.yaowanggu.trainer.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaowanggu.trainer.data.RecordKind
import com.yaowanggu.trainer.data.RecordMatch
import com.yaowanggu.trainer.data.SaveAnalyzer
import com.yaowanggu.trainer.data.export.DiagExporter
import com.yaowanggu.trainer.data.SaveEditor
import com.yaowanggu.trainer.data.SaveStructure
import com.yaowanggu.trainer.shizuku.ShellBackend
import com.yaowanggu.trainer.shizuku.ShizukuRepository
import com.yaowanggu.trainer.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val binderAlive: Boolean = false,
    val permissionOk: Boolean = false,
    val whoami: String = "",
    val backendName: String = "unknown",
    val gameRunning: Boolean? = null,
    val slots: List<ShellBackend.Slot> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val slotPath: String? = null,
    val slotLabel: String = "",
    val structure: SaveStructure? = null,
    val faceRecord: RecordMatch? = null,
    val charRecord: RecordMatch? = null,
    val faceValues: List<Long?> = emptyList(),
    val charValues: List<Long?> = emptyList(),
    val pending: Map<String, Long> = emptyMap(),
    val backupPath: String? = null,
    val rawFaceOffset: Int? = null,
    val rawCharOffset: Int? = null,
) {
    val faceReady: Boolean get() = faceRecord != null || rawFaceOffset != null
    val isTree: Boolean get() = structure?.tree != null
}

class TrainerViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshShizuku()
    }

    fun refreshShizuku(ctx: Context? = null) {
        val alive = ShizukuRepository.binderAlive()
        val perm = ShizukuRepository.permissionGranted()
        _state.value = _state.value.copy(binderAlive = alive, permissionOk = perm)
        viewModelScope.launch {
            if (perm && ctx != null) {
                val who = withContext(Dispatchers.IO) { runCatching { ShizukuRepository.whoAmI(ctx) }.getOrDefault("") }
                _state.value = _state.value.copy(whoami = who, backendName = ShizukuRepository.activeBackendName())
            }
        }
    }

    fun onShizukuPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(permissionOk = granted)
        if (granted) refreshShizuku()
    }

    fun requestShizukuPermission() {
        ShizukuRepository.requestPermission { ok -> onShizukuPermissionResult(ok) }
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    fun loadSlots(ctx: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                val (slots, running) = withContext(Dispatchers.IO) {
                    ShizukuRepository.listSaveSlots(ctx) to runCatching { ShizukuRepository.gameRunning(ctx) }.getOrDefault(false)
                }
                _state.value = _state.value.copy(slots = slots, gameRunning = running, loading = false)
                if (slots.isEmpty()) {
                    _state.value = _state.value.copy(message = "未找到存档文件。请在真机上进入游戏并保存一次后再试；已扫描 /sdcard/Android/data/com.hydrozoa.yyg 与 /sdcard/Android/media/com.hydrozoa.yyg。")
                } else if (_state.value.slotPath == null) {
                    // 自动打开 mtime 最新的槽（游戏在用的存档）
                    AppLog.i("auto-open recommended slot ${slots[0].slot} (${slots[0].path})")
                    openSlot(ctx, slots[0])
                }
            } catch (e: Throwable) {
                val d = ShizukuRepository.diagnose(ctx)
                _state.value = _state.value.copy(
                    loading = false,
                    backendName = d.activeBackend,
                    message = buildString {
                        append("读取存档列表失败: ${e::class.java.simpleName}: ${e.message}\n")
                        if (d.activeBackend == "unknown") append("（两条通道都试过了，详见诊断页）\n")
                        append("诊断：version=${d.serverVersion} uid=${d.serverUid} binder=${d.binderAlive} perm=${d.permissionOk}\n")
                        append(d.notes.take(3).joinToString("\n"))
                    },
                )
            }
        }
    }

    fun openSlot(ctx: Context, slot: ShellBackend.Slot) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                val running = withContext(Dispatchers.IO) {
                    runCatching { ShizukuRepository.gameRunning(ctx) }.getOrDefault(false)
                }
                if (running) {
                    _state.value = _state.value.copy(gameRunning = true)
                }
                val bytes = withContext(Dispatchers.IO) { ShizukuRepository.readFile(ctx, slot.path) }
                val st = SaveAnalyzer.analyze(bytes)
                val face = st.bestFace
                val char = st.bestChar
                val rawFace = st.rawMatches.firstOrNull { it.kind == RecordKind.FACE }?.offset
                val rawChar = st.rawMatches.firstOrNull { it.kind == RecordKind.CHAR }?.offset
                val baseMsg = when {
                    face != null -> "已识别外观记录（${st.format}）"
                    rawFace != null -> "未识别结构化外观，已定位到疑似二进制偏移"
                    else -> "未能自动定位外观数据，请到“诊断”页查看结构"
                }
                AppLog.i("openSlot ${slot.slot}: container=${st.format} inner=${st.innerBytes.size} face=${face != null} rawFace=${rawFace != null} records=${st.records.size}")
                val msg = if (running) "⚠️ 游戏正在运行，修改会被覆盖！\n$baseMsg" else baseMsg
                _state.value = _state.value.copy(
                    loading = false,
                    message = msg,
                    slotPath = slot.path,
                    slotLabel = "槽位 ${slot.slot} · ${slot.size} 字节",
                    structure = st,
                    faceRecord = face,
                    charRecord = char,
                    faceValues = face?.values ?: emptyList(),
                    charValues = char?.values ?: emptyList(),
                    rawFaceOffset = rawFace,
                    rawCharOffset = rawChar,
                    pending = emptyMap(),
                    backupPath = null,
                )
            } catch (e: Throwable) {
                AppLog.e("openSlot failed: ${e.message}", e)
                _state.value = _state.value.copy(loading = false, message = "打开存档失败: ${e.message}")
            }
        }
    }

    /** Stage an edit (not yet written). */
    fun stageFaceEdit(fieldNo: Int, value: Long) {
        val k = "face:$fieldNo"
        _state.value = _state.value.copy(pending = _state.value.pending + (k to value))
    }

    fun stageCharEdit(fieldNo: Int, value: Long) {
        val k = "char:$fieldNo"
        _state.value = _state.value.copy(pending = _state.value.pending + (k to value))
    }

    fun stageRawFaceEdit(fieldNo: Int, value: Long) {
        val k = "rawface:$fieldNo"
        _state.value = _state.value.copy(pending = _state.value.pending + (k to value))
    }

    fun discardPending() { _state.value = _state.value.copy(pending = emptyMap()) }

    /** Build edits and write back (with .bak backup first). */
    fun writeBack(ctx: Context) {
        val s = _state.value
        val st = s.structure ?: return
        val path = s.slotPath ?: return
        if (s.gameRunning == true) {
            _state.value = _state.value.copy(message = "游戏仍在运行！请先完全退出游戏再写回。")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                val edits = ArrayList<SaveEditor.FieldEdit>()
                s.pending.forEach { (k, v) ->
                    when {
                        k.startsWith("face:") -> {
                            val no = k.removePrefix("face:").toInt()
                            val faceMatch = s.faceRecord
                            if (faceMatch != null) {
                                edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(faceMatch.loc, no), v))
                            } else {
                                val off = s.rawFaceOffset
                                if (off != null) edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.RawField(off + (no - 1) * 4), v))
                            }
                        }
                        k.startsWith("char:") -> {
                            val no = k.removePrefix("char:").toInt()
                            val charMatch = s.charRecord
                            if (charMatch != null) edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(charMatch.loc, no), v))
                            else {
                                val off = s.rawCharOffset
                                if (off != null) edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.RawField(off + (no - 1) * 4), v))
                            }
                        }
                        k.startsWith("rawface:") -> {
                            val no = k.removePrefix("rawface:").toInt()
                            val off = s.rawFaceOffset
                            if (off != null) edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.RawField(off + (no - 1) * 4), v))
                        }
                    }
                }
                if (edits.isEmpty()) {
                    _state.value = _state.value.copy(loading = false, message = "没有待写入的修改")
                    return@launch
                }
                val (newBytes, outSize) = withContext(Dispatchers.IO) {
                    val backup = "$path.bak"
                    ShizukuRepository.copyFile(ctx, path, backup)
                    val out = SaveEditor.apply(st, edits)
                    ShizukuRepository.writeFile(ctx, path, out)
                    backup to out.size
                }
                AppLog.i("writeBack ok: $path backup=$newBytes bytes=$outSize edits=${edits.size}")
                _state.value = _state.value.copy(loading = false, backupPath = newBytes, pending = emptyMap(), message = "已写回（备份: $newBytes）。请重开游戏查看。")
                // reload values
                openSlot(ctx, s.slots.firstOrNull { it.path == path } ?: return@launch)
            } catch (e: Throwable) {
                AppLog.e("writeBack failed: ${e.message}", e)
                _state.value = _state.value.copy(loading = false, message = "写回失败: ${e.message}")
            }
        }
    }

    fun currentFaceValue(fieldNo: Int): Long? {
        val s = _state.value
        val staged = s.pending["face:$fieldNo"] ?: s.pending["rawface:$fieldNo"]
        if (staged != null) return staged
        return when {
            s.faceRecord != null && s.faceRecord.values.size >= fieldNo -> s.faceRecord.values[fieldNo - 1]
            s.rawFaceOffset != null -> {
                val off = s.rawFaceOffset + (fieldNo - 1) * 4
                readLe32(s.structure?.innerBytes, off)
            }
            else -> null
        }
    }

    fun currentCharValue(fieldNo: Int): Long? {
        val s = _state.value
        val staged = s.pending["char:$fieldNo"]
        if (staged != null) return staged
        return when {
            s.charRecord != null && s.charRecord.values.size >= fieldNo -> s.charRecord.values[fieldNo - 1]
            s.rawCharOffset != null -> readLe32(s.structure?.innerBytes, s.rawCharOffset + (fieldNo - 1) * 4)
            else -> null
        }
    }

    private fun readLe32(b: ByteArray?, off: Int): Long? {
        b ?: return null
        if (off < 0 || off + 4 > b.size) return null
        return (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)
    }

    // ---------------- 导出诊断包 ----------------

    /** 组装诊断包条目（纯数据，不碰 IO）。单槽 >8MB 跳过并注明。 */
    suspend fun buildExportBundle(ctx: Context): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val s = _state.value
        val entries = LinkedHashMap<String, ByteArray>()

        val d = ShizukuRepository.diagnose(ctx)
        val probe = runCatching { ShizukuRepository.probeSaves(ctx) }.getOrDefault("(probe failed)")
        val diagText = buildString {
            appendLine("=== 药王谷修改器 诊断信息 ===")
            appendLine("app=${com.yaowanggu.trainer.BuildConfig.VERSION_NAME}(${com.yaowanggu.trainer.BuildConfig.VERSION_CODE})")
            appendLine("device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
            appendLine("binder=${d.binderAlive} permission=${d.permissionOk}")
            appendLine("serverVersion=${d.serverVersion} serverUid=${d.serverUid}")
            appendLine("selinux=${d.selinuxContext}")
            appendLine("backend=${d.activeBackend}")
            appendLine("disabled=${d.disabledReason}")
            d.notes.forEach { appendLine("note: $it") }
            appendLine("openedSlot=${s.slotPath ?: "(none)"} container=${s.structure?.format ?: "-"} innerSize=${s.structure?.innerBytes?.size ?: 0}")
            appendLine("--- 槽位列表 ---")
            s.slots.forEach { appendLine("slot=${it.slot} size=${it.size} mtime=${it.mtimeSec} path=${it.path}") }
            appendLine("--- 存档探测/目录清单 ---")
            appendLine(probe)
        }
        entries["diagnostics.txt"] = diagText.toByteArray(Charsets.UTF_8)
        entries["applog.txt"] = AppLog.snapshotText().toByteArray(Charsets.UTF_8)

        var skipped = 0
        s.slots.forEach { slot ->
            if (slot.size > 8L * 1024 * 1024) { skipped++; return@forEach }
            runCatching { ShizukuRepository.readFile(ctx, slot.path) }
                .onSuccess { entries["saves/nfile${slot.slot}.save"] = it }
                .onFailure { entries["saves/nfile${slot.slot}.ERROR.txt"] = it.stackTraceToString().toByteArray() }
        }
        s.structure?.let { st ->
            if (st.format != "msgpack") entries["inner/opened.inner.bin"] = st.innerBytes
        }
        if (skipped > 0) {
            entries["diagnostics.txt"] = (diagText + "\n(skipped $skipped slots >8MB)\n").toByteArray(Charsets.UTF_8)
        }
        entries
    }

    /** 把诊断包 zip 写到用户通过 SAF 选择的位置。 */
    fun exportDiag(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val bundle = buildExportBundle(ctx)
                val zip = DiagExporter.buildZip(bundle)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(zip) }
                        ?: throw IllegalStateException("openOutputStream returned null")
                }
                AppLog.i("export ok: entries=${bundle.size} zip=${zip.size}")
                _state.value = _state.value.copy(message = "已导出诊断包（${bundle.size} 个条目，${zip.size} 字节）。请把这个 zip 发给我。")
            } catch (e: Throwable) {
                AppLog.e("export failed: ${e.message}", e)
                _state.value = _state.value.copy(message = "导出失败: ${e.message}")
            }
        }
    }
}
