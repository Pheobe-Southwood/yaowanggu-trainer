package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonFinderTest {

    private val rnd = java.util.Random(7)

    /** 合成一条角色记录：垃圾前缀（0/-1/1 小值）+ ID 锚定真脸 + 尾零，模拟 nfile30 布局。 */
    private fun personRecord(i: Int, withFace: Boolean = true): List<Long> {
        val prefix = List(30 + rnd.nextInt(60)) {
            if (withFace) when (rnd.nextInt(3)) { 0 -> 0L; 1 -> -1L; else -> 1L }
            else if (rnd.nextBoolean()) 0L else -1L // 无脸记录：仅 0/-1，绝不产生假脸窗口
        }
        if (!withFace) return prefix + listOf(0L)
        val face = listOf(
            (i + 1).toLong(),                 // ID
            rnd.nextInt(13).toLong(),         // 脸型
            rnd.nextInt(13).toLong(),         // 眉毛
            rnd.nextInt(13).toLong(),         // 眼睛
            rnd.nextInt(13).toLong(),         // 嘴巴
            rnd.nextInt(9).toLong(),          // 鼻子
            rnd.nextInt(13).toLong(),         // 前发
            rnd.nextInt(13).toLong(),         // 后发
            rnd.nextInt(10).toLong(),         // 性格
            rnd.nextInt(4).toLong(),          // 痣
            (50 + rnd.nextInt(100)).toLong(), // 肤色饱和
            (50 + rnd.nextInt(100)).toLong(), // 肤色明暗
            (1 + rnd.nextInt(360)).toLong(),  // 发色色相
            (10 + rnd.nextInt(391)).toLong(), // 发色饱和
            (10 + rnd.nextInt(391)).toLong(), // 发色明暗
            (1 + rnd.nextInt(360)).toLong(),  // 瞳色色相
            (10 + rnd.nextInt(391)).toLong(), // 瞳色饱和
            (10 + rnd.nextInt(391)).toLong(), // 瞳色明暗
            rnd.nextInt(13).toLong(),         // 幼前发
            rnd.nextInt(13).toLong(),         // 幼后发
        )
        return prefix + face + listOf(1604748L, 412500L) + listOf(0L)
    }

    private fun treeOf(records: List<List<Long>>): MpValue.Arr =
        MpValue.Arr(records.map { MpValue.Arr(it.map { v -> MpValue.Int(v) }) })

    @Test
    fun findsAllAnchoredPersons() {
        val n = 20
        val records = (0 until n).map { personRecord(it) }
        val persons = PersonFinder.findPersons(treeOf(records))
        assertEquals(n, persons.size)
        persons.forEachIndexed { i, p ->
            assertEquals((i + 1).toLong(), p.charId)
            assertEquals(i, p.recordIndex)
            // 找到的窗口必须与真实脸一致（ID 锚定 + 尾部搜索）
            val expectedFace = records[i].subList(records[i].size - 23, records[i].size - 3)
            assertEquals(expectedFace, p.faceValues)
            assertEquals(listOf(1604748L, 412500L), p.hints)
        }
    }

    @Test
    fun skipsRecordsWithoutFace() {
        val records = (0 until 10).map { personRecord(it) } + List(4) { personRecord(100 + it, withFace = false) }
        val persons = PersonFinder.findPersons(treeOf(records))
        // 10 条有脸 + 4 条无脸（eligible=14, 命中 10 ≥ 半数）
        assertEquals(10, persons.size)
        assertTrue(persons.all { it.charId in 1..10 })
    }

    @Test
    fun rejectsAllZeroRecords() {
        val records = List(10) { List(120) { 0L } }
        assertEquals(0, PersonFinder.findPersons(treeOf(records)).size)
    }

    @Test
    fun rejectsSmallModules() {
        val records = (0 until 3).map { personRecord(it) }
        assertEquals(0, PersonFinder.findPersons(treeOf(records)).size)
    }

    @Test
    fun rejectsNonArrayRoot() {
        val root = MpValue.Map(linkedMapOf(MpValue.Str("a") to MpValue.Int(1)))
        assertEquals(0, PersonFinder.findPersons(root).size)
        assertEquals(0, PersonFinder.findPersons(null).size)
    }

    @Test
    fun analyzerUsesPersonsAsBestFace() {
        val records = (0 until 8).map { personRecord(it) }
        val bytes = MessagePack.serialize(treeOf(records))
        val st = SaveAnalyzer.analyze(bytes)
        assertEquals(8, st.persons.size)
        val bf = st.bestFace!!
        // bestFace 必须是记录 0 的真脸（ID=1），而不是前缀垃圾窗口
        assertEquals(1L, bf.values[0])
        assertEquals(records[0].subList(records[0].size - 23, records[0].size - 3), bf.values)
        assertEquals(0, st.persons[0].recordIndex)
    }

    @Test
    fun findsPersonWithExceededColorVal() {
        // 模拟用户诊断包中主角（ID 1）：发色明暗被改到了 440
        val records = (0 until 8).map { i ->
            val r = personRecord(i).toMutableList()
            if (i == 0) {
                // 将发色明暗（倒数第 9 项：size-3 为 0L，size-4 为 412500，size-5 为 1604748，size-6 为幼后发... size-11 为发色明暗）
                val faceStart = r.size - 23
                r[faceStart + 14] = 440L // 发色明暗超限至 440
            }
            r.toList()
        }
        val persons = PersonFinder.findPersons(treeOf(records))
        assertEquals(8, persons.size)
        val p0 = persons.firstOrNull { it.charId == 1L }
        org.junit.Assert.assertNotNull("主角（ID 1）必须能被正常定位", p0)
        assertEquals(440L, p0!!.faceValues[14])
    }

    @Test
    fun findsPersonWithBothHueZero() {
        // 模拟 NPC #332（ID 333）：发色色相与瞳色色相均为 0（纯黑发/红瞳等正常角色）
        val records = (0 until 8).map { i ->
            val r = personRecord(i).toMutableList()
            if (i == 1) {
                val faceStart = r.size - 23
                r[faceStart + 12] = 0L // 发色色相 0
                r[faceStart + 15] = 0L // 瞳色色相 0
            }
            r.toList()
        }
        val persons = PersonFinder.findPersons(treeOf(records))
        assertEquals(8, persons.size)
        val p1 = persons.firstOrNull { it.charId == 2L }
        org.junit.Assert.assertNotNull(p1)
        assertEquals(0L, p1!!.faceValues[12])
        assertEquals(0L, p1.faceValues[15])
    }

    @Test
    fun findsPersonWithHairFront14() {
        // 模拟 NPC #61（ID 62）：前发为 14
        val records = (0 until 8).map { i ->
            val r = personRecord(i).toMutableList()
            if (i == 2) {
                val faceStart = r.size - 23
                r[faceStart + 6] = 14L // 前发 14
            }
            r.toList()
        }
        val persons = PersonFinder.findPersons(treeOf(records))
        assertEquals(8, persons.size)
        val p2 = persons.firstOrNull { it.charId == 3L }
        org.junit.Assert.assertNotNull(p2)
        assertEquals(14L, p2!!.faceValues[6])
    }
}
