package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.msgpack.MpValue
import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.schema.CharSchema
import com.yaowanggu.trainer.data.schema.FaceField
import com.yaowanggu.trainer.data.schema.FaceSchema

/** A step into the MessagePack tree, for display and for re-walking after edits. */
sealed class PathStep {
    data class Key(val k: String) : PathStep()
    data class IntKey(val k: Long) : PathStep()
    data class Index(val i: Int) : PathStep()
    override fun toString(): String = when (this) {
        is Key -> "\"$k\""
        is IntKey -> "[$k]"
        is Index -> "[$i]"
    }
}

/** Where a record (face or character) lives. */
data class SeqLocation(
    val path: List<PathStep>,
    val startIndex: Int,
) {
    fun describe(): String = "root" + path.joinToString("") + " @$startIndex"
    fun fieldIndexPath(fieldNo: Int): List<PathStep> = path + PathStep.Index(startIndex + fieldNo - 1)
}

enum class RecordKind { FACE, CHAR }

data class RecordMatch(
    val kind: RecordKind,
    val loc: SeqLocation,
    val score: Int,
    val values: List<Long?>,
)

data class RawMatch(
    val kind: RecordKind,
    val offset: Int,
    val score: Int,
    val values: List<Long?>,
)

data class SaveStructure(
    val bytes: ByteArray,
    val format: String,
    val tree: MpValue?,
    val records: List<RecordMatch>,
    val rawMatches: List<RawMatch>,
    /** 解压/去容器后的真正 msgpack 流；raw 模式偏移基于它。plain 时等于 [bytes]。 */
    val innerBytes: ByteArray = bytes,
    /** 结构化定位到的角色记录（外观模块）；非空时优先于通用评分结果。 */
    val persons: List<PersonFinder.PersonRecord> = emptyList(),
) {
    val bestFace: RecordMatch?
        get() = persons.firstOrNull()?.let {
            RecordMatch(RecordKind.FACE, it.loc, 24, it.faceValues)
        } ?: records.firstOrNull { it.kind == RecordKind.FACE }
    val bestChar: RecordMatch? get() = records.firstOrNull { it.kind == RecordKind.CHAR }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SaveStructure) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
}

object SaveAnalyzer {

    fun analyze(bytes: ByteArray): SaveStructure {
        val dec = com.yaowanggu.trainer.data.codec.SaveCodec.decode(bytes)
        val tree = dec.tree
        if (tree != null) {
            val persons = PersonFinder.findPersons(tree)
            val records = findRecordsInTree(tree)
            return SaveStructure(bytes, dec.container, tree, records, emptyList(), dec.innerBytes, persons)
        }
        val raw = findRecordsInRaw(dec.innerBytes)
        return SaveStructure(bytes, dec.container + "+raw", null, emptyList(), raw, dec.innerBytes)
    }

    // ---------- tree walking ----------

    private fun findRecordsInTree(root: MpValue): List<RecordMatch> {
        val out = ArrayList<RecordMatch>()
        fun walk(v: MpValue, path: List<PathStep>) {
            when (v) {
                is MpValue.Arr -> {
                    val vals = v.items.map { it.asIntOrNull() }
                    emitMatches(vals) { kind, start, values, score ->
                        out.add(RecordMatch(kind, SeqLocation(path, start), score, values))
                    }
                    v.items.forEachIndexed { i, item -> walk(item, path + PathStep.Index(i)) }
                }
                is MpValue.Map -> {
                    val entries = v.entries.toList()
                    val vals = entries.map { it.second.asIntOrNull() }
                    emitMatches(vals) { kind, start, values, score ->
                        out.add(RecordMatch(kind, SeqLocation(path, start), score, values))
                    }
                    entries.forEach { (k, vv) ->
                        val step: PathStep? = when (k) {
                            is MpValue.Str -> PathStep.Key(k.v)
                            is MpValue.Int -> PathStep.IntKey(k.v)
                            else -> null
                        }
                        if (step != null) walk(vv, path + step)
                    }
                }
                else -> Unit
            }
        }
        walk(root, emptyList())
        return out.sortedByDescending { it.score }
    }

