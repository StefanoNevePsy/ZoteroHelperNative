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

enum class LibrarySortOption(val label: String) {
    TITLE("Titolo"),
    YEAR("Anno"),
    AUTHOR("Autore"),
    ITEM_TYPE("Tipo"),
    LAST_OPENED("Ultima apertura")
}

data class LibraryState(
    val items: List<ZoteroItem> = emptyList(),
    val collections: List<ZoteroCollection> = emptyList(),
    val activeCollectionId: String? = null,
    val activeItem: ZoteroItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val sortOption: LibrarySortOption = LibrarySortOption.TITLE,
    val sortAscending: Boolean = true,
    val filterTag: String? = null,
    val lastOpened: Map<String, Long> = emptyMap(),
    val pendingSyncCount: Int = 0
)

val LibraryState.allTags: List<String>
    get() {
        return items.flatMap { it.data.tags?.map { t -> t.tag } ?: emptyList() }.distinct().sorted()
    }

private val yearRegex = Regex("""\d{4}""")

val ItemData.year: Int?
    get() = date?.let { yearRegex.find(it)?.value?.toIntOrNull() }

val ItemData.authorSummary: String?
    get() = creators?.firstOrNull { it.lastName != null || it.name != null }
        ?.let { it.lastName ?: it.name }

val LibraryState.filteredItems: List<ZoteroItem>
    get() {
        var list = items.filter {
            it.data.itemType != "attachment" &&
            it.data.itemType != "annotation" &&
            it.data.parentItem == null
        }
        if (activeCollectionId != null) {
            list = list.filter { it.data.collections?.contains(activeCollectionId) == true }
        }
        if (filterTag != null) {
            list = list.filter { item -> item.data.tags?.any { it.tag == filterTag } == true }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter { item ->
                item.data.title?.contains(q, ignoreCase = true) == true ||
                item.data.date?.contains(q, ignoreCase = true) == true ||
                item.data.creators?.any {
                    it.lastName?.contains(q, ignoreCase = true) == true ||
                    it.firstName?.contains(q, ignoreCase = true) == true ||
                    it.name?.contains(q, ignoreCase = true) == true
                } == true
            }
        }
        val comparator: Comparator<ZoteroItem> = when (sortOption) {
            LibrarySortOption.TITLE -> compareBy { it.data.title?.lowercase() ?: "\uFFFF" }
            LibrarySortOption.YEAR -> compareBy { it.data.year ?: Int.MAX_VALUE }
            LibrarySortOption.AUTHOR -> compareBy { it.data.authorSummary?.lowercase() ?: "\uFFFF" }
            LibrarySortOption.ITEM_TYPE -> compareBy { it.data.itemType }
            LibrarySortOption.LAST_OPENED -> compareBy { lastOpened[it.key] ?: 0L }
        }
        val sorted = list.sortedWith(comparator)
        return if (sortAscending) sorted else sorted.reversed()
    }

fun LibraryState.getChildrenForItem(parentKey: String): List<ZoteroItem> {
    return items.filter { it.data.parentItem == parentKey }
}

class LibraryViewModel(
    private val settingsRepository: SettingsRepository,
    private val zoteroRepository: com.example.zoterohelpernative.data.sync.ZoteroRepository,
    private val cacheDir: java.io.File? = null
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

        viewModelScope.launch {
            settingsRepository.lastOpenedMap.collect { map ->
                _state.update { it.copy(lastOpened = map) }
            }
        }

        viewModelScope.launch {
            zoteroRepository.pendingSyncCount.collect { count ->
                _state.update { it.copy(pendingSyncCount = count) }
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
                // A manual refresh must also refetch item children (notes/attachments)
                fetchedChildrenSet.clear()
                zoteroRepository.sync()
                prefetchRecentDocuments()
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(error = e.localizedMessage) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private val webDavClient = com.example.zoterohelpernative.data.WebDavClient()
    private var prefetchJob: kotlinx.coroutines.Job? = null

    /**
     * Downloads the PDFs of the most recently opened items in the background, so
     * documents open instantly and are available offline. Bounded per run and
     * skippable via the 'auto cache' setting.
     */
    private fun prefetchRecentDocuments(maxDownloadsPerRun: Int = 10) {
        val dir = cacheDir ?: return
        if (prefetchJob?.isActive == true) return
        prefetchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (settingsRepository.autoCachePdfs.firstOrNull() != true) return@launch
                val webDavUrl = settingsRepository.webdavUrl.firstOrNull() ?: return@launch
                if (webDavUrl.isEmpty()) return@launch
                val webDavUser = settingsRepository.webdavUser.firstOrNull()
                val webDavPass = settingsRepository.webdavPass.firstOrNull()

                val snapshot = _state.value
                val pdfAttachments = snapshot.items.filter {
                    it.data.itemType == "attachment" &&
                        (it.data.contentType == "application/pdf" ||
                            it.data.filename?.endsWith(".pdf", ignoreCase = true) == true)
                }
                // Most recently opened items first; never-opened ones last
                val ordered = pdfAttachments.sortedByDescending { att ->
                    snapshot.lastOpened[att.data.parentItem] ?: 0L
                }

                var downloads = 0
                for (attachment in ordered) {
                    if (downloads >= maxDownloadsPerRun) break
                    val extractDir = java.io.File(dir, "extracted_${attachment.key}")
                    val alreadyCached = extractDir.listFiles()
                        ?.any { it.isFile && it.extension.equals("pdf", ignoreCase = true) && it.length() > 0 } == true
                    if (alreadyCached) continue

                    val zipFile = webDavClient.downloadAttachment(webDavUrl, webDavUser, webDavPass, attachment.key, dir)
                        ?: continue
                    val extracted = com.example.zoterohelpernative.utils.ZipUtils.extractPdfFromZip(zipFile, extractDir)
                    zipFile.delete()
                    if (extracted != null) {
                        attachment.data.md5?.let { settingsRepository.savePdfMd5(attachment.key, it) }
                        downloads++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setSortOption(option: LibrarySortOption) {
        _state.update {
            if (it.sortOption == option) {
                // Selecting the active criterion again flips the direction
                it.copy(sortAscending = !it.sortAscending)
            } else {
                // "Last opened" is most useful newest-first
                it.copy(sortOption = option, sortAscending = option != LibrarySortOption.LAST_OPENED)
            }
        }
    }

    fun recordItemOpened(itemKey: String) {
        viewModelScope.launch {
            try {
                settingsRepository.recordItemOpened(itemKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setFilterTag(tag: String?) {
        _state.update { it.copy(filterTag = tag) }
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

        viewModelScope.launch {
            try {
                val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                val userId = settingsRepository.zoteroUserId.firstOrNull()
                if (!apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                    val response = apiService.updateItem(userId, updatedItem.key, apiKey, itemData = updatedItemData)
                    // Track the new version: without it the next PATCH on this item
                    // fails with 412 and the change silently never reaches Zotero
                    if (response.isSuccessful) {
                        response.headers()["Last-Modified-Version"]?.toLongOrNull()?.let { newVersion ->
                            _state.update { s ->
                                s.copy(
                                    items = s.items.map { existing ->
                                        if (existing.key == updatedItem.key) {
                                            existing.copy(version = newVersion, data = existing.data.copy(version = newVersion))
                                        } else existing
                                    },
                                    activeItem = s.activeItem?.let { active ->
                                        if (active.key == updatedItem.key) {
                                            active.copy(version = newVersion, data = active.data.copy(version = newVersion))
                                        } else active
                                    }
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
