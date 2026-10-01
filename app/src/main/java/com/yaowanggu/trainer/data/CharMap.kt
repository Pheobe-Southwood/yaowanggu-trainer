package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.schema.CharSchema

/**
 * 角色属性字段映射（v0.1.10 校准：第二位玩家诊断包 + 面板截图 + 社区 GG 字段表 + 双包交叉验证）。
 *
 * 坐标约定：nfile30 每条角色记录内，**相对五官窗口起点 faceStart 的偏移**。
 * 社区字段表（[CharSchema]，pos 1=寿元）与 face 偏移换算：pos N ↔ face−(50−N)。
 *
 * v0.1.10 钉死的结论（证据见 docs/SAVE-FORMAT.md）：
 * - 寿元前数 = face−49（pos1），原值；写回已真机验证生效。
 * - 灵气 = face−20（pos30），**×100 定点存储**：面板 124 ↔ 存档 12430；显示=floor(raw/100)，写入=显示×100。
 * - 武力 = face−19（pos31），原值；面板「武力 X/X」上限恒等于当前值。
 * - 寿元上限 / 灵气上限**不存储**（全记录扫描无候选值），由境界派生：凡人 60、练气 100/1000、金丹 280/4800（已验证条目）。
 *   旧版「寿元上限=face−62」是常量 60（所有 person 记录恒为 60）与凡人上限的巧合，写 face−62 游戏内无效。
 * - 境界 = face−32（−1 凡人, 0 练气 … 7 大乘, 8 飞升）；阶段 = face−31。
 * - 突破几率 = face−10（%）；生日 月/日 = face−48/−47；灵根八旗 = face−28..−21（金水木火土冰风雷）。
 * - face−80..−63 窗口落在**变长列表区**（记录前缀长度随档漂移 84/138），不可作字段。
 */
object CharMap {

    data class CharSlot(
        val key: String,
        val name: String,
        /** 相对 faceStart 的偏移（负数） */
        val faceRel: Int,
        val max: Long,
        /** 定点倍数：显示值 = floor(存档原值 / scale)；写入原值 = 显示值 × scale */
        val scale: Int = 1,
        /** 面向用户的说明：对应游戏面板的哪个数 */
        val desc: String = "",
        /** 未经校准的字段只读展示，禁止写入 */
        val editable: Boolean = true,
    )

    val slots: List<CharSlot> = listOf(
        CharSlot("life", "寿元前数", -49, 1_000_000,
            desc = "面板「寿元 10/100」斜杠**前**的数。真机写回已验证生效；face−63 是另一个字段，勿混淆。"),
        CharSlot("qi", "灵气", -20, 9_999_999, scale = 100,
            desc = "面板「灵气 124/1000」斜杠前的数。存档按面板值 ×100 定点存储（124↔12430），此处填面板值、自动换算。"),
        CharSlot("power", "武力", -19, 99_999_999,
            desc = "面板「武力 1/1」。游戏内武力上限恒等于当前值（面板总是 X/X）。"),
    )

    fun slot(key: String): CharSlot? = slots.firstOrNull { it.key == key }

    /** 该角色记录内某字段的绝对下标；记录太短（faceStart 不足）返回 null。 */
    fun offsetOf(person: PersonFinder.PersonRecord, slot: CharSlot): Int? {
        val abs = person.loc.startIndex + slot.faceRel
        return if (abs >= 0 && abs < person.recordLen) abs else null
    }

    /** 存档原值（未换算）。 */
    fun rawValueOf(person: PersonFinder.PersonRecord, slot: CharSlot): Long? {
        val off = offsetOf(person, slot) ?: return null
        return person.recordValues.getOrNull(off)
    }

    /** 显示值 = floor(原值 / scale)。 */
    fun valueOf(person: PersonFinder.PersonRecord, slot: CharSlot): Long? {
        val raw = rawValueOf(person, slot) ?: return null
        return Math.floorDiv(raw, slot.scale.toLong())
    }

    /** 显示值 → 应写入存档的原值。 */
    fun rawFor(slot: CharSlot, displayValue: Long): Long = displayValue * slot.scale

