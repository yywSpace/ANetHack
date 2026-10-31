package com.yywspace.anethack.keybord

import android.util.Log
import org.json.JSONArray
import org.json.JSONException

class KeyboardLayout {
    val rows: MutableList<Row> = mutableListOf()

    class Key(var row: Int, var column: Int) {
        var rowSpan = 1
        var columnSpan = 1
        var columnWeight = 1.0
        var label = ""
        var value = ""

        constructor(row: Int, column: Int, label: String) : this(row, column) {
            this.label = label
        }

        constructor(row: Int, column: Int, columnWeight: Double, label: String) : this(row, column) {
            this.label = label
            this.columnWeight = columnWeight
        }

        constructor(
            row: Int, column: Int,
            rowSpan: Int, columnSpan: Int, label: String
        ) : this(row, column) {
            this.rowSpan = rowSpan
            this.columnSpan = columnSpan
            this.label = label
        }

        constructor(
            row: Int, column: Int,
            rowSpan: Int, columnSpan: Int, columnWeight: Double, label: String, value: String
        ) : this(row, column) {
            this.rowSpan = rowSpan
            this.columnSpan = columnSpan
            this.columnWeight = columnWeight
            this.label = label
            this.value = value
        }

        override fun toString(): String = label
    }

    class Row {
        val keys: MutableList<Key> = mutableListOf()

        override fun toString(): String = keys.toString()
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

    companion object {
        /** 解析键盘模板 JSON；解析失败时返回空键盘 */
        fun fromJson(json: String): KeyboardLayout {
            val keyboard = KeyboardLayout()
            try {
                val rows = JSONArray(json)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val kbRow = Row()
                    val keys = row.getJSONArray("key")
                    for (j in 0 until keys.length()) {
                        val key = keys.getJSONObject(j)
                        val rowIdx = if (key.has("row")) key.getInt("row") else 0
                        val columnIdx = if (key.has("column")) key.getInt("column") else 0
                        val rowSpan = if (key.has("rowSpan")) key.getInt("rowSpan") else 1
                        val columnSpan = if (key.has("columnSpan")) key.getInt("columnSpan") else 1
                        val columnWeight =
                            if (key.has("columnWeight")) key.getDouble("columnWeight") else 1.0
                        val label = if (key.has("label")) key.getString("label") else ""
                        val value = if (key.has("value")) key.getString("value") else ""
                        kbRow.keys.add(
                            Key(rowIdx, columnIdx, rowSpan, columnSpan, columnWeight, label, value)
                        )
                    }
                    Log.d("KeyboardLayout", kbRow.toString())
                    keyboard.rows.add(kbRow)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return keyboard
        }
    }
}
