package com.yywspace.anethack.setting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import com.yywspace.anethack.R
import com.yywspace.anethack.entity.NHColor

/**
 * 颜色列表偏好：弹出的列表项用对应的 NetHack 颜色显示，方便预览。
 */
class ColorListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {

    override fun onClick() {
        val labels = entries ?: return
        val values = entryValues ?: return
        val adapter = object : ArrayAdapter<CharSequence>(
            context,
            android.R.layout.simple_list_item_1,
            labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(android.R.id.text1)
                val color = values.getOrNull(position)
                    ?.toString()
                    ?.toIntOrNull()
                    ?.let { NHColor.fromInt(it) }
                    ?: NHColor.CLR_GREEN
                text.setTextColor(color.toColor())
                return view
            }
        }
        val checked = values.indexOfFirst { it.toString() == value }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(adapter, checked) { dialog, which ->
                val newValue = values.getOrNull(which)?.toString() ?: return@setSingleChoiceItems
                if (callChangeListener(newValue)) {
                    value = newValue
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
