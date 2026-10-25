package com.yakhrevan.lifetrack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class LifeTrackUpdate(
    val versionName: String,
    val releaseUrl: String
)

internal object UpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Yakhrevan/-Life-track/releases/latest"

    suspend fun check(currentVersion: String): LifeTrackUpdate? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LifeTrack-Android")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(payload)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                return@withContext null
            }
            val tag = release.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val releaseUrl = release.optString("html_url", "")
            if (tag.isBlank() || releaseUrl.isBlank()) return@withContext null
            if (compareVersions(tag, currentVersion) > 0) LifeTrackUpdate(tag, releaseUrl) else null
        } catch (_: Exception) {
            // Updates are optional: network/API errors must never prevent app startup.
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun compareVersions(remote: String, current: String): Int {
        fun parts(value: String): List<Int> = value
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
