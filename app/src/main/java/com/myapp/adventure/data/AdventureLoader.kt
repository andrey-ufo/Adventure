package com.myapp.adventure.data

import com.myapp.adventure.engine.CompileError
import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.analyze.Analyzer
import com.myapp.adventure.engine.analyze.LintIssue
import com.myapp.adventure.engine.format.JsonToIr
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.types.TypeError
import com.myapp.adventure.engine.parse.ParseError
import com.myapp.adventure.engine.parse.ParseResult
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Ф5.7 (P2): one successful parse + compile of an adventure.
 *
 * The IR and runtime indexes are immutable and safe to share between
 * reader and preview ViewModels. Analyzer issues are computed lazily and
 * cached on the first normal (non-preview) open, so reopening an
 * unchanged adventure skips parsing, compiling **and** linting.
 * The cache entry is keyed by the JSON fingerprint, so editing the file
 * invalidates it automatically.
 */
internal class LoadedAdventure(
    val fingerprint: String,
    val adventure: Adventure,
    val compiled: CompiledAdventure,
) {

    @Volatile
    private var lintCache: List<LintIssue>? = null

    fun lintIssues(): List<LintIssue> =
        lintCache ?: synchronized(this) {
            lintCache ?: Analyzer.lint(compiled).also { lintCache = it }
        }
}

/** Result of [AdventureLoader.load]. Failures are never cached. */
internal sealed interface AdventureLoadResult {

    data class Success(
        val loaded: LoadedAdventure,
        val fromCache: Boolean,
        val totalMs: Long,
        val parseMs: Long,
        val compileMs: Long,
    ) : AdventureLoadResult

    data class ParseFailure(val errors: List<ParseError>) : AdventureLoadResult
    data class TypeFailure(val errors: List<TypeError>) : AdventureLoadResult
    data class CompileFailure(val errors: List<CompileError>) : AdventureLoadResult
}

/**
 * Bounded, access-ordered cache of compiled adventures.
 *
 * Default capacity is 3: enough for "open A, peek at B, return to A"
 * without retaining every adventure the user has ever opened. Keying by
 * a content fingerprint (not file name) means a rewritten file is a cache
 * miss even if its URI is unchanged.
 */
internal class AdventureCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    init {
        require(maxEntries > 0) { "Cache must hold at least one adventure" }
    }

    private val entries = object :
        LinkedHashMap<String, LoadedAdventure>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, LoadedAdventure>?,
        ): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(fingerprint: String): LoadedAdventure? = entries[fingerprint]

    @Synchronized
    fun put(fingerprint: String, loaded: LoadedAdventure) {
        entries[fingerprint] = loaded
    }

    @Synchronized
    fun entryCount(): Int = entries.size

    @Synchronized
    fun clear() {
        entries.clear()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 3
    }
}

/**
 * Parses + compiles an adventure once and reuses the result for identical
 * JSON. The JSON is still read from disk on every open (needed for the
 * fingerprint), but the expensive parser/type-checker/compiler pipeline
 * runs only on a cache miss.
 *
 * Parser and compiler are injectable so tests can prove "not called on a
 * second load" without timing assertions.
 */
internal class AdventureLoader(
    private val cache: AdventureCache = AdventureCache(),
    private val parse: (String) -> ParseResult<Adventure> = JsonToIr::parse,
    private val compile: (Adventure) -> CompileResult = Engine::compile,
) {

    fun load(json: String): AdventureLoadResult {
        val startedAt = System.nanoTime()
        val fingerprint = fingerprint(json)

        cache.get(fingerprint)?.let { loaded ->
            return AdventureLoadResult.Success(
                loaded = loaded,
                fromCache = true,
                totalMs = elapsedMs(startedAt),
                parseMs = 0,
                compileMs = 0,
            )
        }

        val parseStartedAt = System.nanoTime()
        val adventure = when (val result = parse(json)) {
            is ParseResult.Success -> result.value
            is ParseResult.Failure -> {
                return if (result.typeErrors.isNotEmpty()) {
                    AdventureLoadResult.TypeFailure(result.typeErrors)
                } else {
                    AdventureLoadResult.ParseFailure(result.errors)
                }
            }
        }
        val parseMs = elapsedMs(parseStartedAt)

        val compileStartedAt = System.nanoTime()
        val compiled = when (val result = compile(adventure)) {
            is CompileResult.Success -> result.compiled
            is CompileResult.Failure -> {
                return AdventureLoadResult.CompileFailure(result.errors)
            }
        }
        val compileMs = elapsedMs(compileStartedAt)

        val loaded = LoadedAdventure(fingerprint, adventure, compiled)
        cache.put(fingerprint, loaded)
        return AdventureLoadResult.Success(
            loaded = loaded,
            fromCache = false,
            totalMs = elapsedMs(startedAt),
            parseMs = parseMs,
            compileMs = compileMs,
        )
    }

    internal fun clearCache() {
        cache.clear()
    }

    internal fun cacheSize(): Int = cache.entryCount()

    companion object {
        internal fun fingerprint(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }

        private fun elapsedMs(startedAt: Long): Long =
            (System.nanoTime() - startedAt) / 1_000_000
    }
}

/** Process-wide cache shared by reader and preview instances. */
internal object AdventureLoads {
    private val loader = AdventureLoader()

    fun load(json: String): AdventureLoadResult = loader.load(json)

    internal fun clearCacheForTests() {
        loader.clearCache()
    }
}
