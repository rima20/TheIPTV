package com.example.theiptv.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.xtreamStore: DataStore<Preferences> by preferencesDataStore(name = "xtream_codes")

class CredentialsStore(context: Context) {
    private val store = context.applicationContext.xtreamStore
    private val gson = Gson()

    suspend fun loadAll(): ProviderSnapshot {
        val prefs = store.data.first()
        val json = prefs[PROVIDERS].orEmpty()
        if (json.isNotBlank()) {
            val parsed = runCatching { gson.fromJson(json, ProviderSnapshot::class.java) }.getOrNull()
            if (parsed != null) {
                val providers = parsed.providers.orEmpty().filter {
                    it.server.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank()
                }
                return ProviderSnapshot(providers, parsed.activeId)
            }
        }
        val legacy = loadLegacy(prefs) ?: return ProviderSnapshot()
        val snapshot = ProviderSnapshot(providers = listOf(legacy), activeId = legacy.id)
        persist(snapshot)
        return snapshot
    }

    suspend fun load(): XtreamCredentials? = loadAll().active?.credentials

    suspend fun upsert(provider: IptvProvider, makeActive: Boolean = true): IptvProvider {
        val snapshot = loadAll()
        val existing = snapshot.providers.firstOrNull { sameAccount(it, provider) }
        val saved = if (existing != null) {
            existing.copy(
                name = provider.name.ifBlank { existing.name },
                server = provider.server,
                username = provider.username,
                password = provider.password,
            )
        } else {
            provider.copy(id = provider.id.ifBlank { UUID.randomUUID().toString() })
        }
        val providers = if (existing != null) {
            snapshot.providers.map { if (it.id == existing.id) saved else it }
        } else {
            snapshot.providers + saved
        }
        persist(
            ProviderSnapshot(
                providers = providers,
                activeId = if (makeActive) saved.id else snapshot.activeId,
            )
        )
        return saved
    }

    suspend fun setActive(id: String) {
        val snapshot = loadAll()
        if (snapshot.providers.none { it.id == id }) return
        persist(snapshot.copy(activeId = id))
    }

    suspend fun remove(id: String): ProviderSnapshot {
        val snapshot = loadAll()
        val remaining = snapshot.providers.filterNot { it.id == id }
        val activeId = when {
            snapshot.activeId != id -> snapshot.activeId
            else -> remaining.firstOrNull()?.id
        }
        val next = ProviderSnapshot(remaining, activeId)
        persist(next)
        return next
    }

    suspend fun save(credentials: XtreamCredentials) {
        upsert(providerFrom(credentials), makeActive = true)
    }

    suspend fun clear() {
        persist(ProviderSnapshot())
    }

    private suspend fun persist(snapshot: ProviderSnapshot) {
        store.edit { prefs ->
            prefs[PROVIDERS] = gson.toJson(snapshot)
            prefs.remove(SERVER)
            prefs.remove(USERNAME)
            prefs.remove(PASSWORD)
        }
    }

    private fun loadLegacy(prefs: Preferences): IptvProvider? {
        val server = prefs[SERVER].orEmpty()
        val username = prefs[USERNAME].orEmpty()
        val password = prefs[PASSWORD].orEmpty()
        if (server.isBlank() || username.isBlank() || password.isBlank()) return null
        return providerFrom(XtreamCredentials(server, username, password))
    }

    private fun sameAccount(left: IptvProvider, right: IptvProvider): Boolean {
        return left.server.trim().trimEnd('/') == right.server.trim().trimEnd('/') &&
            left.username.trim() == right.username.trim()
    }

    private companion object {
        val PROVIDERS = stringPreferencesKey("providers")
        val SERVER = stringPreferencesKey("server")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }
}

fun providerFrom(
    credentials: XtreamCredentials,
    name: String = "",
    id: String = UUID.randomUUID().toString(),
): IptvProvider {
    val host = Uri.parse(credentials.server).host.orEmpty()
    return IptvProvider(
        id = id,
        name = name.trim(),
        server = credentials.server,
        username = credentials.username,
        password = credentials.password,
    ).let { provider ->
        if (provider.name.isNotBlank()) provider
        else provider.copy(name = credentials.username.ifBlank { host.ifBlank { "Provider" } })
    }
}
