package com.trama.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.trama.shared.data.DiaryDao
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureEditingTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<DiaryDao>(relaxed = true)
    private val repo = DiaryRepository(dao)
    private val appContext = mockk<android.content.Context>(relaxed = true)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { dao.getDuplicates() } returns flowOf(emptyList())
        every { dao.getPending() } returns flowOf(emptyList())
        coEvery { dao.getByCreatedAtAndText(any(), any()) } returns null
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `double save creates one entry and acknowledges only after commit`() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        coEvery { dao.insert(any()) } coAnswers { release.await(); 42L }
        val vm = CalendarViewModel(repo, SavedStateHandle(mapOf("captureAt" to 100L)), appContext)
        vm.editDraft("  Una nota  ")
        vm.saveCapture()
        vm.saveCapture()
        runCurrent()
        assertTrue(vm.saving.value)
        assertNull(vm.savedEntryId.value)
        assertEquals("  Una nota  ", vm.draft.value)
        release.complete(Unit)
        runCurrent()
        assertEquals(42L, vm.savedEntryId.value)
        assertEquals("", vm.draft.value)
        coVerify(exactly = 1) { dao.insert(match { it.text == "Una nota" && it.createdAt == 100L }) }
    }

    @Test fun `failed capture keeps draft and can be retried`() = runTest(dispatcher) {
        coEvery { dao.insert(any()) } throws IllegalStateException("disk full")
        val state = SavedStateHandle(mapOf("captureAt" to 100L, "captureOpen" to true))
        val vm = CalendarViewModel(repo, state, appContext)
        vm.editDraft("Una nota")
        vm.saveCapture()
        runCurrent()
        assertTrue(vm.captureOpen.value)
        assertEquals("Una nota", vm.draft.value)
        assertNotNull(vm.error.value)
        assertFalse(vm.saving.value)
        coEvery { dao.insert(any()) } returns 7L
        vm.saveCapture()
        runCurrent()
        assertEquals(7L, vm.savedEntryId.value)
    }

    @Test fun `restored draft reuses entry already committed before acknowledgment`() = runTest(dispatcher) {
        val entry = DiaryEntry(id = 9, text = "Nota", keyword = "manual", category = "Nota",
            confidence = 1f, source = Source.PHONE, duration = 0, isManual = true, createdAt = 100)
        coEvery { dao.getByCreatedAtAndText(100, "Nota") } returns entry
        val state = SavedStateHandle(mapOf("captureAt" to 100L, "captureOpen" to true, "captureDraft" to "Nota"))
        val vm = CalendarViewModel(repo, state, appContext)
        vm.saveCapture()
        runCurrent()
        assertEquals(9L, vm.savedEntryId.value)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test fun `failed edit stays open and empty edit cannot overwrite entry`() = runTest(dispatcher) {
        val entry = DiaryEntry(id = 1, text = "Original", keyword = "manual", category = "Nota",
            confidence = 1f, source = Source.PHONE, duration = 0)
        coEvery { dao.getByIdOnce(1) } returns entry
        coEvery { dao.updateText(any(), any(), any()) } throws IllegalStateException("disk full")
        val vm = EntryEditorViewModel(repo, SavedStateHandle(), mockk(relaxed = true), appContext)
        vm.start("Original")
        vm.change("  ")
        vm.save(1)
        runCurrent()
        coVerify(exactly = 0) { dao.updateText(any(), any(), any()) }
        vm.change("Editada")
        vm.save(1)
        runCurrent()
        assertTrue(vm.editing.value)
        assertEquals("Editada", vm.draft.value)
        assertNotNull(vm.error.value)
        coEvery { dao.updateText(any(), any(), any()) } returns 1
        vm.save(1)
        runCurrent()
        assertFalse(vm.editing.value)
    }

    @Test fun `local reanalysis stays editable when model is unavailable`() = runTest(dispatcher) {
        val vm = EntryEditorViewModel(repo, SavedStateHandle(), mockk(relaxed = true), appContext)

        vm.start("Explicación corregida")
        vm.save(1, requireLocalModel = true)
        runCurrent()

        assertTrue(vm.editing.value)
        assertEquals("Explicación corregida", vm.draft.value)
        assertTrue(vm.error.value.orEmpty().contains("modelo local"))
        coVerify(exactly = 0) { dao.updateText(any(), any(), any()) }
    }
}
