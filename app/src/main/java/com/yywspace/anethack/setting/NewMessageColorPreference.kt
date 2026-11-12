package com.yywspace.anethack.setting

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHColor

/**
 * 消息颜色设置：点击后弹出 NetHack 调色板，并实时预览消息效果。
 */
class NewMessageColorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : DialogPreference(context, attrs) {

    private val colors = NHColor.entries.filter {
        it != NHColor.NO_COLOR && it != NHColor.CLR_MAX
    }

    init {
        updateSummary()
    }

    override fun onClick() {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_message_color, null)
        val grid = view.findViewById<GridLayout>(R.id.message_color_grid)
        val sample = view.findViewById<TextView>(R.id.message_color_sample)

        var selected = readSelected()
        val cells = mutableListOf<Pair<NHColor, FrameLayout>>()
        grid.removeAllViews()

        colors.forEachIndexed { index, color ->
            val cell = FrameLayout(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(48)
                    columnSpec = GridLayout.spec(index % 5, 1f)
                    rowSpec = GridLayout.spec(index / 5)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                contentDescription = color.displayName()
                setOnClickListener {
                    selected = color
                    renderSelection(cells, sample, selected)
                }
            }
            val swatch = android.view.View(context).apply {
                background = GradientDrawable().apply {
                    setColor(color.toColor())
                    cornerRadius = dp(6).toFloat()
                }
            }
            cell.addView(swatch, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            val check = TextView(context).apply {
                text = "✓"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(contrastColor(color.toColor()))
            }
            cell.addView(check, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            cells.add(color to cell)
            grid.addView(cell)
        }

        renderSelection(cells, sample, selected)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val newValue = selected.ordinal.toString()
                if (callChangeListener(newValue)) {
                    persistString(newValue)
                    updateSummary()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        updateSummary()
    }

    private fun readSelected(): NHColor {
        val raw = sharedPreferences?.getString(key, null)
            ?: NHColor.CLR_GREEN.ordinal.toString()
        return NHColor.fromInt(raw.toIntOrNull() ?: NHColor.CLR_GREEN.ordinal)
    }

    private fun updateSummary() {
        summary = readSelected().displayName()
    }

    private fun renderSelection(
        cells: List<Pair<NHColor, FrameLayout>>,
        sample: TextView,
        selected: NHColor
    ) {
        cells.forEach { (color, cell) ->
            val isSelected = color == selected
            cell.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(
                    if (isSelected) dp(3) else dp(1),
                    if (isSelected) context.getColor(R.color.app_text) else 0x55888888
                )
                cornerRadius = (dp(6) + dp(2)).toFloat()
            }
            cell.getChildAt(1)?.visibility =
                if (isSelected) android.view.View.VISIBLE else android.view.View.INVISIBLE
        }
        sample.setTextColor(selected.toColor())
    }

    private fun NHColor.displayName(): String =
        name.removePrefix("CLR_").replace('_', ' ').lowercase()

    private fun contrastColor(color: Int): Int =
        if (Color.luminance(color) > 0.55f) Color.BLACK else Color.WHITE

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
