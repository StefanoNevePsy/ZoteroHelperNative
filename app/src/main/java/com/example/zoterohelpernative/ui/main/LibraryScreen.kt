package com.example.zoterohelpernative.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.theme.AppColors
import com.example.zoterohelpernative.theme.Radius
import com.example.zoterohelpernative.theme.Spacing
import com.example.zoterohelpernative.theme.TouchTarget
import com.example.zoterohelpernative.ui.LibraryViewModel
import com.example.zoterohelpernative.ui.filteredItems
import com.example.zoterohelpernative.ui.allTags
import com.example.zoterohelpernative.ui.getChildrenForItem
import com.example.zoterohelpernative.ui.year
import com.example.zoterohelpernative.ui.authorSummary
import com.example.zoterohelpernative.ui.components.BackgroundCanvas
import com.example.zoterohelpernative.ui.components.CollectionsSidebar
import com.example.zoterohelpernative.ui.components.ContentSurface
import com.example.zoterohelpernative.ui.components.GlassVariant
import com.example.zoterohelpernative.ui.components.ItemDetailsPanel
import com.example.zoterohelpernative.ui.components.LiquidGlass
import com.example.zoterohelpernative.ui.components.ScrollEdge
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.example.zoterohelpernative.ui.components.LocalHazeState

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
    val hazeState = remember { HazeState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    CompositionLocalProvider(LocalHazeState provides hazeState) {
    Box(modifier = modifier.fillMaxSize()) {
        // Content plane: the background and everything that scrolls on it is what
        // the functional (glass) layer refracts.
        BackgroundCanvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState))

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Functional layer: a Liquid Glass bar floating above the content
                LiquidGlass(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Chrome,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    borderColor = Color.Transparent,
                    borderWidth = 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(64.dp)
                            .padding(horizontal = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSidebarOpen = !isSidebarOpen },
                                modifier = Modifier.size(TouchTarget.min)
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = if (isSidebarOpen) "Nascondi collezioni" else "Mostra collezioni",
                                    tint = AppColors.Label.Primary
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "Libreria",
                                color = AppColors.Label.Primary,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.pendingSyncCount > 0) {
                                PendingSyncBadge(
                                    count = state.pendingSyncCount,
                                    onClick = { viewModel.loadLibrary() }
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                            }
                            IconButton(
                                onClick = { viewModel.loadLibrary() },
                                modifier = Modifier.size(TouchTarget.min)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Sincronizza", tint = AppColors.Label.Primary)
                            }
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.size(TouchTarget.min)
                            ) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Impostazioni", tint = AppColors.Label.Primary)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(modifier = Modifier.fillMaxSize()) {

                    // Left Sidebar (Collections) — functional layer
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

                    // Content layer: no glass here, the list sits on the background
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        LibraryFilterBar(
                            searchQuery = state.searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            sortOption = state.sortOption,
                            sortAscending = state.sortAscending,
                            onSortOptionSelect = { viewModel.setSortOption(it) },
                            filterTag = state.filterTag,
                            allTags = state.allTags,
                            onFilterTagSelect = { viewModel.setFilterTag(it) }
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                state.isLoading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = AppColors.Accent
                                    )
                                }
                                state.error != null -> {
                                    EmptyState(
                                        icon = Icons.Outlined.CloudOff,
                                        title = "Impossibile caricare la libreria",
                                        message = state.error ?: "Errore sconosciuto",
                                        tint = AppColors.Status.Danger,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                else -> {
                                    val items = state.filteredItems
                                    if (items.isEmpty()) {
                                        EmptyState(
                                            icon = Icons.Outlined.Description,
                                            title = "Nessun documento",
                                            message = if (state.searchQuery.isNotBlank() || state.filterTag != null)
                                                "Nessun risultato per i filtri attivi."
                                            else
                                                "Questa collezione non contiene documenti.",
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    } else {
                                        LazyColumn(
                                            contentPadding = PaddingValues(
                                                start = Spacing.l,
                                                end = Spacing.l,
                                                top = Spacing.s,
                                                bottom = Spacing.xxxl
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(items, key = { it.key }) { item ->
                                                val isSelected = state.activeItem?.key == item.key
                                                val subtitle = listOfNotNull(
                                                    item.data.authorSummary,
                                                    item.data.year?.toString(),
                                                    item.data.itemType?.replaceFirstChar { it.uppercase() }
                                                ).joinToString("  ·  ")
                                                LibraryItemCard(
                                                    title = item.data.title ?: "Senza titolo",
                                                    subtitle = subtitle.ifBlank { "Documento" },
                                                    isSelected = isSelected,
                                                    onClick = { viewModel.setActiveItem(item) }
                                                )
                                            }
                                        }
                                        // Transition between the scrolling list and the bar above
                                        ScrollEdge(modifier = Modifier.align(Alignment.TopCenter))
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Sidebar (Item Details) — functional layer, above everything
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
                            onOpenPdf = { attachmentKey ->
                                viewModel.recordItemOpened(item.key)
                                onNavigateToReader(attachmentKey)
                            },
                            onToggleTag = { viewModel.toggleTagOnActiveItem(it) },
                            isAttachmentCached = { attachmentKey ->
                                java.io.File(context.cacheDir, "extracted_$attachmentKey").listFiles()
                                    ?.any { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) && f.length() > 0 } == true
                            },
                            modifier = Modifier.width(360.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

/** Offline queue indicator. Icon + count, so it doesn't rely on color alone. */
@Composable
private fun PendingSyncBadge(count: Int, onClick: () -> Unit) {
    Surface(
        color = AppColors.Status.Warning.copy(alpha = 0.16f),
        shape = RoundedCornerShape(Radius.m),
        modifier = Modifier
            .heightIn(min = TouchTarget.compact)
            .clip(RoundedCornerShape(Radius.m))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CloudUpload,
                contentDescription = "$count modifiche in attesa di sincronizzazione",
                tint = AppColors.Status.Warning,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "$count",
                color = AppColors.Status.Warning,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.Label.Tertiary
) {
    Column(
        modifier = modifier.padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.height(Spacing.l))
        Text(
            text = title,
            color = AppColors.Label.Primary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = message,
            color = AppColors.Label.Secondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun LibraryFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: com.example.zoterohelpernative.ui.LibrarySortOption,
    sortAscending: Boolean,
    onSortOptionSelect: (com.example.zoterohelpernative.ui.LibrarySortOption) -> Unit,
    filterTag: String?,
    allTags: List<String>,
    onFilterTagSelect: (String?) -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var tagMenuOpen by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.Label.Primary,
        unfocusedTextColor = AppColors.Label.Primary,
        cursorColor = AppColors.Accent,
        focusedBorderColor = AppColors.Accent.copy(alpha = 0.6f),
        unfocusedBorderColor = AppColors.Separator
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = {
                Text(
                    "Cerca per titolo, autore o anno",
                    color = AppColors.Label.Placeholder,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = AppColors.Label.Secondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(TouchTarget.min)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Pulisci ricerca", tint = AppColors.Label.Secondary)
                    }
                }
            },
            shape = RoundedCornerShape(Radius.m),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(Spacing.s))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // Sort selector
            Box {
                FilterChip(
                    selected = false,
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.heightIn(min = TouchTarget.compact),
                    label = {
                        Text(
                            "${sortOption.label} ${if (sortAscending) "↑" else "↓"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = "Ordina",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = AppColors.Label.Primary,
                        iconColor = AppColors.Label.Secondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = false,
                        borderColor = AppColors.Separator
                    )
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    com.example.zoterohelpernative.ui.LibrarySortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (option == sortOption) "${option.label} ${if (sortAscending) "↑" else "↓"}"
                                    else option.label
                                )
                            },
                            leadingIcon = {
                                if (option == sortOption) Icon(Icons.Outlined.Check, contentDescription = null)
                            },
                            onClick = {
                                onSortOptionSelect(option)
                                // Keep the menu open when toggling direction on the active option
                                if (option != sortOption) sortMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Tag filter — selected state shown by icon + label, not color alone
            Box {
                FilterChip(
                    selected = filterTag != null,
                    onClick = { tagMenuOpen = true },
                    modifier = Modifier.heightIn(min = TouchTarget.compact),
                    label = {
                        Text(
                            filterTag ?: "Tutti i tag",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (filterTag != null) Icons.Filled.Sell else Icons.Outlined.Sell,
                            contentDescription = "Filtra per tag",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = AppColors.Label.Primary,
                        iconColor = AppColors.Label.Secondary,
                        selectedContainerColor = AppColors.Accent.copy(alpha = 0.18f),
                        selectedLabelColor = AppColors.Label.Primary,
                        selectedLeadingIconColor = AppColors.Accent
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = filterTag != null,
                        borderColor = AppColors.Separator,
                        selectedBorderColor = AppColors.Accent.copy(alpha = 0.5f)
                    )
                )
                DropdownMenu(
                    expanded = tagMenuOpen,
                    onDismissRequest = { tagMenuOpen = false },
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Tutti i documenti") },
                        leadingIcon = { if (filterTag == null) Icon(Icons.Outlined.Check, contentDescription = null) },
                        onClick = {
                            onFilterTagSelect(null)
                            tagMenuOpen = false
                        }
                    )
                    if (allTags.isEmpty()) {
                        DropdownMenuItem(text = { Text("Nessun tag in libreria") }, enabled = false, onClick = {})
                    }
                    allTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag) },
                            leadingIcon = { if (filterTag == tag) Icon(Icons.Outlined.Check, contentDescription = null) },
                            onClick = {
                                onFilterTagSelect(tag)
                                tagMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content-layer card: a standard material, never Liquid Glass.
 * Selection is shown by an accent rail plus a tinted fill, so it reads without
 * relying on color perception alone.
 */
@Composable
fun LibraryItemCard(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    ContentSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.l),
        color = if (isSelected) AppColors.Accent.copy(alpha = 0.14f) else AppColors.Fill.Secondary,
        borderColor = if (isSelected) AppColors.Accent.copy(alpha = 0.45f) else AppColors.Separator
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection rail
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(if (isSelected) AppColors.Accent else Color.Transparent)
            )
            Row(
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(AppColors.Fill.Primary, RoundedCornerShape(Radius.s)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) AppColors.Accent else AppColors.Label.Secondary
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = AppColors.Label.Primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Label.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
