package com.example.services

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.StickerPackEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileNotFoundException

class WhatsAppStickerProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerProvider"
        
        // This MUST align with build.gradle.kts applicationId + ".stickerprovider"
        const val AUTHORITY = "com.aistudio.stickerbridge.vjrtxz.stickerprovider"

        private const val METADATA = 1
        private const val METADATA_ID = 2
        private const val STICKERS = 3
        private const val STICKERS_ID = 4
        private const val STICKERS_ASSET = 5

        private val URL_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA)
            addURI(AUTHORITY, "metadata/*", METADATA_ID)
            addURI(AUTHORITY, "*", STICKERS)
            addURI(AUTHORITY, "*/*", STICKERS_ID)
        }

        // WhatsApp expected Columns for metadata query
        private val METADATA_COLUMNS = arrayOf(
            "android_play_store_link",
            "ios_app_store_link",
            "publisher_email",
            "publisher_website",
            "privacy_policy_website",
            "license_agreement_website",
            "sticker_pack_id",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack",
            "video_sticker_pack"
        )

        // WhatsApp expected Columns for stickers query
        private val STICKER_COLUMNS = arrayOf(
            "sticker_image_file",
            "sticker_emoji"
        )
    }

    private lateinit var database: AppDatabase

    override fun onCreate(): Boolean {
        context?.let {
            database = AppDatabase.getDatabase(it)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val code = URL_MATCHER.match(uri)
        Log.d(TAG, "Query received for URI code $code: $uri")

        return when (code) {
            METADATA -> {
                getMetadataCursor()
            }
            STICKERS -> {
                val packId = uri.lastPathSegment ?: return null
                getStickersCursor(packId)
            }
            else -> {
                null
            }
        }
    }

    private fun getMetadataCursor(): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        val dao = database.stickerDao()

        // Content providers are queried synchronously; run Blocking to retrieve Room details
        val packs = runBlocking {
            var result = emptyList<StickerPackEntity>()
            try {
                // Read from Room
                // We'll collect the Flow once or query directly.
                // Wait, it is safer to query directly, but our DAO has Flow and suspend methods.
                // Since we need to get list, let's look up how we can select all packs.
                // In StickerDao we have: fun getAllStickerPacksFlow(): Flow<List<StickerPackEntity>>.
                // We can query a simple blocking list if we make a separate DAO query or collect first flow value.
                dao.getAllStickerPacksFlow().first()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching packs", e)
                result
            }
        }

        for (pack in packs) {
            cursor.addRow(
                arrayOf(
                    "", // android_play_store_link (can be empty string)
                    "", // ios_app_store_link
                    pack.publisherEmail,
                    pack.publisherWebsite,
                    pack.privacyPolicyWebsite,
                    pack.licenseAgreementWebsite,
                    pack.id,
                    pack.name,
                    pack.publisher,
                    pack.trayIconFileName,
                    "1", // image_data_version
                    0, // avoid_cache
                    if (pack.isAnimated) 1 else 0,
                    if (pack.isVideo) 1 else 0
                )
            )
        }

        return cursor
    }

    private fun getStickersCursor(packId: String): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        val dao = database.stickerDao()

        val stickers = runBlocking {
            try {
                dao.getStickersForPack(packId)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching stickers for pack $packId", e)
                emptyList()
            }
        }

        for (sticker in stickers) {
            // Emojis should be comma-separated list
            val emojis = sticker.emoji.ifEmpty { "😀" }
            cursor.addRow(
                arrayOf(
                    sticker.imageFileName,
                    emojis
                )
            )
        }

        return cursor
    }

    override fun getType(uri: Uri): String? {
        val code = URL_MATCHER.match(uri)
        return when (code) {
            METADATA -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            METADATA_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            STICKERS_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.stickers"
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d(TAG, "OpenFile query for Uri: $uri")
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 2) {
            Log.e(TAG, "OpenFile URI path too short: $uri")
            throw FileNotFoundException("File not found.")
        }
        
        // Structure expected: content://com.aistudio.stickerbridge.vjrtxz.stickerprovider/<sticker_pack_id>/<filename>
        val packId = pathSegments[pathSegments.size - 2]
        val filename = pathSegments[pathSegments.size - 1]

        val context = context ?: throw FileNotFoundException("Context is null")
        val file = File(context.filesDir, "sticker_packs/$packId/$filename")

        if (!file.exists()) {
            Log.e(TAG, "Sticker file does not exist on disk: ${file.absolutePath}")
            throw FileNotFoundException("File not found: " + file.absolutePath)
        }

        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening sticker file: ${file.absolutePath}", e)
            throw FileNotFoundException("Error opening sticker file: " + e.message)
        }
    }

    // CRUD operations are not implemented/needed for consumer apps
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
