// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.data

import io.terraxaleid.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
 * Deliberately no dependency: one GET on `/releases/latest`, read `tag_name` and `html_url`, compare
 * the tag to `BuildConfig.VERSION_NAME`. GitHub answers 404 both for "repository is empty" and for
 * "no release yet", which here means the same thing to the user, so it is reported as such.
 */
internal object UpdateCheck {
    private const val ENDPOINT =
        "https://api.github.com/repos/Dragrowl/TerraXaleid/releases/latest"

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
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim().removePrefix("v")
            val url = json.optString("html_url").trim()
            if (tag.isEmpty()) return@withContext UpdateResult.Failed("empty-tag")

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
