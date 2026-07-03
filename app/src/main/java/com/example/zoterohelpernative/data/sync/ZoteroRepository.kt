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

    // ---- Local annotation cache / offline queue ----

    suspend fun saveLocalAnnotation(itemData: ItemData, dirty: Boolean, deleted: Boolean = false) = withContext(Dispatchers.IO) {
        database.zoteroDao().insertItem(
            ZoteroItemEntity(
                key = itemData.key,
                version = itemData.version,
                itemType = itemData.itemType,
                parentItem = itemData.parentItem,
                jsonData = gson.toJson(itemData),
                isDirty = dirty,
                isDeleted = deleted
            )
        )
    }

    suspend fun saveLocalAnnotations(items: List<ItemData>, dirty: Boolean) = withContext(Dispatchers.IO) {
        database.zoteroDao().insertItems(items.map {
            ZoteroItemEntity(
                key = it.key,
                version = it.version,
                itemType = it.itemType,
                parentItem = it.parentItem,
                jsonData = gson.toJson(it),
                isDirty = dirty,
                isDeleted = false
            )
        })
    }

    suspend fun getCachedAnnotations(parentKey: String): List<ItemData> = withContext(Dispatchers.IO) {
        database.zoteroDao().getAnnotationsForParent(parentKey).mapNotNull { entity ->
            try {
                gson.fromJson(entity.jsonData, ItemData::class.java)
            } catch (e: Exception) { null }
        }
    }

    suspend fun getPendingAnnotations(parentKey: String): List<ItemData> = withContext(Dispatchers.IO) {
        database.zoteroDao().getAnnotationsForParent(parentKey)
            .filter { it.isDirty }
            .mapNotNull { entity ->
                try {
                    gson.fromJson(entity.jsonData, ItemData::class.java)
                } catch (e: Exception) { null }
            }
    }

    suspend fun removeLocalItem(key: String) = withContext(Dispatchers.IO) {
        database.zoteroDao().deleteItem(key)
    }

    // Keys of annotations deleted offline (deletion not yet pushed): the reader
    // must hide these even if the server still returns them
    suspend fun getPendingDeletionKeys(parentKey: String): Set<String> = withContext(Dispatchers.IO) {
        database.zoteroDao().getDeletedAnnotationKeys(parentKey).toSet()
    }

    // Latest version of an item known locally (the periodic sync may have pushed a
    // creation while the reader still holds a version-0 copy)
    suspend fun getLocalVersion(key: String): Long? = withContext(Dispatchers.IO) {
        database.zoteroDao().getItem(key)?.takeIf { !it.isDirty || it.version > 0 }?.version
    }

    suspend fun markDeletedLocally(itemData: ItemData) = withContext(Dispatchers.IO) {
        saveLocalAnnotation(itemData, dirty = true, deleted = true)
    }

    val pendingSyncCount: Flow<Int> = database.zoteroDao().getDirtyCountFlow()

    private fun isPermanentRejection(code: Int?): Boolean =
        code != null && code in 400..499 &&
            code != 401 && code != 403 && code != 408 && code != 412 && code != 429

    suspend fun sync() = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
        val userId = settingsRepository.zoteroUserId.firstOrNull()
        if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) return@withContext

        try {
            // 1. Push local dirty items (offline queue: creations, edits, deletions)
            val dirtyItems = database.zoteroDao().getDirtyItems()
            for (localItem in dirtyItems) {
                try {
                    val itemData = gson.fromJson(localItem.jsonData, ItemData::class.java)

                    if (localItem.isDeleted) {
                        val res = apiService.deleteItem(userId, localItem.key, apiKey, version = localItem.version)
                        if (res.isSuccessful || res.code() == 404) {
                            database.zoteroDao().deleteItem(localItem.key)
                        }
                        continue
                    }

                    if (localItem.version == 0L) {
                        // Never reached the server: create it
                        val res = apiService.createItems(userId, apiKey, items = listOf(itemData))
                        val created = res.body()?.successful?.values?.firstOrNull()
                        if (res.isSuccessful && created != null) {
                            database.zoteroDao().insertItem(
                                localItem.copy(
                                    version = created.version,
                                    jsonData = gson.toJson(itemData.copy(version = created.version)),
                                    isDirty = false
                                )
                            )
                        } else {
                            // A validation rejection will fail the same way forever: drop it
                            val errorCode = res.body()?.failed?.values?.firstOrNull()?.code
                            if (isPermanentRejection(errorCode) || isPermanentRejection(res.code())) {
                                Log.e("ZoteroSync", "Dropping invalid queued item ${localItem.key} (code $errorCode)")
                                database.zoteroDao().deleteItem(localItem.key)
                            }
                        }
                        continue
                    }

                    // Existing item: fetch the latest server version to resolve conflicts
                    val serverItemRes = apiService.getItems(userId, apiKey, itemKey = localItem.key)
                    if (serverItemRes.isSuccessful) {
                        val serverItem = serverItemRes.body()?.firstOrNull()

                        var mergedData = itemData
                        if (serverItem != null && serverItem.version > localItem.version) {
                            // Conflict detected. Merge changes.
                            // We keep the server's base data but apply our local modifications
                            // Since this app mainly creates/modifies annotations, we favor our local annotation data
                            mergedData = serverItem.data.copy(
                                version = serverItem.version,
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
                            val newVersion = updateRes.headers()["Last-Modified-Version"]?.toLongOrNull()
                                ?: mergedData.version
                            database.zoteroDao().insertItem(
                                localItem.copy(
                                    version = newVersion,
                                    jsonData = gson.toJson(mergedData.copy(version = newVersion)),
                                    isDirty = false
                                )
                            )
                        } else if (updateRes.code() == 404) {
                            // Deleted remotely: requeue as a creation
                            database.zoteroDao().insertItem(
                                localItem.copy(
                                    version = 0,
                                    jsonData = gson.toJson(itemData.copy(version = 0))
                                )
                            )
                        } else if (isPermanentRejection(updateRes.code())) {
                            Log.e("ZoteroSync", "Dropping invalid queued update ${localItem.key} (HTTP ${updateRes.code()})")
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
