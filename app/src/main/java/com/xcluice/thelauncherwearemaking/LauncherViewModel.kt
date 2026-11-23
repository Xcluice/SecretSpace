package com.xcluice.thelauncherwearemaking

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context = application
    private val prefs = ctx.getSharedPreferences("vanta", Context.MODE_PRIVATE)
    private var loadJob: Job? = null

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var appMap by mutableStateOf<Map<String, AppInfo>>(emptyMap())
        private set
    var pages by mutableStateOf<List<List<String>>>(listOf(emptyList()))
        private set
    var dock by mutableStateOf<List<String>>(emptyList())
        private set
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set
    var settings by mutableStateOf(VantaSettings())
        private set
    var homeSignal by mutableIntStateOf(0)
        private set
    var ready by mutableStateOf(false)
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_PACKAGE_REMOVED &&
                i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            refresh()
        }
    }

    init {
        loadPrefs()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    override fun onCleared() {
        try {
            ctx.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    // ---------- loading ----------

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { queryApps() }
            apps = list
            appMap = list.associateBy { it.key }
            reconcile()
            ready = true
        }
    }

    private fun queryApps(): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val px = (ctx.resources.displayMetrics.density * 64f).toInt().coerceAtLeast(96)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .mapNotNull { ri ->
                try {
                    val ai = ri.activityInfo
                    AppInfo(
                        key = ai.packageName + "/" + ai.name,
                        pkg = ai.packageName,
                        cls = ai.name,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm).toBitmap(px, px).asImageBitmap(),
                    )
                } catch (e: Throwable) {
                    null
                }
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun reconcile() {
        val valid = appMap.keys
        if (!prefs.contains("pages")) {
            seedDefaults()
        } else {
            pages = normalize(pages.map { p -> p.filter { it in valid } })
            dock = dock.filter { it in valid }
            hidden = hidden.filter { it in valid }.toSet()
        }
        persist()
    }

    private fun resolveKey(intent: Intent): String? {
        val ri = ctx.packageManager.resolveActivity(intent, 0) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        if (pkg == "android") return null
        return apps.firstOrNull { it.pkg == pkg }?.key
    }

    private fun selector(category: String): Intent =
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    private fun seedDefaults() {
        val dockKeys = listOfNotNull(
            resolveKey(Intent(Intent.ACTION_DIAL)),
            resolveKey(selector(Intent.CATEGORY_APP_MESSAGING)),
            resolveKey(selector(Intent.CATEGORY_APP_BROWSER)),
            resolveKey(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinct()
        val homeKeys = listOfNotNull(
            resolveKey(selector(Intent.CATEGORY_APP_GALLERY)),
            resolveKey(selector(Intent.CATEGORY_APP_MUSIC)),
            resolveKey(selector(Intent.CATEGORY_APP_MAPS)),
            resolveKey(selector(Intent.CATEGORY_APP_CALENDAR)),
            resolveKey(selector(Intent.CATEGORY_APP_EMAIL)),
            resolveKey(selector(Intent.CATEGORY_APP_CONTACTS)),
            resolveKey(Intent(Settings.ACTION_SETTINGS)),
        ).distinct().filter { it !in dockKeys }.toMutableList()
        if (homeKeys.size < 8) {
            apps.map { it.key }
                .filter { it !in dockKeys && it !in homeKeys }
                .take(8 - homeKeys.size)
                .forEach { homeKeys.add(it) }
        }
        dock = dockKeys
        pages = normalize(listOf(homeKeys))
    }

    // ---------- persistence ----------

    private fun loadPrefs() {
        pages = readPages() ?: listOf(emptyList())
        dock = readList("dock") ?: emptyList()
        hidden = readList("hidden")?.toSet() ?: emptySet()
        settings = VantaSettings(
            iconDp = prefs.getInt("iconDp", 56),
            columns = prefs.getInt("columns", 4),
            labels = prefs.getBoolean("labels", true),
            haptics = prefs.getBoolean("haptics", true),
        )
    }

    private fun readList(name: String): List<String>? {
        val s = prefs.getString(name, null) ?: return null
        return try {
            val a = JSONArray(s)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun readPages(): List<List<String>>? {
        val s = prefs.getString("pages", null) ?: return null
        return try {
            val a = JSONArray(s)
            List(a.length()) { i ->
                val p = a.getJSONArray(i)
                List(p.length()) { j -> p.getString(j) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun persist() {
        val pa = JSONArray()
        pages.forEach { p ->
            val a = JSONArray()
            p.forEach { a.put(it) }
            pa.put(a)
        }
        val da = JSONArray()
        dock.forEach { da.put(it) }
        val ha = JSONArray()
        hidden.forEach { ha.put(it) }
        prefs.edit()
            .putString("pages", pa.toString())
            .putString("dock", da.toString())
            .putString("hidden", ha.toString())
            .putInt("iconDp", settings.iconDp)
            .putInt("columns", settings.columns)
            .putBoolean("labels", settings.labels)
            .putBoolean("haptics", settings.haptics)
            .apply()
    }

    private fun normalize(list: List<List<String>>): List<List<String>> {
        val nz = list.filter { it.isNotEmpty() }
        return nz.ifEmpty { listOf(emptyList()) }
    }

    // ---------- actions ----------

    fun onHomePressed() {
        homeSignal++
    }

    fun addToHome(key: String) {
        if (pages.any { key in it }) return
        val cap = settings.columns * 5
        val p = pages.toMutableList()
        if (p.last().size >= cap) p.add(emptyList())
        p[p.lastIndex] = p.last() + key
        pages = p
        persist()
    }

    fun removeFromHome(key: String) {
        pages = normalize(pages.map { it - key })
        persist()
    }

    fun addToDock(key: String) {
        if (key in dock || dock.size >= 5) return
        dock = dock + key
        persist()
    }

    fun removeFromDock(key: String) {
        dock = dock - key
        persist()
    }

    fun hide(key: String) {
        hidden = hidden + key
        pages = normalize(pages.map { it - key })
        dock = dock - key
        persist()
    }

    fun unhideAll() {
        hidden = emptySet()
        persist()
    }

    fun updateSettings(s: VantaSettings) {
        val colsChanged = s.columns != settings.columns
        settings = s
        if (colsChanged) {
            pages = normalize(pages.flatten().chunked(s.columns * 5))
        }
        persist()
    }
}
