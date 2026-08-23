package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.command.NHMenuCommand
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.extensions.show


class NHWMenu(wid: Int, type:NHWindowType, private val nh: NetHack) : NHWindow(wid, type) {
    var title: String = ""
    var behavior: Long = -1
    val nhMenuItems = ArrayList<NHMenuItem>()
    var selectMode: SelectMode = SelectMode.PickNone
    private var numPrefix = -1
    private var menuAdapter:NHWMenuAdapter? = null
    private var menuList:RecyclerView? = null
    private val textList = mutableListOf<NHString>()
    private var selectedAll = false


    fun startMenu(behavior: Long) {
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
        // 如果指令序列中有能命中的则直接选择不显示弹窗
        nh.command.findAnyCommand<NHKeyCommand>()?.apply {
            val selectList = mutableListOf<Long>()
            nhMenuItems.filter {
                item -> (item.accelerator == key) and !item.isHeader() and !item.isHint()
            }.forEach { item ->
                selectList.add(item.identifier)
                selectList.add(item.selectedCount)
            }
            // 清空列表
            textList.clear()
            nhMenuItems.clear()
            if (selectList.isNotEmpty())
                return selectList.toLongArray()
            else // 若未命中列表，后续指令清理，等待用户选择，防止出现意外情况
                nh.command.clear()
        }
        selectMode = SelectMode.fromInt(how)
        showMenu()
        var menuCommand = nh.command.waitForAnyCommand<NHMenuCommand> { other ->
            processMenuOperate(other)
        }
        // 只接受属于本菜单(wid)的命令。上一个菜单的异步残留命令（dismissMenu
        // 回调里发送）即使 identifier 与本菜单条目巧合相同（如连续两个动作菜单
        // 都用小整数动作码），也会因为 wid 不匹配被丢弃。
        while (menuCommand.wid != wid) {
            menuCommand = nh.command.waitForAnyCommand<NHMenuCommand> { other ->
                processMenuOperate(other)
            }
        }
        Log.d("selectMenu", menuCommand.selectedItems.toString())
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
            // 若视图还在缓存的对话框里，先摘除再放进面板
            (menuView.parent as? ViewGroup)?.removeView(menuView)
            dialogViewAttached = false
            binding.dialogContainer.addView(menuView, params)
            binding.dialogContainer.visibility = View.VISIBLE
        }
    }

    private fun showDialogMenu() {
        nh.runOnUi { _, context ->
            ensureCachedDialog(context)
            menuDialog?.show(nh.prefs.immersiveMode)
        }
    }

    /** 确保缓存的对话框存在且视图已挂载；首次或视图被面板摘走时（重）创建 */
    private fun ensureCachedDialog(context: Context) {
        val menuView = initMenuView(context)
        if (menuDialog == null || !dialogViewAttached) {
            menuDialog = AlertDialog.Builder(context).apply {
                setView(menuView)
                setCancelable(false)
            }.create()
            dialogViewAttached = true
        }
    }

    private fun dismissMenu(onDismiss:(()->Unit) ?= null) {
        // 同时关闭，防止打开窗口后切换窗口模式导致旧窗口无法关闭
        nh.runOnUi { binding, _ ->
            // Dialog
            menuDialog?.dismiss()
            // Operation
            if(binding.dialogContainer.isNotEmpty()) {
                binding.panelContainer.apply {
                    isFocusable = false
                    isClickable = false
                }
                binding.dialogContainer.removeAllViews()
                binding.dialogContainer.visibility = View.INVISIBLE
            }
            textList.clear()
            nhMenuItems.clear()
            onDismiss?.invoke()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initMenuView(context: Context): View {
        // 复用静态缓存的弹窗视图（跨窗口实例）；第一次创建，之后只刷新数据
        val menuView = cachedMenuView ?: createMenuView(context)
        if (menuAdapter == null) {
            // 视图可能是其他窗口实例创建的，本实例首次使用补挂自己的 adapter（数据/回调绑定本实例）
            menuAdapter = NHWMenuAdapter(this, nh.tileSet).apply {
                onItemClick = { _, _, item ->
                    if (selectMode == SelectMode.PickOne) {
                        dismissMenu {
                            nh.command.sendCommand(NHMenuCommand(item.accelerator, mutableListOf(item.identifier, item.selectedCount), wid))
                        }
                    }
                }
                onItemLongClick = { _, position, item ->
                    showAmountPickerDialog(context, item, position, this)
                }
            }
        }
        menuList = menuView.findViewById<RecyclerView>(R.id.menu_item_list)
        // 静态视图被多个窗口实例共享，每次打开都确保当前实例的 adapter 挂载
        // （防止残留其他实例已清空数据的 adapter，导致列表空白）
        if (menuList?.adapter !== menuAdapter) {
            menuList?.adapter = menuAdapter
        }

        menuAdapter?.notifyDataSetChanged()
        menuList?.scrollToPosition(0)
        menuView.findViewById<TextView>(R.id.menu_title)?.apply {
            if (title.isEmpty())
                visibility = View.GONE
            else {
                visibility = View.VISIBLE
                text = title
            }
        }
        configureButtons(menuView)
        return menuView
    }

    /** 首次创建弹窗视图（布局+RecyclerView 配置；adapter 由 initMenuView 首次使用时挂载） */
    private fun createMenuView(context: Context): View {
        val menuView = createCachedMenuView(context)
        cachedMenuView = menuView
        return menuView
    }

    /** 创建弹窗视图（含 RecyclerView 配置），供首次打开使用 */
    private fun createCachedMenuView(context: Context): View {
        return View.inflate(context, R.layout.dialog_menu, null).apply {
            findViewById<RecyclerView>(R.id.menu_item_list)?.layoutManager =
                object : LinearLayoutManager(context) {
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
    }

    /** 每次打开菜单时按 selectMode 配置底部按钮（可见性/文案/行为） */
    @SuppressLint("NotifyDataSetChanged")
    private fun configureButtons(menuView: View) {
        selectedAll = false
        menuView.findViewById<MaterialButton>(R.id.menu_btn_1)?.apply {
            visibility = View.VISIBLE
            setText(R.string.dialog_cancel)
            setOnClickListener {
                // 27:Key ESC
                dismissMenu {
                    nh.command.sendCommand(NHMenuCommand(27.toChar(), mutableListOf(-1), wid))
                }
            }
        }
        when (selectMode) {
            SelectMode.PickMany -> {
                menuView.findViewById<MaterialButton>(R.id.menu_btn_2)?.apply {
                    visibility = View.VISIBLE
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
                menuView.findViewById<MaterialButton>(R.id.menu_btn_3)?.apply {
                    visibility = View.VISIBLE
                    setText(R.string.dialog_confirm)
                    setOnClickListener {
                        val count = nhMenuItems.count { item -> item.isSelected }
                        if (count == 0)
                            return@setOnClickListener
                        val selectList = mutableListOf<Long>()
                        nhMenuItems.filter { item -> item.isSelected and !item.isHeader() and !item.isHint()
                        }.forEach { item ->
                            selectList.add(item.identifier)
                            selectList.add(item.selectedCount)
                        }
                        // 13:Key Enter
                        dismissMenu {
                            nh.command.sendCommand(NHMenuCommand(13.toChar(), selectList, wid))
                        }
                    }
                }
            }
            SelectMode.PickOne -> {
                menuView.findViewById<MaterialButton>(R.id.menu_btn_2)?.visibility = View.INVISIBLE
                menuView.findViewById<MaterialButton>(R.id.menu_btn_3)?.visibility = View.INVISIBLE
            }
            else -> {
                menuView.findViewById<MaterialButton>(R.id.menu_btn_1)?.visibility = View.INVISIBLE
                menuView.findViewById<MaterialButton>(R.id.menu_btn_2)?.visibility = View.INVISIBLE
                menuView.findViewById<MaterialButton>(R.id.menu_btn_3)?.apply {
                    visibility = View.VISIBLE
                    setText(R.string.dialog_confirm)
                    setOnClickListener {
                        // 27:Key ESC
                        dismissMenu {
                            nh.command.sendCommand(NHMenuCommand(27.toChar(), mutableListOf(-1), wid))
                        }
                    }
                }
            }
        }
    }
    private fun processMenuOperate(operate:NHCommand) {
        when {
            // ESC
            operate.key.code == 27 -> {
                numPrefix = -1
                dismissMenu {
                    nh.command.sendCommand(NHMenuCommand(operate.key, mutableListOf(-1), wid))
                }
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
                        dismissMenu {
                            nh.command.sendCommand(NHMenuCommand(operate.key, selectList, wid))
                        }
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
                        // 异步回调可能晚于 dismissMenu 执行，此时列表已清空、
                        // indexOf 为 -1，直接滚动会抛 Invalid target position
                        val idx = nhMenuItems.indexOf(this)
                        if (idx >= 0)
                            menuList?.smoothScrollToPosition(idx)
                        if (selectMode == SelectMode.PickOne) {
                            dismissMenu {
                                nh.command.sendCommand(NHMenuCommand(operate.key, mutableListOf(identifier, numPrefix.toLong()), wid))
                            }
                        } else if (selectMode == SelectMode.PickMany) {
                            selectedCount = if (numPrefix != -1) numPrefix.toLong() else selectedCount
                            isSelected = !isSelected
                            if (idx >= 0)
                                menuAdapter?.notifyItemChanged(idx)
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
        dialog.show(nh.prefs.immersiveMode)
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
                        // movementMethod = ScrollingMovementMethod.getInstance()
                        text = NHWText.buildContent(textList.map { it.toString() })
                    }
                }
            val dialog = AlertDialog.Builder(context).apply {
                setView(dialogTextView)
                setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    if (blocking) {
                        nh.command.sendCommand(NHKeyCommand(27.toChar()))
                    }
                }
            }.create()
            dialog.setCancelable(false)
            dialog.show(nh.prefs.immersiveMode)
        }
    }

    override fun clearWindow(isRogueLevel: Int) {
    }

    override fun destroyWindow() {
    }

    override fun putString(attr: Int, msg: String, color: Int) {
        textList.add(NHString(msg, attr))
    }

    companion object {
        // 静态缓存：窗口实例会被 createWindow 反复重建，缓存放静态才能跨实例复用
        // 安全：单 Activity + manifest 声明 configChanges（Activity 不重建），
        // 且 NetHackActivity.onDestroy 会 clearCache()，缓存随进程结束释放，无泄漏
        @SuppressLint("StaticFieldLeak")
        private var cachedMenuView: View? = null
        @SuppressLint("StaticFieldLeak")
        private var menuDialog: AlertDialog? = null
        private var dialogViewAttached = false

        /** 清空静态缓存（Activity 重建等场景兜底） */
        fun clearCache() {
            cachedMenuView = null
            menuDialog = null
            dialogViewAttached = false
        }
    }
}