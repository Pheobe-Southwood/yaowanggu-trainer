package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.msgpack.MpValue
import com.yaowanggu.trainer.data.msgpack.MessagePack

/**
 * Applies field edits to a parsed save and re-serializes.
 * Tree mode: full MessagePack round-trip (values replaced, keys preserved).
 * Raw mode: in-place little-endian int32 patching.
 */
object SaveEditor {

    data class FieldEdit(val ref: FieldRef, val newValue: Long)

    /** Reference to a single editable field. */
    sealed class FieldRef {
        data class TreeField(val loc: SeqLocation, val fieldNo: Int) : FieldRef()
        data class RawField(val offset: Int) : FieldRef()
    }

    /** Read the current value of a field from the structure. */
    fun readField(structure: SaveStructure, ref: FieldRef): Long? = when (ref) {
        is FieldRef.TreeField -> {
            val node = nodeAt(structure.tree ?: return null, ref.loc.fieldIndexPath(ref.fieldNo))
            node?.asIntOrNull()
        }
        is FieldRef.RawField -> {
            val b = structure.bytes
            val o = ref.offset
            if (o < 0 || o + 4 > b.size) null
            else (b[o].toLong() and 0xFF) or
                ((b[o + 1].toLong() and 0xFF) shl 8) or
                ((b[o + 2].toLong() and 0xFF) shl 16) or
                ((b[o + 3].toLong() and 0xFF) shl 24)
        }
    }

    /** Apply edits and return new file bytes. */
    fun apply(structure: SaveStructure, edits: List<FieldEdit>): ByteArray {
        if (structure.tree != null) {
            var t: MpValue = structure.tree
            edits.forEach { e ->
                val ref = e.ref
                if (ref is FieldRef.TreeField) {
                    t = replaceAt(t, ref.loc.fieldIndexPath(ref.fieldNo), MpValue.Int(e.newValue))
                }
            }
            return MessagePack.serialize(t)
        }
        // raw patch mode
        val out = structure.bytes.copyOf()
        edits.forEach { e ->
            val ref = e.ref
            if (ref is FieldRef.RawField) {
                val o = ref.offset
                if (o >= 0 && o + 4 <= out.size) {
                    val v = e.newValue.toInt()
                    out[o] = (v and 0xFF).toByte()
                    out[o + 1] = ((v shr 8) and 0xFF).toByte()
                    out[o + 2] = ((v shr 16) and 0xFF).toByte()
                    out[o + 3] = ((v shr 24) and 0xFF).toByte()
                }
            }
        }
        return out
    }

    private fun nodeAt(root: MpValue, path: List<PathStep>): MpValue? {
        var cur: MpValue? = root
        for (step in path) {
            when (step) {
                is PathStep.Index -> cur = (cur as? MpValue.Arr)?.items?.getOrNull(step.i)
                is PathStep.IntKey -> cur = (cur as? MpValue.Map)?.entries?.get(MpValue.Int(step.k))
                is PathStep.Key -> cur = (cur as? MpValue.Map)?.entries?.get(MpValue.Str(step.k))
            }
            if (cur == null) return null
        }
        return cur
    }

    private fun replaceAt(node: MpValue, path: List<PathStep>, value: MpValue): MpValue {
        if (path.isEmpty()) return value
        val step = path.first()
        return when (step) {
            is PathStep.Index -> {
                val arr = node as? MpValue.Arr ?: return node
                if (step.i !in arr.items.indices) return node
                val new = arr.items.toMutableList()
                new[step.i] = replaceAt(new[step.i], path.drop(1), value)
                MpValue.Arr(new)
            }
            is PathStep.IntKey -> {
                val map = node as? MpValue.Map ?: return node
                val k = MpValue.Int(step.k)
                if (!map.entries.containsKey(k)) return node
                val new = LinkedHashMap<MpValue, MpValue>()
                map.entries.forEach { (kk, vv) -> new[kk] = if (kk == k) replaceAt(vv, path.drop(1), value) else vv }
                MpValue.Map(new)
            }
            is PathStep.Key -> {
                val map = node as? MpValue.Map ?: return node
                val k = MpValue.Str(step.k)
                if (!map.entries.containsKey(k)) return node
                val new = LinkedHashMap<MpValue, MpValue>()
                map.entries.forEach { (kk, vv) -> new[kk] = if (kk == k) replaceAt(vv, path.drop(1), value) else vv }
                MpValue.Map(new)
            }
        }
    }
}
