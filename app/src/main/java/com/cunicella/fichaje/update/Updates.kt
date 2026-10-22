package com.cunicella.fichaje.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cunicella.fichaje.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Actualizaciones fuera de Play Store: cada push a main publica un Release en GitHub
 * con tag v0.1.<versionCode> y el APK firmado. La app compara contra su versionCode.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/nacunicella/fichaje-android/releases/latest"

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String)

    @Serializable
    private data class Release(@SerialName("tag_name") val tag: String, val assets: List<Asset> = emptyList())

    data class Available(val versionCode: Int, val versionName: String, val url: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { HttpClient(Android) }

    val currentName: String get() = BuildConfig.VERSION_NAME

    suspend fun check(): Available? = runCatching {
        val body = http.get(LATEST) { header("Accept", "application/vnd.github+json") }.bodyAsText()
        val r = json.decodeFromString<Release>(body)
        val code = r.tag.substringAfterLast('.').toIntOrNull()
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk") }
        if (code != null && apk != null && code > BuildConfig.VERSION_CODE) Available(code, r.tag.removePrefix("v"), apk.url) else null
    }.getOrNull()

    /** Baja el APK a la caché de la app, informando el avance de 0 a 1. */
    suspend fun download(context: Context, a: Available, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "updates/fichaje.apk").apply { parentFile?.mkdirs(); delete() }
        http.prepareGet(a.url).execute { resp ->
            val total = resp.contentLength() ?: -1L
            val channel = resp.bodyAsChannel()
            val buf = ByteArray(64 * 1024)
            var read = 0L
            file.outputStream().use { out ->
                while (true) {
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n == -1) break
                    if (n > 0) {
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    /** Android pide permiso una sola vez para que Fichaje pueda instalar sus actualizaciones. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
