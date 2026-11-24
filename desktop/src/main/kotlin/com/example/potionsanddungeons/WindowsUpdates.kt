package com.example.potionsanddungeons

import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

private const val MAX_EXTRACTED_BYTES = 450L * 1024 * 1024
private val safeWindowsComponent = Regex("[A-Za-z0-9._+-]+")
private val windowsDevices = Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]", RegexOption.IGNORE_CASE)

internal fun stageWindowsUpdate(launcherRoot: File, update: GameUpdate, archive: File): File {
    require(update.target == UpdateTarget.WINDOWS)
    val updates = launcherRoot.toPath().resolve(".updates")
    Files.createDirectories(updates)
    val destination = updates.resolve(update.version)
    if (isReadyWindowsUpdate(destination, update.version)) return destination.toFile()
    val staging = Files.createTempDirectory(updates, ".staging-")
    try {
        extractWindowsUpdate(archive, staging)
        require(Files.isRegularFile(staging.resolve("runtime/bin/javaw.exe")))
        require(Files.isRegularFile(staging.resolve("lib/PotionsAndDungeons-${update.version}.jar")))
        Files.writeString(staging.resolve("ready"), update.version)
        try {
            // Same-volume rename publishes a complete build without touching the one in use.
            Files.move(staging, destination)
        } catch (exists: FileAlreadyExistsException) {
            if (!isReadyWindowsUpdate(destination, update.version)) throw exists
        }
        return destination.toFile()
    } finally {
        if (Files.isDirectory(staging)) Files.walk(staging).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }
}

private fun isReadyWindowsUpdate(path: Path, version: String): Boolean =
    !Files.exists(path.resolve("failed")) && Files.isRegularFile(path.resolve("ready")) &&
        Files.readString(path.resolve("ready")) == version &&
        Files.isRegularFile(path.resolve("runtime/bin/javaw.exe")) &&
        Files.isRegularFile(path.resolve("lib/PotionsAndDungeons-$version.jar"))

private fun extractWindowsUpdate(archive: File, staging: Path) {
    ZipFile(archive).use { zip ->
        require(zip.size() in 1..4096)
        var bytesRemaining = MAX_EXTRACTED_BYTES
        for (entry in zip.entries()) {
            val path = entry.name.removeSuffix("/").split('/')
            require(path.size <= 20 && path.firstOrNull() == "PotionsAndDungeons" && path.all {
                safeWindowsComponent.matches(it) && !it.endsWith('.') && !windowsDevices.matches(it.substringBefore('.'))
            })
            val relative = path.drop(1)
            if (relative.firstOrNull() !in setOf("runtime", "lib")) continue
            if (relative.first() == "lib" && !entry.isDirectory) {
                require(relative.size == 2 && relative.last().endsWith(".jar"))
            }
            val output = relative.fold(staging) { parent, name -> parent.resolve(name) }
            if (entry.isDirectory) {
                Files.createDirectories(output)
                continue
            }
            Files.createDirectories(output.parent)
            zip.getInputStream(entry).use { input ->
                Files.newOutputStream(output, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE).buffered().use { file ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytesRemaining -= read
                        if (bytesRemaining < 0L) throw IOException("Update archive is too large")
                        file.write(buffer, 0, read)
                    }
                }
            }
        }
    }
}
