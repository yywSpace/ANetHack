package com.yywspace.anethack.gamedata

import com.yywspace.anethack.entity.NHColor

/**
 * 图鉴里的一条怪物信息，字段顺序与 wiki 怪物信息框一致
 * （数据来自 NetHack 5.0.0 源码 monsters.h，`experience` 取自 wiki）。
 *
 * 对应 assets/gamedata/monsters.json 里的每一条。
 */
data class Monster(
    /** 屏幕符号 */
    val symbol: String = "",
    /** 显示颜色（仅用于展示，取色请用 [colorCode]） */
    val color: String = "",
    /** 颜色索引，对应 [NHColor] 的 ordinal（同 NetHack color.h 的 CLR_* 编号） */
    val colorCode: Int = NHColor.NO_COLOR.ordinal,
    /** 怪物名；多形态怪物带后缀，如 "werejackal (human)" / "werejackal (animal)" */
    val name: String,
    val difficulty: Int = 0,
    /** 攻击列表，按源码里的攻击槽顺序，如 ["Bite 1d4 physical", "Claw 1d8 physical"] */
    val attacks: List<String> = emptyList(),
    val baseLevel: Int = 0,
    /** 击杀经验（wiki 数据，源码表里没有该字段） */
    val experience: Int? = null,
    val speed: Int = 0,
    val baseAC: Int = 0,
    val baseMR: Int = 0,
    /** 阵营数值：正数 lawful、负数 chaotic、0 neutral */
    val alignment: Int = 0,
    /** 阵营名：lawful / chaotic / neutral */
    val alignmentName: String = "",
    /** 出现频率（源码里的数字，可能带注释，所以用字符串） */
    val frequency: String = "",
    val genocidable: String = "",
    val weight: Int = 0,
    val nutrition: Int = 0,
    val size: String = "",
    val resistances: String = "",
    val resistancesConveyed: String = "",
    /** 属性标识，如 nohands / animal / hostile */
    val attributes: List<String> = emptyList(),
    /** 属性的可读描述，与 [attributes] 按下标对应 */
    val attributeDescriptions: List<String> = emptyList()
)
