package com.yywspace.anethack.keybord

class NHKeyboard {
    val rows:MutableList<Row> = mutableListOf()
    var rowCount = 0
    var columnCount = 0
    class Key(var row: Int,
              var column: Int) {
        var rowSpan = 1
        var columnSpan = 1
        // var rowPercent = 0.0
        var columnWeight = 0.0
        var label = ""
        var value = ""

        constructor(row: Int, column: Int, label:String) : this(row, column) {
            this.label = label
        }

        constructor(row: Int, column: Int, columnWeight: Double, label:String) : this(row, column) {
            this.label = label
            this.columnWeight = columnWeight
        }

        constructor(row: Int, column: Int,
                    rowSpan:Int, columnSpan:Int, label:String) : this(row, column) {
            this.rowSpan = rowSpan
            this.columnSpan = columnSpan
            this.label = label
        }
        constructor(row: Int, column: Int,
                    rowSpan:Int, columnSpan:Int, columnWeight: Double, label:String, value:String) : this(row, column) {
            this.rowSpan = rowSpan
            this.columnSpan = columnSpan
            this.columnWeight = columnWeight
            this.label = label
            this.value = value
        }

        override fun toString(): String {
            return label
        }
    }
    // 3407

    class Row {
        val keys:MutableList<Key> = mutableListOf()
        var type:Type = Type.SPAN
        enum class Type {
            WEIGHT,
            SPAN,
        }

        override fun toString(): String {
            return keys.toString()
        }
    }
    enum class Type {
        UPPER_LETTER,
        LETTER,
        SYMBOL,
        META,
        CTRL,
        CUSTOM,
        NONE
    }
}