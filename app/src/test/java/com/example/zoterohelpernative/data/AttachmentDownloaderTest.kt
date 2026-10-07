package com.example.zoterohelpernative.data

import com.example.zoterohelpernative.data.AttachmentDownloader.Companion.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentDownloaderTest {

    @Test
    fun plainBaseUrlGetsTheZoteroSegmentFirst() {
        assertEquals(
            listOf("https://dav.example.com/dav/zotero/ABCD1234.zip", "https://dav.example.com/dav/ABCD1234.zip"),
            AttachmentDownloader.webDavCandidates("https://dav.example.com/dav", "ABCD1234")
        )
    }

    @Test
    fun baseUrlAlreadyEndingInZoteroIsNotDoubled() {
        // This produced .../zotero/zotero/KEY.zip and a 404 before the fix
        assertEquals(
            "https://dav.example.com/dav/zotero/ABCD1234.zip",
            AttachmentDownloader.webDavCandidates("https://dav.example.com/dav/zotero/", "ABCD1234").first()
        )
    }

    @Test
    fun trailingSlashesAndSpacesAreTolerated() {
        assertEquals(
            "https://dav.example.com/dav/zotero/K.zip",
            AttachmentDownloader.webDavCandidates("  https://dav.example.com/dav///  ", "K").first()
        )
    }

    @Test
    fun formatIsDetectedFromMagicBytes() {
        assertEquals(Format.ZIP, AttachmentDownloader.detectFormat(byteArrayOf(0x50, 0x4B, 0x03, 0x04), 4))
        assertEquals(Format.PDF, AttachmentDownloader.detectFormat("%PDF".toByteArray(), 4))
        assertEquals(Format.UNKNOWN, AttachmentDownloader.detectFormat("<htm".toByteArray(), 4))
        // An HTML error page or an empty body must not be treated as a document
        assertEquals(Format.UNKNOWN, AttachmentDownloader.detectFormat(ByteArray(4), 0))
    }
}
