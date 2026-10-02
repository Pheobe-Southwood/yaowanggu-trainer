package com.yaowanggu.trainer.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaowanggu.trainer.data.CharMap
import com.yaowanggu.trainer.data.PathStep
import com.yaowanggu.trainer.data.PersonFinder
import com.yaowanggu.trainer.data.SeqLocation
import com.yaowanggu.trainer.data.RecordKind
import com.yaowanggu.trainer.data.RecordMatch
import com.yaowanggu.trainer.data.SaveAnalyzer
import com.yaowanggu.trainer.data.codec.SaveCodec
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
    /** path → 模块扫描信息（容器、角色数） */
    val moduleInfo: Map<String, ModuleInfo> = emptyMap(),
    /** 当前选中角色在 structure.persons 中的下标 */
    val selectedPerson: Int = 0,
    /** 游戏目录中的残留文件（tmp / 旧 .bak），非空时提示清理 */
    val strayFiles: List<String> = emptyList(),
    /** 用户标记的主角 charId（持久化）；未标记为 null */
    val markedCharId: Long? = null,
    /** 用户给角色的命名（charId → 别名，持久化在本机 prefs，不写入存档） */
    val aliases: Map<Long, String> = emptyMap(),
) {
    val faceReady: Boolean get() = faceRecord != null || rawFaceOffset != null
    val isTree: Boolean get() = structure?.tree != null
    val persons: List<PersonFinder.PersonRecord> get() = structure?.persons ?: emptyList()
    val currentPerson: PersonFinder.PersonRecord? get() = persons.getOrNull(selectedPerson)
}

