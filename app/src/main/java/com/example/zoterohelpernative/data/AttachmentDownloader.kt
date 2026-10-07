package com.example.zoterohelpernative.data

import com.example.zoterohelpernative.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed class DownloadOutcome {
    data class Success(val pdf: File) : DownloadOutcome()
    data class Failure(val reason: String) : DownloadOutcome()
}

/**
 * Single entry point for getting an attachment's PDF on disk, shared by the
 * reader, the background prefetch and the cache-freshness check.
 *
 * - One lock per attachment: the prefetch and the reader used to download the
 *   same `KEY.zip` concurrently, and the prefetch deleted it while the reader
 *   was still writing/extracting it.
 * - Writes go to unique temp files and are moved into place only when complete,
 *   so a half-written file never looks like a valid cache entry.
 * - Transient failures (timeouts, 408/429/5xx) are retried with backoff.
 * - WebDAV first; if the file isn't there (or WebDAV isn't configured) it falls
 *   back to Zotero's own file storage via the API.
 * - Failures carry the real cause instead of a generic "no network".
 */
class AttachmentDownloader(private val settingsRepository: SettingsRepository) {

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        private fun lockFor(key: String) = locks.getOrPut(key) { Mutex() }

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
        }

        fun extractDirFor(cacheDir: File, attachmentKey: String) = File(cacheDir, "extracted_$attachmentKey")

        fun cachedPdf(cacheDir: File, attachmentKey: String): File? =
            extractDirFor(cacheDir, attachmentKey).listFiles()
                ?.firstOrNull { it.isFile && it.extension.equals("pdf", ignoreCase = true) && it.length() > 0 }
    }

    private class HttpFailure(val code: Int, val source: String) : IOException("$source HTTP $code")

    /**
     * Returns the cached PDF, downloading it if missing. With [forceRefresh] the
     * existing copy is replaced (used when the PDF changed on Zotero); if the
     * refresh fails the old copy is kept.
     */
    suspend fun ensurePdf(
        attachmentKey: String,
        cacheDir: File,
        forceRefresh: Boolean = false
    ): DownloadOutcome = lockFor(attachmentKey).withLock {
        withContext(Dispatchers.IO) {
            // Another caller may have finished the download while we waited
            if (!forceRefresh) cachedPdf(cacheDir, attachmentKey)?.let { return@withContext DownloadOutcome.Success(it) }

            val tempFile = File(cacheDir, "$attachmentKey.${UUID.randomUUID()}.part")
            try {
                val sourceError = downloadToFile(attachmentKey, tempFile)
                    ?: return@withContext installDownloaded(tempFile, attachmentKey, cacheDir)
                DownloadOutcome.Failure(sourceError)
            } finally {
                tempFile.delete()
            }
        }
    }

    /** Tries WebDAV, then Zotero storage. Returns null on success, else the reason. */
    private suspend fun downloadToFile(attachmentKey: String, target: File): String? {
        val webDavUrl = settingsRepository.webdavUrl.firstOrNull()?.trim().orEmpty()
        val webDavUser = settingsRepository.webdavUser.firstOrNull()
        val webDavPass = settingsRepository.webdavPass.firstOrNull()
        val apiKey = settingsRepository.zoteroApiKey.firstOrNull()
        val userId = settingsRepository.zoteroUserId.firstOrNull()

        val errors = mutableListOf<String>()

        if (webDavUrl.isNotEmpty()) {
            val auth = if (!webDavUser.isNullOrEmpty() && !webDavPass.isNullOrEmpty()) {
                Credentials.basic(webDavUser, webDavPass)
            } else null

            for (url in webDavCandidates(webDavUrl, attachmentKey)) {
                try {
                    fetchWithRetry(url, "WebDAV", target) { builder ->
                        if (auth != null) builder.header("Authorization", auth)
                    }
                    return null
                } catch (e: HttpFailure) {
                    if (e.code == 404) continue // try the next URL layout / Zotero storage
                    errors += describe(e)
                    break
                } catch (e: Exception) {
                    errors += describe(e)
                    break
                }
            }
            if (errors.isEmpty()) errors += "file non trovato su WebDAV"
        }

        if (!apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()) {
            try {
                fetchWithRetry(
                    "https://api.zotero.org/users/$userId/items/$attachmentKey/file",
                    "Zotero",
                    target
                ) { builder ->
                    builder.header("Zotero-API-Key", apiKey).header("Zotero-API-Version", "3")
                }
                return null
            } catch (e: Exception) {
                errors += if (e is HttpFailure && e.code == 404) "file non presente nello storage Zotero" else describe(e)
            }
        }

        return errors.joinToString("; ").ifEmpty { "nessuna sorgente configurata (WebDAV o API Zotero)" }
    }

    /**
     * Zotero appends "/zotero/" to the WebDAV base itself; users often paste the
     * URL already ending in "/zotero". Try the configured layout first, then the
     * other one.
     */
    private fun webDavCandidates(base: String, key: String): List<String> {
        val trimmed = base.trimEnd('/')
        val withSegment = "$trimmed/zotero/$key.zip"
        val asIs = "$trimmed/$key.zip"
        return if (trimmed.endsWith("/zotero", ignoreCase = true)) listOf(asIs, withSegment)
        else listOf(withSegment, asIs)
    }

    private suspend fun fetchWithRetry(
        url: String,
        source: String,
        target: File,
        decorate: (Request.Builder) -> Unit
    ) {
        val backoffMs = longArrayOf(0, 1_000, 3_000)
        var last: Exception? = null
        for (wait in backoffMs) {
            if (wait > 0) delay(wait)
            try {
                val builder = Request.Builder().url(url)
                decorate(builder)
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw HttpFailure(response.code(), source)
                    val body = response.body() ?: throw IOException("$source: risposta vuota")
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                if (target.length() == 0L) throw IOException("$source: file vuoto")
                return
            } catch (e: HttpFailure) {
                last = e
                val retryable = e.code == 408 || e.code == 429 || e.code >= 500
                if (!retryable) throw e
            } catch (e: UnknownHostException) {
                throw e // no point retrying a DNS failure immediately
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("$source: download fallito")
    }

    /** WebDAV serves a ZIP, Zotero storage the raw PDF: detect by magic bytes. */
    private fun installDownloaded(downloaded: File, attachmentKey: String, cacheDir: File): DownloadOutcome {
        val header = ByteArray(4)
        val read = downloaded.inputStream().use { it.read(header) }
        val isZip = read >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        val isPdf = read >= 4 && String(header, Charsets.US_ASCII) == "%PDF"

        val finalDir = extractDirFor(cacheDir, attachmentKey)
        val stagingDir = File(cacheDir, "staging_${attachmentKey}_${UUID.randomUUID()}")
        try {
            stagingDir.mkdirs()
            val pdf = when {
                isZip -> ZipUtils.extractPdfFromZip(downloaded, stagingDir)
                    ?: return DownloadOutcome.Failure("l'archivio scaricato non contiene un PDF")
                isPdf -> File(stagingDir, "$attachmentKey.pdf").also { downloaded.copyTo(it, overwrite = true) }
                else -> return DownloadOutcome.Failure("il file scaricato non è un PDF né uno ZIP")
            }
            // Swap the complete copy into place
            finalDir.deleteRecursively()
            if (!stagingDir.renameTo(finalDir)) {
                stagingDir.copyRecursively(finalDir, overwrite = true)
            }
            val installed = File(finalDir, pdf.relativeTo(stagingDir).path)
            return DownloadOutcome.Success(if (installed.exists()) installed else cachedPdf(cacheDir, attachmentKey)
                ?: return DownloadOutcome.Failure("installazione del PDF in cache fallita"))
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is HttpFailure -> when (e.code) {
            401, 403 -> "${e.source}: accesso negato (HTTP ${e.code}) — controlla le credenziali"
            404 -> "${e.source}: file non trovato"
            429 -> "${e.source}: troppe richieste, riprova tra poco"
            in 500..599 -> "${e.source}: errore del server (HTTP ${e.code})"
            else -> "${e.source}: HTTP ${e.code}"
        }
        is UnknownHostException -> "server non raggiungibile (${e.message ?: "DNS"}) — controlla l'indirizzo o la connessione"
        is SocketTimeoutException -> "timeout durante il download"
        is SSLException -> "errore di sicurezza della connessione (${e.message})"
        else -> e.message ?: e.javaClass.simpleName
    }
}
