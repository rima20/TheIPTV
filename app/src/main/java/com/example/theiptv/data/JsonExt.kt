package com.example.theiptv.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

fun JsonObject.str(key: String): String {
    val element = get(key) ?: return ""
    return element.asFlexibleString()
}

fun JsonObject.int(key: String): Int = str(key).toIntOrNull() ?: 0

fun JsonObject.longOrNull(key: String): Long? {
    val raw = str(key).trim()
    if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return null
    return raw.toLongOrNull()?.takeIf { it > 0L }
}

fun JsonObject.boolish(key: String): Boolean {
    val element = get(key) ?: return false
    return element.asBoolish()
}

fun JsonObject.obj(key: String): JsonObject? {
    val element = get(key) ?: return null
    return if (element.isJsonObject) element.asJsonObject else null
}

fun JsonObject.arr(key: String): JsonArray? {
    val element = get(key) ?: return null
    return if (element.isJsonArray) element.asJsonArray else null
}

fun JsonElement.asFlexibleString(): String {
    if (isJsonNull) return ""
    if (isJsonPrimitive) {
        val primitive = asJsonPrimitive
        return when {
            primitive.isString -> primitive.asString
            else -> primitive.toString()
        }
    }
    return ""
}

fun JsonElement.asBoolish(): Boolean {
    if (isJsonNull) return false
    if (!isJsonPrimitive) return false
    val primitive = asJsonPrimitive
    return when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isNumber -> primitive.asInt != 0
        else -> primitive.asString.equals("1", true) ||
            primitive.asString.equals("true", true) ||
            primitive.asString.equals("yes", true)
    }
}

fun JsonElement.asObjectList(): List<JsonObject> {
    if (!isJsonArray) return emptyList()
    return asJsonArray.mapNotNull { item ->
        if (item != null && item.isJsonObject) item.asJsonObject else null
    }
}

fun JsonReader.nextFlexibleString(): String {
    return when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            ""
        }
        JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> {
            skipValue()
            ""
        }
        JsonToken.BOOLEAN -> if (nextBoolean()) "1" else "0"
        else -> nextString()
    }
}

fun JsonReader.nextStringList(): List<String> {
    return when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            emptyList()
        }
        JsonToken.BEGIN_ARRAY -> {
            val values = ArrayList<String>(4)
            beginArray()
            while (hasNext()) {
                val value = nextFlexibleString().trim()
                if (value.isNotBlank()) values += value
            }
            endArray()
            values
        }
        else -> nextFlexibleString()
            .split(',', '|', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
