package com.example.zoterohelpernative.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.Response

interface ZoteroApiService {

    @GET("users/{userId}/collections")
    suspend fun getCollections(
        @Path("userId") userId: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Query("limit") limit: Int = 100
    ): Response<List<ZoteroCollection>>

    @GET("users/{userId}/items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Query("itemType") itemType: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("itemKey") itemKey: String? = null
    ): Response<List<ZoteroItem>>

    @GET("users/{userId}/items/{itemKey}/children")
    suspend fun getItemChildren(
        @Path("userId") userId: String,
        @Path("itemKey") itemKey: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Query("limit") limit: Int = 100
    ): Response<List<ZoteroItem>>

    @GET("users/{userId}/tags")
    suspend fun getTags(
        @Path("userId") userId: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Query("limit") limit: Int = 1000
    ): Response<List<ZoteroTag>>

    @POST("users/{userId}/items")
    suspend fun createItems(
        @Path("userId") userId: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Body items: List<ItemData>
    ): Response<Any> // Returns a SuccessfulItemCreationResponse

    @PATCH("users/{userId}/items/{itemKey}")
    suspend fun updateItem(
        @Path("userId") userId: String,
        @Path("itemKey") itemKey: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Body itemData: ItemData
    ): Response<Any>

    @DELETE("users/{userId}/items/{itemKey}")
    suspend fun deleteItem(
        @Path("userId") userId: String,
        @Path("itemKey") itemKey: String,
        @Header("Zotero-API-Key") apiKey: String,
        @Header("Zotero-API-Version") apiVersion: String = "3",
        @Header("If-Unmodified-Since-Version") version: Long
    ): Response<Unit>
}
