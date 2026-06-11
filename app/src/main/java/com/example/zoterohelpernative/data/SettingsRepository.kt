package com.example.zoterohelpernative.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.zoterohelpernative.data.Palette

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    companion object {
        val ZOTERO_API_KEY = stringPreferencesKey("zotero_api_key")
        val ZOTERO_USER_ID = stringPreferencesKey("zotero_user_id")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        val WEBDAV_PASS = stringPreferencesKey("webdav_pass")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val ACTIVE_PALETTE_ID = stringPreferencesKey("active_palette_id")
        val CUSTOM_PALETTES = stringPreferencesKey("custom_palettes")
        val PEN_HIGHLIGHT_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("pen_highlight_only")
        val FINGER_SELECTION_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("finger_selection_only")
        val PDF_THEME = stringPreferencesKey("pdf_theme")
        val TOOL_ICONS = stringPreferencesKey("tool_icons")
        val LAST_OPENED_MAP = stringPreferencesKey("last_opened_map")
        val LAST_PAGE_MAP = stringPreferencesKey("last_page_map")
    }

    private val gson = Gson()

    val zoteroApiKey: Flow<String?> = context.dataStore.data.map { it[ZOTERO_API_KEY] }
    val zoteroUserId: Flow<String?> = context.dataStore.data.map { it[ZOTERO_USER_ID] }
    val webdavUrl: Flow<String?> = context.dataStore.data.map { it[WEBDAV_URL] }
    val webdavUser: Flow<String?> = context.dataStore.data.map { it[WEBDAV_USER] }
    val webdavPass: Flow<String?> = context.dataStore.data.map { it[WEBDAV_PASS] }
    val geminiApiKey: Flow<String?> = context.dataStore.data.map { it[GEMINI_API_KEY] }
    val activePaletteId: Flow<String?> = context.dataStore.data.map { it[ACTIVE_PALETTE_ID] }
    
    val customPalettes: Flow<Map<String, Palette>> = context.dataStore.data.map { prefs ->
        val json = prefs[CUSTOM_PALETTES]
        if (json.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, Palette>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    val penHighlightOnly: Flow<Boolean> = context.dataStore.data.map { it[PEN_HIGHLIGHT_ONLY] ?: false }
    val fingerSelectionOnly: Flow<Boolean> = context.dataStore.data.map { it[FINGER_SELECTION_ONLY] ?: false }
    val pdfTheme: Flow<String?> = context.dataStore.data.map { it[PDF_THEME] }

    val toolIcons: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val json = prefs[TOOL_ICONS]
        if (json.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    // itemKey (parent item) -> epoch millis of the last time a PDF of that item was opened
    val lastOpenedMap: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        val json = prefs[LAST_OPENED_MAP]
        if (json.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, Long>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    suspend fun recordItemOpened(itemKey: String) {
        context.dataStore.edit { preferences ->
            val current: Map<String, Long> = try {
                val json = preferences[LAST_OPENED_MAP]
                if (json.isNullOrEmpty()) emptyMap()
                else gson.fromJson(json, object : TypeToken<Map<String, Long>>() {}.type)
            } catch (e: Exception) {
                emptyMap()
            }
            val updated = (current + (itemKey to System.currentTimeMillis()))
                // Keep the map bounded: drop the oldest entries past 500
                .entries.sortedByDescending { it.value }.take(500)
                .associate { it.key to it.value }
            preferences[LAST_OPENED_MAP] = gson.toJson(updated)
        }
    }

    // attachmentKey -> last read page (0-based), to resume reading where you left off
    val lastPageMap: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        val json = prefs[LAST_PAGE_MAP]
        if (json.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    suspend fun saveLastReadPage(attachmentKey: String, page: Int) {
        context.dataStore.edit { preferences ->
            val current: Map<String, Int> = try {
                val json = preferences[LAST_PAGE_MAP]
                if (json.isNullOrEmpty()) emptyMap()
                else gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } catch (e: Exception) {
                emptyMap()
            }
            val updated = (current + (attachmentKey to page)).entries
                .toList().takeLast(500)
                .associate { it.key to it.value }
            preferences[LAST_PAGE_MAP] = gson.toJson(updated)
        }
    }

    suspend fun savePdfTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[PDF_THEME] = theme
        }
    }

    suspend fun savePenHighlightOnly(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PEN_HIGHLIGHT_ONLY] = value
        }
    }

    suspend fun saveZoteroCredentials(apiKey: String, userId: String) {
        context.dataStore.edit { preferences ->
            preferences[ZOTERO_API_KEY] = apiKey
            preferences[ZOTERO_USER_ID] = userId
        }
    }

    suspend fun saveWebDavCredentials(url: String, user: String, pass: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_URL] = url
            preferences[WEBDAV_USER] = user
            preferences[WEBDAV_PASS] = pass
        }
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey
        }
    }

    suspend fun saveActivePaletteId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_PALETTE_ID] = id
        }
    }

    suspend fun saveCustomPalettes(palettes: Map<String, Palette>) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_PALETTES] = gson.toJson(palettes)
        }
    }
    
    suspend fun saveFingerSelectionOnly(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FINGER_SELECTION_ONLY] = value
        }
    }

    suspend fun saveToolIcons(icons: Map<String, String>) {
        context.dataStore.edit { preferences ->
            preferences[TOOL_ICONS] = gson.toJson(icons)
        }
    }
}
