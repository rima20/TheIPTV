package com.example.theiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.EpgProgram
import com.example.theiptv.ui.theme.AccentAmber
import com.example.theiptv.ui.theme.NightSurface
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusedItemBanner(
    item: ContentItem?,
    categoryName: String,
    nowPlaying: EpgProgram?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(NightSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (item == null) {
            Text(
                text = "Focus a title to see details",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MetaLine(item = item, categoryName = categoryName)
            Text(
                text = bannerDetail(item, nowPlaying).orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetaLine(item: ContentItem, categoryName: String) {
    val parts = buildList {
        add(item.type.label)
        if (categoryName.isNotBlank()) add(categoryName)
        item.year?.let { add(it) }
        formatDuration(item.duration)?.let { add(it) }
        item.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = parts.joinToString("  ·  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        val rating = item.rating
        if (!rating.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = AccentAmber,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(rating, style = MaterialTheme.typography.labelMedium, color = AccentAmber)
        }
    }
}

private fun bannerDetail(item: ContentItem, nowPlaying: EpgProgram?): String? {
    if (item.type == ContentType.Live && nowPlaying != null) {
        val window = "${formatTime(nowPlaying.startMs)} – ${formatTime(nowPlaying.endMs)}"
        return buildString {
            append("Now  ·  ")
            append(window)
            append("  ·  ")
            append(nowPlaying.title)
            if (nowPlaying.description.isNotBlank()) {
                append("  —  ")
                append(nowPlaying.description)
            }
        }
    }
    return item.plot?.takeIf { it.isNotBlank() }
}

private fun formatTime(timeMs: Long): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMs))
}

private fun formatDuration(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value == "0") return null
    if (value.contains(':')) return value
    val minutes = value.toIntOrNull() ?: return value
    if (minutes <= 0) return null
    val normalized = if (minutes > 300) minutes / 60 else minutes
    return "$normalized min"
}
