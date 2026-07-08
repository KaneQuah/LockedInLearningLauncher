package com.lockedinlearning.launcher3.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lockedinlearning.launcher3.LauncherSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    /** Every row regardless of container — used to resolve folder-contents rows (container = a folder's own id),
     *  which live outside both CONTAINER_DESKTOP and CONTAINER_HOTSEAT. */
    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<FavoriteItemEntity>>

    @Query("SELECT * FROM favorites WHERE container = ${LauncherSettings.CONTAINER_DESKTOP}")
    fun observeDesktop(): Flow<List<FavoriteItemEntity>>

    @Query("SELECT * FROM favorites WHERE container = ${LauncherSettings.CONTAINER_HOTSEAT} ORDER BY cellX")
    fun observeHotseat(): Flow<List<FavoriteItemEntity>>

    @Query("SELECT * FROM favorites WHERE container = :folderItemId ORDER BY cellY, cellX")
    fun observeFolderContents(folderItemId: Long): Flow<List<FavoriteItemEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteItemEntity>

    @Query("SELECT * FROM favorites WHERE container = ${LauncherSettings.CONTAINER_DESKTOP}")
    suspend fun getDesktopOnce(): List<FavoriteItemEntity>

    @Query("SELECT * FROM favorites WHERE container = ${LauncherSettings.CONTAINER_HOTSEAT} ORDER BY cellX")
    suspend fun getHotseatOnce(): List<FavoriteItemEntity>

    @Insert
    suspend fun insert(item: FavoriteItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<FavoriteItemEntity>): List<Long>

    @Update
    suspend fun update(item: FavoriteItemEntity)

    @Delete
    suspend fun delete(item: FavoriteItemEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorites WHERE container = :folderItemId")
    suspend fun deleteFolderContents(folderItemId: Long)

    /** Prunes rows for apps that are no longer installed (mirrors HomeViewModel's old refreshApps() cleanup). */
    @Query("DELETE FROM favorites WHERE packageName IS NOT NULL AND packageName NOT IN (:installedPackages) AND itemType = ${LauncherSettings.ITEM_TYPE_APPLICATION}")
    suspend fun pruneUninstalled(installedPackages: List<String>)
}
