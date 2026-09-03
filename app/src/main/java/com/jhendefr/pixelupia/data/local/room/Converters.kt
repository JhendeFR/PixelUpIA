package com.jhendefr.pixelupia.data.local.room

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        val result = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            return json.split("|||").filter { it.isNotBlank() }
        }
        return result
    }
}
