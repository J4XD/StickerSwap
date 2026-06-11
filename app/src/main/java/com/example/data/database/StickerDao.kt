package com.example.data.database

import androidx.room.*
import com.example.data.model.ImportHistoryEntity
import com.example.data.model.StickerEntity
import com.example.data.model.StickerPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM sticker_packs ORDER BY importedAt DESC")
    fun getAllStickerPacksFlow(): Flow<List<StickerPackEntity>>

    @Query("SELECT * FROM sticker_packs WHERE id = :id LIMIT 1")
    suspend fun getStickerPackById(id: String): StickerPackEntity?

    @Query("SELECT * FROM sticker_packs WHERE isFavorite = 1 ORDER BY importedAt DESC")
    fun getFavoritePacksFlow(): Flow<List<StickerPackEntity>>

    @Query("SELECT * FROM stickers WHERE packId = :packId")
    suspend fun getStickersForPack(packId: String): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE packId = :packId")
    fun getStickersForPackFlow(packId: String): Flow<List<StickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickerPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Update
    suspend fun updateStickerPack(pack: StickerPackEntity)

    @Query("DELETE FROM sticker_packs WHERE id = :id")
    suspend fun deleteStickerPack(id: String)

    @Query("DELETE FROM stickers WHERE packId = :packId")
    suspend fun deleteStickersForPack(packId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ImportHistoryEntity)

    @Query("SELECT * FROM import_history ORDER BY timestamp DESC")
    fun getHistoryFlow(): Flow<List<ImportHistoryEntity>>

    @Query("DELETE FROM import_history")
    suspend fun clearHistory()
}
