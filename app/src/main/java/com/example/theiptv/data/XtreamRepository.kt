package com.example.theiptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class XtreamRepository(
    context: Context,
    private val client: XtreamClient = XtreamClient(),
) {
    private val epgStore by lazy { EpgStore(context) }
    private val epgSyncLock = Mutex()
    private val _epgEpoch = MutableStateFlow(0)
    val epgEpoch: StateFlow<Int> = _epgEpoch
    val epgReady: Boolean
        get() = epgStore.isReady
    var session: XtreamSession? = null
        private set

    var pendingPlayback: PlaybackRequest? = null

    private val catalogLocks = ConcurrentHashMap<ContentType, Mutex>()
    private val categoryCache = ConcurrentHashMap<ContentType, List<Category>>()
    private val itemCache = ConcurrentHashMap<ContentType, List<ContentItem>>()
    private val epgCache = ConcurrentHashMap<String, CachedEpg>()
    private val epgLocks = ConcurrentHashMap<String, Mutex>()
    private val epgGate = Semaphore(4)
    private val vodCache = ConcurrentHashMap<String, ItemDetails>()
    private val vodLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun login(credentials: XtreamCredentials): ApiResult<XtreamSession> = withContext(Dispatchers.IO) {
        when (val result = client.login(credentials)) {
            is ApiResult.Ok -> {
                session = result.data
                result
            }
            is ApiResult.Err -> {
                session = null
                result
            }
        }
    }

    fun logout() {
        session = null
        pendingPlayback = null
        categoryCache.clear()
        itemCache.clear()
        epgCache.clear()
        epgLocks.clear()
        vodCache.clear()
        vodLocks.clear()
        epgStore.clear()
        _epgEpoch.value = 0
    }

    suspend fun categories(type: ContentType): ApiResult<List<Category>> {
        val current = session ?: return ApiResult.Err("Not signed in.")
        categoryCache[type]?.let { return ApiResult.Ok(it) }
        return catalogLock(type).withLock {
            categoryCache[type]?.let { return@withLock ApiResult.Ok(it) }
            withContext(Dispatchers.IO) {
                when (val result = client.categories(current, type)) {
                    is ApiResult.Ok -> {
                        categoryCache[type] = result.data
                        result
                    }
                    is ApiResult.Err -> result
                }
            }
        }
    }

    suspend fun items(type: ContentType): ApiResult<List<ContentItem>> {
        val current = session ?: return ApiResult.Err("Not signed in.")
        itemCache[type]?.let { return ApiResult.Ok(it) }
        return catalogLock(type).withLock {
            itemCache[type]?.let { return@withLock ApiResult.Ok(it) }
            withContext(Dispatchers.IO) {
                when (val result = client.items(current, type)) {
                    is ApiResult.Ok -> {
                        itemCache[type] = result.data
                        result
                    }
                    is ApiResult.Err -> result
                }
            }
        }
    }

    fun cachedCatalog(type: ContentType): CachedCatalog? {
        val categories = categoryCache[type] ?: return null
        val items = itemCache[type] ?: return null
        return CachedCatalog(categories, items)
    }

    fun cachedEpg(streamId: String): List<EpgProgram>? {
        val cached = epgCache[streamId] ?: return null
        if (System.currentTimeMillis() - cached.fetchedAt > EPG_TTL_MS) return null
        return cached.programs
    }

    suspend fun shortEpg(streamId: String): ApiResult<List<EpgProgram>> {
        val current = session ?: return ApiResult.Err("Not signed in.")
        val now = System.currentTimeMillis()
        localEpg(streamId, now - 6L * 60L * 60L * 1000L, now + 12L * 60L * 60L * 1000L)
            ?.takeIf { programs -> programs.any { it.endMs > now } }
            ?.let { return ApiResult.Ok(it) }
        cachedEpg(streamId)?.let { return ApiResult.Ok(it) }
        val lock = epgLocks.getOrPut(streamId) { Mutex() }
        return lock.withLock {
            cachedEpg(streamId)?.let { return@withLock ApiResult.Ok(it) }
            epgGate.withPermit {
                withContext(Dispatchers.IO) {
                    when (val result = client.shortEpg(current, streamId)) {
                        is ApiResult.Ok -> {
                            val programs = coalesceOverlappingProgrammes(result.data)
                            epgCache[streamId] = CachedEpg(System.currentTimeMillis(), programs)
                            ApiResult.Ok(programs)
                        }
                        is ApiResult.Err -> result
                    }
                }
            }
        }
    }

    fun localEpg(channelId: String, startMs: Long, endMs: Long): List<EpgProgram>? {
        if (!epgStore.isReady) return null
        val keys = epgKeys(channelId)
        val rows = epgStore.programs(keys, startMs, endMs)
        if (rows.isEmpty()) return emptyList()
        return coalesceOverlappingProgrammes(
            keys.flatMap { rows[it].orEmpty() }.distinctBy { it.startMs },
        )
    }

    fun localEpgs(channelIds: List<String>, startMs: Long, endMs: Long): Map<String, List<EpgProgram>> {
        if (!epgStore.isReady || channelIds.isEmpty()) return emptyMap()
        val itemsById = itemCache[ContentType.Live]?.associateBy { it.id }
        val keyToChannels = HashMap<String, MutableList<String>>()
        for (channelId in channelIds) {
            for (key in epgKeys(channelId, itemsById)) {
                keyToChannels.getOrPut(key) { mutableListOf() }.add(channelId)
            }
        }
        val rows = epgStore.programs(keyToChannels.keys.toList(), startMs, endMs)
        val merged = HashMap<String, MutableList<EpgProgram>>()
        for ((key, programs) in rows) {
            for (channelId in keyToChannels[key].orEmpty()) {
                merged.getOrPut(channelId) { ArrayList() }.addAll(programs)
            }
        }
        return merged.mapValues { (_, programs) ->
            coalesceOverlappingProgrammes(programs.distinctBy { it.startMs })
        }
    }

    suspend fun syncEpg(force: Boolean = false) {
        val current = session ?: return
        val coverageUntil = System.currentTimeMillis() + EPG_COVERAGE_AHEAD_MS
        if (!force && epgStore.isFresh(EPG_SYNC_TTL_MS, coverageUntil)) return
        epgSyncLock.withLock {
            val stillFreshUntil = System.currentTimeMillis() + EPG_COVERAGE_AHEAD_MS
            if (!force && epgStore.isFresh(EPG_SYNC_TTL_MS, stillFreshUntil)) return
            try {
                withContext(Dispatchers.IO) {
                    items(ContentType.Live)
                    val aliases = xmltvAliases(itemCache[ContentType.Live].orEmpty())
                    client.xmltv(current.credentials) { stream ->
                        epgStore.replaceAll { inserter ->
                            XmltvParser.parse(stream) { channelId, startMs, endMs, title, description ->
                                val ids = aliases[channelId]
                                if (ids == null) {
                                    inserter.add(channelId, startMs, endMs, title, description)
                                } else {
                                    for (id in ids) {
                                        inserter.add(id, startMs, endMs, title, description)
                                    }
                                }
                            }
                        }
                    }
                }
                _epgEpoch.value = _epgEpoch.value + 1
            } catch (_: Exception) {
            }
        }
    }

    private fun epgKeys(
        channelId: String,
        itemsById: Map<String, ContentItem>? = null,
    ): List<String> {
        val item = if (itemsById != null) {
            itemsById[channelId]
        } else {
            itemCache[ContentType.Live]?.firstOrNull { it.id == channelId }
        }
        val extra = item?.epgChannelId?.takeIf { it.isNotBlank() && it != channelId }
        return if (extra == null) listOf(channelId) else listOf(channelId, extra)
    }

    private fun xmltvAliases(items: List<ContentItem>): Map<String, List<String>> {
        val map = HashMap<String, MutableList<String>>()
        for (item in items) {
            map.getOrPut(item.id) { mutableListOf(item.id) }
            val epgId = item.epgChannelId?.takeIf { it.isNotBlank() } ?: continue
            val ids = map.getOrPut(epgId) { mutableListOf() }
            if (item.id !in ids) ids += item.id
        }
        return map
    }

    fun cachedVodDetails(vodId: String): ItemDetails? = vodCache[vodId]

    suspend fun vodDetails(vodId: String): ApiResult<ItemDetails> {
        val current = session ?: return ApiResult.Err("Not signed in.")
        vodCache[vodId]?.let { return ApiResult.Ok(it) }
        val lock = vodLocks.getOrPut(vodId) { Mutex() }
        return lock.withLock {
            vodCache[vodId]?.let { return@withLock ApiResult.Ok(it) }
            epgGate.withPermit {
                withContext(Dispatchers.IO) {
                    when (val result = client.vodDetails(current, vodId)) {
                        is ApiResult.Ok -> {
                            vodCache[vodId] = result.data
                            result
                        }
                        is ApiResult.Err -> result
                    }
                }
            }
        }
    }

    suspend fun seriesDetails(seriesId: String): ApiResult<SeriesDetails> = withContext(Dispatchers.IO) {
        val current = session ?: return@withContext ApiResult.Err("Not signed in.")
        client.seriesDetails(current, seriesId)
    }

    suspend fun refresh(type: ContentType): ApiResult<CachedCatalog> {
        catalogLock(type).withLock {
            categoryCache.remove(type)
            itemCache.remove(type)
        }
        val categories = when (val result = categories(type)) {
            is ApiResult.Ok -> result.data
            is ApiResult.Err -> return result
        }
        val items = when (val result = items(type)) {
            is ApiResult.Ok -> result.data
            is ApiResult.Err -> return result
        }
        return ApiResult.Ok(CachedCatalog(categories, items))
    }

    fun hasCachedItems(type: ContentType): Boolean = itemCache.containsKey(type)

    fun cachedItem(type: ContentType, id: String): ContentItem? {
        return itemCache[type]?.firstOrNull { it.id == id }
    }

    fun searchCached(query: String, limit: Int = 200): List<ContentItem> {
        val needle = query.trim()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        val results = ArrayList<ContentItem>(limit.coerceAtMost(256))
        for (type in SEARCH_ORDER) {
            val items = itemCache[type] ?: continue
            for (item in items) {
                if (item.name.contains(needle, ignoreCase = true)) {
                    results += item
                    if (results.size >= limit) return results
                }
            }
        }
        return results
    }

    fun play(item: ContentItem): PlaybackRequest? {
        val current = session ?: return null
        val request = client.playbackFor(current, item)
        pendingPlayback = request
        return request
    }

    fun play(item: ContentItem, program: EpgProgram?, fromStart: Boolean = false): PlaybackRequest? {
        val current = session ?: return null
        val now = System.currentTimeMillis()
        val live = client.playbackFor(current, item)
        val timeshift = if (program != null && item.hasCatchupFor(program, now)) {
            client.playbackForTimeshift(current, item, program)
        } else {
            null
        }
        val request = if (fromStart && timeshift != null) {
            timeshift
        } else if (timeshift != null && program?.isPlayingAt(now) == true) {
            live.copy(fromStartUrls = timeshift.urls, fromStartTitle = timeshift.title)
        } else if (timeshift != null) {
            timeshift
        } else {
            live
        }.withProgramme(program)
        pendingPlayback = request
        return request
    }

    private fun PlaybackRequest.withProgramme(program: EpgProgram?): PlaybackRequest {
        if (program == null || program.placeholder) return this
        return copy(programmeStartMs = program.startMs, programmeEndMs = program.endMs)
    }

    fun play(seriesTitle: String, episode: SeriesEpisode): PlaybackRequest? {
        val current = session ?: return null
        val request = client.playbackFor(current, seriesTitle, episode)
        pendingPlayback = request
        return request
    }

    private fun catalogLock(type: ContentType): Mutex = catalogLocks.getOrPut(type) { Mutex() }

    data class CachedCatalog(
        val categories: List<Category>,
        val items: List<ContentItem>,
    )

    private data class CachedEpg(
        val fetchedAt: Long,
        val programs: List<EpgProgram>,
    )

    private companion object {
        const val EPG_TTL_MS = 15 * 60 * 1000L
        const val EPG_SYNC_TTL_MS = 3L * 24L * 60L * 60L * 1000L
        const val EPG_COVERAGE_AHEAD_MS = 12L * 60L * 60L * 1000L
        val SEARCH_ORDER = listOf(ContentType.Live, ContentType.Series, ContentType.Movies)
    }
}
