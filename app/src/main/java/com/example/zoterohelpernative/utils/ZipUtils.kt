package com.example.zoterohelpernative.utils

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ZipUtils {
    /**
     * Unzips a file and looks for a .pdf file inside.
     * Returns the File object pointing to the extracted PDF if found, or null otherwise.
     */
    fun extractPdfFromZip(zipFile: File, destDir: File): File? {
        var extractedPdf: File? = null
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var zipEntry = zis.nextEntry
            val buffer = ByteArray(4096)

            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)
                
                // Prevent Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                    throw SecurityException("Invalid zip entry: ${zipEntry.name}")
                }

                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    File(newFile.parent).mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        BufferedOutputStream(fos, buffer.size).use { bos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                        }
                    }
                    if (newFile.name.endsWith(".pdf", ignoreCase = true)) {
                        extractedPdf = newFile
                    }
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
        return extractedPdf
    }
}
