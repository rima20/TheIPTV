package com.example.theiptv.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.atomic.AtomicBoolean

class EpgStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val ready = AtomicBoolean(false)

    init {
        setWriteAheadLoggingEnabled(true)
        ready.set(hasProgrammes())
    }

    val isReady: Boolean
        get() = ready.get()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE programmes (
              channel_id TEXT NOT NULL,
              start_ms INTEGER NOT NULL,
              end_ms INTEGER NOT NULL,
              title TEXT NOT NULL,
              description TEXT NOT NULL,
              PRIMARY KEY (channel_id, start_ms)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_epg_window ON programmes(channel_id, start_ms, end_ms)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS programmes")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    fun lastSyncAt(): Long = meta("last_sync")?.toLongOrNull() ?: 0L

    fun isFresh(ttlMs: Long, minCoverageUntilMs: Long): Boolean {
        if (!isReady) return false
        val synced = lastSyncAt()
        if (synced <= 0L || System.currentTimeMillis() - synced >= ttlMs) return false
        // A sync can still be inside the multi-day TTL after the file's listings
        // have ended. This provider's XMLTV window is only about two days long.
        return coverageEndMs() >= minCoverageUntilMs
    }

    fun programs(channelId: String, startMs: Long, endMs: Long): List<EpgProgram> {
        return programs(listOf(channelId), startMs, endMs)[channelId].orEmpty()
    }

    fun programs(channelIds: List<String>, startMs: Long, endMs: Long): Map<String, List<EpgProgram>> {
        if (channelIds.isEmpty()) return emptyMap()
        val result = HashMap<String, MutableList<EpgProgram>>(channelIds.size)
        for (chunk in channelIds.distinct().chunked(80)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val args = Array(chunk.size + 2) { index ->
                when {
                    index < chunk.size -> chunk[index]
                    index == chunk.size -> startMs.toString()
                    else -> endMs.toString()
                }
            }
            readableDatabase.rawQuery(
                """
                SELECT channel_id, start_ms, end_ms, title, description
                FROM programmes
                WHERE channel_id IN ($placeholders)
                  AND end_ms > ?
                  AND start_ms < ?
                ORDER BY channel_id, start_ms
                """.trimIndent(),
                args,
            ).use { cursor ->
                val idCol = cursor.getColumnIndex("channel_id")
                val startCol = cursor.getColumnIndex("start_ms")
                val endCol = cursor.getColumnIndex("end_ms")
                val titleCol = cursor.getColumnIndex("title")
                val descCol = cursor.getColumnIndex("description")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    result.getOrPut(id) { ArrayList() }.add(
                        EpgProgram(
                            id = "$id-${cursor.getLong(startCol)}",
                            title = cursor.getString(titleCol),
                            description = cursor.getString(descCol),
                            startMs = cursor.getLong(startCol),
                            endMs = cursor.getLong(endCol),
                        )
                    )
                }
            }
        }
        return result
    }

    fun replaceAll(block: (Inserter) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM programmes")
            val inserter = Inserter(db)
            block(inserter)
            inserter.flush()
            val syncedAt = System.currentTimeMillis().toString()
            db.replace(
                "meta",
                null,
                ContentValues().apply {
                    put("key", "last_sync")
                    put("value", syncedAt)
                },
            )
            db.replace(
                "meta",
                null,
                ContentValues().apply {
                    put("key", "coverage_end")
                    put("value", inserter.coverageEndMs().toString())
                },
            )
            db.setTransactionSuccessful()
            ready.set(inserter.inserted > 0)
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        writableDatabase.execSQL("DELETE FROM programmes")
        writableDatabase.execSQL("DELETE FROM meta")
        ready.set(false)
    }

    private fun hasProgrammes(): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM programmes LIMIT 1", null).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun meta(key: String): String? {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun coverageEndMs(): Long {
        meta("coverage_end")?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        val channels = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT channel_id) FROM programmes",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (channels <= 0) return 0L
        return readableDatabase.rawQuery(
            """
            SELECT MAX(end_ms) FROM programmes
            GROUP BY channel_id
            ORDER BY MAX(end_ms)
            LIMIT 1 OFFSET ?
            """.trimIndent(),
            arrayOf((channels / 2).toString()),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }
    }

    class Inserter(private val db: SQLiteDatabase) {
        private val statement = db.compileStatement(
            "INSERT OR REPLACE INTO programmes(channel_id, start_ms, end_ms, title, description) VALUES(?,?,?,?,?)"
        )
        var inserted: Int = 0
            private set
        private val channelEnds = HashMap<String, Long>(4_096)

        fun add(channelId: String, startMs: Long, endMs: Long, title: String, description: String) {
            if (channelId.isBlank() || endMs <= startMs) return
            statement.clearBindings()
            statement.bindString(1, channelId)
            statement.bindLong(2, startMs)
            statement.bindLong(3, endMs)
            statement.bindString(4, title)
            statement.bindString(5, description)
            statement.executeInsert()
            inserted++
            val previous = channelEnds[channelId]
            if (previous == null || endMs > previous) channelEnds[channelId] = endMs
        }

        fun coverageEndMs(): Long {
            if (channelEnds.isEmpty()) return 0L
            val ends = channelEnds.values.toLongArray()
            ends.sort()
            return ends[ends.size / 2]
        }

        fun flush() = Unit
    }

    private companion object {
        const val DB_NAME = "epg.db"
        const val DB_VERSION = 1
    }
}
