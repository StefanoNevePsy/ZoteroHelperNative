package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.data.ZoteroCollection
import com.google.gson.JsonElement
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*

fun getParentKey(collection: ZoteroCollection): String? {
    val parent = collection.data.parentCollection
    if (parent == null || parent.isJsonNull) return null
    if (parent.isJsonPrimitive) {
        val prim = parent.asJsonPrimitive
        if (prim.isBoolean && !prim.asBoolean) return null
        if (prim.isString) return prim.asString
    }
    return null
}

@Composable
fun CollectionTreeNode(
    collection: ZoteroCollection,
    childrenMap: Map<String?, List<ZoteroCollection>>,
    activeCollectionId: String?,
    onCollectionSelect: (String?) -> Unit,
    depth: Int
) {
    val children = childrenMap[collection.key] ?: emptyList()
    var isExpanded by remember { mutableStateOf(false) }
    val isSelected = activeCollectionId == collection.key

    Column {
        Surface(
            color = if (isSelected) Color(0x33FFFFFF) else Color.Transparent,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCollectionSelect(collection.key) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = (12 + depth * 16).dp, 
                    end = 12.dp, 
                    top = 10.dp, 
                    bottom = 10.dp
                )
            ) {
                if (children.isNotEmpty()) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xB3FFFFFF),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { isExpanded = !isExpanded }
                    )
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF60A5FA) else Color(0xB3FFFFFF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = collection.data.name,
                    color = if (isSelected) Color.White else Color(0xD9FFFFFF),
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        if (isExpanded && children.isNotEmpty()) {
            children.forEach { child ->
                CollectionTreeNode(
                    collection = child,
                    childrenMap = childrenMap,
                    activeCollectionId = activeCollectionId,
                    onCollectionSelect = onCollectionSelect,
                    depth = depth + 1
                )
            }
        }
    }
}

@Composable
fun CollectionsSidebar(
    collections: List<ZoteroCollection>,
    activeCollectionId: String?,
    onCollectionSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxHeight(),
        color = Color(0x1AFFFFFF), // more transparent
        borderColor = Color(0x33FFFFFF)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Libreria",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(24.dp)
            )

            // "Tutti gli elementi" root option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCollectionSelect(null) }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LibraryBooks,
                    contentDescription = null,
                    tint = if (activeCollectionId == null) Color(0xFF60A5FA) else Color(0xB3FFFFFF)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Tutti gli elementi",
                    color = if (activeCollectionId == null) Color.White else Color(0xD9FFFFFF),
                    fontWeight = if (activeCollectionId == null) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val childrenMap = collections.groupBy { getParentKey(it) }
            val rootCollections = childrenMap[null] ?: emptyList()

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rootCollections) { rootCollection ->
                    CollectionTreeNode(
                        collection = rootCollection,
                        childrenMap = childrenMap,
                        activeCollectionId = activeCollectionId,
                        onCollectionSelect = onCollectionSelect,
                        depth = 0
                    )
                }
            }
        }
    }
}
