package com.yywspace.anethack.gamedata

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.window.NHDialog
import com.yywspace.anethack.extensions.show

/**
 * 怪物信息弹窗：默认展示全部怪物；输入名字后按名字过滤。
 * 结果按 "基本信息网格 + 攻击 / 抗性 / 特性卡片" 分块展示。
 *
 * 显示方式和价格鉴定弹窗一致：[AlertDialog.show] 带沉浸式参数，
 * 这样弹出时不会把系统导航栏带出来。
 */
class NHMonsterDialog(
    private val context: Context,
    private val nh: NetHack
) {

    /** 每次打开都重建，避免复用时还挂着上一次的父布局 */
    private lateinit var content: LinearLayout
    private lateinit var recycler: RecyclerView
    private var sourceMonsters: List<Monster>? = null
    private val listAdapter = InfoCardAdapter(
        cardOf = { buildMonsterCard(it) },
        diffCallback = object : DiffUtil.ItemCallback<Monster>() {
            override fun areItemsTheSame(oldItem: Monster, newItem: Monster) =
                oldItem == newItem
            override fun areContentsTheSame(oldItem: Monster, newItem: Monster) = oldItem == newItem
        }
    )

    /** 单条详情的滚动区 */
    private lateinit var scroll: ScrollView
    private lateinit var input: TextInputEditText
    private var dialog: Dialog? = null
    private val ui = GameInfoUi(context)

    /** 图鉴入口：搜索栏 + 全部怪物。 */
    fun showMonsterList() {
        if (dialog?.isShowing == true)
            return
        sourceMonsters = null
        val view = buildView()
        renderMatches()
        showDialog(view)
    }

    /** 同名多形态怪物：展示一组结果。 */
    fun showMonsters(monsters: List<Monster>) {
        if (dialog?.isShowing == true || monsters.isEmpty())
            return
        GameData.ensureLoaded(context)
        sourceMonsters = monsters
        val view = buildView()
        listAdapter.submitList(monsters.sortedBy { it.name })
        showDialog(view)
    }

    /** 消息里点击怪物名：直接使用已经解析出的怪物对象。 */
    fun showMonster(monster: Monster) {
        if (dialog?.isShowing == true)
            return
        GameData.ensureLoaded(context)
        val view = buildSingleView()
        renderSingle(monster)
        showDialog(view)
    }

    /** 构建弹窗的内容视图 */
    private fun buildView(): View {
        GameData.ensureLoaded(context)
        val scaffold = ui.recyclerScaffold(
            R.string.monster_lookup_hint,
            R.string.monster_lookup_search
        ) {
            renderMatches()
        }
        recycler = scaffold.recycler
        recycler.adapter = listAdapter
        input = scaffold.search.input
        return scaffold.root
    }

    /** 单怪物详情页：没有搜索栏，只有可滚动的内容区。 */
    private fun buildSingleView(): View {
        val scaffold = ui.singleScaffold()
        content = scaffold.content
        scroll = scaffold.scroll
        return scaffold.root
    }

    private fun renderSingle(monster: Monster) {
        content.removeAllViews()
        render(monster, content)
        ui.scrollToTop(scroll)
    }

    private fun showDialog(view: View) {
        dialog = ui.createDialog(R.string.monster_lookup_title, view)
        // 和价格鉴定弹窗一样：沉浸式时不带出系统导航栏
        dialog?.show()
    }

    /** 按输入框内容查找怪物，并把结果交给列表。 */
    private fun renderMatches() {
        val key = input.text.toString().trim()
        val source = sourceMonsters ?: GameData.monsters
        val matched = if (key.isEmpty())
            source.sortedBy { it.name }
        else
            findMatches(source, key)
        listAdapter.submitList(matched) {
            recycler.post { recycler.scrollToPosition(0) }
        }
    }

    private fun findMatches(source: List<Monster>, key: String): List<Monster> {
        var matched = source.filter { it.name.equals(key, ignoreCase = true) }
        if (matched.isEmpty())
            matched = source.filter { it.name.contains(key, ignoreCase = true) }
        return matched.sortedBy { it.name }
    }

    private fun buildMonsterCard(monster: Monster): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        render(monster, card)
        return card
    }

    private fun render(monster: Monster, target: LinearLayout) {
        // 标题区：符号（怪物颜色）+ 名字 + 副标题（体型 · 阵营 · 能否被灭绝）
        val subtitleParts = mutableListOf(monster.size, monster.alignmentName)
        if (monster.genocidable)
            subtitleParts.add(context.getString(R.string.monster_genocidable_yes))
        target.addView(ui.header(
            symbol = monster.symbol,
            color = monster.colorValue,
            name = monster.name,
            subtitle = subtitleParts.filter { it.isNotBlank() }.joinToString(" · ")
        ))

        // 数值属性：3 行 × 3 列，居中，外面套一层浅色圆角背景
        target.addView(ui.panel(infoGrid(monster)))

        // 攻击：重复的攻击合并成 "2× …"，保留攻击次数
        if (monster.attacks.isNotEmpty())
            section(target, R.string.monster_field_attacks, ui.chips(groupAttacks(monster.attacks)))
        // 抗性：chip 是自身抗性，下面一行文字说明吃尸体能获得什么
        val resistances = monster.resistances
        val conveyed = monster.resistancesConveyed
        if (resistances.isNotEmpty() || conveyed.isNotEmpty()) {
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            if (resistances.isNotEmpty())
                body.addView(ui.chips(resistances))
            if (conveyed.isNotEmpty())
                body.addView(TextView(context).apply {
                    // 更小的字号，作为上面这组抗性下面的补充说明
                    text = context.getString(
                        R.string.monster_conveyed_line, conveyed.joinToString(", ")
                    )
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
                    setPadding(0, ui.dp(2), 0, 0)
                })
            section(target, R.string.monster_field_resistances, body)
        }
        // 特性：原始标志经过映射表转成展示文字
        if (monster.attributes.isNotEmpty())
            section(
                target,
                R.string.monster_field_attributes,
                ui.chips(monster.attributes.map { GameData.attributeLabel(it) })
            )
    }

    /**
     * 3 行 × 3 列，按含义分行：
     *   第一行 等级与出现：Level / Difficulty / Freq
     *   第二行 防御与移动：AC / MR / Speed
     *   第三行 其余：XP / Weight / Nutrition
     */
    private fun infoGrid(monster: Monster): View {
        val rows = listOf(
            listOf(
                R.string.monster_field_level to monster.baseLevel.toString(),
                R.string.monster_field_difficulty to monster.difficulty.toString(),
                R.string.monster_field_frequency to monster.frequency.toString()
            ),
            listOf(
                R.string.monster_field_ac to monster.baseAC.toString(),
                R.string.monster_field_mr to monster.baseMR.toString(),
                R.string.monster_field_speed to monster.speed.toString()
            ),
            listOf(
                R.string.monster_field_nutrition to monster.nutrition.toString(),
                R.string.monster_field_weight to monster.weight.toString(),
                R.string.monster_field_experience to (monster.experience?.toString() ?: "—")
            )
        )
        return ui.infoGridRes(rows, 3)
    }

    /** 小节标题 + 内容，整体放进和基本信息一样的圆角背景里，用来分组 */
    private fun section(parent: LinearLayout, labelRes: Int, body: View) {
        ui.section(parent, context.getString(labelRes), body)
    }

    /**
     * 把重复的攻击合并成 "2× Claw 1d3 physical" 这种形式：
     * 同一部位连续攻击多次时，原样列出会得到几个一模一样的 chip。
     */
    private fun groupAttacks(attacks: List<String>): List<String> {
        val counts = LinkedHashMap<String, Int>()
        attacks.filter { it.isNotBlank() }.forEach { attack ->
            counts[attack] = (counts[attack] ?: 0) + 1
        }
        return counts.map { (attack, times) -> if (times > 1) "${times}× $attack" else attack }
    }

}

