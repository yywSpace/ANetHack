package com.yywspace.anethack.gamedata

import com.google.gson.annotations.SerializedName
import com.yywspace.anethack.entity.NHColor

/**
 * 图鉴里的一个物品。
 *
 * 对应 assets/gamedata/items 目录下的 JSON 文件 里的每一条；
 * 所有可能出现的字段都定义在这里，具体物品只填充所属类别的字段。
 */
data class Item(
    val name: String,
    val description: String? = null,

    /** JSON 字段名是 class，Kotlin 里避开关键字 */
    @SerializedName("class")
    val itemClass: String = "",

    /** 地图显示符号 */
    val symbol: String = "",

    /** 生成概率 */
    val probability: Int = 0,

    /** 重量 */
    val weight: Int = 0,

    /** 基础价格，单位 zorkmids */
    val price: Int = 0,

    /** 材质名称，如 iron / glass */
    val material: String = "",

    /** NetHack 颜色名，如 CLR_RED */
    val color: String = "",

    /** 使用耗时；数据里只在该值大于 0 时出现 */
    val delay: Int? = null,

    /** 食物/宝石营养值 */
    val nutrition: Int? = null,

    /** 护甲/工具携带的具体效果，可能不止一个 */
    val effect: List<String>? = null,

    /** 魔杖/法术书方向 */
    val direction: String? = null,

    /** 卷轴/法术书的书写墨水消耗 */
    val writing: Writing? = null,

    /** 类型标志，只存存在的标志名，如 ["merge", "magic"] */
    val flags: List<String>? = null,

    /** weapon 类专属字段 */
    val weapon: Weapon? = null,

    /** armor 类专属字段 */
    val armor: Armor? = null,

    /** spellbook 类专属字段 */
    val spellbook: Spellbook? = null,
) {
    /** 把数据里的 CLR_* 名称映射成当前主题下的实际颜色 */
    val colorValue: Int
        get() = runCatching { NHColor.valueOf(color).toColor() }
            .getOrDefault(NHColor.NO_COLOR.toColor())
}

/** 卷轴/法术书用魔法标记笔书写时的墨水消耗范围 */
data class Writing(
    val inkCostMin: Int = 0,
    val inkCostMax: Int = 0
)

/** 武器的伤害、技能和攻击方式 */
data class Weapon(
    /** 小型怪物伤害骰，例如 1d8 或 1d6 + 1 */
    val damageSmall: String = "",
    /** 大型怪物伤害骰，例如 1d12 或 1d6 + 1 */
    val damageLarge: String = "",
    /** One-handed / Two-handed */
    val handedness: String? = null,
    val skill: Int = 0,
    /** 技能名称，例如 Long sword */
    val skillType: String? = null,
    val strike: Int = 0,
    val strikeName: String = "",
    val toHit: Int = 0
)

/** 护甲类别、AC 和 MC */
data class Armor(
    val category: Int = 0,
    val categoryName: String = "",
    val ac: Int = 0,
    val can: Int = 0
)

/** 法术书等级、学派和施法消耗 */
data class Spellbook(
    val level: Int = 0,
    val skill: String = "",
    /** 施放该法术消耗的 Pw */
    val castingCost: Int = 0
)

