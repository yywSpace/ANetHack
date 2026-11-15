package com.yywspace.anethack.gamedata

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.R
import com.yywspace.anethack.databinding.DialogPriceIdentifyBinding
import com.yywspace.anethack.entity.NHMenuItem
import com.yywspace.anethack.extensions.show

class NHPriceIDialog(val context: Context, val nh: NetHack) {

    private lateinit var typeAdapter: ArrayAdapter<String>
    private lateinit var surchargeAdapter: ArrayAdapter<String>
    private lateinit var idModeAdapter: ArrayAdapter<String>

    private var pending: PricePrefill? = null

    private val objListAdapter = NHPriceObjListAdapter()
    private lateinit var binding: DialogPriceIdentifyBinding
    private val priceID = NHPriceID(context)
    private var isShowing = false

    init {
        initAdapter()
        initView()
    }

    private fun initAdapter() {
        val types = priceID.getObjTypes()
        typeAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            types
        )
        surchargeAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            Surcharge.entries.map { context.getString(it.labelRes) }
        )
        idModeAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            PriceMode.entries.map { context.getString(it.labelRes) }
        )
        objListAdapter.onItemClick = { item ->
            nh.itemDialog?.showItem(item.name, item.itemClass)
        }
    }

    private fun initView() {
        val view = View.inflate(context, R.layout.dialog_price_identify, null)
        binding = DialogPriceIdentifyBinding.bind(view)

        binding.objTypeInput.setAdapter(typeAdapter)
        binding.objSurchargeInput.setAdapter(surchargeAdapter)
        binding.objIdModInput.setAdapter(idModeAdapter)

        binding.objTypeInput.setText(typeAdapter.getItem(0), false)
        binding.objSurchargeInput.setText(context.getString(Surcharge.NONE.labelRes), false)
        binding.objIdModInput.setText(context.getString(PriceMode.BASE.labelRes), false)

        binding.priceSubmitBtn.setOnClickListener {
            hideSoftKeyboard()
            binding.objFilterPanel.clearFocus()
            queryObjList()
        }
        binding.objList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = objListAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun surchargeOf(value: String?): Surcharge? =
        Surcharge.entries.firstOrNull {
            context.getString(it.labelRes).equals(value, ignoreCase = true)
        }

    /** 兼容价格段的本地化文字、界面标签和内部解析键。 */
    private fun modeOf(value: String?): PriceMode? =
        PriceMode.entries.firstOrNull {
            it.quoteKey == value ||
                it.quoteRes?.let { res ->
                    context.getString(res).equals(value, ignoreCase = true)
                } == true ||
                context.getString(it.labelRes).equals(value, ignoreCase = true)
        }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(context, InputMethodManager::class.java) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun queryObjList() {
        val price = binding.objPriceInput.text?.toString().orEmpty()
        val query = PriceQuery(
            type = binding.objTypeInput.text?.toString().orEmpty(),
            mode = modeOf(binding.objIdModInput.text?.toString()) ?: PriceMode.BASE,
            surcharge = surchargeOf(binding.objSurchargeInput.text?.toString()) ?: Surcharge.NONE,
            price = price,
            charisma = binding.roleCharismaInput.text?.toString()?.toIntOrNull() ?: 0
        )
        objListAdapter.submitList(priceID.search(query))
    }

    /** 从菜单价格段打开：价格和方向来自 quote，类型来自菜单标题。 */
    fun showFromQuote(menuItem: NHMenuItem, quote: String) {
        if (!nh.prefs.priceId)
            return
        val parts = quote.split(" ").filter { it.isNotEmpty() }
        pending = PricePrefill(
            type = priceID.parseObjType(menuItem.title.value),
            mode = modeOf(parts.firstOrNull()),
            price = parts.getOrNull(1)
        )
        showPriceIDialog()
    }

    fun showFromMessages() {
        val messageList = nh.messages.getRecentMessageList(5)
        for (message in messageList) {
            val info = priceID.tradeInfoOf(message.toString()) ?: continue
            pending = PricePrefill(
                type = info.itemType,
                mode = modeOf(info.mode),
                price = info.price
            )
            break
        }
        showPriceIDialog()
    }

    private fun showPriceIDialog() {
        if (isShowing)
            return
        isShowing = true

        pending?.let { prefill ->
            prefill.type?.takeIf { it.isNotBlank() }?.let {
                binding.objTypeInput.setText(it, false)
            }
            prefill.mode?.let {
                binding.objIdModInput.setText(context.getString(it.labelRes), false)
            }
            prefill.price?.let {
                binding.objPriceInput.setText(it)
            }
        }
        pending = null

        binding.root.apply {
            if (parent != null)
                (parent as ViewGroup).removeView(this)
        }

        binding.roleCharismaInput.setText(nh.status.charisma.realVal)

        val dialog = AlertDialog.Builder(context).apply {
            setView(binding.root)
            setTitle(R.string.pride_id_title)
            setPositiveButton(R.string.dialog_confirm) { _, _ ->
                objListAdapter.submitList(emptyList())
            }
            setOnDismissListener {
                isShowing = false
            }
        }.create()
        queryObjList()
        dialog.show(nh.prefs.immersiveMode)
    }
}
