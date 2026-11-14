package com.yywspace.anethack.gamedata

import android.content.Context
import android.view.View
import android.view.ViewGroup
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
 * 物品资料弹窗：搜索并按类别展示 [Item]。
 *
 * 只显示该物品实际拥有的类别属性。
 */
class NHItemDialog(
    private val context: Context,
    private val nh: NetHack
) {
    private var dialog: AlertDialog? = null
    private val ui = GameInfoUi(context)
    private lateinit var content: LinearLayout
    private lateinit var input: TextInputEditText
    private lateinit var scroll: ScrollView
    private var pending: List<Item> = emptyList()
    private var rendered = 0
    private var moreButton: TextView? = null

    fun showItems() {
        if (dialog?.isShowing == true)
            return
        GameData.ensureLoaded(context)
        val view = buildView()
        dialog = ui.createDialog(R.string.item_lookup_title, view)
        dialog?.show(nh.prefs.immersiveMode)
        renderMatches()
    }

    /** 指定物品详情：没有搜索栏。 */
    fun showItem(name: String) {
        if (dialog?.isShowing == true)
            return
        GameData.ensureLoaded(context)
        val scaffold = ui.singleScaffold()
        content = scaffold.content
        scroll = scaffold.scroll
        renderSingle(name)
        dialog = ui.createDialog(R.string.item_lookup_title, scaffold.root)
        dialog?.show(nh.prefs.immersiveMode)
    }

    private fun renderSingle(name: String) {
        val matched = GameData.items.filter { it.name.equals(name, ignoreCase = true) }
        content.removeAllViews()
        matched.sortedWith(compareBy<Item> { it.itemClass }.thenBy { it.name })
            .forEach { content.addView(card(it)) }
        ui.scrollToTop(scroll)
    }

    private fun buildView(): View {
        val scaffold = ui.contentScaffold(
            R.string.item_lookup_hint,
            R.string.item_lookup_search
        ) {
            renderMatches()
        }
        content = scaffold.content
        scroll = scaffold.scroll
        input = scaffold.search.input
        return scaffold.root
    }


    private fun renderMatches() {
        val query = input.text?.toString()?.trim().orEmpty()
        pending = if (query.isEmpty()) {
            GameData.items
        } else {
            GameData.items.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.itemClass.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true)
            }
        }.sortedWith(compareBy<Item> { it.itemClass }.thenBy { it.name })
        content.removeAllViews()
        rendered = 0
        moreButton = null
        if (pending.isEmpty()) {
            content.addView(TextView(context).apply {
                text = context.getString(R.string.item_lookup_empty)
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
                setPadding(ui.dp(4), ui.dp(16), ui.dp(4), ui.dp(16))
            })
            return
        }
        appendNext()
        ui.scrollToTop(scroll)
    }

    private fun appendNext() {
        moreButton?.let { content.removeView(it) }
        moreButton = null
        val end = minOf(rendered + BATCH_SIZE, pending.size)
        while (rendered < end) {
            content.addView(card(pending[rendered++]))
        }
        if (rendered < pending.size) {
            val more = ui.moreButton(R.string.item_lookup_more) { appendNext() }
            content.addView(more)
            moreButton = more
        }
    }

    private fun card(item: Item): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ui.dp(8) }
        }

        // 标题下面显示默认外观和材质，不再显示类别
        val subtitle = listOfNotNull(
            item.description,
            item.material
        ).filter { it.isNotBlank() }.joinToString(" · ")
        card.addView(ui.header(
            symbol = item.symbol,
            color = item.colorValue,
            name = item.name,
            subtitle = subtitle
        ))

        card.addView(ui.panel(infoGrid(item)))

        item.weapon?.let { w ->
            val rows = mutableListOf(
                listOf(
                    R.string.item_label_damage_small to w.damageSmall,
                    R.string.item_label_damage_large to w.damageLarge,
                    R.string.item_label_to_hit to w.toHit.toString()
                )
            )
            rows.add(listOf(
                R.string.item_label_skill to (w.skillType ?: "—"),
                R.string.item_label_strike to w.strikeName,
                R.string.item_label_handed to (w.handedness ?: "One-handed")
            ))
            sectionGrid(card, R.string.item_section_weapon, rows)
        }
        item.armor?.let { a ->
            sectionGrid(card, R.string.item_section_armor, listOf(listOf(
                R.string.item_label_armor_type to a.categoryName,
                R.string.item_label_ac to a.ac.toString(),
                R.string.item_label_mc to a.can.toString()
            )))
        }
        item.spellbook?.let { s ->
            val cells = mutableListOf(R.string.item_label_spell_level to s.level.toString())
            if (s.skill.isNotBlank()) cells.add(R.string.item_label_school to s.skill)
            cells.add(R.string.item_label_pw_cost to
                if (s.castingCost > 0) s.castingCost.toString()
                else context.getString(R.string.item_value_na))
            sectionGrid(card, R.string.item_section_spellbook, listOf(cells))
        }
        item.writing?.let { w ->
            ui.section(card, context.getString(R.string.item_section_writing), TextView(context).apply {
                text = context.getString(R.string.item_writing_ink, w.inkCostMin, w.inkCostMax)
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.app_text))
                setPadding(0, ui.dp(2), 0, ui.dp(2))
            })
        }

        val effects = item.effect.orEmpty()
        if (effects.isNotEmpty()) {
            ui.section(
                card,
                context.getString(R.string.item_section_effect),
                ui.chips(effects.map { GameData.effectLabel(it) })
            )
        }

        val trueFlags = item.flags.orEmpty()
            .map { GameData.flagLabel(it) }
        if (trueFlags.isNotEmpty())
            ui.section(card, context.getString(R.string.item_section_flags), ui.chips(trueFlags))
        return card
    }

    private fun infoGrid(item: Item): View {
        val cells = mutableListOf(
            context.getString(R.string.item_label_price) to item.price.toString(),
            context.getString(R.string.item_label_weight) to item.weight.toString(),
            context.getString(R.string.item_label_probability) to item.probability.toString()
        )
        item.delay?.takeIf { it > 0 }?.let {
            cells.add(GameData.delayLabel(item.itemClass) to it.toString())
        }
        item.nutrition?.let {
            cells.add(context.getString(R.string.item_label_nutrition) to it.toString())
        }
        item.direction?.let {
            cells.add(context.getString(R.string.item_label_direction) to it)
        }
        return ui.infoGrid(cells.chunked(3), 3)
    }

    private fun sectionGrid(
        parent: LinearLayout,
        titleRes: Int,
        rows: List<List<Pair<Int, String>>>
    ) {
        ui.section(parent, context.getString(titleRes), ui.infoGridRes(rows, 3))
    }

    private companion object {
        const val BATCH_SIZE = 5
    }
}

