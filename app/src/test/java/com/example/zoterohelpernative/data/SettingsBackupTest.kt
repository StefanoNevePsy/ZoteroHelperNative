package com.example.zoterohelpernative.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {

    @Test
    fun roundTripsEveryTypeExactly() {
        val values = mapOf<String, Any>(
            "zotero_api_key" to "abc",
            "pen_highlight_only" to true,
            "some_int" to 7,
            "some_long" to 9_000_000_000L,
            "some_float" to 1.5f,
            "some_set" to setOf("a", "b"),
            "custom_palettes" to "{\"x\":{\"id\":\"x\"}}"
        )
        assertEquals(values, SettingsBackup.decode(SettingsBackup.encode(values)))
    }

    @Test
    fun deviceStateIsNotBackedUp() {
        val json = SettingsBackup.encode(mapOf("app_theme" to "notte", "last_page_map" to "{}", "pdf_md5_map" to "{}"))
        assertFalse(json.contains("last_page_map"))
        assertEquals(mapOf("app_theme" to "notte"), SettingsBackup.decode(json))
    }

    @Test
    fun rejectsFilesThatAreNotOurBackups() {
        for (bad in listOf("not json", "{\"app\":\"Other\",\"version\":1,\"values\":{}}", "{\"app\":\"ZoteroHelperNative\",\"version\":99,\"values\":{}}")) {
            try {
                SettingsBackup.decode(bad)
                fail("accepted: $bad")
            } catch (expected: SettingsBackup.InvalidBackup) {
            }
        }
    }

    @Test
    fun aMalformedEntryDoesNotSpoilTheRest() {
        val json = """{"app":"ZoteroHelperNative","version":1,"values":{
            "ok":{"type":"string","value":"x"},
            "bad":{"type":"int","value":"not a number"},
            "unknown":{"type":"blob","value":1}}}"""
        assertEquals(mapOf("ok" to "x"), SettingsBackup.decode(json))
    }
}
