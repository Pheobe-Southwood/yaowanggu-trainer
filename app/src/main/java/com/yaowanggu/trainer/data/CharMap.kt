package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.schema.CharSchema

/**
 * 角色属性字段映射（v0.1.11 校准：第三位玩家诊断包 20261002 + 4 张面板截图逐 chip 对照）。
 *
 * 坐标约定：nfile30 每条角色记录内，**相对五官窗口起点 faceStart 的偏移**。
 * 社区字段表（[CharSchema]，pos 1=寿元）与 face 偏移换算：pos N ↔ face−(50−N)。
 *
 * v0.1.11 钉死的结论（证据见 docs/SAVE-FORMAT.md）：
 * - 寿元=face−49、灵气=face−20（×100 定点）、武力=face−19：写回真机验证生效。
 * - 生日月/日=face−48/−47、性别=face−45（3 男 4 女）、所在地=face−44、门派=face−43、
 *   种族/兽耳=face−41、境界=face−32、阶段=face−31、灵根总纲=face−29、八旗=face−28..−21：
 *   读值与面板逐 chip 对齐（党允文/居植/于至行/弘元 四角色），v0.1.11 起开放编辑。
 * - 寿元上限 = f(境界, 阶段)、灵气上限 = f(境界)：**存档不存储**（全记录扫描无候选值），
 *   仅收录面板验证条目：寿元 (2,3)=300 (6,1)=3000 (6,3)=3500 (7,0)=5000、凡人 60、炼气 100；
 *   灵气 炼气 1000、金丹 4800、合体 100400、大乘 220800。未验证显示 —，不臆造。
 *   （v0.1.10 的「金丹 280」作废：面板金丹大圆满=300。）
 * - 突破几率/渡劫死亡率**不存储**（face−10 实测 106/81/190/180 ≠ 面板 9/9/35/13；pos20/pos33 处恒 0），
 *   为游戏侧计算值：v0.1.10 的 face−10 结论辟谣，不再展示/编辑。
 * - 单灵根命名：type0 → 「X变异天灵根」、type1 → 「X天灵根」（面板：风/冰/雷变异天灵根）。
 * - face−80..−63 窗口落在**变长列表区**（记录前缀长度随档漂移），不可作字段。
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
        /** 非空时 UI 渲染为枚举下拉（值 → 标签） */
        val enumLabels: Map<Long, String> = emptyMap(),
        /** 数值字段的已知取值提示（无完整枚举表时） */
        val hint: String = "",
    )

    private val FLAG_ENUM: Map<Long, String> = mapOf(0L to "无", 1L to "有")

    private val realmEnum: Map<Long, String> =
        CharSchema.byIndex(18)?.enumLabels?.mapKeys { it.key.toLong() } ?: emptyMap()

    val slots: List<CharSlot> = listOf(
        CharSlot("life", "寿元前数", -49, 1_000_000,
            desc = "面板「寿元 10/100」斜杠前的数"),
        CharSlot("qi", "灵气", -20, 9_999_999, scale = 100,
            desc = "面板「灵气 124/1000」斜杠前的数（存档 ×100 定点，自动换算）"),
        CharSlot("power", "武力", -19, 99_999_999,
            desc = "面板「武力 X/X」"),
        CharSlot("birthM", "生日·月", -48, 12),
        CharSlot("birthD", "生日·日", -47, 31),
        CharSlot("gender", "性别", -45, 4, enumLabels = mapOf(3L to "男", 4L to "女")),
        CharSlot("location", "所在地", -44, 999, hint = "已知：2=药王谷 4=万剑山 7=大自在殿"),
        CharSlot("sect", "门派", -43, 999, hint = "已知：2=药王谷 4=万剑山 7=大自在殿"),
        CharSlot("race", "种族(兽耳)", -41, 11, enumLabels = mapOf(0L to "人")),
        CharSlot("realm", "境界", -32, 8, enumLabels = realmEnum),
        CharSlot("stage", "阶段", -31, 9),
        CharSlot("rootType", "灵根总纲", -29, 4,
            enumLabels = mapOf(0L to "变异天灵根", 1L to "天灵根", 2L to "变异灵根", 3L to "杂灵根", 4L to "双灵根(实测)")),
        CharSlot("rootMetal", "金灵根", -28, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootWater", "水灵根", -27, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootWood", "木灵根", -26, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootFire", "火灵根", -25, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootEarth", "土灵根", -24, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootIce", "冰灵根", -23, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootWind", "风灵根", -22, 1, enumLabels = FLAG_ENUM),
        CharSlot("rootThunder", "雷灵根", -21, 1, enumLabels = FLAG_ENUM),
    )

    /** 八旗槽位（金水木火土冰风雷），顺序与 rootNames 一致。 */
    val rootFlagSlots: List<CharSlot> =
        listOf("rootMetal", "rootWater", "rootWood", "rootFire", "rootEarth", "rootIce", "rootWind", "rootThunder")
            .map { slot(it)!! }

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

    // ---------------- 派生/展示字段 ----------------

    private fun rel(person: PersonFinder.PersonRecord, faceRel: Int): Long? =
        person.recordValues.getOrNull(person.loc.startIndex + faceRel)

    /** 境界代码：−1 凡人, 0 练气, 1 筑基, 2 金丹, 3 元婴, 4 出窍, 5 分神, 6 合体, 7 大乘, 8 飞升。 */
    fun realmOf(person: PersonFinder.PersonRecord): Long? = rel(person, -32)

    fun realmName(realm: Long?): String =
        realm?.toInt()?.let { CharSchema.byIndex(18)?.enumLabels?.get(it) } ?: "—"

    fun stageOf(person: PersonFinder.PersonRecord): Long? = rel(person, -31)

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

    /** 例：练气初期 / 金丹中期 / 凡人。 */
    fun realmText(person: PersonFinder.PersonRecord): String {
        val realm = realmOf(person)
        val name = realmName(realm)
        if (name == "—") return "—"
        val stage = stageLabel(realm, stageOf(person))
        return if (stage.isEmpty()) name else name + stage
    }

    /** 阶段可选项（随境界变化）：金丹+ 0..3；练气/筑基 0..9 层；凡人不可改。 */
    fun stageOptions(realm: Long?): Map<Long, String> {
        val r = realm ?: return emptyMap()
        if (r <= -1L) return emptyMap()
        return if (r >= 2L) {
            mapOf(0L to "前期", 1L to "中期", 2L to "后期", 3L to "大圆满")
        } else {
            (0L..9L).associate { it to ("第" + (it + 1) + "层") }
        }
    }

    /** 寿元上限：凡人/炼气按境界；金丹及以上按 (境界, 阶段)。仅收录面板验证条目。 */
    val lifeMaxRealmTable: Map<Long, Long> = mapOf(-1L to 60L, 0L to 100L)
    val lifeMaxStageTable: Map<Pair<Long, Long>, Long> = mapOf(
        (2L to 3L) to 300L,     // 金丹大圆满（于至行 106/300）
        (6L to 1L) to 3000L,    // 合体中期（党允文 1067/3000）
        (6L to 3L) to 3500L,    // 合体大圆满（弘元 1244/3500）
        (7L to 0L) to 5000L,    // 大乘前期（居植 2804/5000）
    )

    /** 灵气上限 = f(境界)，同上。 */
    val qiMaxTable: Map<Long, Long> = mapOf(0L to 1_000L, 2L to 4_800L, 6L to 100_400L, 7L to 220_800L)

    fun lifeMaxFor(realm: Long?, stage: Long? = null): Long? {
        realm ?: return null
        lifeMaxRealmTable[realm]?.let { return it }
        return stage?.let { lifeMaxStageTable[realm to it] }
    }

    fun qiMaxFor(realm: Long?): Long? = realm?.let { qiMaxTable[it] }
    fun lifeMaxOf(person: PersonFinder.PersonRecord): Long? = lifeMaxFor(realmOf(person), stageOf(person))
    fun qiMaxOf(person: PersonFinder.PersonRecord): Long? = qiMaxFor(realmOf(person))

    /** 生日（月, 日），face−48 / face−47。 */
    fun birthOf(person: PersonFinder.PersonRecord): Pair<Long?, Long?> = rel(person, -48) to rel(person, -47)

    private val rootNames = listOf("金", "水", "木", "火", "土", "冰", "风", "雷")

    /** 八旗标志位（金水木火土冰风雷），缺失记 0。 */
    fun rootFlagsOf(person: PersonFinder.PersonRecord): List<Long> =
        (0..7).map { rel(person, -28 + it) ?: 0L }

    fun rootTypeOf(person: PersonFinder.PersonRecord): Long? = rel(person, -29)

    /** 例：木火双灵根 / 风变异天灵根 / 无灵根。八旗 face−28..−21，总纲 face−29。 */
    fun rootsString(person: PersonFinder.PersonRecord): String =
        rootsStringFrom(rootFlagsOf(person), rootTypeOf(person))

    /** 纯函数版本：UI 可用待写入值预览灵根文案。 */
    fun rootsStringFrom(flags: List<Long>, type: Long?): String {
        val elems = rootNames.filterIndexed { i, _ -> (flags.getOrNull(i) ?: 0L) != 0L }
        if (elems.isEmpty()) return "无灵根"
        if (elems.size == 1) {
            return when (type) {
                0L -> elems[0] + "变异天灵根"
                1L -> elems[0] + "天灵根"
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
