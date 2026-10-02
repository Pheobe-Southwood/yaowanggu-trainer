package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.codec.SaveCodec
import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharMapTest {

    /**
     * 合成记录（face@84，长度 105）：
     * life@35(face−49) birthM@36 birthD@37 gender@39 location@40 sect@41 race@43
     * realm@52 stage@53 rootType@55 八旗@56..63(face−28..−21: 金水木火土冰风雷)
     * qiRaw@64(face−20) power@65(face−19)
     */
    private fun record(
        id: Int,
        life: Long,
        qiRaw: Long,
        power: Long,
        realm: Long = 0,
        stage: Long = 0,
        month: Long = 1,
        day: Long = 1,
        gender: Long = 4,
        location: Long = 2,
        sect: Long = 2,
        race: Long = 0,
        roots: Set<Int> = emptySet(),
        rootType: Long = 4,
    ): List<Long> {
        val r = MutableList(105) { 0L }
        r[35] = life
        r[36] = month
        r[37] = day
        r[39] = gender
        r[40] = location
        r[41] = sect
        r[43] = race
        r[52] = realm
        r[53] = stage
        r[55] = rootType
        roots.forEach { i -> r[56 + i] = 1 }
        r[64] = qiRaw
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

    private fun fourRecords() = listOf(
        record(1, 10, 12430, 1, roots = setOf(2, 3)),
        record(2, 23, 500, 7, realm = 1, stage = 3),
        record(3, 15, 200, 3, realm = 2, stage = 3, roots = setOf(6), rootType = 0),
        record(4, 40, 99903, 9, realm = -1),
    )

    @Test
    fun calibratedDisplayValuesMatchPanel() {
        val st = structureOf(fourRecords())
        assertEquals(4, st.persons.size)
        val p0 = st.persons[0]
        // P2 面板 ground truth：寿元 10/100 · 灵气 124/1000 · 武力 1 · 炼气初期 · 生日 1/1 · 木火双灵根
        assertEquals(10L, CharMap.valueOf(p0, CharMap.slot("life")!!))
        assertEquals(124L, CharMap.valueOf(p0, CharMap.slot("qi")!!))   // 12430 / 100
        assertEquals(12430L, CharMap.rawValueOf(p0, CharMap.slot("qi")!!))
        assertEquals(1L, CharMap.valueOf(p0, CharMap.slot("power")!!))
        assertEquals(0L, CharMap.realmOf(p0))
        assertEquals("炼气初期", CharMap.realmText(p0))
        assertEquals(100L, CharMap.lifeMaxOf(p0))
        assertEquals(1000L, CharMap.qiMaxOf(p0))
        assertEquals(1L to 1L, CharMap.birthOf(p0))
        assertEquals(4L, CharMap.valueOf(p0, CharMap.slot("gender")!!))
        assertEquals(2L, CharMap.valueOf(p0, CharMap.slot("location")!!))
        assertEquals("木火双灵根", CharMap.rootsString(p0))
        // 2026-10-02 面板 ground truth：金丹大圆满 寿元 x/300 · 灵气 x/4800 · 单灵根=风变异天灵根
        val p2 = st.persons[2]
        assertEquals("金丹大圆满", CharMap.realmText(p2))
        assertEquals(300L, CharMap.lifeMaxOf(p2))
        assertEquals(4800L, CharMap.qiMaxOf(p2))
        assertEquals("风变异天灵根", CharMap.rootsString(p2))
        // 凡人：上限 60、灵气上限未验证 → null
        val p3 = st.persons[3]
        assertEquals("凡人", CharMap.realmText(p3))
        assertEquals(60L, CharMap.lifeMaxOf(p3))
        assertNull(CharMap.qiMaxOf(p3))
        assertEquals(999L, CharMap.valueOf(p3, CharMap.slot("qi")!!)) // 99903/100 向下取整
    }

    @Test
    fun stageAwareCapTables() {
        // 寿元上限 = f(境界, 阶段)：2026-10-02 四面板验证条目
        assertEquals(60L, CharMap.lifeMaxFor(-1))
        assertEquals(100L, CharMap.lifeMaxFor(0))
        assertEquals(300L, CharMap.lifeMaxFor(2, 3))
        assertNull(CharMap.lifeMaxFor(2, 1))          // 金丹非大圆满未验证 → 不臆造
        assertNull(CharMap.lifeMaxFor(2))             // 缺阶段 likewise
        assertEquals(3000L, CharMap.lifeMaxFor(6, 1))
        assertEquals(3500L, CharMap.lifeMaxFor(6, 3))
        assertNull(CharMap.lifeMaxFor(6, 0))
        assertEquals(5000L, CharMap.lifeMaxFor(7, 0))
        assertNull(CharMap.lifeMaxFor(5, 3))
        // 灵气上限 = f(境界)
        assertEquals(1000L, CharMap.qiMaxFor(0))
        assertEquals(4800L, CharMap.qiMaxFor(2))
        assertEquals(100400L, CharMap.qiMaxFor(6))
        assertEquals(220800L, CharMap.qiMaxFor(7))
        assertNull(CharMap.qiMaxFor(3))
    }

    @Test
    fun stageOptionsAndLabels() {
        assertEquals(4, CharMap.stageOptions(2).size)
        assertEquals("大圆满", CharMap.stageOptions(6)[3L])
        assertEquals(10, CharMap.stageOptions(0).size)
        assertEquals("第10层", CharMap.stageOptions(1)[9L])
        assertEquals(emptyMap<Long, String>(), CharMap.stageOptions(-1))
        assertEquals(emptyMap<Long, String>(), CharMap.stageOptions(null))
        assertEquals("凡人", CharMap.realmName(-1))
        assertEquals("金丹", CharMap.realmName(2))
        assertEquals("大乘", CharMap.realmName(7))
        assertEquals("—", CharMap.realmName(99))
        assertEquals("初期", CharMap.stageLabel(0, 0))
        assertEquals("中期", CharMap.stageLabel(0, 4))
        assertEquals("大圆满", CharMap.stageLabel(1, 9))
        assertEquals("中期", CharMap.stageLabel(2, 1))
        assertEquals("大圆满", CharMap.stageLabel(3, 7)) // ≥2 境界 clamp 到 0..3
        assertEquals("", CharMap.stageLabel(-1, 0))    // 凡人无阶段
    }

    @Test
    fun rootsNaming() {
        val wind = listOf(0L, 0, 0, 0, 0, 0, 1, 0)
        assertEquals("风变异天灵根", CharMap.rootsStringFrom(wind, 0L))
        assertEquals("风天灵根", CharMap.rootsStringFrom(wind, 1L))
        assertEquals("风灵根", CharMap.rootsStringFrom(wind, null))
        assertEquals("火土双灵根", CharMap.rootsStringFrom(listOf(0L, 0, 0, 1, 1, 0, 0, 0), 4L))
        assertEquals("无灵根", CharMap.rootsStringFrom(List(8) { 0L }, 0L))
    }

    @Test
    fun newSlotsOffsetsAndEnums() {
        val st = structureOf(fourRecords())
        val p0 = st.persons[0]
        assertEquals(36, CharMap.offsetOf(p0, CharMap.slot("birthM")!!))
        assertEquals(37, CharMap.offsetOf(p0, CharMap.slot("birthD")!!))
        assertEquals(39, CharMap.offsetOf(p0, CharMap.slot("gender")!!))
        assertEquals(40, CharMap.offsetOf(p0, CharMap.slot("location")!!))
        assertEquals(41, CharMap.offsetOf(p0, CharMap.slot("sect")!!))
        assertEquals(43, CharMap.offsetOf(p0, CharMap.slot("race")!!))
        assertEquals(52, CharMap.offsetOf(p0, CharMap.slot("realm")!!))
        assertEquals(53, CharMap.offsetOf(p0, CharMap.slot("stage")!!))
        assertEquals(55, CharMap.offsetOf(p0, CharMap.slot("rootType")!!))
        assertEquals(62, CharMap.offsetOf(p0, CharMap.slot("rootWind")!!))
        assertEquals("男", CharMap.slot("gender")!!.enumLabels[3L])
        assertEquals("金丹", CharMap.slot("realm")!!.enumLabels[2L])
        assertEquals(1L, CharMap.valueOf(p0, CharMap.slot("rootWood")!!))
        assertEquals(0L, CharMap.valueOf(p0, CharMap.slot("rootIce")!!))
        assertEquals(listOf(0L, 0, 1, 1, 0, 0, 0, 0), CharMap.rootFlagsOf(p0))
    }

    @Test
    fun qiScaleConversion() {
        val qi = CharMap.slot("qi")!!
        assertEquals(12400L, CharMap.rawFor(qi, 124))
        assertEquals(0L, CharMap.rawFor(qi, 0))
        val life = CharMap.slot("life")!!
        assertEquals(500L, CharMap.rawFor(life, 500)) // scale=1 原值直写
        val gender = CharMap.slot("gender")!!
        assertEquals(3L, CharMap.rawFor(gender, 3))
    }

    @Test
    fun editRoundTripThroughZlibContainer() {
        val tree = MpValue.Arr(fourRecords().map { MpValue.Arr(it.map { MpValue.Int(it) }) })
        val inner = MessagePack.serialize(tree)
        val gameFile = TestWrap.zlib(inner)
        val st = SaveAnalyzer.analyze(gameFile)
        val p = st.persons[0]
        val offLife = CharMap.offsetOf(p, CharMap.slot("life")!!)!!
        val offQi = CharMap.offsetOf(p, CharMap.slot("qi")!!)!!
        val offGender = CharMap.offsetOf(p, CharMap.slot("gender")!!)!!
        val offRootFire = CharMap.offsetOf(p, CharMap.slot("rootFire")!!)!!
        assertEquals(35, offLife)
        assertEquals(64, offQi)

        // 显示值 500 的灵气 → 存档原值 50000；寿元直写 999；性别改男；加火灵根
        val edited = SaveEditor.apply(st, listOf(
            SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(SeqLocation(listOf(PathStep.Index(0)), 0), offLife + 1), 999L),
            SaveEditor.FieldEdit(
                SaveEditor.FieldRef.TreeField(SeqLocation(listOf(PathStep.Index(0)), 0), offQi + 1),
                CharMap.rawFor(CharMap.slot("qi")!!, 500),
            ),
            SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(SeqLocation(listOf(PathStep.Index(0)), 0), offGender + 1), 3L),
            SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(SeqLocation(listOf(PathStep.Index(0)), 0), offRootFire + 1), 1L),
        ))
        val out = SaveCodec.encode(st.format, edited, st.bytes)
        val st2 = SaveAnalyzer.analyze(out)
        assertEquals("zlib+msgpack", st2.format)
        val q = st2.persons[0]
        assertEquals(999L, CharMap.valueOf(q, CharMap.slot("life")!!))
        assertEquals(50000L, CharMap.rawValueOf(q, CharMap.slot("qi")!!))
        assertEquals(500L, CharMap.valueOf(q, CharMap.slot("qi")!!))
        assertEquals(3L, CharMap.valueOf(q, CharMap.slot("gender")!!))
        assertEquals(1L, CharMap.valueOf(q, CharMap.slot("rootFire")!!))
        assertEquals(1L, CharMap.valueOf(q, CharMap.slot("power")!!)) // 未动字段不变
    }

    @Test
    fun shortRecordHasNoStats() {
        // face 太靠前 → 偏移为负 → 不可编辑
        val short = MutableList(30) { 0L }
        val face = listOf(1L, 1, 2, 3, 4, 5, 6, 7, 8, 0, 78, 118, 169, 84, 56, 229, 79, 161, 10, 8)
        for (j in face.indices) short[10 + j] = face[j]
        val recs = (1..4).map { id -> short.toMutableList().also { it[10] = id.toLong() } }
        val tree = MpValue.Arr(recs.map { MpValue.Arr(it.map { MpValue.Int(it) }) })
        val st = SaveAnalyzer.analyze(MessagePack.serialize(tree))
        if (st.persons.isNotEmpty()) {
            assertNull(CharMap.offsetOf(st.persons[0], CharMap.slot("life")!!))
            assertNull(CharMap.offsetOf(st.persons[0], CharMap.slot("rootThunder")!!))
        }
    }
}
