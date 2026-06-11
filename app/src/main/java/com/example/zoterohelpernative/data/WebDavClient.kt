package com.example.zoterohelpernative.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebDavClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAttachment(
        url: String,
        user: String?,
        pass: String?,
        attachmentKey: String,
        cacheDir: File
    ): File? = withContext(Dispatchers.IO) {
        // Construct the expected Zotero WebDAV URL
        // Zotero WebDAV URLs typically point to a .zip file like "zotero/{attachmentKey}.zip"
        // But some implementations just store the PDF directly if using custom WebDAV sync plugins.
        // Assuming standard Zotero WebDAV structure for this app (zotero/{key}.zip)
        
        val targetUrl = if (url.endsWith("/")) "${url}zotero/$attachmentKey.zip" else "$url/zotero/$attachmentKey.zip"
        
        val requestBuilder = Request.Builder().url(targetUrl)
        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            val credential = Credentials.basic(user, pass)
            requestBuilder.header("Authorization", credential)
        }

        val request = requestBuilder.build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // If the file is not a zip, maybe it's stored directly as .pdf
                // Fallback logic could go here if Zotero stores it differently.
                throw IOException("Unexpected code $response")
            }

            response.body()?.let { responseBody ->
                val targetFile = File(cacheDir, "$attachmentKey.zip")
                val fos = FileOutputStream(targetFile)
                fos.use { output ->
                    responseBody.byteStream().copyTo(output)
                }
                
                // Usually we'd need to unzip the .zip to get the .pdf inside.
                // Assuming `UnzipUtils.unzip(targetFile, cacheDir)` here.
                // Returning the zipped file for now.
                return@withContext targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
        return@withContext null
    }
}
