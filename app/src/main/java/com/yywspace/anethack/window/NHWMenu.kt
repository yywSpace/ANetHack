package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout.*
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHMenuCommand
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.extensions.showImmersive

class NHWMenu(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    var title: String = ""
    var behavior: Long = -1
    val nhMenuItems = mutableListOf<NHMenuItem>()
    var selectMode: SelectMode = SelectMode.PickNone
    private var numPrefix = -1
    private var menuAdapter:NHWMenuAdapter? = null
    private var menuList:RecyclerView? = null
    private var menuDialog:AlertDialog? = null
    private val textList = mutableListOf<NHString>()
    private var selectedAll = false


    fun startMenu(behavior: Long) {
        clearWindow(0)
        this.behavior = behavior
    }

    fun addMenu(
        glyph: Int,
        identifier: Long,
        accelerator: Char,
        groupAcc: Char,
        attr: Int,
        clr: Int,
        text: String,
        preselected: Boolean
    ) {
        if (text.isEmpty())
            return
        nhMenuItems.add(
            NHMenuItem(
                glyph, identifier,
                accelerator, groupAcc, NHString(text, attr, if (clr == 8) 0 else clr), preselected
            )
        )
    }

    fun endMenu(prompt: String?) {
        title = prompt ?: ""
    }

    fun selectMenu(how: Int): LongArray {
        selectMode = SelectMode.fromInt(how)
        showMenu()
        val menuCommand = nh.command.waitForAnyCommand<NHMenuCommand> { other ->
            processMenuOperate(other)
        }
        return menuCommand.selectedItems.toLongArray()
    }

    private fun showMenu() {
        if(nh.prefs.menuType == "1") // dialog
            showDialogMenu()
        else // operation
            showOperateMenu()
    }

    private fun showOperateMenu() {
        nh.runOnUi { binding, context ->
            val menuView = initMenuView(context)
            binding.panelContainer.apply {
                isFocusable = true
                isClickable = true
            }
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            binding.dialogContainer.addView(menuView, params)
            binding.dialogContainer.visibility = View.VISIBLE
        }
    }

    private fun showDialogMenu() {
        nh.runOnUi { _, context ->
            val menuView = initMenuView(context)
            menuDialog = AlertDialog.Builder(context).run {
                setView(menuView)
                setCancelable(false)
                create()
                showImmersive()
            }
        }
    }

    private fun dismissMenu() {
        // 同时关闭，防止打开窗口后切换窗口模式导致旧窗口无法关闭
        nh.runOnUi { binding, _ ->
            // Dialog
            menuDialog?.dismiss()
            // Operation
            if(binding.dialogContainer.childCount > 0) {
                binding.panelContainer.apply {
                    isFocusable = false
                    isClickable = false
                }
                binding.dialogContainer.removeAllViews()
                binding.dialogContainer.visibility = View.INVISIBLE
            }

        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initMenuView(context: Context):View {
        menuAdapter = NHWMenuAdapter(this@NHWMenu, nh.tileSet).apply {
            onItemClick = { _, _, item ->
                if (selectMode == SelectMode.PickOne) {
                    nh.command.sendCommand(NHMenuCommand(item.accelerator, mutableListOf(item.identifier, item.selectedCount)))
                    dismissMenu()
                }
            }
            onItemLongClick = { _, position, item ->
                showAmountPickerDialog(context, item, position, this)
            }
        }
        // set the custom layout
        val menuView = View.inflate(context, R.layout.dialog_menu, null)
            .apply {
                findViewById<TextView>(R.id.menu_title)?.apply {
                    if (title.isEmpty())
                        visibility = View.GONE
                    else
                        text = title
                }
                menuList = findViewById<RecyclerView>(R.id.menu_item_list)?.apply {
                    adapter = menuAdapter
                    layoutManager = object :LinearLayoutManager(context) {
                        override fun onLayoutChildren(
                            recycler: RecyclerView.Recycler?,
                            state: RecyclerView.State?
                        ) {
                            try {
                                super.onLayoutChildren(recycler, state)
                            } catch (e: IndexOutOfBoundsException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                findViewById<MaterialButton>(R.id.menu_btn_1)?.apply {
                    setText(R.string.dialog_cancel)
                    setOnClickListener {
                        // 27:Key ESC
                        nh.command.sendCommand(NHMenuCommand(27.toChar(), mutableListOf(-1)))
                        dismissMenu()
                    }
                }
                if (selectMode == SelectMode.PickMany) {
                    findViewById<MaterialButton>(R.id.menu_btn_2)?.apply {
                        setText(R.string.dialog_select_all)
                        setOnClickListener {
                            if (!selectedAll) {
                                setText(R.string.dialog_clear_all)
                                nhMenuItems.forEach {
                                    if (!it.isHeader())
                                        it.isSelected = true
                                }
                            } else {
                                setText(R.string.dialog_select_all)
                                nhMenuItems.forEach {
                                    it.isSelected = false
                                }
                            }
                            menuAdapter?.notifyDataSetChanged()
                            selectedAll = !selectedAll
                        }
                    }
                    findViewById<MaterialButton>(R.id.menu_btn_3)?.apply {
                        setText(R.string.dialog_confirm)
                        setOnClickListener {
                            val count = nhMenuItems.count { item -> item.isSelected }
                            if (count == 0)
                                return@setOnClickListener
                            val selectList = mutableListOf<Long>()
                            nhMenuItems.filter { item -> item.isSelected }.forEach { item ->
                                selectList.add(item.identifier)
                                selectList.add(item.selectedCount)
                            }
                            // 13:Key Enter
                            nh.command.sendCommand(NHMenuCommand(13.toChar(), selectList))
                            dismissMenu()
                        }
                    }
                } else {
                    findViewById<MaterialButton>(R.id.menu_btn_2)?.visibility = View.INVISIBLE
                    findViewById<MaterialButton>(R.id.menu_btn_3)?.visibility = View.INVISIBLE
                }
            }
        return menuView
    }
    private fun processMenuOperate(operate:NHCommand) {
        when {
            // ESC
            operate.key.code == 27 -> {
                numPrefix = -1
                nh.command.sendCommand(NHMenuCommand(operate.key, mutableListOf(-1)))
                dismissMenu()
            }
            // ENTER
            operate.key.code == 13 -> {
                if (selectMode == SelectMode.PickMany) {
                    val count = nhMenuItems.count { item -> item.isSelected }
                    if (count != 0) {
                        val selectList = mutableListOf<Long>()
                        nhMenuItems.filter { item -> item.isSelected }.forEach { item ->
                            selectList.add(item.identifier)
                            selectList.add(item.selectedCount)
                        }
                        // 13:Key Enter
                        nh.command.sendCommand(NHMenuCommand(operate.key, selectList))
                        dismissMenu()
                    }
                }
            }
            // 翻页
            operate.key == '>' -> {
                nh.runOnUi { _, _ ->
                    menuList?.apply {
                        val lastPosition = (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                        smoothScrollToPosition(lastPosition+childCount)
                    }
                }
            }
            // 翻页
            operate.key == '<' -> {
                nh.runOnUi { _, _ ->
                    menuList?.apply {
                        val firstPosition = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                        smoothScrollToPosition(firstPosition-childCount)
                    }
                }
            }
            // 数字前缀
            operate.key.isDigit() -> {
                val digit = operate.key.toString().toInt()
                numPrefix = if (numPrefix == -1) digit else numPrefix * 10 + digit
            }
            else -> {
                nhMenuItems.firstOrNull { it.accelerator == operate.key }?.apply {
                    nh.runOnUi { _, _ ->
                        menuList?.smoothScrollToPosition(nhMenuItems.indexOf(this))
                        if (selectMode == SelectMode.PickOne) {
                            dismissMenu()
                            nh.command.sendCommand(NHMenuCommand(operate.key, mutableListOf(identifier, numPrefix.toLong())))
                        } else if (selectMode == SelectMode.PickMany) {
                            selectedCount = if (numPrefix != -1) numPrefix.toLong() else selectedCount
                            isSelected = !isSelected
                            menuAdapter?.notifyItemChanged(nhMenuItems.indexOf(this))
                        }
                        numPrefix = -1
                    }
                }
            }
        }
    }

    private fun showAmountPickerDialog(
        context: Context, parentItem: NHMenuItem,
        parentPosition: Int, parentAdapter: NHWMenuAdapter
    ) {
        if (parentItem.count < 0) return
        val dialogView = View.inflate(context, R.layout.dialog_amount_selecter, null)
        val amountPicker = dialogView.findViewById<NumberPicker>(R.id.item_amount_picker).apply {
            minValue = 1
            maxValue = parentItem.count
        }
        val itemName = parentItem.title.toString().run {
            substring(indexOf(' '), length)
        }
        val title = context.resources.getString(R.string.item_amount_select, itemName)
        val dialog = AlertDialog.Builder(context).run {
            setTitle(title)
            setView(dialogView)
            setPositiveButton(R.string.dialog_confirm) { _, _ ->
                amountPicker.requestFocus()
                parentItem.selectedCount = amountPicker.value.toLong()
                parentAdapter.notifyItemChanged(parentPosition)
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ ->

            }
            create()
        }
        dialog.setCancelable(false)
        dialog.showImmersive()
    }

    enum class SelectMode {
        PickNone, PickOne, PickMany;

        companion object {
            fun fromInt(i: Int): SelectMode {
                if (i == 2) return PickMany
                return if (i == 1) PickOne else PickNone
            }
        }
    }

    override fun curs(x: Int, y: Int) {

    }

    override fun displayWindow(blocking: Boolean) {
        nh.runOnUi { _, context ->
            val dialogTextView = View.inflate(context, R.layout.dialog_text, null)
                .apply {
                    findViewById<TextView>(R.id.text_view).apply {
                        movementMethod = ScrollingMovementMethod.getInstance()
                        text = textList.joinToString("\n")
                    }
                }
            val dialog = AlertDialog.Builder(context).apply {
                setView(dialogTextView)
                setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    if (blocking) {
                        nh.command.sendCommand(NHCommand(27.toChar()))
                    }
                }
            }.create()
            dialog.setCancelable(false)
            dialog.showImmersive()
        }
    }

    override fun clearWindow(isRogueLevel: Int) {
        textList.clear()
        nhMenuItems.clear()
    }

    override fun destroyWindow() {
        textList.clear()
        nhMenuItems.clear()
    }

    override fun putString(attr: Int, msg: String, color: Int) {
        textList.add(NHString(msg, attr))
    }
}