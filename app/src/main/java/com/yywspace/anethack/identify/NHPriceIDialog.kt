package com.yywspace.anethack.identify

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.databinding.DialogPriceIdentifyBinding
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.extensions.show


class NHPriceIDialog (val context: Context, val nh: NetHack){
    private lateinit var typeAdapter: ArrayAdapter<String>
    private lateinit var surchargeAdapter: ArrayAdapter<String>
    private lateinit var idModeAdapter: ArrayAdapter<String>
    private lateinit var currentType: String
    private lateinit var currentSurcharge :String
    private lateinit var currentIdMode :String
    private val objList :MutableList<Map<String,String>> = mutableListOf()
    private lateinit var objListAdapter:NHPriceObjListAdapter
    private lateinit var binding: DialogPriceIdentifyBinding
    private val priceID: NHPriceID = NHPriceID(context)
    private var tradePrice = ""
    private var isShowing = false
    private var dropdown: ListPopupWindow? = null
    private var dropdownDismissTime = 0L
    private var dropdownDismissField: EditText? = null

    init {
        initAdapter()
        initView()
    }


    private fun parseTradeInfo(info:String):Boolean {
        val result = priceID.parseTradeInfo(info)
        if (result.isEmpty())
            return false
        tradePrice = result["tradePrice"]?:tradePrice
        currentType = result["objType"]?:currentType
        currentIdMode = when(result["tradeMode"]) {
            "buy"-> context.getString(R.string.price_id_mode_buy)
            "sell"-> context.getString(R.string.price_id_mode_sell)
            else -> currentIdMode
        }
        return true
    }


