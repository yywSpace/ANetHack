package com.yywspace.anethack.gamedata

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
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
    /** 待渲染的匹配结果与已渲染数量（滑到底部时继续追加） */
    private var lazyMonsters: List<Monster> = emptyList()
    private var lazyRendered = 0
    /** 列表末尾的 More 按钮（每次追加前先移除） */
    private var moreButton: TextView? = null

    /** 结果滚动区：高度按内容自适应 */
    private lateinit var scroll: ScrollView
    private lateinit var input: TextInputEditText
    private var dialog: AlertDialog? = null
    private val ui = GameInfoUi(context)

    /**
     * 打开弹窗。
     *
     * [name] 为空时展示全部怪物；不为空时预填该名字并过滤
     * （消息里点到怪物名就是这条路径）。
     */
    fun showForMonster(name: String? = null) {
        // 设置里关掉了怪物信息，或者已经有一个弹窗在显示
        if (!nh.prefs.monsterInfo || dialog?.isShowing == true)
            return
        val view = buildView(name)
        renderMatches()
        // 带名字进来（点怪物名字查看）时不抢焦点，否则光标会在搜索框里闪
        showDialog(view, focusInput = name.isNullOrEmpty())
    }

    /** 构建弹窗的内容视图：搜索行 + 结果区；[initialText] 不为空时预填到输入框 */
    private fun buildView(initialText: String? = null): View {
        GameData.ensureLoaded(context)
        val scaffold = ui.contentScaffold(
            R.string.monster_lookup_hint,
            R.string.monster_lookup_search,
            initialText
        ) {
            renderMatches()
        }
        content = scaffold.content
        scroll = scaffold.scroll
        input = scaffold.search.input
        return scaffold.root
    }

    /** 真正把弹窗显示出来；[focusInput] 为 false 时不拉焦点、不弹输入法 */
    private fun showDialog(view: View, focusInput: Boolean) {
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.monster_lookup_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> }
            .create()
        // 和价格鉴定弹窗一样：沉浸式时不带出系统导航栏
        dialog?.show(nh.prefs.immersiveMode)
        if (focusInput) {
            input.requestFocus()
        } else {
            dialog?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            input.clearFocus()
        }
    }

    /** 查询后收起输入法：结果列表就在下方，键盘会挡住它们 */
    /** 按输入框内容查找怪物，并把结果渲染到 content 里 */
    private fun renderMatches() {
        val key = input.text.toString().trim()
        // 查询为空时展示全部怪物；有输入时再按名字过滤
        var matched = if (key.isEmpty())
            GameData.monsters
        else
            GameData.findMonsters(key)
        if (key.isNotEmpty() && matched.isEmpty())
            matched = GameData.monsters.filter { it.name.contains(key, ignoreCase = true) }
        // 结果按名字排序，加载顺序稳定
        matched = matched.sortedBy { it.name }
        content.removeAllViews()
        // 查不到就保持空列表，不显示额外提示
        if (matched.isEmpty()) {
            fitScrollHeight()
            ui.scrollToTop(scroll)
            return
        }
        val shown = matched
        if (shown.size <= BATCH_SIZE) {
            // 结果不多：直接一次性渲染
            shown.forEachIndexed { index, monster ->
                if (index > 0)
                    content.addView(divider())
                render(monster)
            }
            fitScrollHeight()
        } else {
            // 结果较多：只渲染第一批，剩下的滑到底部时再追加
            lazyMonsters = shown
            lazyRendered = 0
            content.removeAllViews()
            reserveMaxHeight()
            renderNextBatch()
        }
        ui.scrollToTop(scroll)
    }

    /** 多条结果之间的分隔线 */
    private fun divider() = ui.divider()

    private fun render(monster: Monster) {
        // 标题区：符号（怪物颜色）+ 名字 + 副标题（体型 · 阵营 · 能否被灭绝）
        val subtitleParts = mutableListOf(monster.size, monster.alignmentName)
        if (monster.genocidable)
            subtitleParts.add(context.getString(R.string.monster_genocidable_yes))
        content.addView(ui.header(
            symbol = monster.symbol,
            color = monster.colorValue,
            name = monster.name,
            subtitle = subtitleParts.filter { it.isNotBlank() }.joinToString(" · ")
        ))

        // 数值属性：3 行 × 3 列，居中，外面套一层浅色圆角背景
        content.addView(ui.panel(infoGrid(monster)))

        // 攻击：重复的攻击合并成 "2× …"，保留攻击次数
        if (monster.attacks.isNotEmpty())
            section(R.string.monster_field_attacks, ui.chips(groupAttacks(monster.attacks)))
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
            section(R.string.monster_field_resistances, body)
        }
        // 特性：原始标志经过映射表转成展示文字
        if (monster.attributes.isNotEmpty())
            section(
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
    private fun section(labelRes: Int, body: View) {
        ui.section(content, context.getString(labelRes), body)
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

    /** 追加下一批结果卡片，并在末尾放一个 More 按钮继续加载 */
    private fun renderNextBatch() {
        moreButton?.let { content.removeView(it) }
        val end = minOf(lazyRendered + BATCH_SIZE, lazyMonsters.size)
        for (index in lazyRendered until end) {
            if (index > 0)
                content.addView(divider())
            render(lazyMonsters[index])
        }
        lazyRendered = end
        val remaining = lazyMonsters.size - lazyRendered
        if (remaining > 0) {
            val button = ui.moreButton(R.string.monster_lookup_more_button) { renderNextBatch() }
            moreButton = button
            content.addView(button)
        } else {
            moreButton = null
            fitScrollHeight()
        }
    }

    /** 直接把结果区高度设成上限（分帧渲染期间用，避免高度反复变化） */
    private fun reserveMaxHeight() {
        val screenHeight = context.resources.displayMetrics.heightPixels
        val params = scroll.layoutParams
        params.height = (screenHeight * MAX_HEIGHT_FRACTION).toInt()
        scroll.layoutParams = params
    }

    private fun fitScrollHeight() {
        scroll.post {
            val screenHeight = context.resources.displayMetrics.heightPixels
            val max = (screenHeight * MAX_HEIGHT_FRACTION).toInt()
            val params = scroll.layoutParams
            params.height = minOf(content.height, max)
            scroll.layoutParams = params
        }
    }

    companion object {
        /** 结果区最多占屏幕高度的比例，超过就滚动 */
        private const val MAX_HEIGHT_FRACTION = 0.60f
        /** 每次渲染多少张卡片（首批也用它） */
        private const val BATCH_SIZE = 2
    }
}

