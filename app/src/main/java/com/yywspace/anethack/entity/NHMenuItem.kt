package com.yywspace.anethack.entity

import androidx.core.text.isDigitsOnly

data class NHMenuItem(
    val glyph: Int,
    val identifier: Long,
    val accelerator: Char,
    val groupAcc: Char,
    val title: NHString,
    val preselected: Boolean
) {
    var isSelected:Boolean = preselected
    var subtitle:String = ""
    var selectedCount :Long = -1
    var count = -1
    init {
        if(!isHeader() and !isHint()) {
            Regex("(.*)(?<!\\[)\\((.*)\\)(?!])").find(title.value)?.apply {
                if(groupValues.size >= 3 && groupValues[1].isNotEmpty()) {
                    title.value = groupValues[1]
                    subtitle = groupValues[2]
                }
            }

            val num = title.value.split(" ").first()
            count = if(num == "a" || num == "an")
                1
            else if(num.isDigitsOnly() && num.isNotEmpty())
                num.toInt()
            else
                -1
        }
    }

    fun isHeader():Boolean {
        return (accelerator.code == 0) and (identifier == 0L) and (title.attr == 128)
    }

    // 括号内用于提示上一个选项作用的Item，一般被括号括起来
    // (ignored unless some other choices are also picked)
    fun isHint():Boolean {
        if (isHeader())
            return false
        return (accelerator.code == 0) and (identifier == 0L)
    }

    fun hasSubtitle():Boolean {
        return subtitle.isNotEmpty()
    }
}
