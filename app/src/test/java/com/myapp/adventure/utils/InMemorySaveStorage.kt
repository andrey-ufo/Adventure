package com.myapp.adventure.utils

/**
 * Shared JVM test double for [SaveStorage]. Mirrors the production path
 * semantics (nested directories are implied by path prefixes) closely
 * enough to exercise autosave/slot separation without SAF.
 */
internal class InMemorySaveStorage : SaveStorage {

    val files = linkedMapOf<List<String>, String>()
    val writes = mutableListOf<List<String>>()
    val reads = mutableListOf<List<String>>()

    /** Test switch: makes the next/all writes fail without touching [files]. */
    var failWrites = false

    override fun readDocument(path: List<String>): String? {
        reads += path.toList()
        return files[path]
    }

    override fun writeDocument(path: List<String>, content: String): Boolean {
        if (failWrites) return false
        val stablePath = path.toList()
        writes += stablePath
        files[stablePath] = content
        return true
    }

    override fun listDocuments(directory: List<String>): List<String> =
        files.keys
            .filter { it.size == directory.size + 1 && it.dropLast(1) == directory }
            .map { it.last() }

    override fun deleteDocument(path: List<String>): Boolean =
        files.remove(path) != null
}
