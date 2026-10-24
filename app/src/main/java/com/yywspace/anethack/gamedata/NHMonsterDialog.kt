package com.yywspace.anethack.gamedata

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHColor
import com.yywspace.anethack.extensions.show

/**
 * 怪物信息弹窗：输入名字查询（不区分大小写，找不到精确名字时按包含匹配），
 * 下方按 wiki 信息框的顺序展示这只怪物的全部属性。
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
    private lateinit var input: EditText
    private var dialog: AlertDialog? = null

    /** 按怪物名打开弹窗：预填名字并直接查出结果（比如消息里点到怪物名时） */
    fun showFromMonsterName(name: String) {
        show()
        input.setText(name)
        query()
    }

    fun show() {
        // 设置里关掉"怪物信息"后什么都不做（不建视图、不加载数据）
        if (nh?.prefs?.monsterInfo == false)
            return
        if (dialog?.isShowing == true)
            return
        GameData.ensureLoaded(context)
        content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        val search = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(context).apply {
            hint = context.getString(R.string.monster_lookup_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val button = TextView(context).apply {
            text = context.getString(R.string.monster_lookup_show)
            setTextColor(ContextCompat.getColor(context, R.color.app_text))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { query() }
        }
        search.addView(input)
        search.addView(button)
        root.addView(search)

        root.addView(
            ScrollView(context).apply {
                addView(content)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(420)
                )
            }
        )

        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.monster_lookup_title)
            .setView(root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> }
            .create()
        // 和价格鉴定弹窗一样：沉浸式时不带出系统导航栏
        dialog?.show(nh.prefs.immersiveMode)
        input.requestFocus()
    }

    private fun query() {
        val key = input.text.toString().trim()
        if (key.isEmpty())
            return
        // 一个名字可能对应多条（比如 wererat 有人形/兽形两条），全部展示
        var matched = GameData.findMonsters(key)
        if (matched.isEmpty())
            matched = GameData.monsters.filter { it.name.contains(key, ignoreCase = true) }
        content.removeAllViews()
        if (matched.isEmpty()) {
            content.addView(text(context.getString(R.string.monster_lookup_not_found)))
            return
        }
        matched.forEachIndexed { index, monster ->
            if (index > 0)
                content.addView(divider())
            render(monster)
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
        // 标题行：符号 + 名字
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        header.addView(TextView(context).apply {
            text = monster.symbol
            textSize = 28f
            setTextColor(colorOf(monster.color))
            setPadding(0, 0, dp(12), 0)
        })
        header.addView(TextView(context).apply {
            text = monster.name
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.app_text))
        })
        content.addView(header)

        row(R.string.monster_field_difficulty, monster.difficulty.toString())
        row(R.string.monster_field_attacks, monster.attacks)
        row(R.string.monster_field_level, monster.baseLevel.toString())
        row(R.string.monster_field_experience, monster.experience?.toString() ?: "—")
        row(R.string.monster_field_speed, monster.speed.toString())
        row(R.string.monster_field_ac, monster.baseAC.toString())
        row(R.string.monster_field_mr, monster.baseMR.toString())
        row(R.string.monster_field_alignment, "${monster.alignment} (${monster.alignmentName})")
        row(R.string.monster_field_frequency, monster.frequency)
        row(R.string.monster_field_genocidable, monster.genocidable)
        row(R.string.monster_field_weight, monster.weight.toString())
        row(R.string.monster_field_nutrition, monster.nutrition.toString())
        row(R.string.monster_field_size, monster.size)
        row(R.string.monster_field_resistances, monster.resistances)
        row(R.string.monster_field_resistances_conveyed, monster.resistancesConveyed)
        row(
            R.string.monster_field_attributes,
            monster.attributeDescriptions.joinToString(" ")
        )
    }

    private fun row(labelRes: Int, value: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(context).apply {
            text = context.getString(labelRes)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.app_text))
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        content.addView(row)
    }

    private fun text(value: String) = TextView(context).apply {
        text = value
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.app_text))
        setPadding(0, dp(8), 0, dp(8))
    }

    /** 颜色名 → NHColor（bright blue → CLR_BRIGHT_BLUE） */
    private fun colorOf(name: String): Int {
        val enumName = "CLR_" + name.uppercase().replace(" ", "_")
        val nhColor = NHColor.entries.firstOrNull { it.name == enumName } ?: NHColor.NO_COLOR
        return nhColor.toColor()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
