package com.yywspace.anethack

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SharedPreferencesUtils(val context: Context) {

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var _saves by SharedPreferenceDelegates.map()
    private var inputPrompts by SharedPreferenceDelegates.stringSet()

    var dumpLogMaxSize by SharedPreferenceDelegates.string2int(10)
    var messageHistorySize by SharedPreferenceDelegates.string2int(500)
    var menuType by SharedPreferenceDelegates.string("1")
    var lockView by SharedPreferenceDelegates.boolean(false)
    var immersiveMode by SharedPreferenceDelegates.boolean(true)
    var keyboardVibrate by SharedPreferenceDelegates.boolean(true)
    var tileSet by SharedPreferenceDelegates.string("1")
    var showIndicator by SharedPreferenceDelegates.boolean(true)
    var showLastTravelIndicator by SharedPreferenceDelegates.boolean(true)
    var indicatorSymbols by SharedPreferenceDelegates.string(">")
    var priceId by SharedPreferenceDelegates.boolean(true)

    fun getInputPrompts():List<String> {
        return inputPrompts.toList()
    }
    fun addInputPrompts(prompt:String) {
        inputPrompts = inputPrompts.toMutableList().run {
            if (size > 50)
                removeFirst()
            add(prompt)
            toMutableSet()
        }
    }
    fun removeInputPrompts(prompt:String) {
        inputPrompts = inputPrompts.toMutableSet().apply {
            remove(prompt)
        }
    }
    fun getSaves():Map<String, String> {
        return _saves
    }
    fun addSaves(player:String, mode:String) {
        _saves = _saves.toMutableMap().apply {
            put(player, mode)
        }
    }
    private object SharedPreferenceDelegates {

        fun map() = object : ReadWriteProperty<SharedPreferencesUtils, Map<String, String>> {

            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Map<String, String> {
                // a:b,c:d
                val map = HashMap<String, String>()
                thisRef.preferences.getString(property.name, "")?.apply {
                    split(",").forEach { kvStr ->
                        val kv = kvStr.split(":")
                        if (kv.size == 2)
                            map[kv[0]] = kv[1]
                    }
                }
                return map
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Map<String, String>) {
                val sb = StringBuilder()
                value.forEach {
                    sb.append("${it.key}:${it.value},")
                }
                thisRef.preferences.edit().putString(property.name, sb.toString()).apply()
            }

        }
        fun int(defaultValue: Int = 0) = object : ReadWriteProperty<SharedPreferencesUtils, Int> {

            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Int {
                return thisRef.preferences.getInt(property.name, defaultValue)
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Int) {
                thisRef.preferences.edit().putInt(property.name, value).apply()
            }
        }

        fun long(defaultValue: Long = 0L) = object : ReadWriteProperty<SharedPreferencesUtils, Long> {

            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Long {
                return thisRef.preferences.getLong(property.name, defaultValue)
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Long) {
                thisRef.preferences.edit().putLong(property.name, value).apply()
            }
        }

        fun boolean(defaultValue: Boolean = false) = object : ReadWriteProperty<SharedPreferencesUtils, Boolean> {
            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Boolean {
                return thisRef.preferences.getBoolean(property.name, defaultValue)
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Boolean) {
                thisRef.preferences.edit().putBoolean(property.name, value).apply()
            }
        }

        fun float(defaultValue: Float = 0.0f) = object : ReadWriteProperty<SharedPreferencesUtils, Float> {
            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Float {
                return thisRef.preferences.getFloat(property.name, defaultValue)
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Float) {
                thisRef.preferences.edit().putFloat(property.name, value).apply()
            }
        }

        fun string(defaultValue: String? = null) = object : ReadWriteProperty<SharedPreferencesUtils, String?> {
            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): String? {
                return thisRef.preferences.getString(property.name, defaultValue)
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: String?) {
                thisRef.preferences.edit().putString(property.name, value).apply()
            }
        }

        fun string2int(defaultValue: Int = 0) = object : ReadWriteProperty<SharedPreferencesUtils, Int> {
            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Int {
                return thisRef.preferences.getString(property.name, defaultValue.toString())?.toInt()?:defaultValue
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Int) {
                thisRef.preferences.edit().putString(property.name, value.toString()).apply()
            }
        }

        fun stringSet(defaultValue: Set<String> = emptySet()) = object : ReadWriteProperty<SharedPreferencesUtils, Set<String>> {
            override fun getValue(thisRef: SharedPreferencesUtils, property: KProperty<*>): Set<String> {
                return thisRef.preferences.getStringSet(property.name, defaultValue)?: emptySet()
            }

            override fun setValue(thisRef: SharedPreferencesUtils, property: KProperty<*>, value: Set<String>) {
                thisRef.preferences.edit().putStringSet(property.name, value).apply()
            }
        }
    }
}
