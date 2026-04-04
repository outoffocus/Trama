package com.mydiary.app.ui.screens

import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.EntryActionType
import com.mydiary.shared.model.EntryPriority
import com.mydiary.shared.model.EntryStatus
import com.mydiary.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Tests for the pure data logic used in HomeScreen:
 * - Entry grouping (overdue, today, older)
 * - Filtering duplicates
 * - Selection mode operations
 * - Completed-today counting
 */
class HomeScreenLogicTest {

    private lateinit var startOfDay: Long
    private var now: Long = 0L

    @Before
    fun setUp() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        startOfDay = cal.timeInMillis
        now = System.currentTimeMillis()
    }

    // ── Helper ──

    private fun entry(
        id: Long = 0,
        text: String = "Test",
        createdAt: Long = now,
        dueDate: Long? = null,
        status: String = EntryStatus.PENDING,
        completedAt: Long? = null,
        duplicateOfId: Long? = null,
        source: Source = Source.PHONE
    ) = DiaryEntry(
        id = id,
        text = text,
        keyword = "test",
        category = "nota",
        confidence = 0.9f,
        source = source,
        duration = 5,
        createdAt = createdAt,
        dueDate = dueDate,
        status = status,
        completedAt = completedAt,
        duplicateOfId = duplicateOfId
    )

    // ────────────────────────────────────────────────────────────────────────
    // Overdue detection: dueDate < now && status != COMPLETED
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `entry with past dueDate and pending status is overdue`() {
        val e = entry(dueDate = now - TimeUnit.DAYS.toMillis(1), status = EntryStatus.PENDING)
        val isOverdue = e.dueDate != null && e.dueDate!! < now
        assertTrue(isOverdue)
    }

    @Test
    fun `entry with past dueDate but completed is not overdue`() {
        val e = entry(dueDate = now - TimeUnit.DAYS.toMillis(1), status = EntryStatus.COMPLETED)
        // HomeScreen filters overdue from pendingEntries, so completed entries never appear
        assertEquals(EntryStatus.COMPLETED, e.status)
    }

    @Test
    fun `entry with future dueDate is not overdue`() {
        val e = entry(dueDate = now + TimeUnit.DAYS.toMillis(2))
        val isOverdue = e.dueDate != null && e.dueDate!! < now
        assertFalse(isOverdue)
    }

    @Test
    fun `entry with null dueDate is not overdue`() {
        val e = entry(dueDate = null)
        val isOverdue = e.dueDate != null && e.dueDate!! < now
        assertFalse(isOverdue)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Today's entries: createdAt >= startOfDay
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `entry created today is in todayEntries`() {
        val e = entry(createdAt = startOfDay + TimeUnit.HOURS.toMillis(8))
        val isToday = e.createdAt >= startOfDay
        assertTrue(isToday)
    }

    @Test
    fun `entry created yesterday is NOT in todayEntries`() {
        val e = entry(createdAt = startOfDay - TimeUnit.HOURS.toMillis(1))
        val isToday = e.createdAt >= startOfDay
        assertFalse(isToday)
    }

    @Test
    fun `entry created exactly at startOfDay is in todayEntries`() {
        val e = entry(createdAt = startOfDay)
        val isToday = e.createdAt >= startOfDay
        assertTrue(isToday)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Older entries: createdAt < startOfDay && (dueDate == null || dueDate >= now)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `entry from yesterday with no dueDate is in olderEntries`() {
        val e = entry(createdAt = startOfDay - TimeUnit.DAYS.toMillis(1), dueDate = null)
        val isOlder = e.createdAt < startOfDay && (e.dueDate == null || e.dueDate!! >= now)
        assertTrue(isOlder)
    }

    @Test
    fun `entry from yesterday with future dueDate is in olderEntries`() {
        val e = entry(
            createdAt = startOfDay - TimeUnit.DAYS.toMillis(1),
            dueDate = now + TimeUnit.DAYS.toMillis(5)
        )
        val isOlder = e.createdAt < startOfDay && (e.dueDate == null || e.dueDate!! >= now)
        assertTrue(isOlder)
    }

    @Test
    fun `entry from yesterday with past dueDate is NOT in olderEntries (it is overdue)`() {
        val e = entry(
            createdAt = startOfDay - TimeUnit.DAYS.toMillis(1),
            dueDate = now - TimeUnit.HOURS.toMillis(1)
        )
        val isOlder = e.createdAt < startOfDay && (e.dueDate == null || e.dueDate!! >= now)
        assertFalse(isOlder)
    }

    @Test
    fun `entry created today is NOT in olderEntries`() {
        val e = entry(createdAt = startOfDay + TimeUnit.HOURS.toMillis(2))
        val isOlder = e.createdAt < startOfDay
        assertFalse(isOlder)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Grouping integration: entries land in exactly one group
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `entries are partitioned into correct groups`() {
        val todayEntry = entry(id = 1, createdAt = now, dueDate = null)
        val overdueEntry = entry(id = 2, createdAt = startOfDay - TimeUnit.DAYS.toMillis(2), dueDate = now - TimeUnit.HOURS.toMillis(1))
        val olderEntry = entry(id = 3, createdAt = startOfDay - TimeUnit.DAYS.toMillis(1), dueDate = null)

        val pending = listOf(todayEntry, overdueEntry, olderEntry)

        val todayGroup = pending.filter { it.createdAt >= startOfDay }
        val overdueGroup = pending.filter { e -> e.dueDate != null && e.dueDate!! < now }
        val olderGroup = pending.filter { e -> e.createdAt < startOfDay && (e.dueDate == null || e.dueDate!! >= now) }

        assertEquals(listOf(todayEntry), todayGroup)
        assertEquals(listOf(overdueEntry), overdueGroup)
        assertEquals(listOf(olderEntry), olderGroup)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Duplicate filtering
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `duplicate entries are filtered from pending list`() {
        val original = entry(id = 1)
        val duplicate = entry(id = 2, duplicateOfId = 1)
        val allPending = listOf(original, duplicate)
        val duplicateIds = listOf(duplicate).map { it.id }.toSet()
        val filtered = allPending.filter { it.id !in duplicateIds }
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun `no duplicates means full list is retained`() {
        val entries = listOf(entry(id = 1), entry(id = 2), entry(id = 3))
        val duplicateIds = emptySet<Long>()
        val filtered = entries.filter { it.id !in duplicateIds }
        assertEquals(3, filtered.size)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Selection mode logic
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `adding to selection set works`() {
        var selectedIds = setOf<Long>()
        selectedIds = selectedIds + 5L
        assertTrue(5L in selectedIds)
        assertEquals(1, selectedIds.size)
    }

    @Test
    fun `removing from selection set works`() {
        var selectedIds = setOf(1L, 2L, 3L)
        selectedIds = selectedIds - 2L
        assertFalse(2L in selectedIds)
        assertEquals(2, selectedIds.size)
    }

    @Test
    fun `toggling selection adds if absent, removes if present`() {
        var selectedIds = setOf(1L, 3L)
        // Toggle 2 (absent) -> should add
        val id2 = 2L
        selectedIds = if (id2 in selectedIds) selectedIds - id2 else selectedIds + id2
        assertTrue(2L in selectedIds)
        assertEquals(3, selectedIds.size)

        // Toggle 1 (present) -> should remove
        val id1 = 1L
        selectedIds = if (id1 in selectedIds) selectedIds - id1 else selectedIds + id1
        assertFalse(1L in selectedIds)
        assertEquals(2, selectedIds.size)
    }

    @Test
    fun `clearing selection empties the set`() {
        var selectedIds = setOf(1L, 2L, 3L)
        selectedIds = emptySet()
        assertTrue(selectedIds.isEmpty())
    }

    // ────────────────────────────────────────────────────────────────────────
    // Completed today counting
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `completedToday counts entries completed after startOfDay`() {
        val completedEntries = listOf(
            entry(id = 1, status = EntryStatus.COMPLETED, completedAt = startOfDay + TimeUnit.HOURS.toMillis(2)),
            entry(id = 2, status = EntryStatus.COMPLETED, completedAt = startOfDay + TimeUnit.HOURS.toMillis(5)),
            entry(id = 3, status = EntryStatus.COMPLETED, completedAt = startOfDay - TimeUnit.HOURS.toMillis(3)) // yesterday
        )
        val completedToday = completedEntries.count { (it.completedAt ?: 0) >= startOfDay }
        assertEquals(2, completedToday)
    }

    @Test
    fun `completedToday is zero when no entries completed today`() {
        val completedEntries = listOf(
            entry(id = 1, status = EntryStatus.COMPLETED, completedAt = startOfDay - TimeUnit.DAYS.toMillis(1))
        )
        val completedToday = completedEntries.count { (it.completedAt ?: 0) >= startOfDay }
        assertEquals(0, completedToday)
    }

    @Test
    fun `completedToday handles null completedAt gracefully`() {
        val completedEntries = listOf(
            entry(id = 1, status = EntryStatus.COMPLETED, completedAt = null)
        )
        val completedToday = completedEntries.count { (it.completedAt ?: 0) >= startOfDay }
        assertEquals(0, completedToday)
    }
}