    private fun emitMatches(values: List<Long?>, emit: (RecordKind, Int, List<Long?>, Int) -> Unit) {
        if (values.size >= FaceSchema.count) {
            for (start in 0..values.size - FaceSchema.count) {
                val vals = values.subList(start, start + FaceSchema.count)
                val s = scoreFace(vals)
                if (s >= FACE_THRESHOLD) emit(RecordKind.FACE, start, vals.toList(), s)
            }
        }
        if (values.size >= CharSchema.count) {
            for (start in 0..values.size - CharSchema.count) {
                val vals = values.subList(start, start + CharSchema.count)
                val s = scoreChar(vals)
                if (s >= CHAR_THRESHOLD) emit(RecordKind.CHAR, start, vals.toList(), s)
            }
        }
    }

    private const val FACE_THRESHOLD = 16
    private const val CHAR_THRESHOLD = 14

    // ---------- scoring ----------

    private fun scoreFace(v: List<Long?>): Int {
        var score = 0
        FaceSchema.fields.forEachIndexed { i, f ->
            val x = v[i] ?: return@forEachIndexed
            val ok = when (f.kind) {
                FaceField.Kind.ID -> x in 0..99_999
                FaceField.Kind.OPTION -> x in 0..f.max
                FaceField.Kind.TRAIT -> x in 0..f.max
                FaceField.Kind.MOLE -> x in 0..f.max
                FaceField.Kind.HUE -> x in 0..1000
                FaceField.Kind.SAT_VAL -> x in 0..1000
            }
            if (ok) score++
        }
        if ((v[12] ?: -1) in 0..1000 && (v[13] ?: -1) in 0..1000 && (v[14] ?: -1) in 0..1000) score += 2
        if ((v[15] ?: -1) in 0..1000 && (v[16] ?: -1) in 0..1000 && (v[17] ?: -1) in 0..1000) score += 2
        // 退化窗口降权：全零 / 无 ID 的窗口不是真脸（实证：会压过真窗口）
        if (v.subList(1, FaceSchema.count).all { (it ?: 0L) == 0L }) score -= 8
        if ((v[0] ?: 0) < 1) score -= 4
        return score
    }

    private fun scoreChar(v: List<Long?>): Int {
        var score = 0
        if ((v[0] ?: -1) in 0..1_000_000) score += 2
        if ((v[1] ?: -1) in 1..12) score += 3
        if ((v[2] ?: -1) in 1..31) score += 3
        if ((v[4] ?: -1) in 1..4) score += 2
        if ((v[17] ?: -1) in 1..8) score += 2
        for (i in 21..28) if ((v[i] ?: -1) in 0..1) score++
        if ((v[30] ?: -1) in 0..99_999_999) score++
        if ((v[31] ?: -1) in 0..99_999_999) score++
        if ((v[38] ?: -1) in 0..99_999_999) score++
        return score
    }

    // ---------- raw binary fallback ----------

    private fun findRecordsInRaw(bytes: ByteArray): List<RawMatch> {
        val out = ArrayList<RawMatch>()
        val n = bytes.size / 4
        if (n < FaceSchema.count) return out
        fun at(i: Int): Long? {
            if (i < 0 || i >= n) return null
            val o = i * 4
            return (bytes[o].toLong() and 0xFF) or
                ((bytes[o + 1].toLong() and 0xFF) shl 8) or
                ((bytes[o + 2].toLong() and 0xFF) shl 16) or
                ((bytes[o + 3].toLong() and 0xFF) shl 24)
        }
        for (start in 0..n - FaceSchema.count) {
            val vals = (0 until FaceSchema.count).map { at(start + it) }
            val s = scoreFace(vals)
            if (s >= FACE_THRESHOLD) out.add(RawMatch(RecordKind.FACE, start * 4, s, vals))
        }
        for (start in 0..n - CharSchema.count) {
            val vals = (0 until CharSchema.count).map { at(start + it) }
            val s = scoreChar(vals)
            if (s >= CHAR_THRESHOLD) out.add(RawMatch(RecordKind.CHAR, start * 4, s, vals))
        }
        return out.sortedByDescending { it.score }
    }
}
