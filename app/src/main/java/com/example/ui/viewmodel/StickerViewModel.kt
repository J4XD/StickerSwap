package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.ImportHistoryEntity
import com.example.data.model.StickerPackEntity
import com.example.data.network.TelegramApiService
import com.example.data.repository.StickerRepository
import com.example.services.WhatsAppStickerProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    object Idle : ImportUiState
    data class Loading(val progress: Float) : ImportUiState
    data class Success(val pack: StickerPackEntity) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class StickerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val telegramApi = TelegramApiService.create()
    
    private val repository = StickerRepository(
        database.stickerDao(),
        telegramApi,
        context
    )

    private val prefs = context.getSharedPreferences("stickerbridge_prefs", Context.MODE_PRIVATE)

    // Observable states
    val stickerPacks: StateFlow<List<StickerPackEntity>> = repository.allStickerPacks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePacks: StateFlow<List<StickerPackEntity>> = repository.favoritePacks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val importHistory: StateFlow<List<ImportHistoryEntity>> = repository.importHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _storageSize = MutableStateFlow(0L)
    val storageSize: StateFlow<Long> = _storageSize.asStateFlow()

    // Settings config
    private val _botToken = MutableStateFlow(prefs.getString("telegram_token", "") ?: "")
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _theme = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    init {
        updateStorageSize()
    }

    fun updateStorageSize() {
        viewModelScope.launch {
            _storageSize.value = repository.getStorageUsageBytes()
        }
    }

    fun saveBotToken(token: String) {
        _botToken.value = token
        prefs.edit().putString("telegram_token", token).apply()
    }

    fun saveTheme(themeName: String) {
        _theme.value = themeName
        prefs.edit().putString("theme", themeName).apply()
    }

    fun getStickersForPack(packId: String) = repository.getStickersForPackFlow(packId)

    suspend fun getStickerPackById(packId: String) = repository.getStickerPackById(packId)

    fun importPack(packUrlOrName: String) {
        if (packUrlOrName.trim().isEmpty()) {
            _importState.value = ImportUiState.Error("Please enter a valid Telegram sticker pack URL or name.")
            return
        }

        viewModelScope.launch {
            _importState.value = ImportUiState.Loading(0.01f)
            Log.d("StickerViewModel", "Starting import for: $packUrlOrName")
            
            val result = repository.importTelegramPack(
                token = _botToken.value,
                packName = packUrlOrName
            ) { progress ->
                _importState.value = ImportUiState.Loading(progress)
            }

            result.fold(
                onSuccess = { pack ->
                    _importState.value = ImportUiState.Success(pack)
                    updateStorageSize()
                },
                onFailure = { error ->
                    _importState.value = ImportUiState.Error(error.localizedMessage ?: "Unknown error occurred.")
                }
            )
        }
    }

    fun resetImportState() {
        _importState.value = ImportUiState.Idle
    }

    fun toggleFavorite(packId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(packId)
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            repository.deletePack(packId)
            updateStorageSize()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearAllCache()
            updateStorageSize()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    /**
     * Builds the Intent to open WhatsApp sticker add page
     */
    fun getWhatsAppIntent(packId: String, packName: String): Intent {
        return Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra("sticker_pack_id", packId)
            putExtra("sticker_pack_authority", WhatsAppStickerProvider.AUTHORITY)
            putExtra("sticker_pack_name", packName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
