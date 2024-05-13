package com.yywspace.anethack.window

import android.annotation.SuppressLint
import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yywspace.anethack.entity.NHString
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.command.NHCommand
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.extensions.showImmersive
import java.util.Arrays
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NHWMenu(wid: Int, private val nh: NetHack) : NHWindow(wid) {
    var title:String = ""
    var behavior:Long = -1
    val nhMenuItems = mutableListOf<NHMenuItem>()
    var selectMode: SelectMode = SelectMode.PickNone
    private var selectedItems:MutableList<Long> = mutableListOf()
    private val textList = mutableListOf<NHString>()
    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private var selectedAll = false


    public fun startMenu(behavior:Long) {
        clearWindow(0)
        this.behavior = behavior
    }

    public fun addMenu(
        glyph: Int,
        identifier: Long,
        accelerator: Char,
        groupAcc: Char,
        attr: Int,
        clr:Int,
        text: String,
        preselected: Boolean
    ) {
        if(text.isEmpty())
            return
        nhMenuItems.add(
            NHMenuItem(glyph, identifier,
                accelerator, groupAcc, NHString(text, attr, clr), preselected)
        )
    }

    public fun endMenu(prompt: String?) {
        title = prompt ?: ""
    }

    public fun selectMenu(how: Int): LongArray {
        selectMode = SelectMode.fromInt(how)
        showMenuSelectDialog()
        lock.withLock {
            condition.await()
        }
        return selectedItems.toLongArray()
    }
    @SuppressLint("NotifyDataSetChanged")
    private fun showMenuSelectDialog() {
        nh.runOnUi() { _, context ->
            val menuAdapter = NHWMenuAdapter(this@NHWMenu)
            // set the custom layout
            val dialogMenuView = View.inflate(context, R.layout.dialog_menu, null)
                .apply {
                    findViewById<RecyclerView>(R.id.menu_item_list)?.apply {
                        adapter = menuAdapter
                        layoutManager = LinearLayoutManager(context)
                    }
                }
            val dialog = AlertDialog.Builder(context).run {
                setTitle(title)
                setView(dialogMenuView)
                setCancelable(false)
                create()
            }
            dialogMenuView.apply {
                findViewById<MaterialButton>(R.id.menu_btn_1)?.apply {
                    setText(R.string.dialog_cancel)
                    setOnClickListener {
                        selectedItems = mutableListOf(-1)
                        selectMenuFinish()
                        dialog.dismiss()
                    }
                }
                if(selectMode == SelectMode.PickMany) {
                    findViewById<MaterialButton>(R.id.menu_btn_2)?.apply {
                        setText(R.string.dialog_select_all)
                        setOnClickListener {
                            if(!selectedAll)  {
                                setText(R.string.dialog_clear_all)
                                nhMenuItems.forEach {
                                    if (!it.isHeader())
                                        it.isSelected = true
                                }
                            }else{
                                setText(R.string.dialog_select_all)
                                nhMenuItems.forEach {
                                    it.isSelected = false
                                }
                            }
                            menuAdapter.notifyDataSetChanged()
                            selectedAll = !selectedAll
                        }
                    }
                    findViewById<MaterialButton>(R.id.menu_btn_3)?.apply {
                        setText(R.string.dialog_confirm)
                        setOnClickListener {
                            val count = nhMenuItems.count { item -> item.isSelected }
                            selectedItems = if (count == 0) {
                                mutableListOf(0)
                            } else {
                                val selectList = mutableListOf<Long>()
                                nhMenuItems.filter { item -> item.isSelected }.forEach { item ->
                                    selectList.add(item.identifier)
                                    selectList.add(item.selectedCount)
                                }
                                selectList
                            }
                            if (count == 0)
                                return@setOnClickListener
                            selectMenuFinish()
                            dialog.dismiss()
                        }
                    }
                } else {
                    findViewById<MaterialButton>(R.id.menu_btn_2)?.visibility = View.INVISIBLE
                    findViewById<MaterialButton>(R.id.menu_btn_3)?.visibility = View.INVISIBLE
                }
            }

            menuAdapter.onItemClick = { _, _, item ->
                if(selectMode == SelectMode.PickOne) {
                    selectedItems =  mutableListOf(item.identifier, item.selectedCount)
                    selectMenuFinish()
                    dialog.dismiss()
                }
            }
            menuAdapter.onItemLongClick = { _, position, item ->
                showAmountPickerDialog(context, item, position, menuAdapter)
            }
            dialog.showImmersive()
            Log.d("11111111111111", "22222222222222")
        }

    }

    private fun showAmountPickerDialog(context: Context, parentItem: NHMenuItem,
                                       parentPosition:Int, parentAdapter: NHWMenuAdapter) {
        if(parentItem.count < 0) return
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
            setPositiveButton(R.string.dialog_confirm){ _, _ ->
                parentItem.selectedCount = amountPicker.value.toLong()
                parentAdapter.notifyItemChanged(parentPosition)
            }
            setNegativeButton(R.string.dialog_cancel){ _, _ ->

            }
            create()
        }
        dialog.setCancelable(false)
        dialog.showImmersive()

    }
     private fun selectMenuFinish() {
        lock.withLock {
            condition.signal()
        }
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
        selectedItems.clear()
        textList.clear()
        nhMenuItems.clear()
    }

    override fun destroyWindow() {
        textList.clear()
        nhMenuItems.clear()
        selectedItems.clear()
    }

    override fun putString(attr: Int, msg: String, color: Int) {
        textList.add(NHString(msg, attr))
    }
}