package com.example.theiptv.data

import android.net.Uri
import android.text.Html
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class XtreamClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val xmltvHttp: OkHttpClient = http.newBuilder()
        .readTimeout(8, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build(),
) {
    fun login(credentials: XtreamCredentials): ApiResult<XtreamSession> {
        val parsed = parseCredentials(credentials.server, credentials.username, credentials.password)
            ?: return ApiResult.Err("Enter a server address, username, and password.")
        return try {
            val body = get(parsed, action = null)
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) {
                return ApiResult.Err("This server didn't return a valid Xtream Codes response.")
            }
            val rootObj = root.asJsonObject
            val user = parseUserInfo(rootObj.obj("user_info"))
                ?: return ApiResult.Err("Login failed. Check your Xtream Codes details.")
            if (user.status.equals("Disabled", true) || user.status.equals("Banned", true)) {
                return ApiResult.Err("This account is ${user.status.lowercase()}.")
            }
            ApiResult.Ok(XtreamSession(parsed, user, parseServerTimeZone(rootObj.obj("server_info"))))
        } catch (error: Throwable) {
            ApiResult.Err(humanize(error))
        }
    }

    fun categories(session: XtreamSession, type: ContentType): ApiResult<List<Category>> {
        return try {
            val categories = streamArray(session.credentials, type.categoriesAction) { reader ->
                val parsed = ArrayList<Category>(64)
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue()
                        continue
                    }
                    val category = readCategory(reader)
                    if (category.id.isNotBlank()) parsed += category
                }
                parsed
            }
            ApiResult.Ok(listOf(Category.All) + categories)
        } catch (error: Throwable) {
            ApiResult.Err(humanize(error))
        }
    }

    fun items(session: XtreamSession, type: ContentType): ApiResult<List<ContentItem>> {
        return try {
            val items = streamArray(session.credentials, type.streamsAction) { reader ->
                val parsed = ArrayList<ContentItem>(4_096)
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue()
                        continue
                    }
                    val item = readContentItem(reader, type)
                    if (item != null) parsed += item
                }
                parsed
            }
            ApiResult.Ok(items)
        } catch (error: Throwable) {
            ApiResult.Err(humanize(error))
        }
    }

    fun <T> xmltv(credentials: XtreamCredentials, parse: (InputStream) -> T): T {
        val base = credentials.server.toHttpUrlOrNull()
            ?: throw IOException("The server address isn't a valid URL.")
        val url = base.newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/xml,text/xml,application/gzip,*/*")
            .header("Accept-Encoding", "gzip")
            .get()
            .build()
        xmltvHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}.")
            }
            val body = response.body ?: throw IOException("The server returned an empty EPG.")
            val peeked = BufferedInputStream(body.byteStream())
            peeked.mark(2)
            val header = byteArrayOf(peeked.read().toByte(), peeked.read().toByte())
            peeked.reset()
            val stream: InputStream = if (header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
                GZIPInputStream(peeked)
            } else {
                peeked
            }
            return parse(stream)
        }
    }

    fun shortEpg(session: XtreamSession, streamId: String, limit: Int = 50): ApiResult<List<EpgProgram>> {
        return try {
            val nearby = fetchEpgListings(
                session.credentials,
                "get_short_epg",
                mapOf("stream_id" to streamId, "limit" to limit.toString()),
                session.timeZone,
            )
            val programs = nearby.ifEmpty {
                fetchEpgListings(
                    session.credentials,
                    "get_simple_data_table",
                    mapOf("stream_id" to streamId),
                    session.timeZone,
                )
            }
            ApiResult.Ok(programs.sortedBy { it.startMs }.distinctBy { "${it.startMs}-${it.title}" })
        } catch (error: Throwable) {
            ApiResult.Err(humanize(error))
        }
    }

    private fun fetchEpgListings(
        credentials: XtreamCredentials,
        action: String,
        extra: Map<String, String>,
        timeZone: TimeZone,
    ): List<EpgProgram> {
        val body = get(credentials, action, extra)
        val root = JsonParser.parseString(body)
        return epgListingObjects(root).mapNotNull { readEpgProgram(it, timeZone) }
    }

    private fun epgListingObjects(root: JsonElement): List<JsonObject> {
        val element = when {
            root.isJsonArray -> root
            root.isJsonObject -> {
                val obj = root.asJsonObject
                obj.get("epg_listings")
                    ?: obj.get("listings")
                    ?: obj.get("epg")
                    ?: obj.obj("js")?.get("epg_listings")
                    ?: obj.get("data")
            }
            else -> null
        } ?: return emptyList()
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { item ->
                if (item != null && item.isJsonObject) item.asJsonObject else null
            }
        }
        if (element.isJsonObject) {
            return element.asJsonObject.entrySet().mapNotNull { entry ->
                val value = entry.value
                if (value != null && value.isJsonObject) value.asJsonObject else null
            }
        }
        return emptyList()
    }

    fun vodDetails(session: XtreamSession, vodId: String): ApiResult<ItemDetails> {
        return try {
            val body = get(session.credentials, "get_vod_info", mapOf("vod_id" to vodId))
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) {
                return ApiResult.Err("Couldn't load this movie.")
            }
            val obj = root.asJsonObject
            val info = obj.obj("info") ?: JsonObject()
            val movie = obj.obj("movie_data") ?: obj.obj("movie") ?: JsonObject()
            ApiResult.Ok(
                ItemDetails(
                    plot = cleanPlot(
                        info.str("plot").ifBlank { info.str("description") }.ifBlank { info.str("overview") }
                    ),
                    year = extractYear(
                        info.str("year"),
                        info.str("releasedate").ifBlank { info.str("releaseDate") }.ifBlank { info.str("release_date") },
                    ),
                    genre = info.str("genre").ifBlank { null },
                    duration = info.str("duration").ifBlank { info.str("episode_run_time") }.ifBlank { null },
                    rating = info.str("rating").ifBlank { movie.str("rating") }.ifBlank { null },
                    director = info.str("director").ifBlank { null },
                    cast = info.str("cast").ifBlank { info.str("actors") }.ifBlank { null },
                    country = info.str("country").ifBlank { null },
                    posterUrl = info.str("movie_image")
                        .ifBlank { info.str("cover_big") }
                        .ifBlank { movie.str("stream_icon") }
                        .ifBlank { null },
                    backdropUrl = firstImageUrl(info, "backdrop_path", "backdrop", "movie_backdrop")
                        .ifBlank { firstImageUrl(movie, "backdrop_path", "backdrop") }
                        .ifBlank { null },
                    title = movie.str("name").ifBlank { info.str("name") }.ifBlank { info.str("o_name") }.ifBlank { null },
                    containerExtension = movie.str("container_extension").ifBlank { null },
                )
            )
        } catch (error: Throwable) {
            ApiResult.Err(humanize(error))
        }
    }

    fun seriesDetails(session: XtreamSession, seriesId: String): ApiResult<SeriesDetails> {
        return try {
            val body = get(session.credentials, "get_series_info", mapOf("series_id" to seriesId))
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) {
                return ApiResult.Err("Couldn't load this series.")
            }
            ApiResult.Ok(parseSeriesDetails(seriesId, root.asJsonObject))
        } catch (error: Throwable) {
            ApiResult.Err(humanize(error))
        }
    }

    fun playbackForTimeshift(
        session: XtreamSession,
        item: ContentItem,
        program: EpgProgram,
    ): PlaybackRequest {
        val durationMin = ((program.endMs - program.startMs) / 60_000L).coerceAtLeast(1L)
        val urls = timeshiftUrls(session.credentials, item.id, program.startMs, durationMin)
        return PlaybackRequest("${item.name}  ·  ${program.title}", urls)
    }

    fun playbackFor(session: XtreamSession, item: ContentItem): PlaybackRequest {
        val creds = session.credentials
        val urls = when (item.type) {
            ContentType.Live -> liveUrls(creds, item.id, session.userInfo.allowedOutputFormats)
            ContentType.Movies -> listOf(pathUrl(creds, "movie", "${item.id}.${item.containerExtension ?: "mp4"}"))
            ContentType.Series -> emptyList()
        }
        return PlaybackRequest(item.name, urls.filter { it.isNotBlank() })
    }

    fun playbackFor(session: XtreamSession, seriesTitle: String, episode: SeriesEpisode): PlaybackRequest {
        val ext = episode.extension.ifBlank { "mp4" }
        return PlaybackRequest(
            title = "$seriesTitle · ${episode.title}",
            urls = listOf(pathUrl(session.credentials, "series", "${episode.id}.$ext")),
        )
    }

    private fun readCategory(reader: JsonReader): Category {
        var id = ""
        var name = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "category_id", "id" -> if (id.isBlank()) id = reader.nextFlexibleString()
                "category_name", "name" -> if (name.isBlank()) name = reader.nextFlexibleString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Category(id = id, name = name.ifBlank { "Untitled" })
    }

    private fun readContentItem(reader: JsonReader, type: ContentType): ContentItem? {
        var streamId = ""
        var seriesId = ""
        var genericId = ""
        var name = ""
        var title = ""
        var streamIcon = ""
        var cover = ""
        var categoryIds = emptyList<String>()
        var extension: String? = null
        var rating = ""
        var plot = ""
        var description = ""
        var year = ""
        var releaseDate = ""
        var genre = ""
        var duration = ""
        var tvArchive = false
        var tvArchiveDays = 0
        var epgChannelId = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = reader.nextFlexibleString()
                "series_id" -> seriesId = reader.nextFlexibleString()
                "id" -> genericId = reader.nextFlexibleString()
                "name" -> name = reader.nextFlexibleString()
                "title" -> title = reader.nextFlexibleString()
                "stream_icon" -> streamIcon = reader.nextFlexibleString()
                "cover" -> cover = reader.nextFlexibleString()
                "category_id", "category_ids" -> categoryIds = reader.nextStringList()
                "container_extension" -> extension = reader.nextFlexibleString().ifBlank { null }
                "rating", "rating_5based" -> {
                    val value = reader.nextFlexibleString()
                    if (rating.isBlank()) rating = value
                }
                "plot", "overview" -> {
                    val value = reader.nextFlexibleString()
                    if (plot.isBlank()) plot = value
                }
                "description" -> description = reader.nextFlexibleString()
                "year" -> year = reader.nextFlexibleString()
                "releaseDate", "releasedate", "release_date" -> releaseDate = reader.nextFlexibleString()
                "genre" -> genre = reader.nextFlexibleString()
                "duration", "episode_run_time" -> {
                    val value = reader.nextFlexibleString()
                    if (duration.isBlank()) duration = value
                }
                "info" -> {
                    val extra = readNestedInfo(reader)
                    if (plot.isBlank()) plot = extra.plot
                    if (description.isBlank()) description = extra.description
                    if (year.isBlank()) year = extra.year
                    if (releaseDate.isBlank()) releaseDate = extra.releaseDate
                    if (genre.isBlank()) genre = extra.genre
                    if (duration.isBlank()) duration = extra.duration
                    if (rating.isBlank()) rating = extra.rating
                    if (cover.isBlank()) cover = extra.cover
                }
                "tv_archive" -> tvArchive = isTruthy(reader.nextFlexibleString()) || tvArchive
                "tv_archive_duration" -> {
                    val days = reader.nextFlexibleString().toIntOrNull() ?: 0
                    if (days > 0) {
                        tvArchiveDays = days
                        tvArchive = true
                    }
                }
                "catchup" -> {
                    val value = reader.nextFlexibleString()
                    if (isTruthy(value) || value.equals("default", true) || value.equals("shift", true)) {
                        tvArchive = true
                    }
                }
                "catchup_duration" -> {
                    val days = reader.nextFlexibleString().toIntOrNull() ?: 0
                    if (days > 0) {
                        tvArchiveDays = days
                        tvArchive = true
                    }
                }
                "epg_channel_id", "epgid", "tvg_id", "tvg-id" -> {
                    if (epgChannelId.isBlank()) epgChannelId = reader.nextFlexibleString()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val id = when (type) {
            ContentType.Series -> seriesId.ifBlank { genericId }
            else -> streamId.ifBlank { genericId }
        }
        if (id.isBlank()) return null
        val poster = when (type) {
            ContentType.Series -> cover.ifBlank { streamIcon }
            else -> streamIcon.ifBlank { cover }
        }
        return ContentItem(
            id = id,
            name = name.ifBlank { title }.ifBlank { "Untitled" },
            posterUrl = poster,
            categoryIds = categoryIds,
            type = type,
            containerExtension = extension,
            rating = rating.ifBlank { null },
            plot = cleanPlot(plot.ifBlank { description }),
            year = extractYear(year, releaseDate),
            genre = genre.ifBlank { null },
            duration = duration.ifBlank { null },
            tvArchive = tvArchive,
            tvArchiveDurationDays = if (tvArchive) tvArchiveDays.coerceAtLeast(1) else 0,
            epgChannelId = epgChannelId.ifBlank { null },
        )
    }

    private fun readNestedInfo(reader: JsonReader): NestedInfo {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return NestedInfo()
        }
        var plot = ""
        var description = ""
        var year = ""
        var releaseDate = ""
        var genre = ""
        var duration = ""
        var rating = ""
        var cover = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "plot", "overview" -> {
                    val value = reader.nextFlexibleString()
                    if (plot.isBlank()) plot = value
                }
                "description" -> description = reader.nextFlexibleString()
                "year" -> year = reader.nextFlexibleString()
                "releaseDate", "releasedate", "release_date" -> releaseDate = reader.nextFlexibleString()
                "genre" -> genre = reader.nextFlexibleString()
                "duration", "episode_run_time" -> {
                    val value = reader.nextFlexibleString()
                    if (duration.isBlank()) duration = value
                }
                "rating" -> {
                    val value = reader.nextFlexibleString()
                    if (rating.isBlank()) rating = value
                }
                "movie_image", "cover_big", "cover" -> {
                    val value = reader.nextFlexibleString()
                    if (cover.isBlank()) cover = value
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return NestedInfo(plot, description, year, releaseDate, genre, duration, rating, cover)
    }

    private data class NestedInfo(
        val plot: String = "",
        val description: String = "",
        val year: String = "",
        val releaseDate: String = "",
        val genre: String = "",
        val duration: String = "",
        val rating: String = "",
        val cover: String = "",
    )

    private fun firstImageUrl(obj: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val element = obj.get(key) ?: continue
            val url = firstImageUrl(element)
            if (url.isNotBlank()) return url
        }
        return ""
    }

    private fun firstImageUrl(element: JsonElement): String {
        if (element.isJsonNull) return ""
        if (element.isJsonArray) {
            element.asJsonArray.forEach { item ->
                if (item != null) {
                    val url = firstImageUrl(item)
                    if (url.isNotBlank()) return url
                }
            }
            return ""
        }
        if (element.isJsonObject) {
            val nested = element.asJsonObject
            return normalizeImageUrl(
                nested.str("url").ifBlank { nested.str("file_path") }.ifBlank { nested.str("path") }
            )
        }
        return normalizeImageUrl(element.asFlexibleString())
    }

    private fun normalizeImageUrl(raw: String): String {
        val value = raw.trim().trim('"')
        if (value.isEmpty() || value.equals("null", true)) return ""
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        if (value.startsWith("/")) return "https://image.tmdb.org/t/p/original$value"
        return value
    }

    private fun extractYear(year: String, releaseDate: String): String? {
        val direct = year.trim()
        if (direct.length >= 4 && direct.take(4).all { it.isDigit() }) {
            val value = direct.take(4)
            if (value.startsWith("19") || value.startsWith("20")) return value
        }
        return Regex("(19|20)\\d{2}").find(releaseDate)?.value
    }

    private fun cleanPlot(raw: String): String? {
        val stripped = raw
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;|&apos;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
        return stripped.ifBlank { null }
    }

    private fun parseSeriesDetails(seriesId: String, root: JsonObject): SeriesDetails {
        val info = root.obj("info") ?: JsonObject()
        val seasons = parseSeasons(root)
        return SeriesDetails(
            id = seriesId,
            title = info.str("name").ifBlank { info.str("title") }.ifBlank { "Series" },
            plot = info.str("plot").ifBlank { info.str("description") },
            cover = info.str("cover").ifBlank { info.str("movie_image") },
            backdropUrl = firstImageUrl(info, "backdrop_path", "backdrop", "movie_backdrop"),
            genre = info.str("genre"),
            rating = info.str("rating"),
            cast = info.str("cast"),
            director = info.str("director"),
            seasons = seasons,
        )
    }

    private fun parseSeasons(root: JsonObject): List<SeriesSeason> {
        val grouped = linkedMapOf<Int, MutableList<SeriesEpisode>>()
        val episodes = root.get("episodes")
        if (episodes != null && episodes.isJsonObject) {
            for ((seasonKey, value) in episodes.asJsonObject.entrySet()) {
                val seasonNumber = seasonKey.toIntOrNull() ?: continue
                if (value == null || !value.isJsonArray) continue
                val list = grouped.getOrPut(seasonNumber) { mutableListOf() }
                value.asJsonArray.forEach { item ->
                    if (item != null && item.isJsonObject) {
                        list += parseEpisode(item.asJsonObject, seasonNumber)
                    }
                }
            }
        } else if (episodes != null && episodes.isJsonArray) {
            episodes.asJsonArray.forEach { item ->
                if (item != null && item.isJsonObject) {
                    val obj = item.asJsonObject
                    val seasonNumber = obj.int("season").takeIf { it > 0 } ?: 1
                    grouped.getOrPut(seasonNumber) { mutableListOf() } += parseEpisode(obj, seasonNumber)
                }
            }
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { (number, eps) ->
                SeriesSeason(
                    number = number,
                    episodes = eps.sortedBy { it.number }.distinctBy { it.id },
                )
            }
    }

    private fun parseEpisode(obj: JsonObject, seasonNumber: Int): SeriesEpisode {
        val info = obj.obj("info")
        val number = obj.int("episode_num").takeIf { it > 0 } ?: obj.int("episode")
        val title = listOf(
            obj.str("title"),
            obj.str("name"),
            obj.str("episode_name"),
            info?.str("name").orEmpty(),
            info?.str("title").orEmpty(),
        ).firstOrNull { candidate ->
            candidate.isNotBlank() && candidate != number.toString()
        } ?: "Episode $number"
        return SeriesEpisode(
            id = obj.str("id").ifBlank { obj.str("episode_id") },
            number = number,
            title = title,
            extension = obj.str("container_extension").ifBlank { "mp4" },
            duration = info?.str("duration").orEmpty().ifBlank { info?.str("duration_secs").orEmpty() },
            plot = info?.str("plot").orEmpty().ifBlank { info?.str("description").orEmpty() },
            image = info?.str("movie_image").orEmpty().ifBlank { obj.str("cover") },
            director = info?.str("director").orEmpty(),
            cast = info?.str("cast").orEmpty().ifBlank { info?.str("actors").orEmpty() },
        )
    }

    private fun readEpgProgram(obj: JsonObject, timeZone: TimeZone): EpgProgram? {
        val startMs = firstEpgTime(
            timeZone,
            unixCandidates = arrayOf(obj.str("start_timestamp")),
            dateCandidates = arrayOf(obj.str("start"), obj.str("begin"), obj.str("start_time")),
        )
        var endMs = firstEpgTime(
            timeZone,
            unixCandidates = arrayOf(obj.str("stop_timestamp"), obj.str("end_timestamp")),
            dateCandidates = arrayOf(obj.str("end"), obj.str("stop"), obj.str("stop_time")),
        )
        if (startMs <= 0L) return null
        if (endMs <= startMs) endMs = startMs + 30 * 60 * 1000L
        val title = decodeEpgText(
            obj.str("title").ifBlank { obj.str("name") }.ifBlank { obj.str("programme") }
        ).ifBlank { "Unknown programme" }
        return EpgProgram(
            id = obj.str("id").ifBlank { obj.str("epg_id") }.ifBlank { "$startMs-$endMs" },
            title = title,
            description = decodeEpgText(obj.str("description").ifBlank { obj.str("plot") }.ifBlank { obj.str("desc") }),
            startMs = startMs,
            endMs = endMs,
        )
    }

    private fun firstEpgTime(
        timeZone: TimeZone,
        unixCandidates: Array<String>,
        dateCandidates: Array<String>,
    ): Long {
        val localZone = TimeZone.getDefault()
        for (raw in dateCandidates) {
            val parsed = parseEpgDateTime(raw, localZone)
            if (parsed > 0L) return parsed
        }
        if (timeZone.id != localZone.id) {
            for (raw in dateCandidates) {
                val parsed = parseEpgDateTime(raw, timeZone)
                if (parsed > 0L) return parsed
            }
        }
        for (raw in unixCandidates) {
            val unix = parseUnixTime(raw)
            if (unix > 0L) return unix
            val parsed = parseEpgDateTime(raw, localZone)
            if (parsed > 0L) return parsed
        }
        return 0L
    }

    private fun parseUnixTime(raw: String): Long {
        val value = raw.trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return 0L
        val numeric = value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong() ?: return 0L
        return when {
            numeric in 1_000_000_000L..9_999_999_999L -> numeric * 1000L
            numeric in 100_000_000_000L..9_999_999_999_999L -> numeric
            else -> 0L
        }
    }

    private fun parseEpgDateTime(raw: String, timeZone: TimeZone): Long {
        val value = raw.trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return 0L
        val normalized = value.replace("T", " ")
        val offsetPatterns = arrayOf(
            "yyyy-MM-dd HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ssZ",
            "yyyy-MM-dd HH:mmX",
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmssZ",
        )
        for (pattern in offsetPatterns) {
            parseWith(pattern, value, TimeZone.getTimeZone("UTC"))?.let { return it }
            if (normalized != value) {
                parseWith(pattern, normalized, TimeZone.getTimeZone("UTC"))?.let { return it }
            }
        }
        val naivePatterns = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
        )
        for (pattern in naivePatterns) {
            parseWith(pattern, value, timeZone)?.let { return it }
            if (normalized != value) {
                parseWith(pattern, normalized, timeZone)?.let { return it }
            }
        }
        return 0L
    }

    private fun parseWith(pattern: String, raw: String, zone: TimeZone): Long? {
        return try {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = false
            format.timeZone = zone
            format.parse(raw)?.time?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseServerTimeZone(server: JsonObject?): TimeZone {
        val name = server?.str("timezone")?.trim().orEmpty()
        if (name.isEmpty()) return TimeZone.getDefault()
        val zone = TimeZone.getTimeZone(name)
        val looksUnknown = zone.id == "GMT" &&
            !name.equals("GMT", true) &&
            !name.equals("UTC", true) &&
            !name.equals("Etc/GMT", true)
        return if (looksUnknown) TimeZone.getDefault() else zone
    }

    private fun decodeEpgText(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        val decoded = try {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (_: Exception) {
            return cleanEpgHtml(value)
        }
        if (decoded.isEmpty()) return cleanEpgHtml(value)
        val binary = decoded.any { ch -> ch.code < 9 || (ch.code in 14..31) }
        return cleanEpgHtml(if (binary) value else decoded)
    }

    private fun cleanEpgHtml(raw: String): String {
        if (raw.isEmpty()) return ""
        if (!raw.contains('<') && !raw.contains('&')) return raw.trim()
        var value = raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>\\s*"), "\n")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("<[^>]*$"), "")
        value = try {
            Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Exception) {
            value
        }
        return value
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n+ *"), "\n")
            .trim()
    }

    private fun parseUserInfo(obj: JsonObject?): UserInfo? {
        if (obj == null) return null
        val auth = obj.get("auth")?.asBoolish() ?: false
        if (!auth) return null
        val formats = obj.arr("allowed_output_formats")
            ?.mapNotNull { it.asFlexibleString().ifBlank { null } }
            .orEmpty()
        return UserInfo(
            username = obj.str("username"),
            status = obj.str("status").ifBlank { "Active" },
            expDateSeconds = obj.longOrNull("exp_date"),
            isTrial = obj.boolish("is_trial"),
            activeConnections = obj.int("active_cons"),
            maxConnections = obj.int("max_connections"),
            allowedOutputFormats = formats,
            message = obj.str("message"),
        )
    }

    private fun get(
        credentials: XtreamCredentials,
        action: String?,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val base = credentials.server.toHttpUrlOrNull()
            ?: throw IOException("The server address isn't a valid URL.")
        val builder = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
        if (!action.isNullOrBlank()) {
            builder.addQueryParameter("action", action)
        }
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}.")
            }
            if (body.isBlank()) {
                throw IOException("The server returned an empty response.")
            }
            if (body.trimStart().startsWith("<")) {
                throw IOException("This server didn't return a valid Xtream Codes response.")
            }
            return body
        }
    }

    private inline fun <T> streamArray(
        credentials: XtreamCredentials,
        action: String,
        extra: Map<String, String> = emptyMap(),
        parse: (JsonReader) -> T,
    ): T {
        val base = credentials.server.toHttpUrlOrNull()
            ?: throw IOException("The server address isn't a valid URL.")
        val builder = base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .addQueryParameter("action", action)
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}.")
            }
            val body = response.body ?: throw IOException("The server returned an empty response.")
            JsonReader(body.charStream()).use { reader ->
                reader.setStrictness(Strictness.LENIENT)
                return when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        val parsed = parse(reader)
                        if (reader.hasNext()) {
                            while (reader.hasNext()) reader.skipValue()
                        }
                        reader.endArray()
                        parsed
                    }
                    else -> throw IOException("This server didn't return a valid Xtream Codes list.")
                }
            }
        }
    }

    private fun liveUrls(
        credentials: XtreamCredentials,
        streamId: String,
        formats: List<String>,
    ): List<String> {
        val preferred = when {
            formats.any { it.equals("m3u8", true) } -> listOf("m3u8", "ts")
            formats.any { it.equals("ts", true) } -> listOf("ts", "m3u8")
            else -> listOf("m3u8", "ts")
        }
        val urls = preferred.map { ext -> pathUrl(credentials, "live", "$streamId.$ext") }.toMutableList()
        val shortForm = pathUrl(credentials, pathPrefix = null, lastSegment = streamId)
        if (shortForm.isNotBlank()) urls += shortForm
        return urls.distinct()
    }

    private fun timeshiftUrls(
        credentials: XtreamCredentials,
        streamId: String,
        startMs: Long,
        durationMin: Long,
    ): List<String> {
        val base = credentials.server.trimEnd('/')
        val user = credentials.username
        val pass = credentials.password
        val duration = durationMin.toString()
        val stamps = listOf(
            formatTimeshiftStart(startMs, "yyyy-MM-dd:HH-mm", TimeZone.getDefault()),
            formatTimeshiftStart(startMs, "yyyy-MM-dd:HH-mm", TimeZone.getTimeZone("UTC")),
            formatTimeshiftStart(startMs, "yyyyMMdd-HHmm", TimeZone.getDefault()),
        ).distinct()
        val urls = ArrayList<String>(stamps.size * 2)
        for (start in stamps) {
            urls += "$base/timeshift/$user/$pass/$duration/$start/$streamId.ts"
            urls += "$base/timeshift/$user/$pass/$duration/$start/$streamId.m3u8"
        }
        return urls
    }

    private fun formatTimeshiftStart(startMs: Long, pattern: String, zone: TimeZone): String {
        val format = SimpleDateFormat(pattern, Locale.US)
        format.timeZone = zone
        return format.format(java.util.Date(startMs))
    }

    private fun isTruthy(value: String): Boolean {
        val raw = value.trim()
        return raw == "1" || raw.equals("true", true) || raw.equals("yes", true)
    }

    private fun pathUrl(
        credentials: XtreamCredentials,
        pathPrefix: String?,
        lastSegment: String,
    ): String {
        val base = credentials.server.toHttpUrlOrNull() ?: return ""
        val builder = base.newBuilder()
        if (!pathPrefix.isNullOrBlank()) builder.addPathSegment(pathPrefix)
        builder.addPathSegment(credentials.username)
        builder.addPathSegment(credentials.password)
        builder.addPathSegment(lastSegment)
        return builder.build().toString()
    }

    private fun humanize(error: Throwable): String {
        return when (error) {
            is OutOfMemoryError -> "This catalog is too large to load on this device."
            is UnknownHostException -> "Can't reach that server. Check the address."
            is SocketTimeoutException -> "The server took too long to respond."
            is SSLException -> "Secure connection failed. Try http:// instead of https://."
            is IOException -> error.message ?: "Couldn't connect to the server."
            else -> error.message ?: "Something went wrong."
        }
    }

    companion object {
        const val USER_AGENT = "TheIPTV/1.0 (Android)"

        fun parseCredentials(server: String, username: String, password: String): XtreamCredentials? {
            val raw = server.trim()
            if (raw.isEmpty()) return null
            val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
                raw
            } else {
                "http://$raw"
            }
            val uri = Uri.parse(withScheme)
            val host = uri.host
            if (host.isNullOrBlank()) return null
            val extractedUser = username.trim().ifBlank { uri.getQueryParameter("username").orEmpty() }
            val extractedPass = password.trim().ifBlank { uri.getQueryParameter("password").orEmpty() }
            if (extractedUser.isBlank() || extractedPass.isBlank()) return null
            val path = uri.path.orEmpty()
                .trimEnd('/')
                .replace(Regex("(?i)/(player_api\\.php|get\\.php|xmltv\\.php)$"), "")
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            val base = buildString {
                append(uri.scheme ?: "http")
                append("://")
                append(host)
                append(portPart)
                append(path)
            }.trimEnd('/')
            base.toHttpUrlOrNull() ?: return null
            return XtreamCredentials(base, extractedUser, extractedPass)
        }
    }
}
