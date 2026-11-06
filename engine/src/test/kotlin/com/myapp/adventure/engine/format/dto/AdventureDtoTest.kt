package com.myapp.adventure.engine.format.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdventureDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `minimal adventure`() {
        val input = """
            {
              "title": "Test",
              "chapters": [
                {
                  "id": "c1",
                  "scenes": [
                    {"id": "s1", "text": "Hello"}
                  ]
                }
              ]
            }
        """.trimIndent()
        val dto = json.decodeFromString<AdventureDto>(input)
        assertEquals("Test", dto.title)
        assertEquals(1, dto.chapters.size)
        assertEquals("c1", dto.chapters[0].id)
        assertEquals(0, dto.chapters[0].requiredSteps)
        assertEquals(1, dto.chapters[0].scenes.size)
        assertEquals("s1", dto.chapters[0].scenes[0].id)
        assertEquals(0, dto.chapters[0].scenes[0].choices.size)
    }

    @Test
    fun `version absent means null in DTO`() {
        val input = """{"title":"Test","chapters":[]}"""
        val dto = json.decodeFromString<AdventureDto>(input)
        assertNull(dto.version)
    }

    @Test
    fun `version present is read`() {
        val input = """{"version":2,"title":"Test","chapters":[]}"""
        val dto = json.decodeFromString<AdventureDto>(input)
        assertEquals(2, dto.version)
    }

    @Test
    fun `explicit null field becomes default`() {
        val input = """
            {"title":"Test","author":null,"chapters":[
              {"id":"c1","scenes":[{"id":"s1","text":"Hello","set_flags":null}]}
            ]}
        """.trimIndent()
        val dto = json.decodeFromString<AdventureDto>(input)
        assertEquals("", dto.author)
        assertEquals(emptyList(), dto.chapters[0].scenes[0].setFlags)
    }

    @Test
    fun `choice with condition and effects`() {
        val input = """
            {
              "title": "T",
              "chapters": [{
                "id": "c1",
                "scenes": [{
                  "id": "s1",
                  "text": "Hello",
                  "choices": [{
                    "text": "Go",
                    "next_scene": "s2",
                    "condition": "has_sword",
                    "modify_stats": {"health": -5}
                  }]
                }]
              }]
            }
        """.trimIndent()
        val dto = json.decodeFromString<AdventureDto>(input)
        val choice = dto.chapters[0].scenes[0].choices[0]
        assertEquals("Go", choice.text)
        assertEquals("s2", choice.nextScene)
        assertEquals("has_sword", choice.condition)
        assertEquals(mapOf("health" to -5), choice.modifyStats)
    }

    @Test
    fun `unknown keys are ignored`() {
        val input = """{"title":"T","chapters":[],"future_field":"x"}"""
        json.decodeFromString<AdventureDto>(input)
    }
}