package com.fitztv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fitztv.data.Http
import com.fitztv.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.io.File

data class Release(val build: Int, val title: String, val notes: String, val assetUrl: String, val assetName: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val build: Int) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Updates the app from this project's GitHub Releases (published by CI on every push).
 * Only talks to api.github.com; the optional token is for while the repo is private.
 */
class Updater(
    private val context: Context,
    private val settings: SettingsStore,
    /** "TV" or "Mobile": which APK in the release belongs to this app. */
    private val assetKeyword: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Build number of the installed app (CI run number; 0 for local builds). */
    val currentBuild: Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
        code - 100
    }.getOrDefault(0)

    private fun request(url: String, accept: String): Request {
        val b = Request.Builder().url(url).header("Accept", accept).header("User-Agent", "FitzTV-Updater")
        settings.value.githubToken.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        return b.build()
    }

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        val result = fetchLatest()
        _state.value = result
        result
    }

    /** Asks GitHub for the latest release without touching the shown state. */
    private fun fetchLatest(): UpdateState {
        return runCatching {
            Http.bulk.newCall(request("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json")).execute().use { r ->
                when {
                    r.code == 404 && settings.value.githubToken.isBlank() ->
                        return@runCatching UpdateState.Error("Can't see releases: the repo is private. Add a GitHub token in Settings › App updates.")
                    r.code == 401 || r.code == 403 || r.code == 404 ->
                        return@runCatching UpdateState.Error("GitHub refused the request (HTTP ${r.code}). Check the token in Settings › App updates.")
                    !r.isSuccessful -> return@runCatching UpdateState.Error("GitHub error (HTTP ${r.code})")
                }
                val o = json.parseToJsonElement(r.body!!.string()) as JsonObject
                val tag = (o["tag_name"] as? JsonPrimitive)?.content.orEmpty()
                val build = tag.substringAfterLast('-').toIntOrNull() ?: 0
                // "FitzTV-TV.apk" (current releases) or "FitzTV-TV-build-N.apk" (older ones).
                val assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                fun named(o: JsonObject) = (o["name"] as? JsonPrimitive)?.content.orEmpty()
                val asset = assets.firstOrNull { named(it).equals("FitzTV-$assetKeyword.apk", true) }
                    ?: assets.firstOrNull { named(it).contains("-$assetKeyword-", true) }
                    ?: return@runCatching UpdateState.Error("Latest release has no $assetKeyword APK")
                val release = Release(
                    build = build,
                    title = (o["name"] as? JsonPrimitive)?.content ?: tag,
                    notes = (o["body"] as? JsonPrimitive)?.content.orEmpty().lineSequence().take(12).joinToString("\n"),
                    assetUrl = (asset["url"] as JsonPrimitive).content,
                    assetName = (asset["name"] as JsonPrimitive).content,
                    size = (asset["size"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                )
                if (build > currentBuild) UpdateState.Available(release) else UpdateState.UpToDate(currentBuild)
            }
        }.getOrElse { UpdateState.Error("Couldn't check for updates (${it.message})") }
    }

    /** Downloads the APK and hands it to Android's installer (asks once to allow installs from Fitz TV). */
    suspend fun downloadAndInstall(release: Release) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            _state.value = UpdateState.Error("Allow Fitz TV to install apps (screen just opened), then press Update again.")
            return@withContext
        }
        runCatching {
            val file = downloadFile(release)
            install(file)
            _state.value = UpdateState.Available(release)
        }.onFailure { _state.value = UpdateState.Error("Update failed: ${it.message}") }
    }

    /** Streams the release APK to the cache folder (progress shown) and checks it is a Fitz TV APK. */
    private fun downloadFile(release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, release.assetName)
        Http.bulk.newCall(request(release.assetUrl, "application/octet-stream")).execute().use { r ->
            if (!r.isSuccessful) error("download failed (HTTP ${r.code})")
            val body = r.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != lastPct) { lastPct = pct; _state.value = UpdateState.Downloading(release, pct) }
                    }
                }
            }
        }
        if (release.size > 0 && file.length() != release.size) error("download incomplete")
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (info == null || info.packageName != context.packageName) error("downloaded file is not a Fitz TV update")
        return file
    }

    private suspend fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Launch-time update: check, download the new build and hand it to Android's installer,
     * up to [maxTries] attempts with a short wait between. If it still fails the update is
     * skipped quietly (the next launch tries again). Android itself asks you to confirm the
     * install; an app cannot install silently. If installs from Fitz TV aren't allowed yet,
     * the normal update prompt is shown instead of opening settings unasked.
     */
    suspend fun autoUpdate(maxTries: Int = 3) = withContext(Dispatchers.IO) {
        val latest = fetchLatest()
        val release = (latest as? UpdateState.Available)?.release
        if (release == null) { _state.value = if (latest is UpdateState.UpToDate) latest else UpdateState.Idle; return@withContext }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Available(release)
            return@withContext
        }
        for (attempt in 1..maxTries) {
            try {
                _state.value = UpdateState.Downloading(release, 0)
                val file = downloadFile(release)
                install(file)
                _state.value = UpdateState.Idle
                return@withContext
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt < maxTries) kotlinx.coroutines.delay(attempt * 4_000L)
            }
        }
        // Skipped: no error banner, the app just carries on with the current build.
        _state.value = UpdateState.Idle
    }

    fun dismiss() { if (_state.value is UpdateState.Available) _state.value = UpdateState.Idle }

    companion object {
        const val REPO = "Fitzy1590/Fitz-TV"
    }
}
