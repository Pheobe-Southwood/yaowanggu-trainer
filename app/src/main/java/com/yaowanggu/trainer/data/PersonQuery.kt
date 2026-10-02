package com.yaowanggu.trainer.data

/**
 * 角色搜索匹配（纯函数，便于单测）：
 * 查询串非空时，命中「别名包含 / 角色 ID 子串 / 武力值子串」任一即匹配。
 */
object PersonQuery {
    fun matches(charId: Long, alias: String?, power: Long?, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        alias?.takeIf { it.isNotBlank() }?.let { if (it.contains(q, ignoreCase = true)) return true }
        if (charId.toString().contains(q)) return true
        if (power?.toString()?.contains(q) == true) return true
        return false
    }
}
