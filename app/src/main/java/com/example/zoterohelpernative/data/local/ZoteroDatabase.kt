package com.example.zoterohelpernative.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoteroDao {
    @Query("SELECT * FROM zotero_items WHERE isDeleted = 0")
    fun getAllItemsFlow(): Flow<List<ZoteroItemEntity>>

    @Query("SELECT * FROM zotero_items WHERE isDeleted = 0")
    suspend fun getAllItems(): List<ZoteroItemEntity>

    @Query("SELECT * FROM zotero_collections")
    fun getAllCollectionsFlow(): Flow<List<ZoteroCollectionEntity>>

    @Query("SELECT * FROM zotero_collections")
    suspend fun getAllCollections(): List<ZoteroCollectionEntity>

    @Query("SELECT * FROM zotero_items WHERE isDirty = 1")
    suspend fun getDirtyItems(): List<ZoteroItemEntity>

    @Query("SELECT * FROM zotero_items WHERE parentItem = :parentKey AND itemType = 'annotation' AND isDeleted = 0")
    suspend fun getAnnotationsForParent(parentKey: String): List<ZoteroItemEntity>

    @Query("SELECT * FROM zotero_items WHERE `key` = :key LIMIT 1")
    suspend fun getItem(key: String): ZoteroItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ZoteroItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ZoteroItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<ZoteroCollectionEntity>)

    @Query("DELETE FROM zotero_items")
    suspend fun clearItems()

    @Query("DELETE FROM zotero_collections")
    suspend fun clearCollections()
    
    @Query("DELETE FROM zotero_items WHERE `key` = :key")
    suspend fun deleteItem(key: String)
}

@Database(entities = [ZoteroItemEntity::class, ZoteroCollectionEntity::class], version = 1, exportSchema = false)
abstract class ZoteroDatabase : RoomDatabase() {
    abstract fun zoteroDao(): ZoteroDao
}
