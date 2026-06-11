package com.example.zoterohelpernative.data.sync

import com.example.zoterohelpernative.data.*
import com.example.zoterohelpernative.data.local.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class ZoteroRepository(
    private val database: ZoteroDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.zotero.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ZoteroApiService::class.java)
    }

    val itemsFlow: Flow<List<ZoteroItem>> = database.zoteroDao().getAllItemsFlow().map { entities ->
        entities.mapNotNull { entity ->
            try {
                val itemData = gson.fromJson(entity.jsonData, ItemData::class.java)
                ZoteroItem(
                    key = entity.key,
                    version = entity.version,
                    library = Library("user", 0, "", Links(null, null, null)),
                    links = Links(null, null, null),
                    meta = null,
                    data = itemData
                )
            } catch (e: Exception) { null }
        }
    }

    val collectionsFlow: Flow<List<ZoteroCollection>> = database.zoteroDao().getAllCollectionsFlow().map { entities ->
        entities.mapNotNull { entity ->
            try {
                val data = gson.fromJson(entity.jsonData, CollectionData::class.java)
                ZoteroCollection(
                    key = entity.key,
                    version = entity.version,
                    data = data
                )
            } catch (e: Exception) { null }
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        database.zoteroDao().clearItems()
        database.zoteroDao().clearCollections()
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
        val userId = settingsRepository.zoteroUserId.firstOrNull()
        if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) return@withContext

        try {
            // 1. Push local dirty items
            val dirtyItems = database.zoteroDao().getDirtyItems()
            for (localItem in dirtyItems) {
                try {
                    val itemData = gson.fromJson(localItem.jsonData, ItemData::class.java)
                    // We must fetch the latest version from server to resolve conflicts
                    val serverItemRes = apiService.getItems(userId, apiKey, itemKey = localItem.key)
                    if (serverItemRes.isSuccessful) {
                        val serverItems = serverItemRes.body()
                        val serverItem = serverItems?.firstOrNull()
                        
                        var mergedData = itemData
                        if (serverItem != null && serverItem.version > localItem.version) {
                            // Conflict detected. Merge changes.
                            // We keep the server's base data but apply our local modifications
                            // Since this app mainly creates/modifies annotations, we favor our local annotation data
                            mergedData = serverItem.data.copy(
                                annotationText = itemData.annotationText ?: serverItem.data.annotationText,
                                annotationComment = itemData.annotationComment ?: serverItem.data.annotationComment,
                                annotationColor = itemData.annotationColor ?: serverItem.data.annotationColor,
                                annotationPosition = itemData.annotationPosition ?: serverItem.data.annotationPosition,
                                tags = itemData.tags ?: serverItem.data.tags
                            )
                        }
                        
                        // Push to server
                        val updateRes = apiService.updateItem(userId, localItem.key, apiKey, itemData = mergedData)
                        if (updateRes.isSuccessful) {
                            // Remove dirty flag
                            database.zoteroDao().insertItem(localItem.copy(isDirty = false))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ZoteroSync", "Failed to sync item ${localItem.key}: ${e.message}")
                }
            }

            // 2. Fetch Collections
            val collResponse = apiService.getCollections(userId, apiKey)
            if (collResponse.isSuccessful) {
                val collections = collResponse.body() ?: emptyList()
                val entities = collections.map {
                    ZoteroCollectionEntity(
                        key = it.key,
                        version = it.version,
                        name = it.data.name,
                        parentCollection = null, // simplified
                        jsonData = gson.toJson(it.data)
                    )
                }
                database.zoteroDao().insertCollections(entities)
            }

            // 3. Fetch Items, paginated (the API caps each page at 100).
            // Annotations are excluded here: the reader loads them per-document.
            var start = 0
            while (true) {
                val itemsResponse = apiService.getItems(userId, apiKey, itemType = "-annotation", start = start)
                if (!itemsResponse.isSuccessful) break
                val items = itemsResponse.body() ?: emptyList()
                val entities = items.map {
                    ZoteroItemEntity(
                        key = it.key,
                        version = it.version,
                        itemType = it.data.itemType,
                        parentItem = it.data.parentItem,
                        jsonData = gson.toJson(it.data),
                        isDirty = false,
                        isDeleted = false
                    )
                }
                database.zoteroDao().insertItems(entities)
                if (items.size < 100 || start >= 10000) break
                start += 100
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ZoteroSync", "Sync failed: ${e.message}")
        }
    }
}
