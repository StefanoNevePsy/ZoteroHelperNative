package com.example.zoterohelpernative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.data.ZoteroCollection
import com.example.zoterohelpernative.theme.AppColors
import com.example.zoterohelpernative.theme.Radius
import com.example.zoterohelpernative.theme.Spacing
import com.example.zoterohelpernative.theme.TouchTarget
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
            color = if (isSelected) AppColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
            shape = RoundedCornerShape(Radius.s),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .clip(RoundedCornerShape(Radius.s))
                .clickable { onCollectionSelect(collection.key) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = (Spacing.xs.value + depth * 14).dp,
                    end = Spacing.m
                )
            ) {
                // Disclosure control needs its own adequate hit area
                if (children.isNotEmpty()) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(TouchTarget.min)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "Comprimi" else "Espandi",
                            tint = AppColors.Label.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(TouchTarget.min))
                }
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (isSelected) AppColors.Accent else AppColors.Label.Secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.m))
                Text(
                    text = collection.data.name,
                    color = if (isSelected) AppColors.Label.Primary else AppColors.Label.Secondary,
                    style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    // Sidebar = functional layer. Larger glass elements stay more opaque to keep
    // their text legible over a busy content plane.
    LiquidGlass(
        modifier = modifier.fillMaxHeight(),
        variant = GlassVariant.Regular,
        shape = RoundedCornerShape(topEnd = Radius.xxl, bottomEnd = Radius.xxl),
        borderColor = AppColors.Glass.Border
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Collezioni",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.Label.Primary,
                modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s)
            )

            // "Tutti gli elementi" root option
            Surface(
                color = if (activeCollectionId == null) AppColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(Radius.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m)
                    .heightIn(min = TouchTarget.min)
                    .clip(RoundedCornerShape(Radius.s))
                    .clickable { onCollectionSelect(null) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.m)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryBooks,
                        contentDescription = null,
                        tint = if (activeCollectionId == null) AppColors.Accent else AppColors.Label.Secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.m))
                    Text(
                        text = "Tutti gli elementi",
                        color = if (activeCollectionId == null) AppColors.Label.Primary else AppColors.Label.Secondary,
                        style = if (activeCollectionId == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s))
            HorizontalDivider(color = AppColors.Separator, modifier = Modifier.padding(horizontal = Spacing.m))

            val childrenMap = collections.groupBy { getParentKey(it) }
            val rootCollections = childrenMap[null] ?: emptyList()

            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                items(rootCollections, key = { it.key }) { rootCollection ->
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
