package com.myapp.adventure.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for theme state: which theme is active, its
 * night-mode override, and the list of user-created themes.
 *
 * Backed by a [DataStore] (see [ThemeDataStore]). The class caches the
 * latest state in memory as volatile fields so synchronous UI code
 * (`ThemeManager.currentThemeSync()` for Compose) can read it without
 * blocking. All writes go through DataStore; the cache is updated
 * optimistically so reads immediately reflect the change.
 *
 * Threading: the constructor starts a coroutine in [scope] that keeps the
 * cache in sync with DataStore. Call [awaitInitialized] before relying on
 * the cache; it suspends only until the first DataStore emission has been
 * processed, not until the DataStore closes.
 */
class ThemeRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {

    // ------------------------------------------------------------------
    // In-memory cache (volatile: written from IO, read from main)
    // ------------------------------------------------------------------

    @Volatile private var cachedThemeId: String = ThemeSelection.DEFAULT_THEME_ID
    @Volatile private var cachedNightMode: Int = ThemeSelection.DEFAULT_NIGHT_MODE
    @Volatile private var cachedCustomThemes: List<AppThemeConfig> = emptyList()

    private val initialized = CompletableDeferred<Unit>()

    // ------------------------------------------------------------------
    // Flows
    // ------------------------------------------------------------------

    private val _selection = MutableStateFlow(ThemeSelection.Default)
    val selection: StateFlow<ThemeSelection> = _selection.asStateFlow()

    private val _customThemes = MutableStateFlow<List<AppThemeConfig>>(emptyList())
    val customThemes: StateFlow<List<AppThemeConfig>> = _customThemes.asStateFlow()

    /** All available themes: built-in presets plus user-created ones. */
    val allThemes: StateFlow<List<AppThemeConfig>> =
        combine(selection, customThemes) { _, customs ->
            BuiltInThemes.all + customs
        }.stateIn(scope, SharingStarted.Eagerly, BuiltInThemes.all)

    /** The currently active theme, resolved from [selection]. */
    val currentTheme: StateFlow<AppThemeConfig> =
        combine(selection, customThemes) { sel, customs ->
            BuiltInThemes.byId(sel.themeId)
                ?: customs.firstOrNull { it.id == sel.themeId }
                ?: BuiltInThemes.DEFAULT
        }.stateIn(scope, SharingStarted.Eagerly, BuiltInThemes.DEFAULT)

    // ------------------------------------------------------------------
    // Init
    // ------------------------------------------------------------------

    init {
        scope.launch {
            // onEach fires on every emission, including the first one, so
            // `initialized` completes as soon as the cache is warm.
            // `collect()` then keeps us subscribed for subsequent updates
            // (writes from setX() methods, DataStore edits from elsewhere).
            dataStore.data
                .onEach { prefs ->
                    applyPrefs(prefs)
                    initialized.complete(Unit)   // idempotent
                }
                .collect()
        }
    }

    private fun applyPrefs(prefs: Preferences) {
        val themeId = prefs[ThemePreferences.KEY_THEME_ID]
            ?: ThemeSelection.DEFAULT_THEME_ID
        val nightMode = prefs[ThemePreferences.KEY_NIGHT_MODE]
            ?: ThemeSelection.DEFAULT_NIGHT_MODE
        val customs = decodeCustomThemes(prefs[ThemePreferences.KEY_CUSTOM_THEMES])

        cachedThemeId = themeId
        cachedNightMode = nightMode
        cachedCustomThemes = customs

        _selection.value = ThemeSelection(themeId, nightMode)
        _customThemes.value = customs
    }

    /** Suspends until the initial DataStore read has populated the cache. */
    suspend fun awaitInitialized() = initialized.await()

    // ------------------------------------------------------------------
    // Synchronous reads (safe after awaitInitialized)
    // ------------------------------------------------------------------

    fun getThemeIdSync(): String = cachedThemeId
    fun getNightModeSync(): Int = cachedNightMode
    fun getCustomThemesSync(): List<AppThemeConfig> = cachedCustomThemes

    fun getThemeByIdSync(id: String): AppThemeConfig =
        BuiltInThemes.byId(id)
            ?: cachedCustomThemes.firstOrNull { it.id == id }
            ?: BuiltInThemes.DEFAULT

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    suspend fun setThemeId(themeId: String) {
        awaitInitialized()
        val safeId = if (isKnownTheme(themeId)) themeId
        else ThemeSelection.DEFAULT_THEME_ID
        cachedThemeId = safeId                         // optimistic update
        dataStore.edit { it[ThemePreferences.KEY_THEME_ID] = safeId }
    }

    suspend fun setNightMode(nightMode: Int) {
        awaitInitialized()
        cachedNightMode = nightMode                    // optimistic update
        dataStore.edit { it[ThemePreferences.KEY_NIGHT_MODE] = nightMode }
    }

    suspend fun setTheme(themeId: String, nightMode: Int) {
        awaitInitialized()
        val safeId = if (isKnownTheme(themeId)) themeId
        else ThemeSelection.DEFAULT_THEME_ID
        cachedThemeId = safeId
        cachedNightMode = nightMode
        dataStore.edit {
            it[ThemePreferences.KEY_THEME_ID] = safeId
            it[ThemePreferences.KEY_NIGHT_MODE] = nightMode
        }
    }

    suspend fun addOrReplaceCustomTheme(config: AppThemeConfig) {
        awaitInitialized()
        require(!config.isBuiltIn) {
            "Cannot store a built-in theme as custom: ${config.id}"
        }
        val updated = cachedCustomThemes
            .filterNot { it.id == config.id } + config
        persistCustomThemes(updated)
    }

    suspend fun removeCustomTheme(id: String) {
        awaitInitialized()
        val updated = cachedCustomThemes.filterNot { it.id == id }
        val removeActive = cachedThemeId == id

        cachedCustomThemes = updated
        if (removeActive) cachedThemeId = ThemeSelection.DEFAULT_THEME_ID

        val encoded = json.encodeToString(
            ListSerializer(AppThemeConfig.serializer()),
            updated,
        )
        dataStore.edit { prefs ->
            prefs[ThemePreferences.KEY_CUSTOM_THEMES] = encoded
            if (removeActive) {
                prefs[ThemePreferences.KEY_THEME_ID] = ThemeSelection.DEFAULT_THEME_ID
            }
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun isKnownTheme(id: String): Boolean =
        BuiltInThemes.byId(id) != null || cachedCustomThemes.any { it.id == id }

    private suspend fun persistCustomThemes(themes: List<AppThemeConfig>) {
        val encoded = json.encodeToString(
            ListSerializer(AppThemeConfig.serializer()),
            themes,
        )
        cachedCustomThemes = themes                    // optimistic update
        dataStore.edit { it[ThemePreferences.KEY_CUSTOM_THEMES] = encoded }
    }

    private fun decodeCustomThemes(encoded: String?): List<AppThemeConfig> {
        if (encoded.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(
                ListSerializer(AppThemeConfig.serializer()),
                encoded,
            )
        } catch (_: Exception) {
            // Corrupted JSON — drop custom themes silently rather than crash.
            // Logging via Logger requires a folder URI that may not be ready
            // at this point in the startup sequence.
            emptyList()
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true   // forward-compat: old readers don't break on new fields
            encodeDefaults = true
        }
    }
}