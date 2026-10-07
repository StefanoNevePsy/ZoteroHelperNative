package com.example.zoterohelpernative.ui

import com.example.zoterohelpernative.annotations.AnnotationOps
import com.example.zoterohelpernative.data.ItemData
import com.example.zoterohelpernative.data.SettingsRepository
import com.example.zoterohelpernative.data.ZoteroApiService
import com.example.zoterohelpernative.data.sync.ZoteroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Writes the reader's annotation changes to Zotero: creations, updates and
 * deletions, serialized so versions stay consistent, with the offline queue as
 * fallback and transient messages for what couldn't be saved.
 * Extracted from ReaderViewModel; the code is unchanged.
 */
class AnnotationSync(
    private val state: MutableStateFlow<ReaderState>,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val zoteroRepository: ZoteroRepository?,
    private val apiService: ZoteroApiService
) {
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private var syncErrorJob: kotlinx.coroutines.Job? = null

    fun setSyncError(message: String) {
        state.update { it.copy(syncError = message) }
        syncErrorJob?.cancel()
        syncErrorJob = scope.launch {
            kotlinx.coroutines.delay(8000)
            state.update { it.copy(syncError = null) }
        }
    }

    private fun updateLocalAnnotation(key: String, transform: (ItemData) -> ItemData) {
        state.update { s ->
            s.copy(annotations = s.annotations.map { if (it.key == key) transform(it) else it })
        }
    }

    // "annotation" items reject fields like title/collections: strip them before writing.
    private fun sanitizeForWrite(itemData: ItemData): ItemData =
        if (itemData.itemType == "annotation") itemData.copy(title = null, collections = null) else itemData

    // Queue the annotation locally so the periodic sync retries it later
    private suspend fun queueOffline(itemData: ItemData, reason: String) {
        try {
            zoteroRepository?.saveLocalAnnotation(itemData, dirty = true)
            setSyncError("$reason Annotazione salvata sul dispositivo: verrà sincronizzata.")
        } catch (e: Exception) {
            e.printStackTrace()
            setSyncError("$reason Salvataggio locale fallito.")
        }
    }

    // Validation rejections will fail identically on every retry: queueing them
    // would poison the offline queue. 401/403 (credentials), 408/429 (transient)
    // and 412 (version conflict, resolved by the periodic sync) are retryable.
    private fun isPermanentRejection(code: Int?): Boolean =
        code != null && code in 400..499 &&
            code != 401 && code != 403 && code != 408 && code != 412 && code != 429

    private suspend fun dropInvalidAnnotation(itemData: ItemData, reason: String) {
        try {
            zoteroRepository?.removeLocalItem(itemData.key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setSyncError("Annotazione rifiutata dal server ($reason): non verrà ritentata.")
    }

    fun syncItemToZotero(itemData: ItemData) {
        scope.launch {
            syncMutex.withLock {
                // Re-read from state: a previous sync may have assigned key/version
                val current = state.value.annotations.find { it.key == itemData.key } ?: itemData
                var payload = sanitizeForWrite(current)

                // The periodic sync may already have created this annotation while the
                // reader still holds a version-0 copy: adopt the known version instead
                // of creating a duplicate on the server.
                if (payload.version == 0L) {
                    zoteroRepository?.getLocalVersion(payload.key)?.takeIf { it > 0 }?.let { known ->
                        payload = payload.copy(version = known)
                        updateLocalAnnotation(payload.key) { it.copy(version = known) }
                    }
                }

                try {
                    val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                    val userId = settingsRepository.zoteroUserId.firstOrNull()
                    if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) {
                        queueOffline(payload, "Credenziali Zotero mancanti.")
                        return@withLock
                    }

                    if (payload.version == 0L) {
                        val response = apiService.createItems(userId, apiKey, items = listOf(payload))
                        val body = response.body()
                        val created = body?.successful?.values?.firstOrNull()
                        when {
                            response.isSuccessful && created != null -> {
                                updateLocalAnnotation(current.key) { it.copy(key = created.key, version = created.version) }
                                zoteroRepository?.saveLocalAnnotation(payload.copy(key = created.key, version = created.version), dirty = false)
                            }
                            else -> {
                                val itemError = body?.failed?.values?.firstOrNull()
                                val reason = itemError?.message ?: "HTTP ${response.code()}"
                                val permanent = (response.isSuccessful && isPermanentRejection(itemError?.code))
                                    || isPermanentRejection(response.code())
                                if (permanent) {
                                    dropInvalidAnnotation(payload, reason)
                                } else {
                                    queueOffline(payload, "Salvataggio fallito ($reason).")
                                }
                            }
                        }
                    } else {
                        val response = apiService.updateItem(userId, payload.key, apiKey, itemData = payload)
                        if (response.isSuccessful) {
                            val newVersion = response.headers()["Last-Modified-Version"]?.toLongOrNull()
                            if (newVersion != null) {
                                updateLocalAnnotation(payload.key) { it.copy(version = newVersion) }
                            }
                            zoteroRepository?.saveLocalAnnotation(payload.copy(version = newVersion ?: payload.version), dirty = false)
                        } else if (response.code() == 404) {
                            // Deleted remotely: recreate instead of updating forever
                            updateLocalAnnotation(payload.key) { it.copy(version = 0) }
                            queueOffline(payload.copy(version = 0), "Annotazione assente sul server.")
                        } else if (isPermanentRejection(response.code())) {
                            dropInvalidAnnotation(payload, "HTTP ${response.code()}")
                        } else {
                            queueOffline(payload, "Aggiornamento fallito (HTTP ${response.code()}).")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    queueOffline(payload, "Rete assente.")
                }
            }
        }
    }

    fun deleteItemFromZotero(annotation: ItemData) {
        scope.launch {
            syncMutex.withLock {
                try {
                    val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
                    val userId = settingsRepository.zoteroUserId.firstOrNull()
                    if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) {
                        zoteroRepository?.markDeletedLocally(annotation)
                        setSyncError("Credenziali mancanti: eliminazione in coda di sincronizzazione.")
                        return@withLock
                    }

                    val response = apiService.deleteItem(userId, annotation.key, apiKey, version = annotation.version)
                    // 404 = already gone on server, treat as success
                    if (response.isSuccessful || response.code() == 404) {
                        zoteroRepository?.removeLocalItem(annotation.key)
                    } else {
                        zoteroRepository?.markDeletedLocally(annotation)
                        setSyncError("Eliminazione fallita (HTTP ${response.code()}): in coda di sincronizzazione.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    zoteroRepository?.markDeletedLocally(annotation)
                    setSyncError("Rete assente: eliminazione in coda di sincronizzazione.")
                }
            }
        }
    }

    /** Creates a Zotero child note (HTML) under [parentItemKey]; used by the AI chat. */
    suspend fun createChildNote(parentItemKey: String?, args: Map<String, Any?>?): Map<String, Any?> {
        val content = (args?.get("content_html") as? String)?.trim()
        if (content.isNullOrBlank()) {
            return mapOf("success" to false, "error" to "Parametro 'content_html' mancante.")
        }
        val parentKey = parentItemKey
            ?: return mapOf("success" to false, "error" to "Item Zotero del documento non disponibile (offline?).")

        val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
        val userId = settingsRepository.zoteroUserId.firstOrNull()
        if (apiKey.isNullOrEmpty() || userId.isNullOrEmpty()) {
            return mapOf("success" to false, "error" to "Credenziali Zotero mancanti.")
        }

        val noteItem = ItemData(
            key = AnnotationOps.generateZoteroKey(),
            version = 0,
            itemType = "note",
            parentItem = parentKey,
            note = content,
            tags = emptyList()
        )
        return try {
            val response = apiService.createItems(userId, apiKey, items = listOf(noteItem))
            val created = response.body()?.successful?.values?.firstOrNull()
            if (response.isSuccessful && created != null) {
                mapOf("success" to true)
            } else {
                val reason = response.body()?.failed?.values?.firstOrNull()?.message ?: "HTTP ${response.code()}"
                mapOf("success" to false, "error" to reason)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.localizedMessage ?: "errore di rete"))
        }
    }
}
