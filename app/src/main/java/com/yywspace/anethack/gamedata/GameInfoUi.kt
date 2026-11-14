package com.yywspace.anethack.gamedata

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.chip.ChipGroup
import com.yywspace.anethack.R

/**
 * 怪物/物品资料卡共用的基础 View 构建器。
 *
 * 只负责外观结构：标题、属性网格、面板、分节；具体数据仍由各自弹窗提供。
 */
class GameInfoUi(private val context: Context) {

    fun header(
        symbol: String,
        color: Int,
        name: String,
        subtitle: String
    ): View {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        header.addView(TextView(context).apply {
            text = symbol
            textSize = 30f
            setTextColor(color)
            setPadding(0, 0, dp(12), 0)
        })
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        titles.addView(TextView(context).apply {
            text = name
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.app_text))
        })
        if (subtitle.isNotBlank()) {
            titles.addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
            })
        }
        header.addView(titles)
        return header
    }

    /** 标签用字符串资源，业务层不需要自己 getString。 */
    fun infoGridRes(rows: List<List<Pair<Int, String>>>, columns: Int): View =
        infoGrid(
            rows.map { row -> row.map { (res, value) -> context.getString(res) to value } },
            columns
        )

    /** 固定 [columns] 列；不足的格子用空 View 补齐，保证每列等宽对齐。 */
    fun infoGrid(rows: List<List<Pair<String, String>>>, columns: Int): View {
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { row ->
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(3))
            }
            row.forEach { (label, value) ->
                rowView.addView(infoCell(label, value))
            }
            repeat((columns - row.size).coerceAtLeast(0)) {
                rowView.addView(View(context), cellParams())
            }
            grid.addView(rowView)
        }
        return grid
    }

    private fun infoCell(label: String, value: String): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = cellParams()
            addView(TextView(context).apply {
                text = label
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

    fun panel(view: View): View =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.info_card))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

    fun section(parent: LinearLayout, title: String, body: View) {
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        card.addView(TextView(context).apply {
            text = title
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
            setPadding(0, 0, 0, dp(4))
        })
        card.addView(body)
        parent.addView(panel(card))
    }

    /** 搜索栏 + 内容区 + ScrollView 的统一布局骨架。 */
    fun contentScaffold(
        hintRes: Int,
        actionRes: Int,
        initialText: String? = null,
        onSearch: () -> Unit
    ): ContentScaffold {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val search = searchBar(hintRes, actionRes, initialText, onSearch)
        root.addView(search.view)
        val scroll = ScrollView(context).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(scroll)
        return ContentScaffold(root, content, scroll, search)
    }

    class ContentScaffold(
        val root: View,
        val content: LinearLayout,
        val scroll: ScrollView,
        val search: SearchBar
    )

    /** 结果列表末尾的 More 按钮。 */
    fun moreButton(textRes: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = context.getString(textRes)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.info_action))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { onClick() }
        }

    /** 搜索结果重建后回到顶部；More 追加时不要调用。 */
    fun scrollToTop(scroll: ScrollView) {
        scroll.post { scroll.scrollTo(0, 0) }
    }

    /** 搜索栏：无边框输入框 + 紧凑文字按钮，整体是一个圆角底。 */
    fun searchBar(
        hintRes: Int,
        actionRes: Int,
        initialText: String? = null,
        onSearch: () -> Unit
    ): SearchBar {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.info_card))
            }
            setPadding(dp(14), dp(2), dp(4), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val input = TextInputEditText(context).apply {
            hint = context.getString(hintRes)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onSearch()
                    hideKeyboard(this)
                    true
                } else false
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            if (!initialText.isNullOrEmpty()) setText(initialText)
        }
        val button = TextView(context).apply {
            text = context.getString(actionRes)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.info_action))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            val out = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                onSearch()
                hideKeyboard(input)
            }
        }
        bar.addView(input)
        bar.addView(button)
        return SearchBar(bar, input)
    }

    private fun hideKeyboard(input: TextInputEditText) {
        input.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    class SearchBar(val view: View, val input: TextInputEditText)

    /** 属性气泡：用于怪物特性、物品 flags 等可枚举内容。 */
    fun chips(items: List<String>): View =
        ChipGroup(context).apply {
            isSingleLine = false
            chipSpacingHorizontal = dp(6)
            chipSpacingVertical = dp(6)
            setPadding(0, 0, 0, dp(4))
            val chipBackground = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                // section 面板是 info_card，气泡用 info_chip 形成层次
                setColor(ContextCompat.getColor(context, R.color.info_chip))
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

    fun divider(): View = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.app_divider))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(8); bottomMargin = dp(8) }
    }

    fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun cellParams() = LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
    )

}
