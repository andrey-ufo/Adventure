package com.myapp.adventure.data

import com.myapp.adventure.engine.CompileError
import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.parse.ParseError
import com.myapp.adventure.engine.parse.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ф5.7 (P2): parse/compile once, reuse by content fingerprint. */
class AdventureLoaderTest {

    private val adventure = Adventure(
        title = "Cache test",
        startScene = SceneId("s1"),
        chapters = emptyList(),
    )
    private val compiled = CompiledAdventure(
        adventure = adventure,
        scenesById = emptyMap(),
        chapterIndexBySceneId = emptyMap(),
    )

    @Test
    fun secondLoadOfSameJsonSkipsParserAndCompiler() {
        var parseCalls = 0
        var compileCalls = 0
        val loader = AdventureLoader(
            cache = AdventureCache(maxEntries = 2),
            parse = {
                parseCalls++
                ParseResult.Success(adventure)
            },
            compile = {
                compileCalls++
                CompileResult.Success(compiled)
            },
        )

        val first = loader.load("json") as AdventureLoadResult.Success
        val second = loader.load("json") as AdventureLoadResult.Success

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(1, parseCalls)
        assertEquals(1, compileCalls)
        assertSame(first.loaded, second.loaded)
    }

    @Test
    fun changedJsonIsANewCacheEntry() {
        var parseCalls = 0
        val loader = AdventureLoader(
            cache = AdventureCache(maxEntries = 2),
            parse = {
                parseCalls++
                ParseResult.Success(adventure)
            },
            compile = { CompileResult.Success(compiled) },
        )

        loader.load("v1")
        loader.load("v2")

        assertEquals(2, parseCalls)
        assertEquals(2, loader.cacheSize())
    }

    @Test
    fun cacheEvictsLeastRecentlyUsedEntry() {
        var parseCalls = 0
        val loader = AdventureLoader(
            cache = AdventureCache(maxEntries = 2),
            parse = {
                parseCalls++
                ParseResult.Success(adventure)
            },
            compile = { CompileResult.Success(compiled) },
        )

        loader.load("A")
        loader.load("B")
        loader.load("A") // hit, makes B the least recently used
        loader.load("C") // evicts B
        assertEquals(3, parseCalls)

        loader.load("A") // still cached
        assertEquals(3, parseCalls)

        loader.load("B") // was evicted, parses again
        assertEquals(4, parseCalls)
    }

    @Test
    fun parseFailuresAreNotCached() {
        var parseCalls = 0
        val loader = AdventureLoader(
            cache = AdventureCache(maxEntries = 2),
            parse = {
                parseCalls++
                ParseResult.Failure(
                    errors = listOf(
                        ParseError.Syntax(message = "bad json", path = "$"),
                    ),
                )
            },
            compile = { CompileResult.Success(compiled) },
        )

        assertTrue(loader.load("bad") is AdventureLoadResult.ParseFailure)
        assertTrue(loader.load("bad") is AdventureLoadResult.ParseFailure)
        assertEquals(2, parseCalls)
        assertEquals(0, loader.cacheSize())
    }

    @Test
    fun compileFailuresAreNotCached() {
        var compileCalls = 0
        val loader = AdventureLoader(
            cache = AdventureCache(maxEntries = 2),
            parse = { ParseResult.Success(adventure) },
            compile = {
                compileCalls++
                CompileResult.Failure(
                    errors = listOf(
                        CompileError.MissingStartScene(SceneId("missing")),
                    ),
                )
            },
        )

        assertTrue(loader.load("json") is AdventureLoadResult.CompileFailure)
        assertTrue(loader.load("json") is AdventureLoadResult.CompileFailure)
        assertEquals(2, compileCalls)
        assertEquals(0, loader.cacheSize())
    }

    @Test
    fun realPipelineHitsCacheOnReopen() {
        val loader = AdventureLoader(cache = AdventureCache(maxEntries = 2))
        val json = """
            {
              "version": 5,
              "title": "Cache integration",
              "start_scene": "s1",
              "chapters": [
                {
                  "id": "c1",
                  "title": "Chapter",
                  "scenes": [
                    {"id": "s1", "text": "Hello", "is_ending": true}
                  ]
                }
              ]
            }
        """.trimIndent()

        val first = loader.load(json) as AdventureLoadResult.Success
        val second = loader.load(json) as AdventureLoadResult.Success

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertSame(first.loaded, second.loaded)
        assertEquals("Cache integration", second.loaded.adventure.title)

        // Analyzer results are also reused on the cached entry.
        assertSame(first.loaded.lintIssues(), second.loaded.lintIssues())
    }
}
