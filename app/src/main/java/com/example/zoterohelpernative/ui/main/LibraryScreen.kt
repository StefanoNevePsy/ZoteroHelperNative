package com.example.zoterohelpernative.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.ui.LibraryViewModel
import com.example.zoterohelpernative.ui.filteredItems
import com.example.zoterohelpernative.ui.allTags
import com.example.zoterohelpernative.ui.getChildrenForItem
import com.example.zoterohelpernative.ui.components.BackgroundCanvas
import com.example.zoterohelpernative.ui.components.CollectionsSidebar
import com.example.zoterohelpernative.ui.components.ItemDetailsPanel
import com.example.zoterohelpernative.ui.components.GlassSurface
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToReader: (itemKey: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var isSidebarOpen by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        // Vibrant Glassmorphism Base Background
        BackgroundCanvas()

        Scaffold(
            containerColor = Color.Transparent, // Let the canvas show through
            topBar = {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    color = Color(0x1A000000), // Dark translucent
                    borderColor = Color.Transparent,
                    blurRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSidebarOpen = !isSidebarOpen }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Zotero Library", 
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row {
                            IconButton(onClick = { viewModel.loadLibrary() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    
                    // Left Sidebar (Collections)
                    AnimatedVisibility(
                        visible = isSidebarOpen,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut()
                    ) {
                        CollectionsSidebar(
                            collections = state.collections,
                            activeCollectionId = state.activeCollectionId,
                            onCollectionSelect = { viewModel.setActiveCollection(it) },
                            modifier = Modifier.width(260.dp)
                        )
                    }

                    // Main Library Grid
                    GlassSurface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = Color(0x1AFFFFFF),
                        borderColor = Color(0x33FFFFFF)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color(0xFF60A5FA)
                            )
                        } else if (state.error != null) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Impossibile caricare la libreria", 
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.error ?: "Errore sconosciuto", 
                                    color = Color(0xB3FFFFFF),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            val items = state.filteredItems
                            if (items.isEmpty()) {
                                // Empty state
                                Column(
                                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = "Empty",
                                        modifier = Modifier.size(64.dp),
                                        tint = Color(0x80FFFFFF)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Nessun documento in questa cartella",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(items) { item ->
                                        val isSelected = state.activeItem?.key == item.key
                                        LibraryItemCard(
                                            title = item.data.title ?: "Senza Titolo",
                                            itemType = item.data.itemType ?: "Document",
                                            isSelected = isSelected,
                                            onClick = { viewModel.setActiveItem(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Sidebar (Item Details) - Overlay
                AnimatedVisibility(
                    visible = state.activeItem != null,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    state.activeItem?.let { item ->
                        ItemDetailsPanel(
                            item = item,
                            children = state.getChildrenForItem(item.key),
                            allLibraryTags = state.allTags,
                            onClose = { viewModel.setActiveItem(null) },
                            onOpenPdf = onNavigateToReader,
                            onToggleTag = { viewModel.toggleTagOnActiveItem(it) },
                            modifier = Modifier.width(360.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryItemCard(title: String, itemType: String, isSelected: Boolean, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0x4D60A5FA) else Color(0x1AFFFFFF),
        borderColor = if (isSelected) Color(0x8060A5FA) else Color(0x33FFFFFF),
        blurRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description, 
                    contentDescription = "Type",
                    tint = if (isSelected) Color.White else Color(0xFF60A5FA)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = itemType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xB3FFFFFF)
                )
            }
        }
    }
}
