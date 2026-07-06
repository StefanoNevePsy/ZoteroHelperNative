package com.example.zoterohelpernative.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.data.ItemData
import androidx.compose.ui.graphics.Color

enum class SidebarTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TOC("Index", Icons.AutoMirrored.Outlined.List),
    SEARCH("Search", Icons.Outlined.Search),
    ANNOTATIONS("Annotations", Icons.Outlined.Star),
    CHAT("Chat AI", Icons.Outlined.AutoAwesome)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSidebar(
    isOpen: Boolean,
    onClose: () -> Unit,
    annotations: List<ItemData>,
    getUiColor: (String) -> Color,
    onDeleteAnnotation: (ItemData) -> Unit,
    chatMessages: List<com.example.zoterohelpernative.ui.ChatMessage> = emptyList(),
    isChatSending: Boolean = false,
    geminiKeySet: Boolean = false,
    nvidiaKeySet: Boolean = false,
    chatProvider: String = "gemini",
    nvidiaModels: List<String> = emptyList(),
    selectedNvidiaModel: String = "",
    isLoadingNvidiaModels: Boolean = false,
    onProviderChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onSaveMessageAsNote: (Int) -> Unit = {},
    onSendChatMessage: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
    searchResults: List<com.example.zoterohelpernative.pdf.SearchHit> = emptyList(),
    isSearching: Boolean = false,
    onSearch: (String) -> Unit = {},
    tocEntries: List<com.example.zoterohelpernative.pdf.TocEntry> = emptyList(),
    onGoToPage: (Int) -> Unit = {},
    onExportAnnotations: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SidebarTab.ANNOTATIONS) }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        GlassSurface(
            modifier = Modifier
                .padding(end = 16.dp, top = 80.dp, bottom = 16.dp)
                .fillMaxHeight()
                .width(320.dp),
            color = Color(0xFA111827), // More opaque background for better contrast
            borderColor = Color(0x33FFFFFF),
            blurRadius = 32.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedTab.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Row {
                        if (selectedTab == SidebarTab.CHAT && chatMessages.isNotEmpty()) {
                            IconButton(onClick = onClearChat) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = "Svuota chat",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (selectedTab == SidebarTab.ANNOTATIONS && annotations.isNotEmpty()) {
                            IconButton(onClick = onExportAnnotations) {
                                Icon(
                                    imageVector = Icons.Outlined.IosShare,
                                    contentDescription = "Esporta annotazioni in Markdown",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close Sidebar",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Tabs (Custom Glassmorphism look)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SidebarTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon, 
                                contentDescription = tab.title, 
                                tint = if (isSelected) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.5f)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(4.dp)
                                        .background(Color(0xFF60A5FA), androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }
                }
                
                Divider(color = Color.White.copy(alpha = 0.1f))

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        SidebarTab.TOC -> {
                            if (tocEntries.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Il documento non ha un indice.", color = Color.White.copy(alpha = 0.5f))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(tocEntries.size) { index ->
                                        val entry = tocEntries[index]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = entry.pageIndex >= 0) { onGoToPage(entry.pageIndex) }
                                                .padding(
                                                    start = (16 + entry.level * 16).dp,
                                                    end = 16.dp,
                                                    top = 10.dp,
                                                    bottom = 10.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = entry.title,
                                                color = if (entry.level == 0) Color.White else Color.White.copy(alpha = 0.75f),
                                                style = if (entry.level == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                                fontWeight = if (entry.level == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (entry.pageIndex >= 0) {
                                                Text(
                                                    text = "${entry.pageIndex + 1}",
                                                    color = Color(0xFF60A5FA),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SidebarTab.SEARCH -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                var searchInput by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = searchInput,
                                    onValueChange = {
                                        searchInput = it
                                        onSearch(it)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    singleLine = true,
                                    placeholder = { Text("Cerca nel documento…", color = Color(0x80FFFFFF)) },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xB3FFFFFF)) },
                                    trailingIcon = {
                                        if (isSearching) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF60A5FA))
                                        } else if (searchInput.isNotEmpty()) {
                                            IconButton(onClick = { searchInput = ""; onSearch("") }) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Pulisci", tint = Color(0xB3FFFFFF))
                                            }
                                        }
                                    },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFF60A5FA),
                                        focusedBorderColor = Color(0x8060A5FA),
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    )
                                )

                                if (searchInput.length >= 2 && !isSearching) {
                                    Text(
                                        text = if (searchResults.isEmpty()) "Nessun risultato"
                                               else "${searchResults.size} risultat${if (searchResults.size == 1) "o" else "i"}",
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(searchResults.size) { index ->
                                        val hit = searchResults[index]
                                        GlassSurface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onGoToPage(hit.pageIndex) },
                                            color = Color(0x26FFFFFF),
                                            borderColor = Color(0x1AFFFFFF),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "Pagina ${hit.pageIndex + 1}",
                                                    color = Color(0xFF60A5FA),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = hit.snippet,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 3
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SidebarTab.CHAT -> {
                            ChatPanel(
                                messages = chatMessages,
                                isSending = isChatSending,
                                geminiKeySet = geminiKeySet,
                                nvidiaKeySet = nvidiaKeySet,
                                chatProvider = chatProvider,
                                nvidiaModels = nvidiaModels,
                                selectedNvidiaModel = selectedNvidiaModel,
                                isLoadingNvidiaModels = isLoadingNvidiaModels,
                                onProviderChange = onProviderChange,
                                onModelChange = onModelChange,
                                onRefreshModels = onRefreshModels,
                                onSaveMessageAsNote = onSaveMessageAsNote,
                                onSendMessage = onSendChatMessage
                            )
                        }
                        SidebarTab.ANNOTATIONS -> {
                            if (annotations.none { it.annotationType == "highlight" || it.annotationType == "underline" }) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Nessuna annotazione nel documento.", color = Color.White.copy(alpha = 0.5f))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = annotations
                                            .filter { it.annotationType == "highlight" || it.annotationType == "underline" }
                                            .sortedBy { it.annotationSortIndex ?: "99999|99999" },
                                        key = { it.key }
                                    ) { ann ->
                                        val annColor = getUiColor(ann.annotationColor ?: "#ffd400")

                                        GlassSurface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // Jump to the annotation's page
                                                    val pageFromPosition = ann.annotationPosition?.let { pos ->
                                                        Regex("\"pageIndex\"\\s*:\\s*(\\d+)").find(pos)?.groupValues?.get(1)?.toIntOrNull()
                                                    }
                                                    val page = pageFromPosition
                                                        ?: ann.annotationPageLabel?.toIntOrNull()?.minus(1)
                                                    if (page != null) onGoToPage(page)
                                                },
                                            color = Color(0x33FFFFFF), // Lighter card over dark background
                                            borderColor = Color(0x1AFFFFFF),
                                            blurRadius = 16.dp,
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                                // Left color bar
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .width(6.dp)
                                                        .background(annColor)
                                                )
                                                Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                                                    // Quoted text
                                                    Text(
                                                        text = "\"${ann.annotationText?.takeIf { it.isNotBlank() } ?: "..."}\"",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                            color = Color.White.copy(alpha = 0.9f)
                                                        ),
                                                        modifier = Modifier.padding(bottom = if (ann.annotationComment.isNullOrBlank()) 0.dp else 8.dp)
                                                    )
                                                    // Comment
                                                    if (!ann.annotationComment.isNullOrBlank()) {
                                                        Text(
                                                            text = ann.annotationComment,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color.White
                                                        )
                                                    }
                                                    
                                                    if (!ann.annotationPageLabel.isNullOrBlank()) {
                                                        Text(
                                                            text = "Pagina ${ann.annotationPageLabel}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                            ),
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                    
                                                    // Tags
                                                    if (!ann.tags.isNullOrEmpty()) {
                                                        FlowRow(
                                                            modifier = Modifier.padding(top = 8.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            ann.tags.forEach { tagObj ->
                                                                val tagColor = getTagColor(tagObj.tag)
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(tagColor.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = tagObj.tag,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = tagColor
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                IconButton(onClick = { onDeleteAnnotation(ann) }) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Elimina Annotazione",
                                                        tint = Color.White.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(
    messages: List<com.example.zoterohelpernative.ui.ChatMessage>,
    isSending: Boolean,
    geminiKeySet: Boolean,
    nvidiaKeySet: Boolean = false,
    chatProvider: String = "gemini",
    nvidiaModels: List<String> = emptyList(),
    selectedNvidiaModel: String = "",
    isLoadingNvidiaModels: Boolean = false,
    onProviderChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onSaveMessageAsNote: (Int) -> Unit = {},
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val chatReady = if (chatProvider == "nvidia") nvidiaKeySet else geminiKeySet

    Column(modifier = Modifier.fillMaxSize()) {
        // Provider + model selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = chatProvider == "gemini",
                onClick = { onProviderChange("gemini") },
                label = { Text("Gemini", style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = chatProvider == "nvidia",
                onClick = { onProviderChange("nvidia") },
                label = { Text("NVIDIA", style = MaterialTheme.typography.labelMedium) }
            )

            if (chatProvider == "nvidia") {
                var modelMenuOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = Color(0x26FFFFFF),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                modelMenuOpen = true
                                onRefreshModels() // self-updating: refetch on every open
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedNvidiaModel.ifBlank { "Scegli modello…" },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isLoadingNvidiaModels) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF76B900))
                            } else {
                                Icon(Icons.Outlined.ExpandMore, contentDescription = "Modelli", tint = Color(0xB3FFFFFF), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                        modifier = Modifier.heightIn(max = 420.dp).width(300.dp)
                    ) {
                        var modelFilter by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = modelFilter,
                            onValueChange = { modelFilter = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            singleLine = true,
                            placeholder = { Text("Filtra modelli…") },
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        val filtered = nvidiaModels.filter {
                            modelFilter.isBlank() || it.contains(modelFilter.trim(), ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (nvidiaModels.isEmpty()) "Nessun modello (controlla la API key)" else "Nessun modello corrispondente") },
                                enabled = false,
                                onClick = {}
                            )
                        }
                        filtered.forEach { modelId ->
                            DropdownMenuItem(
                                text = { Text(modelId, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    if (modelId == selectedNvidiaModel) Icon(Icons.Outlined.Check, contentDescription = null)
                                },
                                onClick = {
                                    onModelChange(modelId)
                                    modelMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!chatReady) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (chatProvider == "nvidia")
                        "Per usare i modelli NVIDIA, inserisci la tua API key di build.nvidia.com nelle Impostazioni."
                    else
                        "Per chattare con l'AI sul documento, inserisci la tua API key di Gemini nelle Impostazioni.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fai una domanda sul documento: riassunti, spiegazioni, metodologia…",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.size) { index ->
                    val message = messages[index]
                    val isUser = message.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .background(
                                    color = when {
                                        message.isError -> Color(0x4DEF4444)
                                        isUser -> Color(0x4D60A5FA)
                                        else -> Color(0x26FFFFFF)
                                    },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isUser) 12.dp else 2.dp,
                                        bottomEnd = if (isUser) 2.dp else 12.dp
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = message.text,
                                color = if (message.isError) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Save an AI answer as a Zotero child note of the document
                            if (!isUser && !message.isError) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 6.dp)
                                        .clickable(enabled = !message.savedAsNote && !message.isSavingNote) {
                                            onSaveMessageAsNote(index)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when {
                                        message.isSavingNote -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFFFCD34D)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salvataggio…", color = Color(0x99FFFFFF), style = MaterialTheme.typography.labelSmall)
                                        }
                                        message.savedAsNote -> {
                                            Icon(
                                                Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Nota salvata", color = Color(0xFF34D399), style = MaterialTheme.typography.labelSmall)
                                        }
                                        else -> {
                                            Icon(
                                                Icons.Outlined.NoteAdd,
                                                contentDescription = "Salva come nota Zotero",
                                                tint = Color(0xFFFCD34D),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salva come nota", color = Color(0xFFFCD34D), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (isSending) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF60A5FA)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sto leggendo il documento…", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Input row
        if (chatReady) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Chiedi qualcosa…", color = Color(0x80FFFFFF)) },
                    maxLines = 4,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF60A5FA),
                        focusedBorderColor = Color(0x8060A5FA),
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isSending) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (inputText.isNotBlank() && !isSending) Color(0xFF60A5FA) else Color(0x33FFFFFF),
                            androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Invia",
                        tint = if (inputText.isNotBlank() && !isSending) Color.Black else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
