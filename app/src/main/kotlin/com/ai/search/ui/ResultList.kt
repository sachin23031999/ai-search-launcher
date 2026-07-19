package com.ai.search.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.search.model.ResultDomain
import com.ai.search.model.ResultItem

/** Grouped, clickable list of results with a section header per domain. */
@Composable
fun ResultList(
    grouped: List<Pair<ResultDomain, List<ResultItem>>>,
    onActivate: (ResultItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((domain, items) in grouped) {
            item(key = "header-$domain") {
                Text(
                    text = domain.header(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(items, key = { it.id }) { item ->
                ResultRow(item, onActivate)
            }
        }
    }
}

@Composable
private fun ResultRow(item: ResultItem, onActivate: (ResultItem) -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().clickable { onActivate(item) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.domain.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun ResultDomain.header(): String = when (this) {
    ResultDomain.SETTINGS -> "SETTINGS"
    ResultDomain.FILES -> "FILES"
}

private fun ResultDomain.icon(): ImageVector = when (this) {
    ResultDomain.SETTINGS -> Icons.Filled.Settings
    ResultDomain.FILES -> Icons.Filled.InsertDriveFile
}
