package com.example.potionsanddungeons

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val GAME_RELEASES_URL = "https://github.com/RAARKA2321/PotionsAndDungeons/releases"
private const val RELEASE_API = "https://api.github.com/repos/RAARKA2321/PotionsAndDungeons/releases/latest"
private const val MAX_METADATA_BYTES = 512 * 1024
private val stableVersion = Regex("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
private val sha256Pattern = Regex("[0-9a-f]{64}")

internal enum class UpdateTarget(val suffix: String, val maxBytes: Long) {
    ANDROID(".apk", 125L * 1024 * 1024), WINDOWS("-Windows-x64.zip", 350L * 1024 * 1024);

    fun fileName(version: String) = "PotionsAndDungeons-$version$suffix"
}

internal data class GameUpdate(val version: String, val target: UpdateTarget, val size: Long, val sha256: String) {
    val fileName: String get() = target.fileName(version)
    val url: String get() = "$GAME_RELEASES_URL/download/v$version/$fileName"

    init {
        require(stableVersion.matches(version) && size in 1..target.maxBytes && sha256Pattern.matches(sha256))
    }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    if (!stableVersion.matches(candidate) || !stableVersion.matches(current)) return false
    return candidate.split('.').map(String::toInt).zip(current.split('.').map(String::toInt))
        .firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } == true
}

internal fun readGameUpdate(json: JSONObject, current: String, target: UpdateTarget): GameUpdate? {
    if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
    val tag = json.getString("tag_name")
    if (!tag.startsWith("v")) return null
    val version = tag.drop(1)
    if (!isNewerVersion(version, current)) return null
    val assets = json.getJSONArray("assets")
    val expected = target.fileName(version)
    for (index in 0 until assets.length()) {
        val asset = assets.getJSONObject(index)
        if (asset.optString("name") != expected || asset.optString("state", "uploaded") != "uploaded") continue
        val digest = asset.optString("digest")
        require(digest.startsWith("sha256:"))
        return GameUpdate(version, target, asset.getLong("size"), digest.removePrefix("sha256:"))
    }
    throw IOException("Missing update package")
}

internal fun checkGameUpdate(current: String, target: UpdateTarget): GameUpdate? {
    if (!stableVersion.matches(current)) return null
    val manifest = runCatching { readReleaseMetadata("$GAME_RELEASES_URL/latest/download/update-manifest.json") }.getOrNull()
    if (manifest != null) return readGameUpdate(manifest, current, target)
    val releaseTag = withReleaseResponse(URI("$GAME_RELEASES_URL/latest"), followRedirects = false) { connection ->
        connection.getHeaderField("Location")?.takeIf { it.startsWith("$GAME_RELEASES_URL/tag/v") }
            ?.removePrefix("$GAME_RELEASES_URL/tag/v")
    }
    if (releaseTag != null && stableVersion.matches(releaseTag) && !isNewerVersion(releaseTag, current)) return null
    val release = readReleaseMetadata(RELEASE_API) ?: throw IOException("Release not found")
    return readGameUpdate(release, current, target)
}

private fun readReleaseMetadata(url: String): JSONObject? =
    withReleaseResponse(URI(url)) { connection ->
        if (connection.responseCode == 404) null
        else {
            checkResponse(connection)
            JSONObject(connection.inputStream.use { it.readBoundedMetadata() })
        }
    }

private fun InputStream.readBoundedMetadata(): String {
    val buffer = ByteArray(MAX_METADATA_BYTES + 1)
    var used = 0
    while (used < buffer.size) {
        val read = read(buffer, used, buffer.size - used)
        if (read < 0) break
        used += read
    }
    if (used > MAX_METADATA_BYTES) throw IOException("Release metadata is too large")
    return buffer.decodeToString(0, used)
}

private val releaseHosts = setOf("github.com", "api.github.com", "release-assets.githubusercontent.com",
    "objects.githubusercontent.com", "github-releases.githubusercontent.com")

private inline fun <T> withReleaseResponse(
    initial: URI, followRedirects: Boolean = true, read: (HttpsURLConnection) -> T
): T {
    var url = initial
    repeat(6) {
        if (url.scheme != "https" || url.host !in releaseHosts || url.userInfo != null || url.port !in listOf(-1, 443)) {
            throw IOException("Unexpected update host")
        }
        val connection = url.toURL().openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "PotionsAndDungeons-Updater")
        try {
            if (followRedirects && connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                url = url.resolve(connection.getHeaderField("Location") ?: throw IOException("Missing redirect"))
            } else return read(connection)
        } finally {
            connection.disconnect()
        }
    }
    throw IOException("Too many update redirects")
}

private fun checkResponse(connection: HttpsURLConnection) {
    if (connection.responseCode != 200) throw IOException("Update response ${connection.responseCode}")
}

internal suspend fun downloadGameUpdate(update: GameUpdate, folder: File, onProgress: (Int) -> Unit): File {
    val job = currentCoroutineContext()
    if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) throw IOException("Cannot create update directory")
    val verified = File(folder, update.fileName)
    val temporary = File.createTempFile("update-", ".part", folder)
    try {
        if (verified.isFile && verified.length() == update.size) {
            val existingHash = verified.inputStream().buffered().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    job.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().hex()
            }
            if (existingHash == update.sha256) return verified
        }
        withReleaseResponse(URI(update.url)) { connection ->
            checkResponse(connection)
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            var lastProgress = -1
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        job.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > update.size) throw IOException("Update length does not match")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        val progress = (count * 100 / update.size).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
            if (count != update.size || digest.digest().hex() != update.sha256) throw IOException("Update verification failed")
        }
        if (verified.exists() && !verified.delete()) throw IOException("Cannot replace cached update")
        if (!temporary.renameTo(verified)) throw IOException("Cannot save verified update")
        return verified
    } finally {
        temporary.delete()
    }
}

private fun ByteArray.hex() = joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
