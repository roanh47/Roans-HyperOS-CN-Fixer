package nl.roanheemstra.cnfixer.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reads the latest release from the repository, no account and no key. */
object UpdateChecker {

    private const val REPO = "roanh47/Roans-HyperOS-CN-Fixer"

    data class Release(
        val tag: String,
        val version: String,
        val notes: String,
        val apkUrl: String?,
        val sizeBytes: Long,
    )

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val connection = runCatching {
            (URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CN-Fixer")
            }
        }.getOrNull() ?: return@withContext null

        try {
            if (connection.responseCode != 200) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            var apkUrl: String? = null
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size")
                        break
                    }
                }
            }

            val tag = json.optString("tag_name")
            Release(
                tag = tag,
                version = tag.removePrefix("v"),
                notes = json.optString("body"),
                apkUrl = apkUrl,
                sizeBytes = apkSize,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Compares dotted versions, so 0.10.0 is newer than 0.9.0. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').mapNotNull { it.trim().toIntOrNull() }
        val b = current.split('.').mapNotNull { it.trim().toIntOrNull() }
        if (a.isEmpty() || b.isEmpty()) return false
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
