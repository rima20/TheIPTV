package com.example.theiptv.data

import androidx.compose.runtime.Immutable

enum class ContentType(
    val label: String,
    val categoriesAction: String,
    val streamsAction: String,
) {
    Live("Live TV", "get_live_categories", "get_live_streams"),
    Movies("Movies", "get_vod_categories", "get_vod_streams"),
    Series("Series", "get_series_categories", "get_series"),
}

data class XtreamCredentials(
    val server: String,
    val username: String,
    val password: String,
)

data class IptvProvider(
    val id: String = "",
    val name: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
) {
    val credentials: XtreamCredentials
        get() = XtreamCredentials(server, username, password)

    val displayName: String
        get() = name.ifBlank { username.ifBlank { server } }
}

data class ProviderSnapshot(
    val providers: List<IptvProvider> = emptyList(),
    val activeId: String? = null,
) {
    val active: IptvProvider?
        get() = providers.firstOrNull { it.id == activeId } ?: providers.firstOrNull()
}

data class XtreamSession(
    val credentials: XtreamCredentials,
    val userInfo: UserInfo,
    val timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
)

data class UserInfo(
    val username: String,
    val status: String,
    val expDateSeconds: Long?,
    val isTrial: Boolean,
    val activeConnections: Int,
    val maxConnections: Int,
    val allowedOutputFormats: List<String>,
    val message: String,
)

@Immutable
data class Category(
    val id: String,
    val name: String,
) {
    companion object {
        const val ALL_ID = "all"
        val All = Category(ALL_ID, "All")
    }
}

@Immutable
data class ContentItem(
    val id: String,
    val name: String,
    val posterUrl: String,
    val categoryIds: List<String>,
    val type: ContentType,
    val containerExtension: String?,
    val rating: String?,
    val plot: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val tvArchive: Boolean = false,
    val tvArchiveDurationDays: Int = 0,
    val epgChannelId: String? = null,
) {
    fun belongsTo(categoryId: String): Boolean {
        if (categoryId == Category.ALL_ID) return true
        return categoryIds.any { it == categoryId }
    }

    val gridKey: String
        get() = "${type.name}-$id"

    fun hasCatchupFor(program: EpgProgram, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (program.placeholder) return false
        if (!tvArchive || tvArchiveDurationDays <= 0) return false
        if (program.startMs >= nowMs) return false
        val oldest = nowMs - tvArchiveDurationDays * 24L * 60L * 60L * 1000L
        return program.startMs >= oldest
    }

    fun withDetails(details: ItemDetails): ContentItem = copy(
        plot = details.plot?.takeIf { it.isNotBlank() } ?: plot,
        year = details.year?.takeIf { it.isNotBlank() } ?: year,
        genre = details.genre?.takeIf { it.isNotBlank() } ?: genre,
        duration = details.duration?.takeIf { it.isNotBlank() } ?: duration,
        rating = details.rating?.takeIf { it.isNotBlank() } ?: rating,
        posterUrl = details.posterUrl?.takeIf { it.isNotBlank() } ?: posterUrl,
        containerExtension = details.containerExtension ?: containerExtension,
    )
}

@Immutable
data class FavoriteRef(
    val providerId: String,
    val id: String,
    val type: ContentType,
    val name: String,
    val posterUrl: String = "",
    val rating: String? = null,
    val plot: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val addedAt: Long = 0L,
) {
    fun toContentItem(): ContentItem = ContentItem(
        id = id,
        name = name,
        posterUrl = posterUrl,
        categoryIds = emptyList(),
        type = type,
        containerExtension = null,
        rating = rating,
        plot = plot,
        year = year,
        genre = genre,
    )

    companion object {
        fun from(providerId: String, item: ContentItem): FavoriteRef = FavoriteRef(
            providerId = providerId,
            id = item.id,
            type = item.type,
            name = item.name,
            posterUrl = item.posterUrl,
            rating = item.rating,
            plot = item.plot,
            year = item.year,
            genre = item.genre,
            addedAt = System.currentTimeMillis(),
        )
    }
}

fun List<ContentItem>.groupedByCategory(): Map<String, List<ContentItem>> {
    val grouped = HashMap<String, MutableList<ContentItem>>()
    for (item in this) {
        for (id in item.categoryIds) {
            if (id.isEmpty() || id == Category.ALL_ID) continue
            grouped.getOrPut(id) { ArrayList() }.add(item)
        }
    }
    val result = HashMap<String, List<ContentItem>>(grouped.size + 1)
    result[Category.ALL_ID] = this
    result.putAll(grouped)
    return result
}

data class ItemDetails(
    val plot: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val title: String? = null,
    val containerExtension: String? = null,
)

data class SeriesDetails(
    val id: String,
    val title: String,
    val plot: String,
    val cover: String,
    val backdropUrl: String,
    val genre: String,
    val rating: String,
    val cast: String,
    val director: String,
    val seasons: List<SeriesSeason>,
)

data class SeriesSeason(
    val number: Int,
    val episodes: List<SeriesEpisode>,
)

data class SeriesEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val extension: String,
    val duration: String,
    val plot: String,
    val image: String,
    val director: String = "",
    val cast: String = "",
) {
    fun displayTitle(seriesTitle: String): String {
        val cleaned = stripSeriesPrefix(title, seriesTitle)
        return cleaned.ifBlank { "Episode $number" }
    }
}

private fun stripSeriesPrefix(episodeTitle: String, seriesTitle: String): String {
    val episode = episodeTitle.replace(Regex("\\s+"), " ").trim()
    val series = seriesTitle.replace(Regex("\\s+"), " ").trim()
    if (episode.isEmpty()) return episode
    if (series.length < 4) return episode
    if (!episode.startsWith(series, ignoreCase = true)) return episode
    val rest = episode.substring(series.length)
        .replace(Regex("^\\s*[-–—:|·]+\\s*"), "")
        .trim()
    return rest.ifBlank { episode }
}

data class PlaybackRequest(
    val title: String,
    val urls: List<String>,
    val fromStartUrls: List<String> = emptyList(),
    val fromStartTitle: String? = null,
    val programmeStartMs: Long? = null,
    val programmeEndMs: Long? = null,
) {
    val canWatchFromStart: Boolean
        get() = fromStartUrls.isNotEmpty()
}

@Immutable
data class EpgProgram(
    val id: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
    val placeholder: Boolean = false,
) {
    fun isPlayingAt(timeMs: Long): Boolean = timeMs >= startMs && timeMs < endMs
}

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Err(val message: String) : ApiResult<Nothing>()
}
