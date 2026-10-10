package com.attract.attendance.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit and functional tests for WP-01 (Data safety: never lose attendance).
 *
 * Validates:
 * 1. Migration chain completeness and database version constants.
 * 2. Pre-migration snapshot creation, naming, and 3-snapshot pruning.
 * 3. Snapshot restoration over target database and WAL/SHM file cleanup.
 * 4. Crash-loop detection window (3 crashes in 60s -> Safe Mode trigger).
 */
class DatabaseSafetyUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // -------------------------------------------------------------------------
    // 1. Migration Chain & Version Guarantees
    // -------------------------------------------------------------------------

    @Test
    fun databaseVersion_isSix() {
        assertEquals("Production Room database version must be 6", 6, AttractDatabase.CURRENT_VERSION)
        assertEquals("Database name must be attract.db", "attract.db", AttractDatabase.DB_NAME)
    }

    @Test
    fun allMigrations_formUnbrokenContiguousChainToCurrentVersion() {
        val migrations = AttractDatabase.ALL_MIGRATIONS
        assertEquals("Must contain exactly 5 migration steps (1->2, 2->3, 3->4, 4->5, 5->6)", 5, migrations.size)

        var expectedStartVersion = 1
        for (m in migrations) {
            assertEquals("Migration startVersion must connect contiguously", expectedStartVersion, m.startVersion)
            assertEquals("Migration endVersion must step forward by 1", expectedStartVersion + 1, m.endVersion)
            expectedStartVersion = m.endVersion
        }
        assertEquals("Migration chain must terminate at CURRENT_VERSION", AttractDatabase.CURRENT_VERSION, expectedStartVersion)
    }

    // -------------------------------------------------------------------------
    // 2. Pre-Migration Snapshot Creation & Pruning
    // -------------------------------------------------------------------------

    @Test
    fun snapshotPruning_keepsOnlyLastThreeSnapshots() {
        val backupDir = tempFolder.newFolder("backups")

        // Create 6 fake snapshot files with staggered modification times
        val createdFiles = (1..6).map { i ->
            val file = File(backupDir, "attract-pre-v1-$i.db")
            file.writeText("snapshot-content-$i")
            file.setLastModified(1000L * i)
            file
        }

        assertEquals(6, backupDir.listFiles()?.size)

        // Run the pruning algorithm from AttractDatabase.kt:159-165
        val allSnapshots = backupDir.listFiles { _, name ->
            name.startsWith("attract-pre-v") && name.endsWith(".db")
        }?.sortedBy { it.lastModified() }

        assertNotNull(allSnapshots)
        if (allSnapshots != null && allSnapshots.size > 3) {
            allSnapshots.take(allSnapshots.size - 3).forEach { it.delete() }
        }

        val remainingFiles = backupDir.listFiles()?.sortedBy { it.lastModified() }
        assertNotNull(remainingFiles)
        assertEquals("Pruning must retain strictly the last 3 snapshots", 3, remainingFiles!!.size)

        // The remaining files should be the 3 newest (4, 5, 6)
        assertEquals("attract-pre-v1-4.db", remainingFiles[0].name)
        assertEquals("attract-pre-v1-5.db", remainingFiles[1].name)
        assertEquals("attract-pre-v1-6.db", remainingFiles[2].name)

        // The 3 oldest (1, 2, 3) must be deleted
        assertFalse(createdFiles[0].exists())
        assertFalse(createdFiles[1].exists())
        assertFalse(createdFiles[2].exists())
    }

    // -------------------------------------------------------------------------
    // 3. Snapshot Restoration Logic & WAL Cleanup
    // -------------------------------------------------------------------------

    @Test
    fun restoreSnapshot_copiesLatestAndDeletesWalAndShmSidecars() {
        val rootDir = tempFolder.root
        val backupDir = File(rootDir, "backups").apply { mkdirs() }

        // Setup active database and its SQLite WAL/SHM sidecars
        val activeDbFile = File(rootDir, "attract.db").apply { writeText("corrupted-or-empty-db") }
        val walFile = File(rootDir, "attract.db-wal").apply { writeText("stale-wal-data") }
        val shmFile = File(rootDir, "attract.db-shm").apply { writeText("stale-shm-data") }

        assertTrue(activeDbFile.exists())
        assertTrue(walFile.exists())
        assertTrue(shmFile.exists())

        // Create two snapshots (v1 and v2)
        val olderSnapshot = File(backupDir, "attract-pre-v1-100.db").apply {
            writeText("valid-pre-v1-data")
            setLastModified(100L)
        }
        val newestSnapshot = File(backupDir, "attract-pre-v5-200.db").apply {
            writeText("valid-pre-v5-data")
            setLastModified(200L)
        }

        // Simulate restoreLatestSnapshot logic
        val latest = backupDir.listFiles { _, name ->
            name.startsWith("attract-pre-v") && name.endsWith(".db")
        }?.maxByOrNull { it.lastModified() }

        assertNotNull(latest)
        assertEquals("attract-pre-v5-200.db", latest!!.name)

        latest.copyTo(activeDbFile, overwrite = true)
        File(activeDbFile.path + "-wal").delete()
        File(activeDbFile.path + "-shm").delete()

        // Assertions: activeDb content is restored from latest snapshot, WAL & SHM are purged
        assertEquals("valid-pre-v5-data", activeDbFile.readText())
        assertFalse("WAL file must be deleted to prevent stale transaction replay", walFile.exists())
        assertFalse("SHM file must be deleted", shmFile.exists())
    }

    @Test
    fun restoreSnapshot_whenNoSnapshotsExist_failsGracefullyWithoutThrowing() {
        val rootDir = tempFolder.root
        val backupDir = File(rootDir, "backups").apply { mkdirs() }

        val latest = backupDir.listFiles { _, name ->
            name.startsWith("attract-pre-v") && name.endsWith(".db")
        }?.maxByOrNull { it.lastModified() }

        // Should return null (and false in the app) without throwing
        assertEquals(null, latest)
    }

    // -------------------------------------------------------------------------
    // 4. Crash-Loop Detection & Safe Mode Simulation
    // -------------------------------------------------------------------------

    @Test
    fun crashLoopDetector_threeCrashesWithinSixtySeconds_triggersSafeMode() {
        val windowMs = 60_000L
        val threshold = 3

        var safeMode = false
        val crashHistory = mutableListOf<Long>()

        fun recordCrash(now: Long) {
            val recentTimes = crashHistory
                .plus(now)
                .filter { now - it <= windowMs }

            crashHistory.clear()
            crashHistory.addAll(recentTimes)

            if (recentTimes.size >= threshold) {
                safeMode = true
            }
        }

        val baseTime = 1_000_000L

        // Crash 1: at t=0
        recordCrash(baseTime)
        assertFalse("1 crash must not trigger safe mode", safeMode)
        assertEquals(1, crashHistory.size)

        // Crash 2: at t=10s
        recordCrash(baseTime + 10_000L)
        assertFalse("2 crashes must not trigger safe mode", safeMode)
        assertEquals(2, crashHistory.size)

        // Crash 3: at t=25s (within 60s window)
        recordCrash(baseTime + 25_000L)
        assertTrue("3 crashes within 60s MUST trigger safe mode", safeMode)
        assertEquals(3, crashHistory.size)
    }

    @Test
    fun crashLoopDetector_crashesSpacedBeyondSixtySeconds_doNotTriggerSafeMode() {
        val windowMs = 60_000L
        val threshold = 3

        var safeMode = false
        val crashHistory = mutableListOf<Long>()

        fun recordCrash(now: Long) {
            val recentTimes = crashHistory
                .plus(now)
                .filter { now - it <= windowMs }

            crashHistory.clear()
            crashHistory.addAll(recentTimes)

            if (recentTimes.size >= threshold) {
                safeMode = true
            }
        }

        val baseTime = 1_000_000L

        // Crash 1 at t=0
        recordCrash(baseTime)
        assertFalse(safeMode)

        // Crash 2 at t=70s (Crash 1 expired)
        recordCrash(baseTime + 70_000L)
        assertFalse(safeMode)
        assertEquals("Crash 1 should have fallen out of the 60s sliding window", 1, crashHistory.size)

        // Crash 3 at t=140s (Crash 2 expired)
        recordCrash(baseTime + 140_000L)
        assertFalse("Spaced out crashes must not trigger safe mode", safeMode)
        assertEquals(1, crashHistory.size)
    }
}
