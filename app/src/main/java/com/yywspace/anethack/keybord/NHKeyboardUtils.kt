package com.yywspace.anethack.keybord

import android.content.Context
import android.util.Log
import com.yywspace.anethack.keybord.NHKeyboard.Key
import com.yywspace.anethack.keybord.NHKeyboard.Row
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.nio.charset.Charset


object NHKeyboardUtils {

    val keyboard1 = NHKeyboard()
    val keyboard2 = NHKeyboard()
    val keyboard3 = NHKeyboard()

    init {
        val row1 = Row().apply {
            keys.add(Key(0, 0, 2, 1, "ESC"))
            keys.add(Key(0,1, "1"))
            keys.add(Key(0,2, "2"))
            keys.add(Key(0,3, "3"))
            keys.add(Key(0,4, "4"))
            keys.add(Key(0,5, "5"))
            keys.add(Key(0,6, "6"))
            keys.add(Key(0,7, "7"))
            keys.add(Key(0,8, "8"))
            keys.add(Key(0,9, "9"))
            keys.add(Key(0,10, "0"))
        }
        val row2 = Row().apply {
            keys.add(Key(1,1, "q"))
            keys.add(Key(1,2, "w"))
            keys.add(Key(1,3, "e"))
            keys.add(Key(1,4, "r"))
            keys.add(Key(1,5, "t"))
            keys.add(Key(1,6, "y"))
            keys.add(Key(1,7, "u"))
            keys.add(Key(1,8, "i"))
            keys.add(Key(1,9, "o"))
            keys.add(Key(1,10, "p"))
        }
        val row3 = Row().apply {
            keys.add(Key(2,0, "a"))
            keys.add(Key(2,1, "s"))
            keys.add(Key(2,2, "d"))
            keys.add(Key(2,3, "f"))
            keys.add(Key(2,4, "g"))
            keys.add(Key(2,5, "h"))
            keys.add(Key(2,6, "j"))
            keys.add(Key(2,7, "k"))
            keys.add(Key(2,8, "l"))
            keys.add(Key(2,9, 1,2,"DEL"))
        }
        val row4 = Row().apply {
            keys.add(Key(3,0, 1,2,"Shift"))
            keys.add(Key(3,2, "z"))
            keys.add(Key(3,3, "x"))
            keys.add(Key(3,4, "c"))
            keys.add(Key(3,5, "v"))
            keys.add(Key(3,6, "b"))
            keys.add(Key(3,7, "n"))
            keys.add(Key(3,8, "m"))
            keys.add(Key(3,9,1,2,"Enter"))
        }

        val row5 = Row().apply {
            type = Row.Type.WEIGHT
            keys.add(Key(4,0,1.0, "Custom"))
            keys.add(Key(4,1,1.0, "Extend"))
            keys.add(Key(4,2,1.0, "Save"))
            keys.add(Key(4,3,1.0, "Quit"))
            keys.add(Key(4,4,1.0, "Search"))
            keys.add(Key(4,5,1.0, "..."))
        }
        keyboard1.rows.add(row1)
        keyboard1.rows.add(row2)
        keyboard1.rows.add(row3)
        keyboard1.rows.add(row4)
        // keyboard1.rows.add(row5)

        keyboard1.rowCount = keyboard1.rows.size
        keyboard1.rows.forEach { row ->
            val keyCount = row.keys.sumOf { it.columnSpan }
            if (keyCount > keyboard1.columnCount)
                keyboard1.columnCount = keyCount
        }

        val row20 =  Row().apply {
            keys.add(Key(0, 0, 2, 1, "ESC"))
            keys.add(Key(0, 1, "!"))
            keys.add(Key(0, 2, "@"))
            keys.add(Key(0, 3, "^"))
            keys.add(Key(0, 4, "("))
            keys.add(Key(0, 5, ")"))
            keys.add(Key(0, 6, "\""))
        }
        val row21 =  Row().apply {
            keys.add(Key(1, 1, "+"))
            keys.add(Key(1, 2, "#"))
            keys.add(Key(1, 3, "$"))
            keys.add(Key(1, 4, "/"))
            keys.add(Key(1, 5, "\\"))
            keys.add(Key(1, 6, "?"))
        }
        val row22 =  Row().apply {
            keys.add(Key(2, 0, "_"))
            keys.add(Key(2, 1, "="))
            keys.add(Key(2, 2, "["))
            keys.add(Key(2, 3, "*"))
            keys.add(Key(2, 4, ";"))
            keys.add(Key(2, 5, 1, 2,"DEL"))


        }

        val row23 =  Row().apply {
            keys.add(Key(3, 0, 1, 2,"Space"))
            keys.add(Key(3, 2, "<"))
            keys.add(Key(3, 3, ">"))
            keys.add(Key(3, 4, "."))
            keys.add(Key(3, 5, ","))
            keys.add(Key(3, 6, ":"))

        }

        val row24 = Row().apply {
            type = Row.Type.WEIGHT
            keys.add(Key(4,0,1.0, "Custom"))
            keys.add(Key(4,1,1.0, "Extend"))
            keys.add(Key(4,2,1.0, "Save"))
            keys.add(Key(4,3,1.0, "Quit"))
            keys.add(Key(4,4,1.0, "Search"))
            keys.add(Key(4,5,1.0, "..."))
        }
        keyboard2.rows.add(row20)
        keyboard2.rows.add(row21)
        keyboard2.rows.add(row22)
        keyboard2.rows.add(row23)
        keyboard2.rows.add(row24)

        keyboard2.rowCount = keyboard2.rows.size
        keyboard2.rows.forEach { row ->
            val keyCount = row.keys.sumOf { it.columnSpan }
            if (keyCount > keyboard2.columnCount)
                keyboard2.columnCount = keyCount
        }

        val row31 = Row().apply {
            type = Row.Type.WEIGHT
            keys.add(Key(0,0,1.0, "Custom"))
            keys.add(Key(0,1,1.0, "Extend"))
            keys.add(Key(0,2,1.0, "Save"))
            keys.add(Key(0,3,1.0, "Quit"))
            keys.add(Key(0,4,1.0, "Search"))
            keys.add(Key(0,5,1.0, "..."))
        }
        keyboard3.rows.add(row31)

        keyboard3.rowCount = keyboard3.rows.size
        keyboard3.rows.forEach { row ->
            val keyCount = row.keys.sumOf { it.columnSpan }
            if (keyCount > keyboard3.columnCount)
                keyboard3.columnCount = keyCount
        }
    }

