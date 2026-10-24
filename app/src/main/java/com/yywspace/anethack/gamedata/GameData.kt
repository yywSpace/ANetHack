package com.yywspace.anethack.gamedata

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 只负责读取游戏资料，不做业务判断。
 *
 * 数据来自 assets/gamedata：
 * - price_items.json：扁平的物品数组（type / name / cost），映射成 [PriceItem]；
 * - price_rules.json：映射成 [PriceRules]；
 * - monsters.json：怪物信息，映射成 [Monster]。
 *
 * 第一次使用时加载一次并缓存。
 */
object GameData {

    private const val ASSET_DIR = "gamedata"
    private const val ITEMS_FILE = "price_items.json"
    private const val RULES_FILE = "price_rules.json"
    private const val MONSTERS_FILE = "monsters.json"

    /** 全部物品条目 */
    val priceItems: List<PriceItem>
        field = mutableListOf<PriceItem>()

    /** 全部怪物条目 */
    val monsters: List<Monster>
        field = mutableListOf<Monster>()

    /** price_rules.json 映射出来的规则；加载前是空规则 */
    var priceRules: PriceRules = PriceRules()
        private set

    /** 名字（小写）→ 怪物，供消息高亮做 O(1) 查询，加载完就建好 */
    private var monsterIndex: Map<String, Monster> = emptyMap()

    @Volatile
    private var loaded = false

    /** 只加载一次；可重复调用，失败时下次还会重试 */
    fun ensureLoaded(context: Context) {
        if (loaded)
            return
        synchronized(this) {
            if (loaded)
                return
            try {
                loadFromAssets(context)
                loaded = true
            } catch (e: Exception) {
                Log.e("GameData", "load assets failed", e)
            }
        }
    }

    /**
     * 按名字查怪物，可能返回多条（不区分大小写），结果按名字排序。
     */
    fun findMonsters(name: String): List<Monster> {
        val key = name.trim()
        if (key.isEmpty())
            return emptyList()
        val exact = monsters.filter { it.name.equals(key, ignoreCase = true) }
        if (exact.isNotEmpty())
            return exact
        return monsters
            .filter { baseName(it.name).equals(key, ignoreCase = true) }
            .sortedBy { it.name }
    }

    /**
     * 消息行 → 怪物，用字典 O(1) 查（消息列表滚动时会大量调用，别用线性扫描）。
     * 完整名和去掉 (human)/(animal) 的基础名都在索引里。
     */
    fun monsterOfLine(line: String): Monster? {
        val key = line.trim().lowercase()
        if (key.isEmpty())
            return null
        return monsterIndex[key]
    }

    /** 去掉多形态怪物的括号后缀："wererat (animal)" → "wererat" */
    private fun baseName(name: String): String =
        name.replace(Regex("\\s*\\((?:human|animal)\\)\\s*$"), "").trim()

    private fun loadFromAssets(context: Context) {
        val gson = Gson()

        val itemsType = object : TypeToken<List<PriceItem>>() {}.type
        val loadedItems: List<PriceItem> =
            gson.fromJson(readAsset(context, ITEMS_FILE), itemsType) ?: emptyList()
        priceItems.clear()
        priceItems.addAll(loadedItems)

        val monstersType = object : TypeToken<List<Monster>>() {}.type
        val loadedMonsters: List<Monster> =
            gson.fromJson(readAsset(context, MONSTERS_FILE), monstersType) ?: emptyList()
        monsters.clear()
        monsters.addAll(loadedMonsters)
        monsterIndex = buildMonsterIndex(loadedMonsters)

        priceRules = gson.fromJson(readAsset(context, RULES_FILE), PriceRules::class.java)
            ?: PriceRules()
    }

    /** 建索引：完整名优先，基础名只在没有同名时补充 */
    private fun buildMonsterIndex(list: List<Monster>): Map<String, Monster> {
        val index = HashMap<String, Monster>(list.size * 2)
        list.forEach { index[it.name.lowercase()] = it }
        list.forEach { monster ->
            val base = baseName(monster.name).lowercase()
            if (base != monster.name.lowercase())
                index.putIfAbsent(base, monster)
        }
        return index
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
}
