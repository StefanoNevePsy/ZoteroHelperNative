package com.example.zoterohelpernative.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Settings backup format: every stored preference with its type, so a file
 * restores exactly what was saved — including settings added in future
 * versions, which need no change here.
 *
 *   { "app": "ZoteroHelperNative", "version": 1,
 *     "values": { "<key>": { "type": "string|boolean|int|long|float|stringSet", "value": ... } } }
 *
 * Pure (no Android types) so it is unit tested.
 */
object SettingsBackup {
    const val APP = "ZoteroHelperNative"
    const val VERSION = 1

    /** Keys that are device state, not settings: never exported or imported. */
    val EXCLUDED = setOf("last_opened_map", "last_page_map", "pdf_md5_map")

    fun encode(values: Map<String, Any>): String {
        val out = JsonObject()
        for ((key, value) in values.toSortedMap()) {
            if (key in EXCLUDED) continue
            val entry = JsonObject()
            when (value) {
                is String -> { entry.addProperty("type", "string"); entry.addProperty("value", value) }
                is Boolean -> { entry.addProperty("type", "boolean"); entry.addProperty("value", value) }
                is Int -> { entry.addProperty("type", "int"); entry.addProperty("value", value) }
                is Long -> { entry.addProperty("type", "long"); entry.addProperty("value", value) }
                is Float -> { entry.addProperty("type", "float"); entry.addProperty("value", value) }
                is Double -> { entry.addProperty("type", "float"); entry.addProperty("value", value) }
                is Set<*> -> {
                    entry.addProperty("type", "stringSet")
                    entry.add("value", com.google.gson.JsonArray().also { a -> value.forEach { a.add(it.toString()) } })
                }
                else -> continue
            }
            out.add(key, entry)
        }
        return JsonObject().apply {
            addProperty("app", APP)
            addProperty("version", VERSION)
            add("values", out)
        }.toString()
    }

    class InvalidBackup(message: String) : Exception(message)

    /** Parsed values with their Kotlin types. Unknown types are skipped. */
    fun decode(json: String): Map<String, Any> {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw InvalidBackup("il file non è un backup valido")
        }
        if (root.get("app")?.asString != APP) throw InvalidBackup("il file non è un backup di questa app")
        val version = root.get("version")?.asInt ?: 0
        if (version > VERSION) throw InvalidBackup("backup creato da una versione più recente dell'app")
        val values = root.getAsJsonObject("values") ?: throw InvalidBackup("backup vuoto")

        val result = LinkedHashMap<String, Any>()
        for ((key, element) in values.entrySet()) {
            if (key in EXCLUDED) continue
            val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val v = entry.get("value") ?: continue
            try {
                result[key] = when (entry.get("type")?.asString) {
                    "string" -> v.asString
                    "boolean" -> v.asBoolean
                    "int" -> v.asInt
                    "long" -> v.asLong
                    "float" -> v.asFloat
                    "stringSet" -> v.asJsonArray.map { it.asString }.toSet()
                    else -> continue
                }
            } catch (e: Exception) {
                continue // one malformed entry doesn't spoil the rest
            }
        }
        return result
    }
}
