package com.example.zoterohelpernative.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zoterohelpernative.data.ItemData
import com.example.zoterohelpernative.data.SettingsRepository
import com.example.zoterohelpernative.data.ZoteroApiService
import com.example.zoterohelpernative.data.ZoteroCollection
import com.example.zoterohelpernative.data.ZoteroItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.Icons

data class LibraryState(
    val items: List<ZoteroItem> = emptyList(),
    val collections: List<ZoteroCollection> = emptyList(),
    val activeCollectionId: String? = null,
    val activeItem: ZoteroItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

val LibraryState.allTags: List<String>
    get() {
        return items.flatMap { it.data.tags?.map { t -> t.tag } ?: emptyList() }.distinct().sorted()
    }

val LibraryState.filteredItems: List<ZoteroItem>
    get() {
        var list = items.filter { it.data.itemType != "attachment" && it.data.itemType != "annotation" }
        if (activeCollectionId != null) {
            list = list.filter { it.data.collections?.contains(activeCollectionId) == true }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.data.title?.contains(searchQuery, ignoreCase = true) == true }
        }
        return list
    }

fun LibraryState.getChildrenForItem(parentKey: String): List<ZoteroItem> {
    return items.filter { it.data.parentItem == parentKey }
}

class LibraryViewModel(
    private val settingsRepository: SettingsRepository,
    private val zoteroRepository: com.example.zoterohelpernative.data.sync.ZoteroRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    
    private val fetchedChildrenSet = mutableSetOf<String>()

    init {
        loadLibrary()
        
        viewModelScope.launch {
            zoteroRepository.itemsFlow.collect { items ->
                _state.update { it.copy(items = items) }
            }
        }
        
        viewModelScope.launch {
            zoteroRepository.collectionsFlow.collect { collections ->
                _state.update { it.copy(collections = collections) }
            }
        }
        
        // Auto-sync every 15 minutes
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15 * 60 * 1000L) // 15 minutes
                try {
                    zoteroRepository.sync()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Example of API Client instantiation
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.zotero.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ZoteroApiService::class.java)
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                zoteroRepository.sync()
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(error = e.localizedMessage) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setActiveCollection(collectionId: String?) {
        _state.update { it.copy(activeCollectionId = collectionId, activeItem = null) }
    }

    fun setActiveItem(item: ZoteroItem?) {
        _state.update { it.copy(activeItem = item) }

        if (item != null && !fetchedChildrenSet.contains(item.key)) {
            viewModelScope.launch {
                try {
                    val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                    val userId = settingsRepository.zoteroUserId.firstOrNull()

                    if (!apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                        val response = apiService.getItemChildren(userId, item.key, apiKey)
                        if (response.isSuccessful) {
                            val children = response.body() ?: emptyList()
                            if (children.isNotEmpty()) {
                                _state.update { currentState ->
                                    val newItems = currentState.items.toMutableList()
                                    // Remove any existing children with same keys to avoid duplicates
                                    val childrenKeys = children.map { it.key }
                                    newItems.removeAll { it.key in childrenKeys }
                                    newItems.addAll(children)
                                    currentState.copy(items = newItems)
                                }
                            }
                            fetchedChildrenSet.add(item.key)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun toggleTagOnActiveItem(tagStr: String) {
        val currentItem = _state.value.activeItem ?: return
        val currentTags = currentItem.data.tags?.toMutableList() ?: mutableListOf()
        val tagExists = currentTags.any { it.tag == tagStr }

        if (tagExists) {
            currentTags.removeAll { it.tag == tagStr }
        } else {
            currentTags.add(com.example.zoterohelpernative.data.ZoteroTag(tag = tagStr))
        }

        // Optimistic UI update
        val updatedItemData = currentItem.data.copy(tags = currentTags)
        val updatedItem = currentItem.copy(data = updatedItemData)
        
        val updatedItems = _state.value.items.map {
            if (it.key == updatedItem.key) updatedItem else it
        }

        _state.update { it.copy(items = updatedItems, activeItem = updatedItem) }

        // Note: Actual API update would be done here via apiService.updateItem(...)
        // We'll leave the optimistic UI for now as it makes the app feel snappy!
        viewModelScope.launch {
            try {
                val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                val userId = settingsRepository.zoteroUserId.firstOrNull()
                if (!apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                    apiService.updateItem(userId, updatedItem.key, apiKey, itemData = updatedItemData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
