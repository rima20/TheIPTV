package com.example.theiptv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

class FavoritesStore(context: Context) {
    private val store = context.applicationContext.favoritesStore
    private val gson = Gson()
    private val listType = object : TypeToken<List<FavoriteRef>>() {}.type

    fun observe(): Flow<List<FavoriteRef>> = store.data.map { prefs ->
        parse(prefs[FAVORITES].orEmpty())
    }

    suspend fun isFavorite(providerId: String, type: ContentType, id: String): Boolean {
        if (providerId.isBlank() || id.isBlank()) return false
        return observe().first().any { it.matches(providerId, type, id) }
    }

    suspend fun toggle(providerId: String, item: ContentItem): Boolean {
        if (providerId.isBlank() || item.id.isBlank()) return false
        if (item.type != ContentType.Movies && item.type != ContentType.Series) return false
        val current = observe().first()
        val exists = current.any { it.matches(providerId, item.type, item.id) }
        val next = if (exists) {
            current.filterNot { it.matches(providerId, item.type, item.id) }
        } else {
            listOf(FavoriteRef.from(providerId, item)) + current
        }
        persist(next)
        return !exists
    }

    private suspend fun persist(favorites: List<FavoriteRef>) {
        store.edit { prefs ->
            prefs[FAVORITES] = gson.toJson(favorites)
        }
    }

    private fun parse(json: String): List<FavoriteRef> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<FavoriteRef>>(json, listType).orEmpty().filter {
                it.providerId.isNotBlank() && it.id.isNotBlank() &&
                    (it.type == ContentType.Movies || it.type == ContentType.Series)
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
    }
}

private fun FavoriteRef.matches(providerId: String, type: ContentType, id: String): Boolean {
    return this.providerId == providerId && this.type == type && this.id == id
}