    private fun initAdapter() {
        typeAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            priceID.getObjTypes().toTypedArray()
        )
        val surchargeData = context.resources.getStringArray(R.array.surcharge_cond_array)
        surchargeAdapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_1, surchargeData)
        val idModeData = context.resources.getStringArray(R.array.pride_id_mode_array)
        idModeAdapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_1, idModeData)
        currentType = if (typeAdapter.isEmpty) "" else typeAdapter.getItem(0) ?: ""
        currentSurcharge = if (surchargeAdapter.isEmpty) "" else surchargeAdapter.getItem(0) ?: ""
        currentIdMode = if (idModeAdapter.isEmpty) "" else idModeAdapter.getItem(0) ?: ""
        objListAdapter = NHPriceObjListAdapter(objList).apply {
            onItemClick = { _, _, obj ->
                var objDesc = ""
                obj.keys.forEach {
                    if (it != "name" && (obj[it]?:"").trim().isNotEmpty())
                        objDesc += "${it}:${obj[it]}\n"
                }
                AlertDialog.Builder(context).run {
                    setTitle(obj["name"])
                    setMessage(objDesc)
                    setPositiveButton(R.string.dialog_confirm, null)
                    create()
                    show(nh.prefs.immersiveMode)
                }
            }
        }
    }
    private fun initView(){
        val view = View.inflate(context, R.layout.dialog_price_identify,null)
        binding = DialogPriceIdentifyBinding.bind(view)
        binding.objTypeInput.setOnClickListener {
            toggleDropDown(binding.objTypeInput, typeAdapter) { currentType = it }
        }
        binding.objTypeLayout.setEndIconOnClickListener { binding.objTypeInput.performClick() }
        binding.objSurchargeInput.setOnClickListener {
            toggleDropDown(binding.objSurchargeInput, surchargeAdapter) { currentSurcharge = it }
        }
        binding.objSurchargeLayout.setEndIconOnClickListener { binding.objSurchargeInput.performClick() }
        binding.objIdModInput.setOnClickListener {
            toggleDropDown(binding.objIdModInput, idModeAdapter) { currentIdMode = it }
        }
        binding.objIdModLayout.setEndIconOnClickListener { binding.objIdModInput.performClick() }
        // 下拉框聚焦只为触发边框高亮，不弹键盘
        binding.objTypeInput.setShowSoftInputOnFocus(false)
        binding.objSurchargeInput.setShowSoftInputOnFocus(false)
        binding.objIdModInput.setShowSoftInputOnFocus(false)
        // 获得焦点（含弹窗打开时自动聚焦）即展开下拉
        binding.objTypeInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.objTypeInput.post {
                showDropdown(binding.objTypeInput, typeAdapter) { currentType = it }
            }
        }
        binding.objSurchargeInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.objSurchargeInput.post {
                showDropdown(binding.objSurchargeInput, surchargeAdapter) { currentSurcharge = it }
            }
        }
        binding.objIdModInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.objIdModInput.post {
                showDropdown(binding.objIdModInput, idModeAdapter) { currentIdMode = it }
            }
        }
        binding.priceSubmitBtn.setOnClickListener {
            hideSoftKeyboard()
            clearFilterFocus()
            queryObjList()
        }
        binding.objList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = objListAdapter
            addItemDecoration(
                DividerItemDecoration(context,DividerItemDecoration.VERTICAL)
            )
        }
    }

    /** 点击切换：列表显示则收起，否则展开 */
    private fun toggleDropDown(
        field: EditText,
        adapter: ArrayAdapter<String>,
        onSelect: (String) -> Unit
    ) {
        if (dropdown?.isShowing == true) {
            dropdown?.dismiss()
            return
        }
        showDropdown(field, adapter, onSelect)
    }

    /** 展开下拉（已显示则忽略）：获得焦点或点击时调用 */
    private fun showDropdown(
        field: EditText,
        adapter: ArrayAdapter<String>,
        onSelect: (String) -> Unit
    ) {
        if (dropdown?.isShowing == true)
            return
        // 点击输入框会让弹出层先因外部触摸收起，同一次点击不再展开，否则会"收起又弹出"
        if (System.currentTimeMillis() - dropdownDismissTime < 300 &&
            dropdownDismissField === field
        )
            return
        // 激活边框高亮（TextInputLayout 的 focused 描边）
        field.requestFocus()
        dropdown = ListPopupWindow(context).apply {
            anchorView = field
            width = field.width
            setAdapter(adapter)
            setOnItemClickListener { _, _, pos, _ ->
                val value = adapter.getItem(pos) ?: return@setOnItemClickListener
                onSelect(value)
                field.setText(value)
                dismiss()
            }
            setOnDismissListener {
                if (dropdown === this) dropdown = null
                dropdownDismissTime = System.currentTimeMillis()
                dropdownDismissField = field
                field.clearFocus()
            }
            show()
        }
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(context, InputMethodManager::class.java) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    /** 移除过滤面板内所有输入框的焦点（下拉框聚焦时边框会高亮，点搜索后应取消） */
    private fun clearFilterFocus() {
        fun clearRecursively(view: View) {
            if (view is EditText) view.clearFocus()
            if (view is ViewGroup) {
                for (i in 0 until view.childCount)
                    clearRecursively(view.getChildAt(i))
            }
        }
        clearRecursively(binding.objFilterPanel)
    }
    @SuppressLint("NotifyDataSetChanged")
    private fun queryObjList(submit:Boolean = true) {
        objList.clear()
        val price = binding.objPriceInput.text.toString()
        val charisma = binding.roleCharismaInput.text.toString()
        val sucker = binding.objSurchargeInput.text.toString() != context.getString(R.string.price_id_surcharge_none)
        // 如果非手动查询，且价格为空则空置
        if (!submit && price.isEmpty())
            return
        when(currentIdMode) {
            context.getString(R.string.price_id_mode_base) -> {
                objList.addAll(priceID.getObjByBasePrice(currentType, price))
            }
            context.getString(R.string.price_id_mode_buy) -> {
                objList.addAll(priceID.getObjByBuyPrice(currentType, price, charisma, sucker))
            }
            context.getString(R.string.price_id_mode_sell) -> {
                objList.addAll(priceID.getObjBySellPrice(currentType, price, sucker))
            }
        }
        tradePrice = price
        objListAdapter.notifyDataSetChanged()
    }

    /** 解析价格段（如 "sell 67" / "buy 2-5"），更新交易信息 */
    private fun parseQuoteInfo(menuItem: NHMenuItem, quote: String) {
        val parts = quote.split(" ").filter { it.isNotEmpty() }
        currentIdMode = when (parts[0]) {
            "buy" -> context.getString(R.string.price_id_mode_buy)
            "sell" -> context.getString(R.string.price_id_mode_sell)
            else -> currentIdMode
        }
        currentType = priceID.parseObjType(menuItem.title.value)
        // 价格可能是范围（如 "2-5"），取最小值
        tradePrice = parts.getOrNull(1)?.split("-")?.first() ?: tradePrice
    }

    /** 从菜单点击的价格段打开弹窗并预填（不解析消息覆盖）：
     *  价格/模式来自 quote，物品类型由菜单项标题判断 */
    fun showFromQuote(menuItem: NHMenuItem, quote: String) {
        parseQuoteInfo(menuItem, quote)
        showPriceIDialog()
    }

    fun showFromMessages() {
        // 正常打开：从最近消息解析交易信息预填
        val messageList = nh.messages.getRecentMessageList(5)
        for (i in messageList.indices) {
            if (parseTradeInfo(messageList[i].toString())) break
        }
        showPriceIDialog()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showPriceIDialog() {
        if (isShowing)
            return
        isShowing = true
        binding.root.apply {
            if (parent != null) {
                (parent as ViewGroup).removeView(this)
            }
        }

        binding.roleCharismaInput.setText(nh.status.charisma.realVal)

        binding.objPriceInput.setText(tradePrice)
        binding.objSurchargeInput.setText(currentSurcharge)
        binding.objIdModInput.setText(currentIdMode)
        binding.objTypeInput.setText(currentType)

        val dialog = AlertDialog.Builder(context).apply {
            setView(binding.root)
            setTitle(R.string.pride_id_title)
            setPositiveButton(R.string.dialog_confirm) { _, _ ->
                objList.clear()
                objListAdapter.notifyDataSetChanged()
            }
            setOnDismissListener {
                isShowing = false
            }
        }.create()
        queryObjList(false)
        dialog.show(nh.prefs.immersiveMode)
    }
}