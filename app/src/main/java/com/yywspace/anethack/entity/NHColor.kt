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

    fun toColor():Int {
        val colorMap = intArrayOf(
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
        return colorMap[ordinal]
    }
    companion object {
        fun fromInt(i: Int): NHColor {
            NHColor.values().forEach {
                if(it.ordinal == i)
                    return it
            }
            return NO_COLOR
        }
    }
}