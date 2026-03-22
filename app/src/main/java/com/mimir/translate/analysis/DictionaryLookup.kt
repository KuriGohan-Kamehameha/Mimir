package com.mimir.translate.analysis

import android.content.Context
import android.util.Log
import com.mimir.translate.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mimir.translate.data.models.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryLookup(private val context: Context) {

    companion object {
        private const val TAG = "Mimir"
    }

    // Synchronization lock for thread-safe access to dictionary data
    private val lock = Any()
    
    private var byKanji: Map<String, Pair<String, String>> = emptyMap()
    private var byReading: Map<String, Pair<String, String>> = emptyMap()

    @Volatile
    var isLoaded = false
        private set

    suspend fun loadAsync() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (isLoaded) return@withContext
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Dictionary: Loading...")
        val json = context.assets.open("dictionary.json").bufferedReader().readText()
        val type = object : TypeToken<List<List<String>>>() {}.type
        val entries: List<List<String>> = Gson().fromJson(json, type)

        val kanjiMap = mutableMapOf<String, Pair<String, String>>()
        val readingMap = mutableMapOf<String, Pair<String, String>>()

        for (entry in entries) {
            if (entry.size < 3) continue
            val kanji = entry[0]
            val reading = entry[1]
            val meaning = entry[2]
            val pair = Pair(reading, meaning)

            kanjiMap.putIfAbsent(kanji, pair)
            readingMap.putIfAbsent(reading, pair)
        }

        // Atomic update: set both maps and flag under lock
        synchronized(lock) {
            if (!isLoaded) {  // Double-check pattern
                byKanji = kanjiMap
                byReading = readingMap
                isLoaded = true
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Dictionary: Loaded ${entries.size} entries")
    }

    fun lookup(surface: String, baseForm: String, reading: String): WordEntry? {
        require(surface.isNotEmpty()) { "Surface form must not be empty" } // NASA Rule 5
        
        synchronized(lock) {
            if (!isLoaded) return null

            byKanji[baseForm]?.let { (r, m) ->
                return WordEntry(surface = baseForm, reading = r, meaning = m, jlptLevel = null)
            }

            byKanji[surface]?.let { (r, m) ->
                return WordEntry(surface = surface, reading = r, meaning = m, jlptLevel = null)
            }

            if (reading.isNotEmpty()) {
                byReading[reading]?.let { (r, m) ->
                    return WordEntry(surface = surface, reading = r, meaning = m, jlptLevel = null)
                }
            }
        }

        return null
    }

    fun lookupTokens(tokens: List<JapaneseTokenizer.TokenResult>): List<WordEntry> {
        return tokens.map { token ->
            lookup(token.surface, token.baseForm, token.reading)
                ?: WordEntry(
                    surface = token.surface,
                    reading = token.reading,
                    meaning = "",
                    jlptLevel = null,
                )
        }
    }
}
