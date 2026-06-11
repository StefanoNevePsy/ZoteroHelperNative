package com.example.zoterohelpernative.data

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

data class ZoteroItem(
    val key: String,
    val version: Long,
    val library: Library,
    val links: Links,
    val meta: Meta?,
    val data: ItemData
)

data class Library(
    val type: String,
    val id: Long,
    val name: String,
    val links: Links
)

data class Links(
    val self: Link?,
    val alternate: Link?,
    val up: Link?
)

data class Link(
    val href: String,
    val type: String
)

data class Meta(
    val creatorSummary: String?,
    val parsedDate: String?,
    val numChildren: Int?
)

data class ItemData(
    val key: String,
    val version: Long = 0,
    val itemType: String,
    val parentItem: String? = null,
    val title: String? = null,
    val date: String? = null,
    val url: String? = null,
    val abstractNote: String? = null,
    val tags: List<ZoteroTag>? = null,
    val collections: List<String>? = null,
    val creators: List<ZoteroCreator>? = null,
    // For attachments
    val contentType: String? = null,
    val filename: String? = null,
    val md5: String? = null,
    val mtime: Long? = null,
    // For annotations
    val annotationType: String? = null,
    val annotationText: String? = null,
    val annotationComment: String? = null,
    val annotationColor: String? = null,
    val annotationPageLabel: String? = null,
    val annotationSortIndex: String? = null,
    val annotationPosition: String? = null
)

data class ZoteroTag(
    val tag: String,
    val type: Int? = null
)

data class ZoteroCollection(
    val key: String,
    val version: Long,
    val data: CollectionData
)

data class CollectionData(
    val key: String,
    val version: Long,
    val name: String,
    val parentCollection: JsonElement?
)

data class ZoteroCreator(
    val creatorType: String?,
    val firstName: String?,
    val lastName: String?,
    val name: String?
)
