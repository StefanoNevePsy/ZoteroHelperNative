package com.example.zoterohelpernative.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "zotero_items")
data class ZoteroItemEntity(
    @PrimaryKey val key: String,
    val version: Long,
    val itemType: String,
    val parentItem: String?,
    @ColumnInfo(name = "json_data") val jsonData: String,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(tableName = "zotero_collections")
data class ZoteroCollectionEntity(
    @PrimaryKey val key: String,
    val version: Long,
    val name: String,
    val parentCollection: String?,
    @ColumnInfo(name = "json_data") val jsonData: String
)
