package com.yywspace.anethack.keybord

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView

/**
 * 自定义布局（Cust）。
 *
 * 视图结构和标准布局一样先建好：
 * - 固定行（第 0/4 行）按 keyboard_custom_template.json 建格子，文字来自 assets；
 * - 中间三行每行一个横向滚动容器，内容来自本地 commands/custom_keyboard_rows.json。
 * 切进来时只装数据，不重建结构。
 */
class NHCustomKeyboardView(context: Context) : KeyboardGridBase(context) {

    /** 固定行的键视图（只建一次，和模板格子一一对应） */
    private val fixedKeyViews: MutableList<View> = mutableListOf()

    /** 固定行当前的数据（assets 里的 keyboard_custom.json） */
    private var fixedLayout: KeyboardLayout = KeyboardLayout()

    /** 自定义模板（结构建一次） */
    private lateinit var template: KeyboardLayout

    /** 中间三行的滚动容器 */
    private val middleScrollers: MutableList<HorizontalScrollView> = mutableListOf()

    /** 上一次装进中间三行的数据；相同时不再重建按钮 */
    private var loadedRows: List<List<CommandEntry>>? = null

    /** 点固定行的布局键时通知容器 */
    var onSwitchLayout: ((KeyboardLayout.Type) -> Unit)? = null

    init {
        buildStructure()
        loadLayout()
    }

    private fun buildStructure() {
        val placeholderRows = resetGrid()
        template = KeyboardStore.readCustomTemplate(context)
        // 固定行来自 assets，只读一次
        fixedLayout = KeyboardStore.readCustom(context)

        for (rowIndex in listOf(0, 4)) {
            template.rows[rowIndex].keys.forEachIndexed { j, cell ->
                val key = fixedLayout.rows[rowIndex].keys[j]
                val view = createKeyView({ fixedLayout.rows[rowIndex].keys[j] },
                    { key ->
                        if (key.label in specialKeyLabels)
                            onSpecialKeyLongPress?.invoke(key)
                    }
                )
                bindKeyView(view, key.label, key.command, if (key.label == "Space") "/Esc" else null)
                grid.addView(view, gridParams(cell, keyHeight))
                fixedKeyViews.add(view)
                if (rowIndex == 0) numberViews.add(view)
            }
        }
        numberViews.addAll(placeholderRows[0])

        // 第三行最左边固定一个 Letter 键，和标准键盘返回字母的位置一致
        val letterKey = KeyboardLayout.Key(3, 0).apply {
            label = "Letter"
            command = "Letter"
        }
        val letterView = createKeyView(
            { letterKey },
            { key -> if (key.label in specialKeyLabels) onSpecialKeyLongPress?.invoke(key) }
        )
        bindKeyView(letterView, letterKey.label, letterKey.command, null)
        grid.addView(letterView, gridParams(template.rows[3].keys.first(), keyHeight))
    }

    fun loadLayout() {
        val localRows = KeyboardStore.readCustomRows(context)
        if (localRows == loadedRows) {
            setNumberPanelVisible(numberShown)
            return
        }

        for (index in 0 until 3) {
            val rowIndex = index + 1
            val entries = localRows.getOrNull(index).orEmpty()
                .filter { it.label.isNotEmpty() || it.command.isNotEmpty() }
            val scroller = CommandUi.scrolledRow(
                context = context,
                entries = entries,
                bar = false,
                onClick = { entry, _ ->
                    KeyboardVibrate.vibrate()
                    handleKeyClick(entry.asLayoutKey(), null)
                },
                onLongClick = { entry, _, _ ->
                    editKey(rowIndex, entry)
                    true
                },
                onAdd = {
                    KeyboardVibrate.vibrate()
                    editKey(rowIndex, null)
                }
            )
            middleScrollers.getOrNull(index)?.let { grid.removeView(it) }
            val cell = template.rows[rowIndex].keys.last()
            grid.addView(scroller, gridParams(cell, keyHeight))
            if (index < middleScrollers.size) middleScrollers[index] = scroller
            else middleScrollers.add(scroller)
        }
        loadedRows = localRows
        setNumberPanelVisible(numberShown)
    }

    override fun applyTheme() {
        fixedKeyViews.clear()
        middleScrollers.clear()
        loadedRows = null
        buildStructure()
        loadLayout()
    }

    override fun handleKeyClick(key: KeyboardLayout.Key, statusImage: ImageView?) {
        if (key.command.isEmpty()) return
        when (key.command) {
            "Letter" -> onSwitchLayout?.invoke(KeyboardLayout.Type.LETTER)
            "Ctrl" -> onSwitchLayout?.invoke(KeyboardLayout.Type.CTRL)
            "Meta" -> onSwitchLayout?.invoke(KeyboardLayout.Type.META)
            "Symbol" -> onSwitchLayout?.invoke(KeyboardLayout.Type.SYMBOL)
            "Num" -> toggleNumberPanel(statusImage)
            else -> onKeyPress?.invoke(key)
        }
    }

    private fun editKey(rowIndex: Int, entry: CommandEntry?) {
        val rows = KeyboardStore.readCustomRows(context).map { it.toMutableList() }.toMutableList()
        while (rows.size < rowIndex) rows.add(mutableListOf())
        val list = rows[rowIndex - 1]
        val index = entry?.let { list.indexOf(it) } ?: -1
        CommandUi.editDialog(
            context = context,
            immersive = immersiveProvider?.invoke() ?: false,
            entry = entry,
            onSave = { newEntry ->
                if (index >= 0) list[index] = newEntry else list.add(newEntry)
                KeyboardStore.writeCustomRows(context, rows.map { it.toList() })
                loadLayout()
            },
            onDelete = if (index >= 0) {
                {
                    list.removeAt(index)
                    KeyboardStore.writeCustomRows(context, rows.map { it.toList() })
                    loadLayout()
                }
            } else null
        )
    }

    private fun CommandEntry.asLayoutKey(): KeyboardLayout.Key =
        KeyboardLayout.Key(0, 0).apply {
            label = this@asLayoutKey.label
            command = this@asLayoutKey.command
        }
}