    fun readFromJson(json:String):NHKeyboard {
        val keyboard = NHKeyboard()
        try {
            val rows = JSONArray(json)
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val type = row.get("type")
                val rowType = if(type == "span") Row.Type.SPAN else  Row.Type.WEIGHT
                val kbRow = Row().apply {
                    this.type = rowType
                }
                val keys = row.getJSONArray("key")
                for (j in 0 until keys.length()) {
                    val key = keys.getJSONObject(j)
                    val rowIdx = if (key.has("row")) key.getInt("row") else 1
                    val columnIdx = if (key.has("column")) key.getInt("column") else 1
                    val rowSpan = if (key.has("rowSpan")) key.getInt("rowSpan") else 1
                    val columnSpan = if (key.has("columnSpan")) key.getInt("columnSpan")  else 1
                    val columnWeight = if (key.has("columnWeight")) key.getDouble("columnWeight")  else 1.0
                    val label = if (key.has("label")) key.getString("label") else ""
                    val value = if (key.has("value")) key.getString("value") else "0"
                    val kbKey = Key(rowIdx, columnIdx, rowSpan, columnSpan, columnWeight, label, value)
                    kbRow.keys.add(kbKey)
                }
                Log.d("readFromJson", kbRow.toString())
                keyboard.rows.add(kbRow)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        keyboard.rowCount = keyboard.rows.size
        keyboard.rows.forEach { row ->
            val keyCount = row.keys.sumOf { it.columnSpan }
            if (keyCount > keyboard.columnCount)
                keyboard.columnCount = keyCount
        }
        return keyboard
    }
}