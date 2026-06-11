package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.StickerDao
import com.example.data.model.ImportHistoryEntity
import com.example.data.model.StickerEntity
import com.example.data.model.StickerPackEntity
import com.example.data.network.TelegramApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class StickerRepository(
    private val stickerDao: StickerDao,
    private val telegramApi: TelegramApiService,
    private val context: Context
) {
    val allStickerPacks: Flow<List<StickerPackEntity>> = stickerDao.getAllStickerPacksFlow()
    val favoritePacks: Flow<List<StickerPackEntity>> = stickerDao.getFavoritePacksFlow()
    val importHistory: Flow<List<ImportHistoryEntity>> = stickerDao.getHistoryFlow()

    fun getStickersForPackFlow(packId: String): Flow<List<StickerEntity>> =
        stickerDao.getStickersForPackFlow(packId)

    suspend fun getStickerPackById(id: String): StickerPackEntity? =
        stickerDao.getStickerPackById(id)

    suspend fun toggleFavorite(packId: String) {
        val pack = stickerDao.getStickerPackById(packId)
        if (pack != null) {
            stickerDao.updateStickerPack(pack.copy(isFavorite = !pack.isFavorite))
        }
    }

    suspend fun deletePack(packId: String) {
        withContext(Dispatchers.IO) {
            stickerDao.deleteStickerPack(packId)
            stickerDao.deleteStickersForPack(packId)
            // Delete local folder
            val packDir = File(context.filesDir, "sticker_packs/$packId")
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
        }
    }

    suspend fun clearHistory() {
        stickerDao.clearHistory()
    }

    // Measure total sticker files storage size in bytes
    suspend fun getStorageUsageBytes(): Long = withContext(Dispatchers.IO) {
        val rootDir = File(context.filesDir, "sticker_packs")
        if (rootDir.exists()) {
            rootDir.walkBottomUp().filter { it.isFile }.map { it.length() }.sum()
        } else {
            0L
        }
    }

    // Delete all downloaded files to clear cache but keep DB structure, or fully clear
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        val packs = stickerDao.getAllStickerPacksFlow() // we can also just delete folders
        val rootDir = File(context.filesDir, "sticker_packs")
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
    }

    /**
     * Main action to import standard Telegram sticker pack
     */
    suspend fun importTelegramPack(
        token: String,
        packName: String,
        onProgress: (Float) -> Unit
    ): Result<StickerPackEntity> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f)
            val cleanPackName = extractPackName(packName)
            if (cleanPackName.isEmpty()) {
                throw Exception("Invalid Telegram sticker set name or URL.")
            }

            // Check if this is a preset/sample pack
            if (isPresetName(cleanPackName)) {
                return@withContext Result.success(importPresetPack(cleanPackName, onProgress))
            }

            if (token.isEmpty() || token == "MY_GEMINI_API_KEY") {
                throw Exception("Telegram Bot Token is missing. Please set your Telegram Bot Token in the Settings page, or import a Demo Pack below!")
            }

            onProgress(0.15f)
            val response = telegramApi.getStickerSet(token, cleanPackName)
            if (!response.ok || response.result == null) {
                throw Exception(response.description ?: "Failed to fetch sticker set from Telegram. Make sure the pack name or URL is valid.")
            }

            val stickerSet = response.result
            val stickers = stickerSet.stickers
            if (stickers.isEmpty()) {
                throw Exception("Sticker pack is empty.")
            }

            onProgress(0.3f)
            val packId = cleanPackName.lowercase()
            val packDir = File(context.filesDir, "sticker_packs/$packId").apply { mkdirs() }

            val stickerEntities = mutableListOf<StickerEntity>()
            // Download each sticker
            val total = stickers.size
            for ((index, item) in stickers.withIndex()) {
                // Fetch file info to get filePath
                val fileInfoResponse = telegramApi.getFile(token, item.fileId)
                if (fileInfoResponse.ok && fileInfoResponse.result != null) {
                    val filePath = fileInfoResponse.result.filePath
                    if (filePath != null) {
                        val body = telegramApi.downloadFile(token, filePath)
                        val fileName = "sticker_${index}.webp"
                        val file = File(packDir, fileName)
                        saveResponseBodyToDisk(body.byteStream(), file)

                        stickerEntities.add(
                            StickerEntity(
                                packId = packId,
                                imageFileName = fileName,
                                emoji = item.emoji ?: "😀"
                            )
                        )
                    }
                }
                // emit progress up to 90%
                val progressVal = 0.3f + (0.6f * ((index + 1).toFloat() / total))
                onProgress(progressVal)
            }

            if (stickerEntities.isEmpty()) {
                throw Exception("Could not download any stickers successfully.")
            }

            // Copy first sticker as tray icon
            val trayName = "tray_icon.webp"
            val trayFile = File(packDir, trayName)
            val firstStickerFile = File(packDir, stickerEntities[0].imageFileName)
            if (firstStickerFile.exists()) {
                firstStickerFile.copyTo(trayFile, overwrite = true)
            }

            onProgress(0.95f)
            val packEntity = StickerPackEntity(
                id = packId,
                name = stickerSet.title,
                publisher = "Telegram Import",
                trayIconFileName = trayName,
                isAnimated = stickerSet.isAnimated,
                isVideo = stickerSet.isVideo
            )

            // Save to DB
            stickerDao.insertStickerPack(packEntity)
            stickerDao.insertStickers(stickerEntities)

            // Log history
            stickerDao.insertHistory(
                ImportHistoryEntity(
                    packId = packId,
                    packName = stickerSet.title,
                    stickerCount = stickerEntities.size,
                    status = "SUCCESS"
                )
            )

            onProgress(1.0f)
            Result.success(packEntity)
        } catch (e: Exception) {
            Log.e("StickerRepository", "Import failed", e)
            val packNameClean = extractPackName(packName)
            stickerDao.insertHistory(
                ImportHistoryEntity(
                    packId = packNameClean.lowercase().ifEmpty { "unknown" },
                    packName = packNameClean.ifEmpty { packName },
                    stickerCount = 0,
                    status = "FAILED"
                )
            )
            Result.failure(e)
        }
    }

    private fun extractPackName(input: String): String {
        var clean = input.trim()
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            // e.g. https://t.me/addstickers/Line_Friends or t.me/addstickers/Line_Friends
            val parts = clean.split("/")
            if (parts.isNotEmpty()) {
                clean = parts.last()
            }
        }
        // Remove trailing query params
        if (clean.contains("?")) {
            clean = clean.substringBefore("?")
        }
        return clean
    }

    private fun isPresetName(name: String): Boolean {
        val checkName = name.lowercase()
        return checkName == "cyber_cats" || checkName == "retro_devs" || checkName == "chill_panda"
    }

    // High fidelity presets that can be imported completely offline / without Telegram Bot Token
    suspend fun importPresetPack(
        presetId: String,
        onProgress: (Float) -> Unit
    ): StickerPackEntity {
        onProgress(0.2f)
        val packId = presetId.lowercase()
        val packDir = File(context.filesDir, "sticker_packs/$packId").apply { mkdirs() }

        val info = getPresetDetails(packId)
        val stickerUrls = info.urls
        val total = stickerUrls.size
        val stickerEntities = mutableListOf<StickerEntity>()
        
        val client = OkHttpClient()

        for ((index, url) in stickerUrls.withIndex()) {
            val fileName = "sticker_${index}.webp"
            val file = File(packDir, fileName)

            try {
                // Securely fetch online WebP pictures or generate them nicely!
                onProgress(0.2f + (0.6f * ((index + 1).toFloat() / total)))
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            saveResponseBodyToDisk(input, file)
                        }
                    } else {
                        // Create standard fallback shape on disk if networking fails
                        writeDefaultStickerBytes(file, index)
                    }
                }
            } catch (e: Exception) {
                // Offline fallback
                writeDefaultStickerBytes(file, index)
            }

            stickerEntities.add(
                StickerEntity(
                    packId = packId,
                    imageFileName = fileName,
                    emoji = info.emojis.getOrElse(index) { "😀" }
                )
            )
        }

        // Copy first sticker as tray icon
        val trayName = "tray_icon.webp"
        val trayFile = File(packDir, trayName)
        val firstStickerFile = File(packDir, stickerEntities[0].imageFileName)
        if (firstStickerFile.exists()) {
            firstStickerFile.copyTo(trayFile, overwrite = true)
        }

        onProgress(0.9f)
        val packEntity = StickerPackEntity(
            id = packId,
            name = info.title,
            publisher = "StickerBridge Presets",
            trayIconFileName = trayName,
            isAnimated = false,
            isVideo = false
        )

        // Save to DB
        stickerDao.insertStickerPack(packEntity)
        stickerDao.insertStickers(stickerEntities)

        // Log history
        stickerDao.insertHistory(
            ImportHistoryEntity(
                packId = packId,
                packName = info.title,
                stickerCount = stickerEntities.size,
                status = "SUCCESS"
            )
        )

        onProgress(1.0f)
        return packEntity
    }

    private fun writeDefaultStickerBytes(file: File, index: Int) {
        // Draw dynamic standard color block WebP image representation (or write precompiled asset)
        // Since we want this to be 100% valid WebP, write a tiny valid stub static 1x1 WebP byte array
        // WebP 1x1 pixel image data
        val webpStubBytes = intArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x1a, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x4c,
            0x0d, 0x00, 0x00, 0x00, 0x2f, 0x00, 0x00, 0x00, 0x10, 0x07, 0x10, 0x11, 0x11, 0x88, 0x88, 0xfe,
            0x07, 0x00
        ).map { it.toByte() }.toByteArray()
        file.writeBytes(webpStubBytes)
    }

    private data class PresetInfo(val title: String, val urls: List<String>, val emojis: List<String>)

    private fun getPresetDetails(packId: String): PresetInfo {
        return when (packId) {
            "cyber_cats" -> PresetInfo(
                "🐾 Cyberpunk Cats",
                listOf(
                    "https://www.gstatic.com/webp/gallery/1.webp",
                    "https://www.gstatic.com/webp/gallery/2.webp",
                    "https://www.gstatic.com/webp/gallery/3.webp",
                    "https://www.gstatic.com/webp/gallery/4.webp",
                    "https://www.gstatic.com/webp/gallery/5.webp"
                ),
                listOf("🐱", "🤖", "🕶️", "☄️", "🐯")
            )
            "retro_devs" -> PresetInfo(
                "💻 Retro Developers",
                listOf(
                    "https://www.gstatic.com/webp/gallery/1.webp",
                    "https://www.gstatic.com/webp/gallery/2.webp",
                    "https://www.gstatic.com/webp/gallery/3.webp",
                    "https://www.gstatic.com/webp/gallery/4.webp"
                ),
                listOf("⌨️", "🖥️", "🕹️", "⚡")
            )
            else -> PresetInfo(
                "🐼 Chill Panda Set",
                listOf(
                    "https://www.gstatic.com/webp/gallery/4.webp",
                    "https://www.gstatic.com/webp/gallery/5.webp",
                    "https://www.gstatic.com/webp/gallery/1.webp"
                ),
                listOf("🐼", "🎋", "💤")
            )
        }
    }

    private fun saveResponseBodyToDisk(inputStream: InputStream, targetFile: File) {
        var outputStream: OutputStream? = null
        try {
            val fileReader = ByteArray(4096)
            outputStream = FileOutputStream(targetFile)
            while (true) {
                val read = inputStream.read(fileReader)
                if (read == -1) {
                    break
                }
                outputStream.write(fileReader, 0, read)
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream?.close()
        }
    }
}
