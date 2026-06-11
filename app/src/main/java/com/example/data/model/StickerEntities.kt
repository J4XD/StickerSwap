package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publisher: String,
    val trayIconFileName: String,
    val publisherEmail: String = "developer@stickerbridge.com",
    val publisherWebsite: String = "https://stickerbridge.com",
    val privacyPolicyWebsite: String = "https://stickerbridge.com/privacy",
    val licenseAgreementWebsite: String = "https://stickerbridge.com/license",
    val isAnimated: Boolean = false,
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packId: String,
    val imageFileName: String,
    val emoji: String = "😀"
)

@Entity(tableName = "import_history")
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packId: String,
    val packName: String,
    val stickerCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // "SUCCESS", "FAILED"
)
