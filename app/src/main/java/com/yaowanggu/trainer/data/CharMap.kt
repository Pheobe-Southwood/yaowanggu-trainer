package com.yaowanggu.trainer.data

/**
 * 角色属性字段映射（2026-10 真机校准：v0.1.5 诊断包 + 游戏内属性面板截图）。
 *
 * 面板 ground truth（主角 叶星华）：寿元 10/60、灵气 0、武力 1、凡人、第1年1月1日。
 * 校准结论（nfile30 记录内，**相对五官窗口起点 faceStart 的偏移**，跨新旧存档 6+ 记录验证）：
 * - 寿元当前 = faceStart-63，寿元上限 = faceStart-62（模式 `(cur, max, 0, 角色ID)`）
 * - 灵气 = faceStart-20，武力 = faceStart-19（尾块首二项；旧档大数对 14185061/3162500 同位）
 *
 * 面板其余数值（灵玉/突破几率/灵根/境界）在本轮数据中无法唯一定位 → 不映射、不可编辑，
 * 避免写坏存档；待带时间差的双包差分分析后再扩展。
 */
object CharMap {

    data class CharSlot(
        val key: String,
        val name: String,
        /** 相对 faceStart 的偏移（负数） */
        val faceRel: Int,
        val max: Long,
        val note: String = "",
    )

    val slots: List<CharSlot> = listOf(
        CharSlot("life", "寿元（当前）", -63, 1_000_000),
        CharSlot("lifeMax", "寿元（上限）", -62, 1_000_000),
        CharSlot("qi", "灵气", -20, 99_999_999),
        CharSlot("power", "武力", -19, 99_999_999),
    )

    /** 该角色记录内某字段的绝对下标；记录太短（faceStart 不足）返回 null。 */
    fun offsetOf(person: PersonFinder.PersonRecord, slot: CharSlot): Int? {
        val abs = person.loc.startIndex + slot.faceRel
        return if (abs >= 0 && abs < person.recordLen) abs else null
    }

    fun valueOf(person: PersonFinder.PersonRecord, slot: CharSlot): Long? {
        val off = offsetOf(person, slot) ?: return null
        return person.recordValues.getOrNull(off)
    }
}