data class ModuleInfo(
    val container: String,
    val personCount: Int,
)

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
                val (slots0, running) = withContext(Dispatchers.IO) {
                    ShizukuRepository.listSaveSlots(ctx) to runCatching { ShizukuRepository.gameRunning(ctx) }.getOrDefault(false)
                }
                // 模块模型：nfileN 是同一存档的数据模块，mtime 全部相同（批量写），按编号排序
                val slots = slots0.sortedBy { it.slot }
                // 全模块扫描：解码 + 角色定位，找出外观模块
                val infos = withContext(Dispatchers.IO) { scanModules(ctx, slots) }
                val stray = withContext(Dispatchers.IO) {
                    val dir = slots.firstOrNull()?.path?.substringBeforeLast('/')
                    if (dir != null) runCatching { ShizukuRepository.listStrayFiles(ctx, dir) }.getOrDefault(emptyList()) else emptyList()
                }
                if (stray.isNotEmpty()) AppLog.w("loadSlots: stray files in game dir: $stray")
                _state.value = _state.value.copy(
                    slots = slots, gameRunning = running, moduleInfo = infos, loading = false, strayFiles = stray,
                    message = if (stray.isNotEmpty()) "游戏目录检测到 ${stray.size} 个残留文件（${stray.first()}…），建议点「清理残留」移到备份目录，避免干扰游戏读写。" else null,
                )
                if (slots.isEmpty()) {
                    _state.value = _state.value.copy(message = "未找到存档文件。请在真机上进入游戏并保存一次后再试；已扫描 /sdcard/Android/data/com.hydrozoa.yyg 与 /sdcard/Android/media/com.hydrozoa.yyg。")
                } else if (_state.value.slotPath == null) {
                    val faceSlot = slots.firstOrNull { (infos[it.path]?.personCount ?: 0) > 0 }
                        ?: slots.filter { infos[it.path]?.container?.startsWith("msgpack") == true || infos[it.path]?.container?.contains("msgpack") == true }
                            .maxByOrNull { it.size }
                    if (faceSlot != null) {
                        AppLog.i("auto-open module slot=${faceSlot.slot} persons=${infos[faceSlot.path]?.personCount} (${faceSlot.path})")
                        openSlot(ctx, faceSlot)
                    }
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

    /** 逐模块 decode + PersonFinder（不做全量记录扫描，速度优先）。 */
    private suspend fun scanModules(ctx: Context, slots: List<ShellBackend.Slot>): Map<String, ModuleInfo> =
        withContext(Dispatchers.IO) {
            val out = LinkedHashMap<String, ModuleInfo>()
            slots.forEach { slot ->
                if (slot.size > 8L * 1024 * 1024) {
                    out[slot.path] = ModuleInfo("too-large", 0)
                    return@forEach
                }
                val info = runCatching {
                    val bytes = ShizukuRepository.readFile(ctx, slot.path)
                    val dec = SaveCodec.decode(bytes)
                    val persons = PersonFinder.findPersons(dec.tree)
                    ModuleInfo(dec.container, persons.size)
                }.getOrElse { e ->
                    AppLog.w("scanModules: slot ${slot.slot} failed: ${e.message}")
                    ModuleInfo("error", 0)
                }
                out[slot.path] = info
                AppLog.i("scanModules: slot=${slot.slot} size=${slot.size} container=${info.container} persons=${info.personCount}")
            }
            out
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
                    st.persons.isNotEmpty() -> "已识别外观模块（${st.format}）· ${st.persons.size} 个角色，默认选中 ID ${st.persons[0].charId}（通常是玩家主角），可在「五官」页切换"
                    face != null -> "已识别外观记录（${st.format}）"
                    rawFace != null -> "未识别结构化外观，已定位到疑似二进制偏移"
                    else -> "此模块未检测到外观数据（${st.format}），可在列表中选择其它模块"
                }
                AppLog.i("openSlot ${slot.slot}: container=${st.format} inner=${st.innerBytes.size} persons=${st.persons.size} face=${face != null} rawFace=${rawFace != null} records=${st.records.size}")
                val marked = readMarkedCharId(ctx)
                val aliasMap = readAliases(ctx)
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
                    selectedPerson = markedIndex(st.persons, marked),
                    markedCharId = marked,
                    aliases = aliasMap,
                    pending = emptyMap(),
                    backupPath = null,
                )
            } catch (e: Throwable) {
                AppLog.e("openSlot failed: ${e.message}", e)
                _state.value = _state.value.copy(loading = false, message = "打开存档失败: ${e.message}")
            }
        }
    }

    private fun readMarkedCharId(ctx: Context): Long? {
        val id = runCatching {
            ctx.getSharedPreferences("dsh_prefs", Context.MODE_PRIVATE).getLong("protagonist_char_id", -1L)
        }.getOrDefault(-1L)
        return if (id > 0) id else null
    }

    /** 读取全部角色别名（prefs 键 alias_<charId>）。 */
    private fun readAliases(ctx: Context): Map<Long, String> {
        return runCatching {
            val all = ctx.getSharedPreferences("dsh_prefs", Context.MODE_PRIVATE).all
            val out = LinkedHashMap<Long, String>()
            all.forEach { (k, v) ->
                if (k.startsWith("alias_") && v is String && v.isNotBlank()) {
                    k.removePrefix("alias_").toLongOrNull()?.let { out[it] = v }
                }
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** 给角色命名（空串=清除）。仅存本机 prefs，便于查找，不写入存档。 */
    fun setAlias(ctx: Context, charId: Long, name: String) {
        runCatching {
            val prefs = ctx.getSharedPreferences("dsh_prefs", Context.MODE_PRIVATE)
            val key = "alias_$charId"
            if (name.isBlank()) prefs.edit().remove(key).apply()
            else prefs.edit().putString(key, name.trim()).apply()
        }
        AppLog.i("setAlias: charId=$charId name=$name")
        val next = _state.value.aliases.toMutableMap()
        if (name.isBlank()) next.remove(charId) else next[charId] = name.trim()
        _state.value = _state.value.copy(aliases = next)
    }

    private fun markedIndex(persons: List<PersonFinder.PersonRecord>, marked: Long?): Int {
        if (marked == null) return 0
        val idx = persons.indexOfFirst { it.charId == marked }
        return if (idx >= 0) idx else 0
    }

    /** 标记当前选中角色为主角（charId 持久化；charId 跨存档重排稳定）。 */
    fun markProtagonist(ctx: Context) {
        val p = _state.value.currentPerson ?: return
        runCatching {
            ctx.getSharedPreferences("dsh_prefs", Context.MODE_PRIVATE).edit()
                .putLong("protagonist_char_id", p.charId).apply()
        }
        AppLog.i("markProtagonist: charId=${p.charId}")
        _state.value = _state.value.copy(markedCharId = p.charId)
    }

    /** 切换选中角色（persons 下标）。 */
    fun selectPerson(index: Int) {
        val s = _state.value
        val p = s.persons.getOrNull(index) ?: return
        val face = RecordMatch(RecordKind.FACE, p.loc, 24, p.faceValues)
        AppLog.i("selectPerson: index=$index charId=${p.charId} recordIndex=${p.recordIndex}")
        _state.value = s.copy(selectedPerson = index, faceRecord = face, faceValues = p.faceValues)
    }

    /** 从最近一次备份恢复当前模块（同样走原子写）。 */
    fun restoreBackup(ctx: Context) {
        viewModelScope.launch {
            val s = _state.value
            val path = s.slotPath ?: return@launch
            val bak = s.backupPath ?: return@launch
            try {
                _state.value = _state.value.copy(loading = true, message = null)
                withContext(Dispatchers.IO) {
                    val bytes = ShizukuRepository.readFile(ctx, bak)
                    ShizukuRepository.writeFileAtomic(ctx, path, bytes) { written ->
                        check(written.contentEquals(bytes)) { "恢复回读不一致" }
                    }
                }
                AppLog.i("restoreBackup: $bak -> $path")
                _state.value = _state.value.copy(loading = false, message = "已从备份恢复：$bak")
                openSlot(ctx, s.slots.firstOrNull { it.path == path } ?: return@launch)
            } catch (e: Throwable) {
                AppLog.e("restoreBackup failed: ${e.message}", e)
                _state.value = _state.value.copy(loading = false, message = "恢复失败: ${e.message}")
            }
        }
    }

    /** 把游戏目录残留文件移到备份目录。 */
    fun cleanStrayFiles(ctx: Context) {
        viewModelScope.launch {
            val dir = _state.value.slots.firstOrNull()?.path?.substringBeforeLast('/') ?: return@launch
            try {
                val out = withContext(Dispatchers.IO) { ShizukuRepository.cleanStrayFiles(ctx, dir) }
                val left = withContext(Dispatchers.IO) { ShizukuRepository.listStrayFiles(ctx, dir) }
                _state.value = _state.value.copy(strayFiles = left, message = "清理完成（$out），残留 ${left.size} 个。备份在 ${ShizukuRepository.BACKUP_DIR}")
            } catch (e: Throwable) {
                _state.value = _state.value.copy(message = "清理失败: ${e.message}")
            }
        }
    }

    /** 按角色 ID 跳转选择。 */
    fun selectPersonByCharId(charId: Long) {
        val idx = _state.value.persons.indexOfFirst { it.charId == charId }
        if (idx >= 0) selectPerson(idx)
        else _state.value = _state.value.copy(message = "未找到角色 ID $charId")
    }

    /** 当前选中角色的 pending 键前缀。 */
    private fun faceKeyPrefix(): String {
        val p = _state.value.currentPerson
        return if (p != null) "face:${p.recordIndex}:" else "face:gen:"
    }

    /** Stage an edit (not yet written). */
    fun stageFaceEdit(fieldNo: Int, value: Long) {
        val k = faceKeyPrefix() + fieldNo
        _state.value = _state.value.copy(pending = _state.value.pending + (k to value))
    }

    /** 属性编辑键：char:<recordIndex>:<slotKey>，定位到选中角色记录内的校准偏移。 */
    fun stageCharEdit(slotKey: String, value: Long) {
        val rec = _state.value.currentPerson?.recordIndex ?: return
        val k = "char:$rec:$slotKey"
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
                            val rest = k.removePrefix("face:")
                            val sep = rest.indexOf(':')
                            val who = if (sep >= 0) rest.substring(0, sep) else "gen"
                            val no = (if (sep >= 0) rest.substring(sep + 1) else rest).toInt()
                            val person = if (who != "gen") who.toIntOrNull()?.let { ri -> s.persons.firstOrNull { it.recordIndex == ri } } else null
                            when {
                                person != null -> edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(person.loc, no), v))
                                who == "gen" && s.faceRecord != null -> edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(s.faceRecord.loc, no), v))
                                else -> {
                                    val off = s.rawFaceOffset
                                    if (off != null) edits.add(SaveEditor.FieldEdit(SaveEditor.FieldRef.RawField(off + (no - 1) * 4), v))
                                }
                            }
                        }
                        k.startsWith("char:") -> {
                            val rest = k.removePrefix("char:")
                            val sep = rest.indexOf(':')
                            if (sep > 0) {
                                val ri = rest.substring(0, sep).toIntOrNull()
                                val key = rest.substring(sep + 1)
                                val person = ri?.let { r -> s.persons.firstOrNull { it.recordIndex == r } }
                                val slot = CharMap.slots.firstOrNull { it.key == key }
                                val off = if (person != null && slot != null) CharMap.offsetOf(person, slot) else null
                                if (person != null && slot != null && off != null) {
                                    // pending 存的是面板显示值；按槽位定点倍数换算成存档原值（灵气 ×100）
                                    val raw = CharMap.rawFor(slot, v)
                                    AppLog.i("writeBack: char edit $k display=$v -> raw=$raw (scale=${slot.scale})")
                                    edits.add(
                                        SaveEditor.FieldEdit(
                                            SaveEditor.FieldRef.TreeField(
                                                SeqLocation(listOf(PathStep.Index(person.recordIndex)), 0),
                                                off + 1,
                                            ),
                                            raw,
                                        )
                                    )
                                } else {
                                    AppLog.w("writeBack: unmapped char edit ignored: $k")
                                }
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
                val prevCharId = s.currentPerson?.charId
                val expectContainer = st.format
                val res = withContext(Dispatchers.IO) {
                    // 备份放游戏目录之外
                    val bak = ShizukuRepository.backupSave(ctx, path)
                    val newInner = SaveEditor.apply(st, edits)
                    // 按原容器重新打包（zlib 模块必须重新压缩，否则游戏解压失败回退 _backup）
                    val out = SaveCodec.encode(st.format, newInner, st.bytes)
                    // 原子写：tmp → 自检 → mv → 回读验证
                    ShizukuRepository.writeFileAtomic(ctx, path, out) { written ->
                        val dec = SaveCodec.decode(written)
                        check(dec.container == expectContainer.removeSuffix("+raw")) {
                            "写回后容器不符: ${dec.container} != $expectContainer"
                        }
                    }
                    val check = ShizukuRepository.readFile(ctx, path)
                    val stReload = SaveAnalyzer.analyze(check)
                    val idx = if (prevCharId != null) {
                        stReload.persons.indexOfFirst { it.charId == prevCharId }.let { if (it >= 0) it else 0 }
                    } else 0
                    WriteBackResult(bak, out.size, stReload, idx)
                }
                val bak2 = res.backup; val size2 = res.outSize; val stR = res.structure; val sel = res.selIdx
                AppLog.i("writeBack ok: $path backup=$bak2 bytes=$size2 edits=${edits.size} container=${stR.format} persons=${stR.persons.size}")
                val p2 = stR.persons.getOrNull(sel)
                val face2 = p2?.let { RecordMatch(RecordKind.FACE, it.loc, 24, it.faceValues) } ?: stR.bestFace
                _state.value = _state.value.copy(
                    loading = false,
                    backupPath = bak2,
                    pending = emptyMap(),
                    structure = stR,
                    faceRecord = face2,
                    faceValues = p2?.faceValues ?: face2?.values ?: emptyList(),
                    selectedPerson = sel,
                    message = "已写回（${stR.format}，备份: $bak2，回读验证 ${stR.persons.size} 个角色）。请重开游戏查看。",
                )
            } catch (e: Throwable) {
                AppLog.e("writeBack failed: ${e.message}", e)
                _state.value = _state.value.copy(loading = false, message = "写回失败: ${e.message}")
            }
        }
    }

    fun currentFaceValue(fieldNo: Int): Long? {
        val s = _state.value
        val staged = s.pending[faceKeyPrefix() + fieldNo]
            ?: s.pending["face:gen:$fieldNo"]
            ?: s.pending["rawface:$fieldNo"]
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

    fun currentCharValue(slotKey: String): Long? {
        val s = _state.value
        val person = s.currentPerson ?: return null
        val rec = person.recordIndex
        val staged = s.pending["char:$rec:$slotKey"]
        if (staged != null) return staged
        val slot = CharMap.slots.firstOrNull { it.key == slotKey } ?: return null
        return CharMap.valueOf(person, slot)
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
            appendLine("backupDir=${ShizukuRepository.BACKUP_DIR} lastBackup=${s.backupPath ?: "-"}")
            appendLine("strayFiles=${s.strayFiles}")
            s.currentPerson?.let {
                appendLine("selectedPerson: charId=${it.charId} recordIndex=${it.recordIndex} faceStart=${it.loc.startIndex} recordLen=${it.recordLen}")
                appendLine("faceValues=${it.faceValues}")
                appendLine(
                    "charFields: realm=${CharMap.realmOf(it)} stage=${CharMap.stageOf(it)} life=${CharMap.valueOf(it, CharMap.slot("life")!!)}" +
                        " lifeMaxDerived=${CharMap.lifeMaxOf(it)} qiRaw=${CharMap.rawValueOf(it, CharMap.slot("qi")!!)}" +
                        " qi=${CharMap.valueOf(it, CharMap.slot("qi")!!)} qiMaxDerived=${CharMap.qiMaxOf(it)}" +
                        " power=${CharMap.valueOf(it, CharMap.slot("power")!!)}" +
                        " birth=${CharMap.birthOf(it)} gender=${CharMap.valueOf(it, CharMap.slot("gender")!!)}" +
                        " location=${CharMap.valueOf(it, CharMap.slot("location")!!)} sect=${CharMap.valueOf(it, CharMap.slot("sect")!!)}" +
                        " race=${CharMap.valueOf(it, CharMap.slot("race")!!)} rootType=${CharMap.rootTypeOf(it)}" +
                        " rootFlags=${CharMap.rootFlagsOf(it)} roots=${CharMap.rootsString(it)}",
                )
            }
            appendLine("--- 模块列表 ---")
            s.slots.forEach {
                val mi = s.moduleInfo[it.path]
                appendLine("slot=${it.slot} size=${it.size} container=${mi?.container ?: "-"} persons=${mi?.personCount ?: 0} path=${it.path}")
            }
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

private data class WriteBackResult(
    val backup: String,
    val outSize: Int,
    val structure: SaveStructure,
    val selIdx: Int,
)
