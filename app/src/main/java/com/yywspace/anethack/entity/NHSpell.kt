package com.yywspace.anethack.entity

/**
 * 法术菜单的一行。
 *
 * 解析放在数据类里，弹窗负责按列展示。
 */
data class NHSpell(
    val name: String,
    val level: String,
    val category: String,
    val fail: String,
    val retention: String,
    val turns: String?
) {
    companion object {
        private val row = Regex(
            """^(.*?)(?:\t|\s{2,})(\d+)(?:\t|\s{2,})(\S+)(?:\t|\s+)(\d+)%(?:\t|\s+)(\S+)(?:(?:\t|\s+)(\d+))?$"""
        )

        fun headerLabels(text: String): List<String> =
            text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }

        fun parse(text: String): NHSpell? {
            if (text.isBlank())
                return null
            val m = row.find(text.trim()) ?: return null
            return NHSpell(
                name = m.groupValues[1].trim(),
                level = m.groupValues[2],
                category = m.groupValues[3],
                fail = "${m.groupValues[4]}%",
                retention = m.groupValues[5],
                turns = m.groupValues.getOrNull(6)?.takeIf { it.isNotBlank() }
            )
        }
    }
}
