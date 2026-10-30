package com.yywspace.anethack.gamedata

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.Utils
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.extensions.show

/**
 * 怪物信息弹窗：输入名字查询（不区分大小写，找不到精确名字时按包含匹配），
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

    /**
     * 打开弹窗。
     *
     * [name] 为空时是空白搜索框；不为空时预填该名字并直接查出结果
     * （消息里点到怪物名就是这条路径）。
     */
    fun showForMonster(name: String? = null) {
        // 设置里关掉了怪物信息，或者已经有一个弹窗在显示
        if (!nh.prefs.monsterInfo || dialog?.isShowing == true)
            return
        val view = buildView(name)
        if (!name.isNullOrEmpty())
            renderMatches()
        showDialog(view)
    }

    /** 构建弹窗的内容视图：搜索行 + 结果区；[initialText] 不为空时预填到输入框 */
    private fun buildView(initialText: String? = null): View {
        GameData.ensureLoaded(context)
        content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        // 搜索栏：整条是一个圆角底，里面放无边框输入框 + 紧凑按钮，读起来是一个整体
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.monster_card))
            }
            setPadding(dp(14), dp(2), dp(4), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        input = TextInputEditText(context).apply {
            hint = context.getString(R.string.monster_lookup_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    renderMatches()
                    true
                } else false
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            if (!initialText.isNullOrEmpty())
                setText(initialText)
        }
        // 按钮做成搜索栏内的文字操作区：无底色、无水波纹，避免和圆角输入区打架
        val button = TextView(context).apply {
            text = context.getString(R.string.monster_lookup_search)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.monster_action))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            // 水波纹背景：结果多时卡片是逐帧插入的，点击回调本身很快，波纹不会被卡住
            val out = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { renderMatches() }
        }
        bar.addView(input)
        bar.addView(button)
        root.addView(bar)

        scroll = ScrollView(context).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(scroll)
        return root
    }

    /** 真正把弹窗显示出来 */
    private fun showDialog(view: View) {
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.monster_lookup_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> }
            .create()
        // 和价格鉴定弹窗一样：沉浸式时不带出系统导航栏
        dialog?.show(nh.prefs.immersiveMode)
        input.requestFocus()
    }

    /** 按输入框内容查找怪物，并把结果渲染到 content 里 */
    private fun renderMatches() {
        val key = input.text.toString().trim()
        if (key.isEmpty())
            return
        // 一个名字可能对应多条（比如 wererat 有人形/兽形两条），全部展示
        var matched = GameData.findMonsters(key)
        if (matched.isEmpty())
            matched = GameData.monsters.filter { it.name.contains(key, ignoreCase = true) }
        // 结果按名字排序，加载顺序稳定
        matched = matched.sortedBy { it.name }
        content.removeAllViews()
        // 查不到就保持空列表，不显示额外提示
        if (matched.isEmpty()) {
            fitScrollHeight()
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
    }

    /** 多条结果之间的分隔线 */
    private fun divider() = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(8); bottomMargin = dp(8) }
    }

    private fun render(monster: Monster) {
        // 标题区：符号（怪物颜色）+ 名字 + 副标题（体型 · 阵营 · 能否被灭绝）
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        header.addView(TextView(context).apply {
            text = monster.symbol
            textSize = 30f
            setTextColor(NHColor.fromInt(monster.colorCode).toColor())
            setPadding(0, 0, dp(12), 0)
        })
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(context).apply {
            text = monster.name
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.app_text))
        })
        val subtitleParts = mutableListOf(monster.size, monster.alignmentName)
        // 可灭绝才加这个单词，不可灭绝就不显示
        if (monster.genocidable)
            subtitleParts.add(context.getString(R.string.monster_genocidable_yes))
        val subtitle = subtitleParts.filter { it.isNotBlank() }.joinToString(" · ")
        titles.addView(TextView(context).apply {
            text = subtitle
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
        })
        header.addView(titles)
        content.addView(header)

        // 数值属性：3 行 × 3 列，居中，外面套一层浅色圆角背景
        content.addView(panel(statsGrid(monster)))

        // 攻击：重复的攻击合并成 "2× …"，保留攻击次数
        if (monster.attacks.isNotEmpty())
            section(R.string.monster_field_attacks, chips(groupAttacks(monster.attacks)))
        // 抗性：chip 是自身抗性，下面一行文字说明吃尸体能获得什么
        val resistances = monster.resistances
        val conveyed = monster.resistancesConveyed
        if (resistances.isNotEmpty() || conveyed.isNotEmpty()) {
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            if (resistances.isNotEmpty())
                body.addView(chips(resistances))
            if (conveyed.isNotEmpty())
                body.addView(TextView(context).apply {
                    // 更小的字号，作为上面这组抗性下面的补充说明
                    text = context.getString(
                        R.string.monster_conveyed_line, conveyed.joinToString(", ")
                    )
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
                    setPadding(0, dp(2), 0, 0)
                })
            section(R.string.monster_field_resistances, body)
        }
        // 特性：原始标志经过映射表转成展示文字
        if (monster.attributes.isNotEmpty())
            section(
                R.string.monster_field_attributes,
                chips(monster.attributes.map { GameData.attributeLabel(it) })
            )
    }

    /**
     * 3 行 × 3 列，按含义分行：
     *   第一行 等级与出现：Level / Difficulty / Freq
     *   第二行 防御与移动：AC / MR / Speed
     *   第三行 其余：XP / Weight / Nutrition
     */
    private fun statsGrid(monster: Monster): View {
        val rows = listOf(
            listOf(
                R.string.monster_field_level to monster.baseLevel.toString(),
                R.string.monster_field_difficulty to monster.difficulty.toString(),
                R.string.monster_field_frequency to monster.frequency.toString(),
            ),
            listOf(
                R.string.monster_field_ac to monster.baseAC.toString(),
                R.string.monster_field_mr to monster.baseMR.toString(),
                R.string.monster_field_speed to monster.speed.toString(),
            ),
            listOf(
                R.string.monster_field_nutrition to monster.nutrition.toString(),
                R.string.monster_field_weight to monster.weight.toString(),
                R.string.monster_field_experience to (monster.experience?.toString() ?: "—"),
            ),
        )
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { row ->
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.forEach { (labelRes, value) -> rowView.addView(statCell(labelRes, value)) }
            grid.addView(rowView)
        }
        return grid
    }

    /** 一个数值格子：小号标签在上、大号数值在下，整体居中 */
    private fun statCell(labelRes: Int, value: String): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = context.getString(labelRes)
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
            })
            addView(TextView(context).apply {
                text = value
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.app_text))
            })
        }

    /** 给网格套一层浅色圆角背景，居中后两侧的留白就不会显得突兀 */
    private fun panel(view: View): View =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.monster_card))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    /** 小节标题 + 内容，整体放进和基本信息一样的圆角背景里，用来分组 */
    private fun section(labelRes: Int, body: View) {
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        card.addView(TextView(context).apply {
            text = context.getString(labelRes)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
            setPadding(0, 0, 0, dp(4))
        })
        card.addView(body)
        content.addView(panel(card))
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

    /**
     * 一组可换行的 chip。
     *
     * 用普通 TextView + 圆角背景代替 Material 的 Chip：一次查询可能要建上百个 chip，
     * Material Chip 每个都带一堆内部状态，会把主线程卡住（水波纹都动不了）。
     */
    private fun chips(items: List<String>): View =
        ChipGroup(context).apply {
            isSingleLine = false
            // 上下、左右间距保持一致
            chipSpacingHorizontal = dp(6)
            chipSpacingVertical = dp(6)
            setPadding(0, 0, 0, dp(4))
            val chipBackground = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.monster_chip))
            }
            items.filter { it.isNotBlank() }.forEach { value ->
                addView(TextView(context).apply {
                    text = value
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(3), dp(10), dp(3))
                    background = chipBackground.constantState?.newDrawable()?.mutate()
                    setTextColor(ContextCompat.getColor(context, R.color.app_text))
                })
            }
        }



    /**
     * 让结果区高度跟随内容：内容少就矮，内容多则停在屏幕可用高度的一定比例上，
     * 超出的部分交给 ScrollView 滚动（不给上限的话结果区会和内容一样高，反而滚不动）。
     */
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
            val button = TextView(context).apply {
                text = context.getString(R.string.monster_lookup_more_button)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.monster_action))
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { renderNextBatch() }
            }
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

    /** dp → px：复用 [Utils.dip2px]，这里只是把调用写短一点 */
    private fun dp(value: Int): Int = Utils.dip2px(context, value.toFloat())

    companion object {
        /** 结果区最多占屏幕高度的比例，超过就滚动 */
        private const val MAX_HEIGHT_FRACTION = 0.60f
        /** 每次渲染多少张卡片（首批也用它） */
        private const val BATCH_SIZE = 2
    }
}
