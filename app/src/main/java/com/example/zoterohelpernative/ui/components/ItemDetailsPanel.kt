package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.data.ZoteroItem
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*

val tagColors = listOf(
    Color(0xFFFCA5A5), // Red
    Color(0xFFFCD34D), // Yellow
    Color(0xFF86EFAC), // Green
    Color(0xFF93C5FD), // Blue
    Color(0xFFC4B5FD), // Purple
    Color(0xFFF9A8D4), // Pink
    Color(0xFF6EE7B7), // Teal
    Color(0xFFFDBA74)  // Orange
)

fun getTagColor(tag: String): Color {
    val index = kotlin.math.abs(tag.hashCode()) % tagColors.size
    return tagColors[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailsPanel(
    item: ZoteroItem,
    children: List<ZoteroItem>,
    allLibraryTags: List<String>,
    onClose: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    isAttachmentCached: (String) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxHeight(),
        color = Color(0xF212121A), // 95% opacity dark color so text underneath doesn't bleed
        borderColor = Color(0x4DFFFFFF)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Dettagli",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Title
                Text(
                    text = item.data.title ?: "Senza Titolo",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Authors
                if (!item.data.creators.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xB3FFFFFF), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            item.data.creators.forEach { creator ->
                                val name = creator.name ?: "${creator.firstName} ${creator.lastName}".trim()
                                Text(text = name, color = Color(0xD9FFFFFF), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val pdfs = children.filter { 
                    it.data.itemType == "attachment" && 
                    (it.data.contentType == "application/pdf" || it.data.filename?.endsWith(".pdf", ignoreCase = true) == true) 
                }
                if (pdfs.isNotEmpty()) {
                    Button(
                        onClick = { onOpenPdf(pdfs.first().key) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA), contentColor = Color.Black),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apri Documento", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Abstract
                if (!item.data.abstractNote.isNullOrBlank()) {
                    Text(
                        text = "Abstract",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.data.abstractNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xB3FFFFFF)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tags Section
                Text(
                    text = "Tags del documento",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentTags = item.data.tags?.map { it.tag } ?: emptyList()
                    if (currentTags.isEmpty()) {
                        Text("Nessun tag", color = Color(0x80FFFFFF), style = MaterialTheme.typography.bodySmall)
                    }
                    currentTags.forEach { tagStr ->
                        val tagColor = getTagColor(tagStr)
                        Surface(
                            color = tagColor,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.clickable { onToggleTag(tagStr) }
                        ) {
                            Text(
                                text = tagStr,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // All Available Tags Grid
                Text(
                    text = "Aggiungi Tag",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Type to filter existing tags, or create a brand-new one
                var newTagText by remember(item.key) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        placeholder = { Text("Cerca o crea tag…", color = Color(0x80FFFFFF)) },
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF34D399),
                            focusedBorderColor = Color(0x8034D399),
                            unfocusedBorderColor = Color(0x33FFFFFF)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val currentTagsForAdd = item.data.tags?.map { it.tag } ?: emptyList()
                    val canCreateTag = newTagText.isNotBlank() && newTagText.trim() !in currentTagsForAdd
                    IconButton(
                        onClick = {
                            onToggleTag(newTagText.trim())
                            newTagText = ""
                        },
                        enabled = canCreateTag,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (canCreateTag) Color(0xFF34D399) else Color(0x33FFFFFF),
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Crea tag",
                            tint = if (canCreateTag) Color.Black else Color(0x80FFFFFF)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentTags = item.data.tags?.map { it.tag } ?: emptyList()
                    allLibraryTags
                        .filter { it !in currentTags }
                        .filter { newTagText.isBlank() || it.contains(newTagText.trim(), ignoreCase = true) }
                        .forEach { tagStr ->
                        val tagColor = getTagColor(tagStr)
                        Surface(
                            color = tagColor.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.clickable { onToggleTag(tagStr) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, tagColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = tagStr,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = tagColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Child Notes
                val notes = children.filter { it.data.itemType == "note" && !it.data.note.isNullOrBlank() }
                if (notes.isNotEmpty()) {
                    Divider(color = Color(0x1AFFFFFF))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Note (${notes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    notes.forEach { noteItem ->
                        // Zotero notes are HTML: render them as plain text
                        val plainText = remember(noteItem.key, noteItem.data.note) {
                            android.text.Html.fromHtml(noteItem.data.note ?: "", android.text.Html.FROM_HTML_MODE_COMPACT)
                                .toString().trim()
                        }
                        var expanded by remember(noteItem.key) { mutableStateOf(false) }
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(vertical = 4.dp),
                            color = Color(0x26FCD34D),
                            borderColor = Color(0x4DFCD34D),
                            shape = MaterialTheme.shapes.medium,
                            blurRadius = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = Color(0xFFFCD34D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = plainText.lineSequence().firstOrNull { it.isNotBlank() } ?: "Nota",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = if (expanded) "Comprimi" else "Espandi",
                                        tint = Color(0xB3FFFFFF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (expanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = plainText,
                                        color = Color(0xD9FFFFFF),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else if (plainText.lines().size > 1) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = plainText,
                                        color = Color(0x99FFFFFF),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Divider(color = Color(0x1AFFFFFF))
                Spacer(modifier = Modifier.height(24.dp))

                // Attachments
                Text(
                    text = "Allegati",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                if (pdfs.isEmpty()) {
                    Text("Nessun PDF collegato", color = Color(0x80FFFFFF), style = MaterialTheme.typography.bodyMedium)
                } else {
                    pdfs.forEach { pdf ->
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPdf(pdf.key) }
                                .padding(vertical = 4.dp),
                            color = Color(0x4D60A5FA), // Accent tint
                            borderColor = Color(0x8060A5FA),
                            shape = MaterialTheme.shapes.medium,
                            blurRadius = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = pdf.data.filename ?: "Documento PDF",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isAttachmentCached(pdf.key)) {
                                    Icon(
                                        Icons.Outlined.OfflinePin,
                                        contentDescription = "Disponibile offline",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.CloudQueue,
                                        contentDescription = "Non ancora scaricato",
                                        tint = Color(0x80FFFFFF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
