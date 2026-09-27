package com.yaowanggu.trainer.refs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 图鉴目录：从 assets/ref/catalog.json 读取，例如
 * {"ears":[{"i":0,"asset":"ears/00.webp"}],"face":[{"i":0,...}] ...}
 * 文件不存在时返回空列表，UI 退化为纯数字选择。
 */
object RefCatalog {

    data class RefItem(val index: Int, val asset: String)

    data class Anchor(val chart: String, val y: Int, val label: String)

    private val cache = HashMap<String, List<RefItem>>()
    private val anchorCache = HashMap<String, List<Anchor>>()

    fun items(context: Context, set: String): List<RefItem> = cache.getOrPut(set) {
        try {
            val text = context.assets.open("ref/catalog.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val arr: JSONArray = root.optJSONArray(set) ?: return@getOrPut emptyList()
            val out = ArrayList<RefItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val idx = o.optInt("i", -1)
                val asset = o.optString("asset", "")
                if (idx >= 0 && asset.isNotBlank()) out.add(RefItem(idx, asset))
            }
            out.sortedBy { it.index }
        } catch (e: Throwable) { emptyList() }
    }

    /** 字段集合 → 教程图表锚点（ref/anchors.json）。 */
    fun anchors(context: Context, set: String): List<Anchor> = anchorCache.getOrPut(set) {
        try {
            val text = context.assets.open("ref/anchors.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val arr: JSONArray = root.optJSONArray(set) ?: return@getOrPut emptyList()
            val out = ArrayList<Anchor>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val chart = o.optString("chart", "")
                if (chart.isBlank()) continue
                out.add(Anchor(chart, o.optInt("y", 0), o.optString("label", set)))
            }
            out
        } catch (e: Throwable) { emptyList() }
    }
}
