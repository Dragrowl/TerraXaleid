// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.data

import io.terraxaleid.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** What the release feed told us: the tag as published, and the page a user can open. */
internal data class ReleaseInfo(val tag: String, val url: String)

/** Outcome of one check. A sealed result keeps the screen dumb: it only maps to text. */
internal sealed interface UpdateResult {
    /** A release newer than this build exists. */
    data class Newer(val release: ReleaseInfo) : UpdateResult

    /** The feed answered and this build is the published one. */
    data class Latest(val release: ReleaseInfo) : UpdateResult

    /** Anything that stopped us answering: no release yet, no network, a proxy in the way. */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Checks the project's own GitHub releases for the newest published tag.
 *
 * Deliberately no dependency: one GET on the release list, read `tag_name` and `html_url` from the
 * newest entry, compare the tag to `BuildConfig.VERSION_NAME`. The list endpoint is used instead of
 * `/releases/latest` because every release of this project is flagged as a pre-release, and `/latest`
 * ignores pre-releases — it would answer 404 forever and the screen would always say "no release yet".
 * An empty list (repository without releases) is reported as `no-release`.
 */
internal object UpdateCheck {
    private const val ENDPOINT =
        "https://api.github.com/repos/Dragrowl/TerraXaleid/releases?per_page=4"

    suspend fun run(): UpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TerraXaleid/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext UpdateResult.Failed("no-release")
            }
            if (code !in 200..299) {
                return@withContext UpdateResult.Failed("http-$code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            var tag = ""
            var url = ""
            for (index in 0 until releases.length()) {
                val entry = releases.optJSONObject(index) ?: continue
                if (entry.optBoolean("draft", false)) continue
                tag = entry.optString("tag_name").trim().removePrefix("v")
                url = entry.optString("html_url").trim()
                break
            }
            if (tag.isEmpty()) return@withContext UpdateResult.Failed("no-release")

            val mine = BuildConfig.VERSION_NAME.trim().removePrefix("v")
            val release = ReleaseInfo(tag = tag, url = url)
            if (tag == mine) UpdateResult.Latest(release) else UpdateResult.Newer(release)
        } catch (error: Exception) {
            // A dropped connection, a DNS failure, a TLS reset through a proxy: all the same to us.
            UpdateResult.Failed(error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
