package com.xcluice.secretspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** In-app updater: reads the latest GitHub release and installs its APK. */
object Updater {
    private const val REPO = "Xcluice/SecretSpace"

    data class Release(val code: Int, val name: String, val apkUrl: String)

    fun latest(): Release? = try {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) null
        else {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = o.getString("tag_name")
            val code = tag.removePrefix("v").toIntOrNull()
            val assets = o.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            if (code == null || url == null) null else Release(code, "1.$code", url)
        }
    } catch (e: Exception) {
        null
    }

    fun download(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "Notes.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        return out
    }

    fun install(ctx: Context, f: File) {
        Lock.suppress = true
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
