package com.example.zoterohelpernative.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val file = tmp.newFile("attachment.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsThePdfFromAZoteroWebDavArchive() {
        val pdfBytes = "%PDF-1.7 test".toByteArray()
        val zip = zipOf("paper.pdf" to pdfBytes, ".zotero-ft-cache" to "text".toByteArray())
        val dest = tmp.newFolder("out")

        val pdf = ZipUtils.extractPdfFromZip(zip, dest)

        assertNotNull(pdf)
        assertEquals("paper.pdf", pdf!!.name)
        assertTrue(pdf.readBytes().contentEquals(pdfBytes))
    }

    @Test
    fun returnsNullWhenTheArchiveHasNoPdf() {
        val zip = zipOf("notes.txt" to "hello".toByteArray())
        assertNull(ZipUtils.extractPdfFromZip(zip, tmp.newFolder("out")))
    }

    @Test
    fun refusesEntriesThatEscapeTheDestination() {
        // Zip slip: a downloaded archive must never write outside its folder
        val zip = zipOf("../../evil.pdf" to "%PDF".toByteArray())
        val dest = tmp.newFolder("out")
        try {
            ZipUtils.extractPdfFromZip(zip, dest)
            fail("expected a SecurityException")
        } catch (expected: SecurityException) {
            assertTrue(!File(dest.parentFile.parentFile, "evil.pdf").exists())
        }
    }
}
