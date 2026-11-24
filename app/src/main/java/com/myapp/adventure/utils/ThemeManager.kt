package com.myapp.adventure.utils

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.myapp.adventure.R
import com.myapp.adventure.data.theme.AppThemeConfig
import com.myapp.adventure.data.theme.BuiltInThemes
import com.myapp.adventure.data.theme.ThemeDataStore
import com.myapp.adventure.data.theme.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Thin facade over [ThemeRepository], kept for two reasons:
 *
 *  1. It exposes a stable API (`getMode`, `setMode`, `apply`) used by
 *     `MainActivity` and `SettingsScreen`; callers do not touch DataStore,
 *     coroutines, or serialization.
 *  2. It maps a theme id to the Activity's `styleRes` and applies
 *     `AppCompatDelegate` night mode. Compose screens read the full
 *     `AppThemeConfig` through `AdventureTheme`.
 *
 * Lifecycle:
 *  - `initialize(context)` — call once per process, from `MainActivity.onCreate`
 *    **before** `super.onCreate()`. Idempotent.
 *  - `awaitInitialized()` — call right after `initialize`, inside `runBlocking`,
 *    to make sure the DataStore cache is warm before `apply()` reads it.
 *  - `apply(activity)` — call **before** `super.onCreate()`.
 *  - The other methods are safe to call at any time after `initialize`.
 */
object ThemeManager {

    /**
     * Mapping theme id -> View-style resource. Mirrors `BuiltInThemes.all`:
     * each built-in theme must have a matching `<style>` and an entry here.
     * Once user-authored themes exist, they will also need a generated
     * `styleRes` — a problem for a later phase, not this one.
     */
    private val styleRegistry: Map<String, Int> = mapOf(
        "default" to R.style.Theme_Adventure,
    )
    private val defaultStyleRes: Int = styleRegistry.getValue("default")

    @Volatile
    private var repository: ThemeRepository? = null

    /** Application-scoped; survives activity recreation. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class Mode(val nightMode: Int) {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            fun fromNightMode(value: Int): Mode =
                entries.firstOrNull { it.nightMode == value } ?: SYSTEM
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Creates the singleton [ThemeRepository] backed by DataStore.
     * Idempotent: repeated calls (activity recreation) are no-ops.
     */
    fun initialize(context: Context) {
        if (repository == null) {
            synchronized(this) {
                if (repository == null) {
                    val appContext = context.applicationContext
                    val dataStore = ThemeDataStore.create(appContext)
                    repository = ThemeRepository(dataStore, scope)
                }
            }
        }
    }

    /**
     * Suspends until the initial DataStore read has populated the cache.
     * Safe to call repeatedly: returns immediately after the first call.
     */
    suspend fun awaitInitialized() {
        repository?.awaitInitialized()
    }

    private fun requireRepository(): ThemeRepository =
        repository ?: error(
            "ThemeManager.initialize(context) must be called before accessing themes"
        )

    /**
     * Returns the current [AppThemeConfig] synchronously. Falls back to
     * [BuiltInThemes.DEFAULT] when the repository has not been initialized
     * yet — this keeps Compose previews and early compositions working
     * even before `MainActivity.onCreate` runs.
     */
    fun currentThemeSync(): AppThemeConfig =
        repository?.currentTheme?.value ?: BuiltInThemes.DEFAULT

    // ------------------------------------------------------------------
    // Theme id axis
    // ------------------------------------------------------------------

    fun getThemeId(@Suppress("UNUSED_PARAMETER") context: Context): String =
        requireRepository().getThemeIdSync()

    fun setThemeId(@Suppress("UNUSED_PARAMETER") context: Context, themeId: String) {
        runBlocking { requireRepository().setThemeId(themeId) }
    }

    // ------------------------------------------------------------------
    // Night-mode axis
    // ------------------------------------------------------------------

    fun getNightMode(@Suppress("UNUSED_PARAMETER") context: Context): Int =
        requireRepository().getNightModeSync()

    fun setNightMode(@Suppress("UNUSED_PARAMETER") context: Context, nightMode: Int) {
        runBlocking { requireRepository().setNightMode(nightMode) }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun setTheme(
        @Suppress("UNUSED_PARAMETER") context: Context,
        themeId: String,
        nightMode: Int,
    ) {
        runBlocking { requireRepository().setTheme(themeId, nightMode) }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    // ------------------------------------------------------------------
    // Enum helpers (used by SettingsScreen)
    // ------------------------------------------------------------------

    fun getMode(context: Context): Mode = Mode.fromNightMode(getNightMode(context))

    fun setMode(context: Context, mode: Mode) = setNightMode(context, mode.nightMode)

    // ------------------------------------------------------------------
    // Application entry point
    // ------------------------------------------------------------------

    /**
     * Applies the persisted night mode and theme style to [activity].
     *
     * MUST be called **before** `super.onCreate()`. AppCompat reads the
     * night mode and theme during `onCreate`, so changing them afterwards
     * forces a flash or a spurious `recreate()`. Safe to call even if
     * [initialize] was not called: falls back to system night mode and the
     * default style.
     */
    fun apply(activity: Activity) {
        val repo = repository
        val nightMode = repo?.getNightModeSync()
            ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        val themeId = repo?.getThemeIdSync() ?: "default"

        AppCompatDelegate.setDefaultNightMode(nightMode)
        activity.setTheme(styleRegistry[themeId] ?: defaultStyleRes)
    }
}