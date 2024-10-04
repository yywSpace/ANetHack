package com.yywspace.anethack.identify

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import com.yywspace.anethack.R
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.databinding.DialogPriceIdentifyBinding
import com.yywspace.anethack.extensions.show
import com.yywspace.anethack.window.NHWindowType
import java.util.regex.Pattern


class NHPriceIDialog (val context: Context, val nh: NetHack){
    private lateinit var typeAdapter:ArrayAdapter<String>
    private lateinit var surchargeAdapter:ArrayAdapter<String>
    private lateinit var idModeAdapter:ArrayAdapter<String>
    private lateinit var currentType: String
    private lateinit var currentSurcharge :String
    private lateinit var currentIdMode :String
    private val objList :MutableList<Map<String,String>> = mutableListOf()
    private lateinit var objListAdapter:NHPriceObjListAdapter
    private lateinit var binding: DialogPriceIdentifyBinding
    private var priceID: NHPriceID = NHPriceID(context)
    private var tradePrice = ""
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
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            priceID.getObjTypes().toTypedArray()
        )
        val surchargeData = context.resources.getStringArray(R.array.surcharge_cond_array)
        surchargeAdapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_1, surchargeData)
        val idModeData = context.resources.getStringArray(R.array.pride_id_mode_array)
        idModeAdapter =
            ArrayAdapter(context, android.R.layout.simple_list_item_1, idModeData)
        currentType = if(typeAdapter.isEmpty) "" else typeAdapter.getItem(0)?:""
        currentSurcharge = if(surchargeAdapter.isEmpty) "" else surchargeAdapter.getItem(0)?:""
        currentIdMode = if(idModeAdapter.isEmpty) "" else idModeAdapter.getItem(0)?:""
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
    @SuppressLint("NotifyDataSetChanged")
    private fun initView(){
        val view = View.inflate(context, R.layout.dialog_price_identify,null)
        binding = DialogPriceIdentifyBinding.bind(view)
        binding.objTypeInput.apply {
            setAdapter(typeAdapter)
            setText(currentType)
            showSoftInputOnFocus = false
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hideSoftKeyboard()
                    if (text.isNotEmpty())
                        typeAdapter.filter.filter(null)
                    this.showDropDown()
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                currentType = typeAdapter.getItem(position) ?: ""
                setSelection(0)
            }
        }
        binding.objSurchargeInput.apply {
            setAdapter(surchargeAdapter)
            setText(currentSurcharge)
            showSoftInputOnFocus = false
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hideSoftKeyboard()
                    if (text.isNotEmpty())
                        surchargeAdapter.filter.filter(null)
                    this.showDropDown()
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                currentSurcharge = surchargeAdapter.getItem(position) ?: ""
                setSelection(0)
            }
        }
        binding.objIdModInput.apply {
            setAdapter(idModeAdapter)
            setText(currentIdMode)
            showSoftInputOnFocus = false
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hideSoftKeyboard()
                    if (text.isNotEmpty())
                        idModeAdapter.filter.filter(null)
                    this.showDropDown()
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                currentIdMode = idModeAdapter.getItem(position) ?: ""
                setSelection(0)
            }
        }
        binding.priceSubmitBtn.setOnClickListener {
            hideSoftKeyboard()
            clearFocus()
            objList.clear()
            val price = binding.objPriceInput.text.toString()
            val charisma = binding.roleCharismaInput.text.toString()
            val sucker = binding.objSurchargeInput.text.toString() != context.getString(R.string.price_id_surcharge_none)
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
        binding.objList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = objListAdapter
            addItemDecoration(
                DividerItemDecoration(context,DividerItemDecoration.VERTICAL)
            )
        }
    }

    private fun clearFocus() {
        for (i in 0 until binding.objFilterPanel.childCount) {
            val view = binding.objFilterPanel.getChildAt(i)
            view.clearFocus()
        }
    }
    private fun hideSoftKeyboard() {
        val imm = getSystemService(context, InputMethodManager::class.java) as InputMethodManager
        for (i in 0 until binding.objFilterPanel.childCount) {
            val view = binding.objFilterPanel.getChildAt(i)
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun show() {
        binding.root.apply {
            if (parent != null) {
                (parent as ViewGroup).removeView(this)
            }
        }
        if (nh.hasWindow(NHWindowType.NHW_MESSAGE)) {
            val messageList = nh.messages.getRecentMessageList(5)
            var valid: Boolean
            for (i in messageList.indices) {
                valid = parseTradeInfo(messageList[i].value.value)
                if (valid) break
            }
        }

        if (nh.hasWindow(NHWindowType.NHW_STATUS))
            binding.roleCharismaInput.setText(nh.status.charisma.realVal)

        binding.objPriceInput.setText(tradePrice)
        binding.objIdModInput.setText(currentIdMode)
        binding.objTypeInput.setText(currentType)

        AlertDialog.Builder(context).run {
            setView(binding.root)
            setTitle(R.string.pride_id_title)
            setPositiveButton(R.string.dialog_confirm)  {_, _ ->
                objList.clear()
                objListAdapter.notifyDataSetChanged()
            }
            create()
            show(nh.prefs.immersiveMode)
        }
    }
}