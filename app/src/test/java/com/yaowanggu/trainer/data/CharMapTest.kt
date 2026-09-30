package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.codec.SaveCodec
import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharMapTest {

    /** 合成记录：face@84 → 寿元@21/22、灵气/武力@64/65（face-63/-62/-20/-19）。 */
    private fun record(id: Int, life: Long, lifeMax: Long, qi: Long, power: Long): List<Long> {
        val r = MutableList(105) { 0L }
        r[21] = life
        r[22] = lifeMax
        r[64] = qi
        r[65] = power
        val face = listOf(
            id.toLong(), 0, 1, 1, 1, 1, 1, 1, 2, 0,
            78, 118, 169, 84, 56, 229, 79, 161, 10, 8,
        )
        for (j in face.indices) r[84 + j] = face[j]
        return r
    }

    private fun structureOf(records: List<List<Long>>): SaveStructure {
        val tree = MpValue.Arr(records.map { MpValue.Arr(it.map { v -> MpValue.Int(v) }) })
        return SaveAnalyzer.analyze(MessagePack.serialize(tree))
    }

    @Test
    fun calibratedOffsetsReadPanelValues() {
        val st = structureOf(listOf(record(1, 10, 60, 0, 1), record(2, 23, 60, 5, 7)))
        assertEquals(2, st.persons.size)
        val p0 = st.persons[0]
        assertEquals(10L, CharMap.valueOf(p0, CharMap.slots[0]))   // 寿元当前
        assertEquals(60L, CharMap.valueOf(p0, CharMap.slots[1]))   // 寿元上限
        assertEquals(0L, CharMap.valueOf(p0, CharMap.slots[2]))    // 灵气
        assertEquals(1L, CharMap.valueOf(p0, CharMap.slots[3]))    // 武力
        val p1 = st.persons[1]
        assertEquals(23L, CharMap.valueOf(p1, CharMap.slots[0]))
        assertEquals(7L, CharMap.valueOf(p1, CharMap.slots[3]))
    }

    @Test
    fun editRoundTripThroughZlibContainer() {
        val tree = MpValue.Arr(listOf(record(1, 10, 60, 0, 1)).map { MpValue.Arr(it.map { MpValue.Int(it) }) })
        val inner = MessagePack.serialize(tree)
        val gameFile = TestWrap.zlib(inner)
        val st = SaveAnalyzer.analyze(gameFile)
        val p = st.persons[0]
        val off = CharMap.offsetOf(p, CharMap.slots[0])!!
        assertEquals(21, off)

        val edited = SaveEditor.apply(st, listOf(
            SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(SeqLocation(listOf(PathStep.Index(0)), 0), off + 1), 999L),
        ))
        val out = SaveCodec.encode(st.format, edited, st.bytes)
        val st2 = SaveAnalyzer.analyze(out)
        assertEquals("zlib+msgpack", st2.format)
        assertEquals(999L, CharMap.valueOf(st2.persons[0], CharMap.slots[0]))
        assertEquals(60L, CharMap.valueOf(st2.persons[0], CharMap.slots[1])) // 未动字段不变
        assertEquals(1L, CharMap.valueOf(st2.persons[0], CharMap.slots[3]))
    }

    @Test
    fun shortRecordHasNoStats() {
        // face 太靠前 → 偏移为负 → 不可编辑
        val short = MutableList(30) { 0L }
        val face = listOf(1L, 1, 2, 3, 4, 5, 6, 7, 8, 0, 78, 118, 169, 84, 56, 229, 79, 161, 10, 8)
        for (j in face.indices) short[10 + j] = face[j]
        val tree = MpValue.Arr(listOf(MpValue.Arr(short.map { MpValue.Int(it) })))
        val st = SaveAnalyzer.analyze(MessagePack.serialize(tree))
        if (st.persons.isNotEmpty()) {
            assertNull(CharMap.offsetOf(st.persons[0], CharMap.slots[0]))
        }
    }
}
