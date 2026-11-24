package com.example.potionsanddungeons

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GameUpdateTest {
    @get:Rule val folder = TemporaryFolder()
    private val hash = "ab".repeat(32)
    private val update = GameUpdate("1.10.0", UpdateTarget.WINDOWS, 1024, hash)

    @Test
    fun selectsOnlyNewStableReleasesWithTheExpectedAssetAndDigest() {
        val asset = JSONObject().put("name", update.fileName).put("size", update.size).put("digest", "sha256:$hash")
        val release = JSONObject().put("tag_name", "v1.10.0").put("assets", JSONArray().put(asset))
        assertEquals(update, readGameUpdate(release, "1.9.9", UpdateTarget.WINDOWS))
        assertNull(readGameUpdate(release, "1.10.0", UpdateTarget.WINDOWS))
        assertNull(readGameUpdate(release.put("prerelease", true), "1.9.9", UpdateTarget.WINDOWS))
        release.put("prerelease", false)
        asset.put("digest", "sha256:bad")
        assertThrows(IllegalArgumentException::class.java) { readGameUpdate(release, "1.9.9", UpdateTarget.WINDOWS) }
    }

    @Test
    fun stagesOnlyACompletePortableBuildWithoutChangingTheRunningOne() {
        val installed = folder.newFolder("game")
        val oldRuntime = File(installed, "runtime/bin/javaw.exe")
        oldRuntime.parentFile.mkdirs()
        oldRuntime.writeText("original")
        val archive = zip(
            "PotionsAndDungeons/runtime/bin/javaw.exe",
            "PotionsAndDungeons/lib/PotionsAndDungeons-1.10.0.jar",
            "PotionsAndDungeons/PotionsAndDungeons.exe"
        )
        val staged = stageWindowsUpdate(installed, update, archive)
        assertEquals("original", oldRuntime.readText())
        assertEquals("1.10.0", File(staged, "ready").readText())
        assertTrue(File(staged, "lib/PotionsAndDungeons-1.10.0.jar").isFile)
        assertFalse(File(staged, "PotionsAndDungeons.exe").exists())
        assertEquals(staged, stageWindowsUpdate(installed, update, archive))
    }

    @Test
    fun rejectsEscapingAndIncompleteArchivesBeforePublishing() {
        val installed = folder.newFolder("game")
        val escaping = zip("PotionsAndDungeons/runtime/../../outside.txt")
        assertThrows(IllegalArgumentException::class.java) { stageWindowsUpdate(installed, update, escaping) }
        val incomplete = zip("PotionsAndDungeons/runtime/bin/javaw.exe")
        assertThrows(IllegalArgumentException::class.java) { stageWindowsUpdate(installed, update, incomplete) }
        assertFalse(File(installed, ".updates/1.10.0").exists())
        assertFalse(File(installed, "outside.txt").exists())
    }

    private fun zip(vararg entries: String): File = folder.newFile().also { archive ->
        ZipOutputStream(archive.outputStream()).use { zip ->
            entries.forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }
    }
}
