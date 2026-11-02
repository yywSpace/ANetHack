package com.yywspace.anethack.setting

import android.content.ClipData
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yywspace.anethack.R
import com.yywspace.anethack.databinding.ActivityCommandEditorBinding
import com.yywspace.anethack.keybord.CommandEntry
import com.yywspace.anethack.keybord.CommandRow
import com.yywspace.anethack.keybord.CommandStore
import com.yywspace.anethack.keybord.KeyboardStore
import com.yywspace.anethack.keybord.CommandUi
import com.yywspace.anethack.theme.AppTheme

/**
 * 命令面板编辑页：所见即所得。
 *
 * 页面里直接摆放实际面板用的按钮布局，编辑时看到的就是游戏里的效果：
 * - 上方是扩展面板，组标题带启用开关和删除，可多行；
 * - 下方是底部命令栏，只有一行。
 *
 * 交互：
 * - 点击按钮：编辑它的标签与命令；
 * - 长按按钮：开始拖拽，拖到别的按钮上即插入到该位置（可跨行）；
 * - 行尾的 "+"：在本行末尾新增一个按钮；
 * - 右下角 "Add group"：给扩展面板加一行。
 *
 * 保存时分别写回 [CommandStore] 的两个文件（命令栏、扩展面板）。
 */
class CommandEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommandEditorBinding

    private val bar = mutableListOf<CommandEntry>()
    private val panel = mutableListOf<CommandRow>()

    /** 自定义键盘的中间三行 */
    private val keyboardRows = mutableListOf<MutableList<CommandEntry>>()

    /** 自定义键盘只编辑中间三行；第 0/4 行原样保留 */
    private val keyboardRowCount = 3


    /** 导出到用户选的文件 */
    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) exportTo(uri) }

    /** 从用户选的文件导入 */
    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importFrom(uri) }

    /** 正在拖拽的条目 */
    private var dragging: CommandEntry? = null
    private var dragSource: MutableList<CommandEntry>? = null
    private var dragIndex: Int = -1

    /** 进入页面时的快照，用来判断有没有改动 */
    private var initialBar: List<CommandEntry> = emptyList()
    private var initialPanel: List<Pair<Boolean, List<CommandEntry>>> = emptyList()
    private var initialKeyboard: List<List<CommandEntry>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCommandEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initStatusBar()

        bar.addAll(CommandStore.readBar(this))
        panel.addAll(CommandStore.readExtra(this))
        if (panel.isEmpty()) panel.add(CommandRow())
        KeyboardStore.readCustomRows(this).forEach { keyboardRows.add(it.toMutableList()) }
        while (keyboardRows.size < keyboardRowCount) keyboardRows.add(mutableListOf())
        initialBar = bar.toList()
        initialPanel = panel.map { it.enabled to it.commands.toList() }
        initialKeyboard = keyboardRows.map { it.toList() }

        setSupportActionBar(binding.editorToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.command_editor_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> confirmExit()
            R.id.command_editor_save -> {
                writeFiles()
                toast(R.string.command_editor_saved)
            }
            R.id.command_editor_import -> openDocument.launch(
                arrayOf("application/json", "text/plain", "*/*")
            )
            R.id.command_editor_export -> createDocument.launch("anh_command_panels.json")
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun toast(text: CharSequence) =
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** 导出：先落盘，再把底部命令栏、扩展面板、自定义键盘三份配置写成一段 JSON */
    private fun exportTo(uri: android.net.Uri) {
        writeFiles()
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(
                    CommandStore.exportJson(
                        bar = bar,
                        extra = panel.filter { it.commands.isNotEmpty() },
                        keyboard = keyboardRows.map { it.toList() }
                    ).toByteArray()
                )
            }
        }.onSuccess { toast(R.string.command_editor_exported) }
            .onFailure { toast(R.string.command_editor_export_failed) }
    }

    /** 导入：用文件里的命令栏、扩展面板、自定义键盘配置替换当前编辑内容 */
    private fun importFrom(uri: android.net.Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val config = text?.let { CommandStore.importJson(it) }
        if (config == null) {
            toast(R.string.command_editor_import_failed)
            return
        }
        val importedBar = config.bar.orEmpty()
        val importedExtra = config.extra.orEmpty()
        val importedKeyboard = config.keyboard.orEmpty()
        bar.clear()
        bar.addAll(importedBar)
        panel.clear()
        panel.addAll(importedExtra.ifEmpty { listOf(CommandRow()) })
        keyboardRows.clear()
        importedKeyboard.forEach { keyboardRows.add(it.toMutableList()) }
        while (keyboardRows.size < keyboardRowCount) keyboardRows.add(mutableListOf())
        render()
        toast(
            getString(
                R.string.command_editor_imported,
                importedBar.size,
                importedExtra.size,
                keyboardRows.size
            )
        )
    }

    /** 有未保存的改动时，返回前先问一句 */
    private fun confirmExit() {
        val changed = bar.toList() != initialBar ||
                panel.map { it.enabled to it.commands.toList() } != initialPanel ||
                keyboardRows.map { it.toList() } != initialKeyboard
        if (!changed) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> save() }
            .setNegativeButton(R.string.unsaved_changes_discard) { _, _ -> finish() }
            .setNeutralButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun initStatusBar() = AppTheme.applyStatusBar(this)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun render() {
        val list = binding.editorList
        list.removeAllViews()

        // 自定义键盘（最上面）
        addSectionTitle(getString(R.string.command_editor_keyboard))
        for (index in 0 until keyboardRowCount) {
            list.addView(keyboardRow(keyboardRows[index]))
        }

        // 扩展面板
        addSectionTitle(getString(R.string.command_editor_extra))
        panel.forEachIndexed { index, row ->
            list.addView(groupHeader(index, row))
            list.addView(commandRow(row.commands, isBar = false))
        }
        list.addView(addRowButton())

        // 底部命令栏（最下面）
        addSectionTitle(getString(R.string.command_editor_bar))
        list.addView(commandRow(bar, isBar = true))
    }

    private fun keyboardRow(row: MutableList<CommandEntry>): View {
        val scroller = CommandUi.scrolledRow(
            context = this,
            entries = row,
            bar = false,
            onClick = { _, index -> editKeyboardKey(row, index) },
            onLongClick = { _, index, chip ->
                startDrag(row, index, chip)
                true
            },
            onAdd = { editKeyboardKey(row, null) }
        )
        val line = scroller.getChildAt(0) as LinearLayout
        line.children.forEachIndexed { index, chip ->
            chip.setOnDragListener { v, event -> handleDrop(v, event, row, index) }
        }
        // 行与行之间留出和键盘按键一样的间距
        scroller.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.command_item_spacing)
        }
        return scroller
    }

    private fun editKeyboardKey(row: MutableList<CommandEntry>, index: Int?) {
        val pos = index ?: 0
        val editing = index != null
        // 和命令栏/面板共用同一个编辑弹窗与删除逻辑
        CommandUi.editDialog(
            context = this,
            immersive = false,
            entry = if (editing) row[pos] else null,
            onSave = {
                if (editing) row[pos] = it else row.add(it)
                render()
            },
            onDelete = if (editing) ({ row.removeAt(pos); render() }) else null
        )
    }

    private fun addSectionTitle(title: String) {
        binding.editorList.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.app_text))
            setPadding(0, dp(16), 0, dp(6))
        })
    }

    /** 一组的标题：Group N + 启用开关 + 删除该组 */
    private fun groupHeader(index: Int, row: CommandRow): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_editor_group_header, binding.editorList, false)
        view.findViewById<TextView>(R.id.group_title).text =
            getString(R.string.command_editor_group, index + 1)
        view.findViewById<SwitchMaterial>(R.id.group_enabled).apply {
            isChecked = row.enabled
            contentDescription = getString(R.string.command_editor_enabled)
            setOnCheckedChangeListener { _, checked ->
                panel[index] = panel[index].copy(enabled = checked)
            }
        }
        view.findViewById<TextView>(R.id.group_delete).setOnClickListener {
            confirmDeleteRow(index)
        }
        return view
    }

    /** 一行按钮：复用实际面板的按钮布局，末尾再加一个 "+" */
    private fun commandRow(entries: MutableList<CommandEntry>, isBar: Boolean): View {
        val scroller = CommandUi.scrolledRow(
            context = this,
            entries = entries,
            bar = isBar,
            onClick = { _, index -> editEntry(entries, index) },
            onLongClick = { _, index, chip ->
                startDrag(entries, index, chip)
                true
            },
            onAdd = { editEntry(entries, null) }
        )
        // 拖动监听只给编辑页用：包括行尾的 "+"（下标正好是 entries.size）
        val line = scroller.getChildAt(0) as LinearLayout
        line.children.forEachIndexed { index, chip ->
            chip.setOnDragListener { v, event -> handleDrop(v, event, entries, index) }
        }
        if (isBar) {
            scroller.background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(getColor(R.color.bg_extend_search))
            }
            scroller.setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        return scroller
    }

    private fun addRowButton(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
            addView(TextView(context).apply {
                text = getString(R.string.command_editor_add_group)
                setTextColor(getColor(R.color.app_text))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    panel.add(CommandRow())
                    render()
                }
            })
        }

    /** 删除整行前先确认，并把这一行的信息显示出来 */
    private fun confirmDeleteRow(index: Int) {
        if (panel.size <= 1) return
        val row = panel[index]
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.command_editor_delete_row, index + 1))
            .setMessage(getString(R.string.command_editor_delete_row_msg, row.commands.size))
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                panel.removeAt(index)
                render()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** [index] 为 null 表示新增 */
    private fun editEntry(entries: MutableList<CommandEntry>, index: Int?) {
        val pos = index ?: 0
        val editing = index != null
        CommandUi.editDialog(
            context = this,
            immersive = false,
            entry = if (editing) entries[pos] else null,
            onSave = {
                if (editing) entries[pos] = it else entries.add(it)
                render()
            },
            onDelete = if (editing) ({ entries.removeAt(pos); render() }) else null
        )
    }

    private fun startDrag(entries: MutableList<CommandEntry>, index: Int, chip: TextView) {
        dragging = entries.getOrNull(index)
        dragSource = entries
        dragIndex = index
        val data = ClipData.newPlainText("command", chip.text)
        // 拖拽时用带底色的格子样式做阴影，并整体抬到手指上方，避免被手指挡住
        val holder = FrameLayout(this)
        val shadowChip = CommandUi.chip(this, holder, bar = false)
        shadowChip.text = chip.text
        holder.addView(shadowChip)
        holder.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        holder.layout(0, 0, holder.measuredWidth, holder.measuredHeight)
        chip.startDragAndDrop(data, LiftShadowBuilder(holder, dp(28)), null, 0)
    }

    /** 让拖拽阴影整体上移 [lift] 像素 */
    private class LiftShadowBuilder(view: View, private val lift: Int) :
        View.DragShadowBuilder(view) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint)
            outShadowTouchPoint.y = outShadowSize.y + lift
        }
    }

    /** 拖到 [target] 上时，把正在拖的条目移动到该位置 */
    private fun handleDrop(
        target: View,
        event: DragEvent,
        entries: MutableList<CommandEntry>,
        index: Int
    ): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> target.alpha = 0.5f
            DragEvent.ACTION_DRAG_EXITED -> target.alpha = 1f
            DragEvent.ACTION_DRAG_ENDED -> {
                target.alpha = 1f
                dragging = null
                dragSource = null
            }
            DragEvent.ACTION_DROP -> {
                target.alpha = 1f
                val moving = dragging
                val source = dragSource
                if (moving != null && source != null) {
                    // remove only from the list the item was dragged out of
                    val from = source.indexOfFirst { it === moving }
                    if (from >= 0) source.removeAt(from)
                    val to = if (source === entries && from in 0 until index) index - 1 else index
                    entries.add(to.coerceIn(0, entries.size), moving)
                    dragging = null
                    dragSource = null
                    dragIndex = -1
                    render()
                }
            }
        }
        return true
    }

    private fun save() {
        writeFiles()
        setResult(RESULT_OK)
        finish()
    }

    /** 只写文件、不关闭页面；写完后同步快照 */
    private fun writeFiles() {
        CommandStore.writeBar(this, bar)
        CommandStore.writeExtra(this, panel.filter { it.commands.isNotEmpty() })
        KeyboardStore.writeCustomRows(
            this,
            (0 until keyboardRowCount).map { index -> keyboardRows[index].toList() }
        )
        initialBar = bar.toList()
        initialPanel = panel.map { it.enabled to it.commands.toList() }
        initialKeyboard = keyboardRows.map { it.toList() }
    }
}
