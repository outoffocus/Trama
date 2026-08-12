package com.trama.app.summary

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelFileResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `configured model wins when it exists`() {
        val configured = modelFile("current.litertlm", "current")
        modelFile("gemma-legacy.task", "legacy").setLastModified(configured.lastModified() + 1_000L)

        assertEquals(
            configured,
            LocalModelFileResolver.find(configured, temporaryFolder.root.listFiles().orEmpty())
        )
    }

    @Test
    fun `compatible legacy model is recovered after default filename changes`() {
        val configured = File(temporaryFolder.root, "current.litertlm")
        val legacy = modelFile("gemma3-1b-it-int4.task", "legacy")

        assertEquals(
            legacy,
            LocalModelFileResolver.find(configured, temporaryFolder.root.listFiles().orEmpty())
        )
    }

    @Test
    fun `newest compatible installed model is selected`() {
        val configured = File(temporaryFolder.root, "missing.litertlm")
        val older = modelFile("gemma-older.task", "older").apply { setLastModified(1_000L) }
        val newer = modelFile("gemma-newer.litertlm", "newer").apply { setLastModified(2_000L) }

        assertEquals(
            newer,
            LocalModelFileResolver.find(configured, arrayOf(older, newer))
        )
    }

    @Test
    fun `empty and unsupported files are ignored`() {
        val configured = File(temporaryFolder.root, "missing.litertlm")
        val emptyModel = temporaryFolder.newFile("empty.task")
        val unrelated = modelFile("notes.txt", "not a model")

        assertNull(LocalModelFileResolver.find(configured, arrayOf(emptyModel, unrelated)))
    }

    @Test
    fun `unrelated task model is never treated as Gemma`() {
        val configured = File(temporaryFolder.root, "missing.litertlm")
        val unrelatedTask = modelFile("vision_encoder.task", "other model")

        assertNull(LocalModelFileResolver.find(configured, arrayOf(unrelatedTask)))
    }

    private fun modelFile(name: String, contents: String): File =
        temporaryFolder.newFile(name).apply { writeText(contents) }
}
