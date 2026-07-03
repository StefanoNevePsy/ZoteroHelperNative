package com.example.zoterohelpernative.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zoterohelpernative.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.zoterohelpernative.data.Palette
import com.example.zoterohelpernative.data.MappedColor
import com.example.zoterohelpernative.theme.DEFAULT_PALETTES
import java.util.UUID
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.Icons

data class SettingsState(
    val zoteroApiKey: String = "",
    val zoteroUserId: String = "",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPass: String = "",
    val geminiApiKey: String = "",
    val nvidiaApiKey: String = "",
    val customPalettes: Map<String, Palette> = emptyMap(),
    val penHighlightOnly: Boolean = false,
    val fingerSelectionOnly: Boolean = false,
    val autoCachePdfs: Boolean = true,
    val toolIcons: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true
)

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    zoteroApiKey = repository.zoteroApiKey.firstOrNull() ?: "",
                    zoteroUserId = repository.zoteroUserId.firstOrNull() ?: "",
                    webdavUrl = repository.webdavUrl.firstOrNull() ?: "",
                    webdavUser = repository.webdavUser.firstOrNull() ?: "",
                    webdavPass = repository.webdavPass.firstOrNull() ?: "",
                    geminiApiKey = repository.geminiApiKey.firstOrNull() ?: "",
                    nvidiaApiKey = repository.nvidiaApiKey.firstOrNull() ?: "",
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            repository.customPalettes.collect { palettes ->
                _state.update { it.copy(customPalettes = palettes) }
            }
        }
        viewModelScope.launch {
            repository.penHighlightOnly.collect { value ->
                _state.update { it.copy(penHighlightOnly = value) }
            }
        }
        viewModelScope.launch {
            repository.fingerSelectionOnly.collect { value ->
                _state.update { it.copy(fingerSelectionOnly = value) }
            }
        }
        viewModelScope.launch {
            repository.autoCachePdfs.collect { value ->
                _state.update { it.copy(autoCachePdfs = value) }
            }
        }
        viewModelScope.launch {
            repository.toolIcons.collect { icons ->
                _state.update { it.copy(toolIcons = icons) }
            }
        }
    }

    fun updateZoteroApiKey(value: String) { _state.update { it.copy(zoteroApiKey = value) } }
    fun updateZoteroUserId(value: String) { _state.update { it.copy(zoteroUserId = value) } }
    fun updateWebdavUrl(value: String) { _state.update { it.copy(webdavUrl = value) } }
    fun updateWebdavUser(value: String) { _state.update { it.copy(webdavUser = value) } }
    fun updateWebdavPass(value: String) { _state.update { it.copy(webdavPass = value) } }
    fun updateGeminiApiKey(value: String) { _state.update { it.copy(geminiApiKey = value) } }
    fun updateNvidiaApiKey(value: String) { _state.update { it.copy(nvidiaApiKey = value) } }

    fun saveSettings(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveZoteroCredentials(state.value.zoteroApiKey, state.value.zoteroUserId)
            repository.saveWebDavCredentials(state.value.webdavUrl, state.value.webdavUser, state.value.webdavPass)
            repository.saveGeminiApiKey(state.value.geminiApiKey)
            repository.saveNvidiaApiKey(state.value.nvidiaApiKey)
            repository.savePenHighlightOnly(state.value.penHighlightOnly)
            repository.saveFingerSelectionOnly(state.value.fingerSelectionOnly)
            repository.saveToolIcons(state.value.toolIcons)
            onComplete()
        }
    }
    
    fun updatePenHighlightOnly(value: Boolean) { 
        _state.update { it.copy(penHighlightOnly = value) }
        viewModelScope.launch { repository.savePenHighlightOnly(value) }
    }
    fun updateFingerSelectionOnly(value: Boolean) {
        _state.update { it.copy(fingerSelectionOnly = value) }
        viewModelScope.launch { repository.saveFingerSelectionOnly(value) }
    }

    fun updateAutoCachePdfs(value: Boolean) {
        _state.update { it.copy(autoCachePdfs = value) }
        viewModelScope.launch { repository.saveAutoCachePdfs(value) }
    }

    fun updateToolIcon(toolName: String, iconName: String) {
        val updatedIcons = _state.value.toolIcons.toMutableMap()
        updatedIcons[toolName] = iconName
        _state.update { it.copy(toolIcons = updatedIcons) }
        viewModelScope.launch { repository.saveToolIcons(updatedIcons) }
    }

    fun createCustomPalette(name: String) {
        val id = UUID.randomUUID().toString()
        val zoteroColors = DEFAULT_PALETTES["zotero"]?.colors ?: return
        val newPalette = Palette(
            id = id,
            name = name,
            colors = zoteroColors.map { it.copy() }
        )
        val updatedMap = _state.value.customPalettes + (id to newPalette)
        viewModelScope.launch { repository.saveCustomPalettes(updatedMap) }
    }

    fun updateCustomPaletteColor(paletteId: String, zoteroHex: String, newUiHex: String) {
        val palette = _state.value.customPalettes[paletteId] ?: return
        val updatedColors = palette.colors.map { 
            if (it.zoteroHex.lowercase() == zoteroHex.lowercase()) it.copy(uiHex = newUiHex) else it
        }
        val updatedPalette = palette.copy(colors = updatedColors)
        val updatedMap = _state.value.customPalettes + (paletteId to updatedPalette)
        viewModelScope.launch { repository.saveCustomPalettes(updatedMap) }
    }

    fun deleteCustomPalette(paletteId: String) {
        val updatedMap = _state.value.customPalettes.toMutableMap()
        updatedMap.remove(paletteId)
        viewModelScope.launch { repository.saveCustomPalettes(updatedMap) }
    }
}
