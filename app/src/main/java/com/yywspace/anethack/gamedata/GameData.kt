package com.yywspace.anethack.gamedata

import com.yywspace.anethack.log.AppLog
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 只负责读取游戏资料，不做业务判断。
 *
 * 数据来自 assets/gamedata：
 * - price_items.json：价格鉴定用的扁平物品数组；
 * - price_rules.json：价格鉴定规则；
 * - monsters.json / monster_attributes.json：怪物资料；
 * - items 目录下的 JSON 文件：分类物品资料；
 * - item_flags.json / item_effects.json / item_delays.json：物品展示映射。
 *
 * 第一次使用时加载一次并缓存。
 */
object GameData {

    private const val ASSET_DIR = "gamedata"
    private const val ITEMS_FILE = "price_items.json"
    private const val RULES_FILE = "price_rules.json"
    private const val MONSTERS_FILE = "monsters.json"
    private const val MONSTER_ATTRIBUTES_FILE = "monster_attributes.json"
    private const val ITEM_FLAGS_FILE = "item_flags.json"
    private const val ITEM_EFFECTS_FILE = "item_effects.json"
    private const val ITEM_DELAYS_FILE = "item_delays.json"
    private const val ITEMS_DIR = "items"

    /** 全部物品条目 */
    val priceItems: List<PriceItem>
        field = mutableListOf<PriceItem>()

    /** 全部怪物条目 */
    val monsters: List<Monster>
        field = mutableListOf<Monster>()

    /** 全部物品条目，按类别文件依次加载 */
    val items: List<Item>
        field = mutableListOf<Item>()

    /** price_rules.json 映射出来的规则；加载前是空规则 */
    var priceRules: PriceRules = PriceRules()
        private set

    /** 名字（小写）→ 怪物，供消息高亮做 O(1) 查询，加载完就建好 */
    private var monsterIndex: Map<String, Monster> = emptyMap()

    /** monster_attributes.json：原始属性标志 → 展示文字 */
    private var attributeLabels: Map<String, String> = emptyMap()
    /** item_flags.json / item_effects.json / item_delays.json：展示映射 */
    private var itemFlagLabels: Map<String, String> = emptyMap()
    private var itemEffectLabels: Map<String, String> = emptyMap()
    private var delayLabels: Map<String, String> = emptyMap()

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
                AppLog.e("GameData", "load assets failed", e)
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

    /** 原始属性标志转成展示文字；没有映射时原样返回 */
    fun attributeLabel(raw: String): String = attributeLabels[raw] ?: raw

    fun flagLabel(raw: String): String = itemFlagLabels[raw] ?: raw

    fun effectLabel(raw: String): String = itemEffectLabels[raw] ?: raw

    fun delayLabel(itemClass: String): String =
        delayLabels[itemClass] ?: itemClass

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
        val loadedPriceItems: List<PriceItem> =
            gson.fromJson(readAsset(context, ITEMS_FILE), itemsType) ?: emptyList()
        priceItems.clear()
        priceItems.addAll(loadedPriceItems)

        val monstersType = object : TypeToken<List<Monster>>() {}.type
        val loadedMonsters: List<Monster> =
            gson.fromJson(readAsset(context, MONSTERS_FILE), monstersType) ?: emptyList()
        monsters.clear()
        monsters.addAll(loadedMonsters)
        monsterIndex = buildMonsterIndex(loadedMonsters)

        priceRules = gson.fromJson(readAsset(context, RULES_FILE), PriceRules::class.java)
            ?: PriceRules()

        val itemType = object : TypeToken<List<Item>>() {}.type
        val loadedGameItems = mutableListOf<Item>()
        context.assets.list("$ASSET_DIR/$ITEMS_DIR")
            ?.filter { it.endsWith(".json") && !it.startsWith("_") }
            ?.sorted()
            ?.forEach { name ->
                val list: List<Item> = gson.fromJson(
                    readAsset(context, "$ITEMS_DIR/$name"), itemType
                ) ?: emptyList()
                loadedGameItems.addAll(list)
            }
        items.clear()
        items.addAll(loadedGameItems)

        val labelsType = object : TypeToken<Map<String, String>>() {}.type
        attributeLabels = gson.fromJson(
            readAsset(context, MONSTER_ATTRIBUTES_FILE), labelsType
        ) ?: emptyMap()
        itemFlagLabels = gson.fromJson(
            readAsset(context, ITEM_FLAGS_FILE), labelsType
        ) ?: emptyMap()
        itemEffectLabels = gson.fromJson(
            readAsset(context, ITEM_EFFECTS_FILE), labelsType
        ) ?: emptyMap()
        delayLabels = gson.fromJson(
            readAsset(context, ITEM_DELAYS_FILE), labelsType
        ) ?: emptyMap()
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
