package com.yywspace.anethack.identify

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yywspace.anethack.R
import org.json.JSONObject
import java.util.regex.Pattern
import kotlin.math.floor

class NHPriceID(context: Context) {
    private val objects = mutableMapOf<String, List<Map<String,String>>>()
    private val tradeTypesRegex = mutableMapOf<String, String>()
    private val objectTypesRegex = mutableMapOf<String, String>()

    init {
        try {
            loadObjFromAssets(context)
        } catch (e:Exception) {
            Toast.makeText(context, R.string.price_id_invalid_json, Toast.LENGTH_LONG).show()
        }
    }
    fun getObjBySellPrice(type:String, price:String, sucker:Boolean):List<Map<String,String>> {
        if (price.isEmpty())
            return emptyList()
        val skpPrice = price.toInt()
        val objList = mutableListOf<Map<String,String>>()
        for (obj in getObjByType(type)) {
            val basePrice = obj["Cost"]?.toInt()?:0
            val priceA = calcSellPrice(basePrice,false, sucker)
            val priceB = calcSellPrice(basePrice,true, sucker)
            if (priceA == skpPrice || priceB == skpPrice)
                objList.add(obj)
        }
        return objList
    }

    fun getObjByBuyPrice(type:String, price:String, charisma:String, sucker:Boolean):List<Map<String,String>> {
        if (charisma.isEmpty() || price.isEmpty())
            return emptyList()
        val ch = charisma.toInt()
        val skpPrice = price.toInt()
        val objList = mutableListOf<Map<String,String>>()
        for (obj in getObjByType(type)) {
            val basePrice = obj["Cost"]?.toInt()?:0
            val priceA = calcBuyPrice(basePrice, ch, false, sucker)
            val priceB = calcBuyPrice(basePrice, ch, true, sucker)
            if (priceA == skpPrice || priceB == skpPrice)
                objList.add(obj)
        }
        return objList
    }
    fun getObjByBasePrice(type:String, basePrice:String):List<Map<String,String>> {
        if (basePrice.isEmpty())
            return getObjByType(type)
        return getObjByType(type).filter { obj->obj["Cost"] == basePrice }
    }
    private fun getObjByType(type:String):List<Map<String,String>> {
        return objects[type]?.sortedWith(compareBy({it["Cost"]?.toInt()?:0}, {it["Name"]}))?: emptyList()
    }

    fun getObjTypes(): List<String> {
        return objects.keys.toList().sorted()
    }

    private fun parseObjType(objDesc:String):String {
        for (type in objectTypesRegex.keys) {
            val typeRegex = objectTypesRegex[type]?:""
            val typeMatcher = Pattern.compile(typeRegex).matcher(objDesc)
            if (typeMatcher.find()) {
                return type
            }
        }
        return ""
    }

    fun parseTradeInfo(info:String): Map<String,String> {
        // you see here a smoky potion (for sale, 67 zorkmids).
        // a smoky potion (for sale, 67 zorkmids)
        // h - a smoky potion (unpaid, 67 zorkmids)
        // you drop a smoky potion (unpaid, 67 zorkmids)
        // Tipor offers 2 gold pieces for you lembas water. Sell it?
        val result = mutableMapOf<String,String>()
        for (tradeType in tradeTypesRegex.keys) {
            val regex = tradeTypesRegex[tradeType]?:""
            val matcher = Pattern.compile(regex).matcher(info)
            while (matcher.find()) {
                val objDesc = matcher.group("objDesc")?:""
                result["tradePrice"] = matcher.group("objPrice")?:""
                result["tradeMode"] = tradeType
                val objType = parseObjType(objDesc)
                if (objType.isNotEmpty())
                    result["objType"] = objType
            }
        }
        Log.d("parseTradeInfo", result.toString())
        return result
    }

    private fun calcBuyPrice(price:Int, ch:Int, surcharge:Boolean, sucker:Boolean):Int {
        var m = 1; var d = 1
        var pri = price.toDouble()
        if (surcharge) { m *= 4; d *= 3 }
        if (sucker) { m *= 4; d *= 3 }
        when {
            ch >= 19 -> { m *= 1; d *= 2 } // 50%
            ch >= 18 -> { m *= 2; d *= 3 } // 67%
            ch >= 16 -> { m *= 3; d *= 4 } // 75%
            ch >= 11 -> { m *= 1; d *= 1 } // 100%
            ch >= 8 -> { m *= 4; d *= 3 } // 133%
            ch >= 6 -> { m *= 3; d *= 2 } // 150%
            else -> { m *= 2; d *= 1 } // 200%
        }
        pri *= m
        if (d > 1) {
            pri *= 10
            pri /= d
            pri += 5
            pri /= 10
            pri = floor(pri)
        }
        if (pri <= 0)
            pri = 1.0
        return pri.toInt()
    }

    private fun calcSellPrice(price:Int, surcharge:Boolean, sucker:Boolean):Int {
        var m = 1
        var d = if (sucker) 3 else 2
        var pri = price.toDouble()
        if (price > 1 && surcharge) {
            m *= 3; d *= 4
        }
        if (pri > 1) {
            pri *= m
            pri *= 10
            pri /= d
            pri += 5
            pri /= 10
            pri = floor(pri)
            if (pri < 1)
                pri = 1.0
        }
        return pri.toInt()
    }

    private fun loadObjFromAssets(context: Context) {
        context.assets.open("nh_default_objects.json").bufferedReader().use {
            val json = it.readText()
            val roots = JSONObject(json)
            for(contentType in roots.keys()) {
                when(contentType) {
                    "tradeTypesRegex" -> {
                        val tradeTypes = roots.getJSONObject("tradeTypesRegex")
                        for(tradeType in tradeTypes.keys()) {
                            tradeTypesRegex[tradeType] = tradeTypes.getString(tradeType)
                        }
                    }
                    "objectTypesRegex" -> {
                        val objectTypes= roots.getJSONObject("objectTypesRegex")
                        for(objectType in objectTypes.keys()) {
                            objectTypesRegex[objectType] = objectTypes.getString(objectType)
                        }
                    }
                    "objects" -> {
                        val objTypes = roots.getJSONObject("objects")
                        for(type in objTypes.keys()) {
                            val objListJson = objTypes.getJSONArray(type)
                            val objList = mutableListOf<Map<String,String>>()
                            for (i in 0 until objListJson.length()) {
                                val obj = objListJson.getJSONObject(i)
                                val attrMap = mutableMapOf<String,String>()
                                val attrKeys = mutableListOf<String>()
                                for(objAttr in obj.keys()) {
                                    attrKeys.add(objAttr)
                                    attrMap[objAttr]=obj.getString(objAttr)
                                }
                                if (attrKeys.containsAll(listOf("Name", "Cost")))
                                    objList.add(attrMap)
                            }
                            objects[type] = objList
                        }
                    }
                }
            }
        }
    }
}