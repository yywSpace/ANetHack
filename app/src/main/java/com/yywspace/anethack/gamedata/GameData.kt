package com.yywspace.anethack.gamedata

import com.yywspace.anethack.log.AppLog
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 只负责读取游戏资料，不做业务判断。
 *
 * 数据来自 assets/gamedata：
 * - parsing_rules.json：交易、物品类别、怪物名称的解析规则；
 * - monsters.json / monster_attributes.json：怪物资料；
 * - items 目录下的 JSON 文件：分类物品资料；
 * - item_flags.json / item_effects.json / item_delays.json：物品展示映射。
 *
 * 第一次使用时加载一次并缓存。
 */
object GameData {

    private const val ASSET_DIR = "gamedata"
    private const val RULES_FILE = "parsing_rules.json"
    private const val MONSTERS_FILE = "monsters.json"
    private const val MONSTER_ATTRIBUTES_FILE = "monster_attributes.json"
    private const val ITEM_FLAGS_FILE = "item_flags.json"
    private const val ITEM_EFFECTS_FILE = "item_effects.json"
    private const val ITEM_DELAYS_FILE = "item_delays.json"
    private const val ITEMS_DIR = "items"

    /** 全部怪物条目 */
    val monsters: List<Monster>
        field = mutableListOf<Monster>()

    /** 全部物品条目，按类别文件依次加载 */
    val items: List<Item>
        field = mutableListOf<Item>()

    /** parsing_rules.json 映射出来的规则；加载前是空规则 */
    var parsingRules: ParsingRules = ParsingRules()
        private set

    /** 名字（小写）→ 怪物，供消息高亮做 O(1) 查询，加载完就建好 */
    private var monsterIndex: Map<String, Monster> = emptyMap()

    /** 精确名称/外观（小写）→ 物品，供菜单标题快速查询 */
    private var itemExactIndex: Map<String, List<Item>> = emptyMap()

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
            .filter { monsterBaseName(it.name).equals(key, ignoreCase = true) }
            .sortedBy { it.name }
    }

    /** 原始属性标志转成展示文字；没有映射时原样返回 */
    fun attributeLabel(raw: String): String = attributeLabels[raw] ?: raw

    fun flagLabel(raw: String): String = itemFlagLabels[raw] ?: raw

    fun effectLabel(raw: String): String = itemEffectLabels[raw] ?: raw

    fun delayLabel(itemClass: String): String =
        delayLabels[itemClass] ?: itemClass

    /**
     * 从文本判断物品类别。
     *
     * 顺序：
     * 1. 精确名称/固定外观
     * 2. 例外正则（未鉴定外观、通用类别词）
     * 3. 模糊名称/外观
     */
    fun itemTypeOf(text: String): String? {
        val exact = exactItemsForTitle(text)
        if (exact.size == 1)
            return itemType(exact.first())

        parsingRules.itemTypeOf(text)?.let { return it }

        val fuzzyTypes = fuzzyItemsForTitle(text).map { itemType(it) }.distinct()
        return fuzzyTypes.singleOrNull()
    }

    /** 物品的类别展示名；护甲用 categoryName，其余用 itemClass。 */
    fun itemType(item: Item): String =
        (item.armor?.categoryName?.takeIf { it.isNotBlank() } ?: item.itemClass)
            .replaceFirstChar { it.uppercaseChar() }

    /** 怪物显示名 → 基础名，去掉 (human)/(animal) 后缀。 */
    fun monsterBaseName(name: String): String = parsingRules.monsterBaseName(name)

    /**
     * 解析菜单标题对应的具体物品和所属类别。
     * [specific] 是匹配到名称/外观的物品；为空时回退到 [category] 全部物品。
     */
    data class ItemLookup(
        val type: String,
        val specific: List<Item>,
        val category: List<Item>
    )

    fun lookupItemsForTitle(title: String): ItemLookup? {
        val type = itemTypeOf(title) ?: return null
        val candidates = itemsOfType(type)
        if (candidates.isEmpty())
            return null

        val matches = candidates.flatMap { item ->
            listOfNotNull(item.name, item.appearance).map { alias -> alias to item }
        }.filter { (alias, _) ->
            alias.isNotBlank() && title.contains(alias, ignoreCase = true)
        }
        val maxLength = matches.maxOfOrNull { (alias, _) -> alias.length }
        val specific = if (maxLength == null) {
            emptyList()
        } else {
            matches.filter { (alias, _) -> alias.length == maxLength }
                .map { (_, item) -> item }
                .distinct()
        }
        return ItemLookup(type, specific, candidates)
    }

    /** 标题是否与某个物品的完整名称/固定外观完全相同。 */
    fun exactItemsForTitle(title: String): List<Item> =
        itemExactIndex[title.lowercase()] ?: emptyList()

    /** 标题中模糊包含的物品名称/固定外观。 */
    fun fuzzyItemsForTitle(title: String): List<Item> =
        items.filter { item ->
            title.contains(item.name, ignoreCase = true) ||
                (item.appearance?.takeIf { it.isNotBlank() }?.let {
                    title.contains(it, ignoreCase = true)
                } == true)
        }

    /** 某类别下的物品；护甲类别使用 categoryName。 */
    fun itemsOfType(type: String): List<Item> {
        val normalized = type.lowercase()
        return items.filter { item ->
            item.armor?.categoryName?.equals(type, ignoreCase = true) == true ||
                (item.armor == null && item.itemClass == normalized)
        }
    }

    private fun buildItemExactIndex(list: List<Item>): Map<String, List<Item>> {
        val index = HashMap<String, MutableList<Item>>(list.size * 2)
        fun add(key: String?, item: Item) {
            if (key.isNullOrBlank())
                return
            index.getOrPut(key.lowercase()) { mutableListOf() }.add(item)
        }
        list.forEach { item ->
            add(item.name, item)
            item.appearance
                ?.takeIf { it != item.name }
                ?.let { add(it, item) }
        }
        return index
    }

    private fun loadFromAssets(context: Context) {
        val gson = Gson()

        val monstersType = object : TypeToken<List<Monster>>() {}.type
        val loadedMonsters: List<Monster> =
            gson.fromJson(readAsset(context, MONSTERS_FILE), monstersType) ?: emptyList()
        monsters.clear()
        monsters.addAll(loadedMonsters)
        monsterIndex = buildMonsterIndex(loadedMonsters)

        parsingRules = gson.fromJson(readAsset(context, RULES_FILE), ParsingRules::class.java)
            ?: ParsingRules()

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
        itemExactIndex = buildItemExactIndex(loadedGameItems)

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
            val base = monsterBaseName(monster.name).lowercase()
            if (base != monster.name.lowercase())
                index.putIfAbsent(base, monster)
        }
        return index
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
}
