package com.yywspace.anethack.window

enum class NHWindowType {
    NHW_MESSAGE,
    NHW_STATUS,
    NHW_MAP,
    NHW_MENU,
    NHW_TEXT,
    NHW_PERMINVENT;
    companion object {
        fun fromInt(type: Int): NHWindowType {
            return when (type) {
                1 -> NHW_MESSAGE
                2 -> NHW_STATUS
                3 -> NHW_MAP
                4 -> NHW_MENU
                5 -> NHW_TEXT
                else -> NHW_PERMINVENT
            }
        }
    }
}