    // ---------------- 只读派生/展示字段 ----------------

    private fun rel(person: PersonFinder.PersonRecord, faceRel: Int): Long? =
        person.recordValues.getOrNull(person.loc.startIndex + faceRel)

    /** 境界代码：−1 凡人, 0 练气, 1 筑基, 2 金丹, 3 元婴, 4 出窍, 5 分神, 6 合体, 7 大乘, 8 飞升。 */
    fun realmOf(person: PersonFinder.PersonRecord): Long? = rel(person, -32)

    fun realmName(realm: Long?): String =
        realm?.toInt()?.let { CharSchema.byIndex(18)?.enumLabels?.get(it) } ?: "—"

    /** 阶段：金丹及以上 0..3=前/中/后/大圆满；练气/筑基 0..9 层按 0-2 初 / 3-5 中 / 6-8 后 / 9 大圆满 归组（best-effort）。 */
    fun stageLabel(realm: Long?, stage: Long?): String {
        val v = stage?.toInt() ?: return ""
        if (v < 0) return ""
        if ((realm ?: -1L) <= -1L) return "" // 凡人无阶段
        return if ((realm ?: -1L) >= 2L) {
            listOf("前期", "中期", "后期", "大圆满")[v.coerceIn(0, 3)]
        } else {
            when {
                v <= 2 -> "初期"
                v <= 5 -> "中期"
                v <= 8 -> "后期"
                else -> "大圆满"
            }
        }
    }

    fun stageOf(person: PersonFinder.PersonRecord): Long? = rel(person, -31)

    /** 例：练气初期 / 金丹中期 / 凡人。 */
    fun realmText(person: PersonFinder.PersonRecord): String {
        val realm = realmOf(person)
        val name = realmName(realm)
        if (name == "—") return "—"
        val stage = stageLabel(realm, stageOf(person))
        return if (stage.isEmpty()) name else name + stage
    }

    /** 寿元上限 = f(境界)，仅收录已真机/面板验证的条目；其余返回 null（UI 显示 —）。 */
    val lifeMaxTable: Map<Long, Long> = mapOf(-1L to 60L, 0L to 100L, 2L to 280L)

    /** 灵气上限 = f(境界)，同上。 */
    val qiMaxTable: Map<Long, Long> = mapOf(0L to 1_000L, 2L to 4_800L)

    fun lifeMaxFor(realm: Long?): Long? = realm?.let { lifeMaxTable[it] }
    fun qiMaxFor(realm: Long?): Long? = realm?.let { qiMaxTable[it] }
    fun lifeMaxOf(person: PersonFinder.PersonRecord): Long? = lifeMaxFor(realmOf(person))
    fun qiMaxOf(person: PersonFinder.PersonRecord): Long? = qiMaxFor(realmOf(person))

    /** 突破几率（%），face−10。 */
    fun breakthroughOf(person: PersonFinder.PersonRecord): Long? = rel(person, -10)

    /** 生日（月, 日），face−48 / face−47。 */
    fun birthOf(person: PersonFinder.PersonRecord): Pair<Long?, Long?> = rel(person, -48) to rel(person, -47)

    private val rootNames = listOf("金", "水", "木", "火", "土", "冰", "风", "雷")

    /** 例：木火双灵根 / 天灵根 / 变异天灵根。八旗 face−28..−21，总纲 face−29。 */
    fun rootsString(person: PersonFinder.PersonRecord): String {
        val flags = (0..7).map { rel(person, -28 + it) ?: 0L }
        val elems = rootNames.filterIndexed { i, _ -> flags[i] != 0L }
        if (elems.isEmpty()) return "无灵根"
        val type = rel(person, -29)
        if (elems.size == 1) {
            return when (type) {
                0L -> "变异天灵根"
                1L -> "天灵根"
                else -> elems[0] + "灵根"
            }
        }
        val countWord = when (elems.size) {
            2 -> "双"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            else -> "杂"
        }
        return elems.joinToString("") + countWord + "灵根"
    }
}
