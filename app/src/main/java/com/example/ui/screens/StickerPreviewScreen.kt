package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.StickerEntity
import com.example.data.model.StickerPackEntity
import com.example.ui.viewmodel.StickerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPreviewScreen(
    packId: String,
    viewModel: StickerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var packEntity by remember { mutableStateOf<StickerPackEntity?>(null) }
    val stickers by viewModel.getStickersForPack(packId).collectAsState(initial = emptyList())
    var selectedSticker by remember { mutableStateOf<StickerEntity?>(null) }

    // Read details of pack
    LaunchedEffect(packId) {
        packEntity = viewModel.getStickerPackById(packId)
    }

    // WhatsApp Sticker Integration Launcher
    val whatsappLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Pack added to WhatsApp!", Toast.LENGTH_LONG).show()
        } else {
            // Sometimes WhatsApp returns 0 or cancel code even if added
            Toast.makeText(context, "Checked Sticker pack integration with WhatsApp.", Toast.LENGTH_SHORT).show()
        }
    }

    val pack = packEntity

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pack?.name ?: "Pack Preview",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back arrow click"
                        )
                    }
                },
                actions = {
                    pack?.let { p ->
                        IconButton(onClick = { viewModel.toggleFavorite(p.id); packEntity = p.copy(isFavorite = !p.isFavorite) }) {
                            Icon(
                                imageVector = if (p.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Toggle favorite",
                                tint = if (p.isFavorite) Color.Red else LocalContentColor.current
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (pack == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Info header layout
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Pack Tray
                            val trayFile = File(context.filesDir, "sticker_packs/${pack.id}/${pack.trayIconFileName}")
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (trayFile.exists()) {
                                    AsyncImage(
                                        model = trayFile,
                                        contentDescription = "Tray Preview",
                                        modifier = Modifier.size(44.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Wallpaper,
                                        contentDescription = "Tray placehold",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = pack.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Publisher: ${pack.publisher}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "Stickers count: ${stickers.size} (Format: WEBP, Static)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // WhatsApp Action CTA Button
                        Button(
                            onClick = {
                                try {
                                    val intent = viewModel.getWhatsAppIntent(pack.id, pack.name)
                                    whatsappLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "WhatsApp is not installed, or your WhatsApp version is unsupported.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366), // Official WhatsApp brand color green
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("add_to_whatsapp_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add green button",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Add to WhatsApp",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Grid of stickers inside pack
                if (stickers.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No stickers found inside this pack.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(80.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(stickers) { sticker ->
                            val stickerFile = File(context.filesDir, "sticker_packs/${pack.id}/${sticker.imageFileName}")
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { selectedSticker = sticker }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (stickerFile.exists()) {
                                        AsyncImage(
                                            model = stickerFile,
                                            contentDescription = "Sticker Grid Piece",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(sticker.emoji, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Size Sticker Detail Dialog
    selectedSticker?.let { sticker ->
        StickerDetailDialog(
            sticker = sticker,
            packId = packId,
            context = context,
            onDismiss = { selectedSticker = null }
        )
    }
}

@Composable
fun StickerDetailDialog(
    sticker: StickerEntity,
    packId: String,
    context: Context,
    onDismiss: () -> Unit
) {
    val stickerFile = File(context.filesDir, "sticker_packs/$packId/${sticker.imageFileName}")
    val sizeString = if (stickerFile.exists()) {
        "${(stickerFile.length() / 1024.0).toInt()} KB"
    } else {
        "Unknown size"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sticker Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close detail dialog",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (stickerFile.exists()) {
                        AsyncImage(
                            model = stickerFile,
                            contentDescription = "Sticker zoom detail view",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(sticker.emoji, fontSize = 64.sp)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DetailRow(label = "Filename", value = sticker.imageFileName)
                    DetailRow(label = "Format", value = "WEBP (WhatsApp Ready)")
                    DetailRow(label = "File Size", value = sizeString)
                    DetailRow(label = "Linked Emoji", value = sticker.emoji.ifEmpty { "None" })
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Preview")
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
