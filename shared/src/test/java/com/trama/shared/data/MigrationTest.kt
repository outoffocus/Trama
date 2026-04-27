package com.trama.shared.data

import androidx.room.migration.Migration
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that all database migrations are properly defined with correct version ranges
 * and that the database version constant stays in sync with the latest migration.
 */
class MigrationTest {
    private fun allMigrations(): List<Migration> = listOf(
        DiaryDatabase.MIGRATION_1_2,
        DiaryDatabase.MIGRATION_2_3,
        DiaryDatabase.MIGRATION_3_4,
        DiaryDatabase.MIGRATION_4_5,
        DiaryDatabase.MIGRATION_5_6,
        DiaryDatabase.MIGRATION_6_7,
        DiaryDatabase.MIGRATION_7_8,
        DiaryDatabase.MIGRATION_8_9,
        DiaryDatabase.MIGRATION_9_10,
        DiaryDatabase.MIGRATION_10_11,
        DiaryDatabase.MIGRATION_11_12
    )

    // ── Individual migration version checks ──

    @Test
    fun `MIGRATION_1_2 has correct version range`() {
        val migration = DiaryDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun `MIGRATION_2_3 has correct version range`() {
        val migration = DiaryDatabase.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
    }

    @Test
    fun `MIGRATION_3_4 has correct version range`() {
        val migration = DiaryDatabase.MIGRATION_3_4
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)
    }

    @Test
    fun `MIGRATION_4_5 has correct version range`() {
        val migration = DiaryDatabase.MIGRATION_4_5
        assertEquals(4, migration.startVersion)
        assertEquals(5, migration.endVersion)
    }

    @Test
    fun `MIGRATION_5_6 has correct version range`() {
        val migration = DiaryDatabase.MIGRATION_5_6
        assertEquals(5, migration.startVersion)
        assertEquals(6, migration.endVersion)
    }

    // ── Migration chain integrity ──

    @Test
    fun `all migrations are instances of Migration`() {
        allMigrations().forEach { migration ->
            assertTrue(
                "Expected Migration instance, got ${migration::class.simpleName}",
                migration is Migration
            )
        }
    }

    @Test
    fun `migrations form a continuous chain from version 1 to 12`() {
        val migrations = allMigrations().sortedBy { it.startVersion }

        // Verify chain continuity: each migration's endVersion == next migration's startVersion
        for (i in 0 until migrations.size - 1) {
            assertEquals(
                "Gap in migration chain between ${migrations[i]} and ${migrations[i + 1]}",
                migrations[i].endVersion,
                migrations[i + 1].startVersion
            )
        }

        // Verify chain starts at 1 and ends at 12
        assertEquals(1, migrations.first().startVersion)
        assertEquals(12, migrations.last().endVersion)
    }

    @Test
    fun `no duplicate migration version ranges exist`() {
        val migrations = allMigrations()
        val ranges = migrations.map { it.startVersion to it.endVersion }
        assertEquals("Duplicate migration ranges found", ranges.size, ranges.toSet().size)
    }

    @Test
    fun `each migration increments version by exactly 1`() {
        allMigrations().forEach { migration ->
            assertEquals(
                "Migration ${migration.startVersion}->${migration.endVersion} should increment by 1",
                1,
                migration.endVersion - migration.startVersion
            )
        }
    }

    @Test
    fun `latest migration target matches current database version`() {
        val latestMigrationEnd = allMigrations().maxOf { it.endVersion }
        assertEquals(
            "Keep this value in sync with @Database(version = ...) in DiaryDatabase",
            12,
            latestMigrationEnd
        )
    }
}
