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
    onSendChatMessage: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
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
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Indice del Documento", color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                        SidebarTab.SEARCH -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Cerca nel PDF", color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                        SidebarTab.CHAT -> {
                            ChatPanel(
                                messages = chatMessages,
                                isSending = isChatSending,
                                geminiKeySet = geminiKeySet,
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
                                            modifier = Modifier.fillMaxWidth(),
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
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!geminiKeySet) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Per chattare con l'AI sul documento, inserisci la tua API key di Gemini nelle Impostazioni.",
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
                        Box(
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
        if (geminiKeySet) {
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
