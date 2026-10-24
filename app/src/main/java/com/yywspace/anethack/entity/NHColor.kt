package com.yywspace.anethack.entity

enum class NHColor {
    CLR_BLACK,
    CLR_RED,
    CLR_GREEN,
    CLR_BROWN,
    CLR_BLUE,
    CLR_MAGENTA,
    CLR_CYAN,
    CLR_GRAY,
    NO_COLOR,
    CLR_ORANGE,
    CLR_BRIGHT_GREEN,
    CLR_YELLOW,
    CLR_BRIGHT_BLUE,
    CLR_BRIGHT_MAGENTA,
    CLR_BRIGHT_CYAN,
    CLR_WHITE,
    CLR_MAX;

    /** 按当前主题取色，浅色主题用原调色板，深色主题用一套柔和变体 */
    fun toColor():Int {
        val colorMap = if (darkTheme) darkColorMap else lightColorMap
        return colorMap[ordinal]
    }

    companion object {
        /** 当前是否深色主题（Utils.applyThemeMode 设置） */
        var darkTheme = false

        /** 浅色主题（原来的颜色表） */
        private val lightColorMap = intArrayOf(
            0xFF262626.toInt(),	// CLR_BLACK
            0xFFFF0000.toInt(),	// CLR_RED
            0xFF008800.toInt(),	// CLR_GREEN
            0xFF664411.toInt(), // CLR_BROWN
            0xFF0000FF.toInt(),	// CLR_BLUE
            0xFFFF00FF.toInt(),	// CLR_MAGENTA
            0xFF00FFFF.toInt(),	// CLR_CYAN
            0xFF888888.toInt(),	// CLR_GRAY
            0xFFFFFFFF.toInt(),	// NO_COLOR
            0xFFFF9900.toInt(),	// CLR_ORANGE
            0xFF00FF00.toInt(),	// CLR_BRIGHT_GREEN
            0xFFFFD700.toInt(),	// CLR_YELLOW
            0xFF0088FF.toInt(),	// CLR_BRIGHT_BLUE
            0xFFFF77FF.toInt(),	// CLR_BRIGHT_MAGENTA
            0xFF77FFFF.toInt(),	// CLR_BRIGHT_CYAN
            0xFFFFFFFF.toInt()	// CLR_WHITE
        )

        /** 深色主题：彩色保持原色，只把纯白调暗一点、近黑换成灰（避免和黑底撞色） */
        private val darkColorMap = intArrayOf(
            0xFF9E9E9E.toInt(),	// CLR_BLACK
            0xFFFF0000.toInt(),	// CLR_RED
            0xFF008800.toInt(),	// CLR_GREEN
            0xFF664411.toInt(),	// CLR_BROWN
            0xFF0000FF.toInt(),	// CLR_BLUE
            0xFFFF00FF.toInt(),	// CLR_MAGENTA
            0xFF00FFFF.toInt(),	// CLR_CYAN
            0xFF888888.toInt(),	// CLR_GRAY
            0xFFD2D2D2.toInt(),	// NO_COLOR
            0xFFFF9900.toInt(),	// CLR_ORANGE
            0xFF00FF00.toInt(),	// CLR_BRIGHT_GREEN
            0xFFFFD700.toInt(),	// CLR_YELLOW
            0xFF0088FF.toInt(),	// CLR_BRIGHT_BLUE
            0xFFFF77FF.toInt(),	// CLR_BRIGHT_MAGENTA
            0xFF77FFFF.toInt(),	// CLR_BRIGHT_CYAN
            0xFFD2D2D2.toInt()	// CLR_WHITE
        )

        fun fromInt(i: Int): NHColor {
            entries.forEach {
                if(it.ordinal == i)
                    return it
            }
            return NO_COLOR
        }
    }
}
