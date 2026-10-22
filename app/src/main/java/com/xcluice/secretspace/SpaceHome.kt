package com.xcluice.secretspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/** The inside of the second space: lists its apps, adds more, freezes, removes. */
class SpaceHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(GradBg)) {
                    ProfileHome(this@SpaceHomeActivity) { finish() }
                }
            }
        }
    }
}

private data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap?)

private fun prefs(c: Context) = c.getSharedPreferences("space", Context.MODE_PRIVATE)
private fun frozenSet(c: Context): Set<String> = prefs(c).getStringSet("frozen", emptySet())!!.toSet()
private fun saveFrozen(c: Context, s: Set<String>) {
    prefs(c).edit().putStringSet("frozen", s).apply()
}

@Suppress("DEPRECATION")
private fun item(pm: PackageManager, ai: ApplicationInfo): AppItem =
    AppItem(
        ai.packageName,
        ai.loadLabel(pm).toString(),
        try {
            ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    )

@Suppress("DEPRECATION")
private fun loadInstalled(c: Context): List<AppItem> {
    val pm = c.packageManager
    val frozen = frozenSet(c)
    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launch, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != c.packageName && it.packageName !in frozen }
        .distinctBy { it.packageName }
        .map { item(pm, it) }
        .sortedBy { it.label.lowercase() }
}

@Suppress("DEPRECATION")
private fun loadFrozen(c: Context): List<AppItem> {
    val pm = c.packageManager
    return frozenSet(c).mapNotNull {
        try {
            item(pm, pm.getApplicationInfo(it, PackageManager.MATCH_UNINSTALLED_PACKAGES))
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.label.lowercase() }
}

/** Apps on the phone that are not yet inside the space. */
@Suppress("DEPRECATION")
private fun loadCandidates(c: Context): List<AppItem> {
    val pm = c.packageManager
    return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        .filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                (it.flags and ApplicationInfo.FLAG_INSTALLED) == 0 &&
                it.packageName != c.packageName
        }
        .map { item(pm, it) }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun AppRow(a: AppItem, onClick: (() -> Unit)?, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (a.icon != null) Image(a.icon, null, Modifier.size(40.dp))
        else Spacer(Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun ProfileHome(act: Activity, onClose: () -> Unit) {
    val dpm = remember { act.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val admin = remember { ComponentName(act, AdminReceiver::class.java) }
    val owner = remember { dpm.isProfileOwnerApp(act.packageName) }
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }

    val installed = remember(tick) { loadInstalled(act) }
    val frozen = remember(tick) { loadFrozen(act) }
    val candidates = remember(tick, tab) { if (tab == 1) loadCandidates(act) else emptyList() }

    fun setHidden(pkg: String, hide: Boolean) {
        try {
            dpm.setApplicationHidden(admin, pkg, hide)
            val s = frozenSet(act).toMutableSet()
            if (hide) s.add(pkg) else s.remove(pkg)
            saveFrozen(act, s)
        } catch (e: Exception) {
            msg = "Couldn't change that app."
        }
        tick++
    }

    fun open(pkg: String) {
        act.packageManager.getLaunchIntentForPackage(pkg)?.let { act.startActivity(it) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Second space", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        if (!owner) {
            Text("Open this from the main Notes app after creating the space.", color = Color(0xB3FFFFFF))
            return@Column
        }
        Row(Modifier.padding(vertical = 10.dp)) {
            listOf("Apps", "Add").forEachIndexed { i, t ->
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (tab == i) GradMain else androidx.compose.ui.graphics.SolidColor(Panel))
                        .clickable { tab = i }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) { Text(t, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = Color(0xB3FFFFFF), modifier = Modifier.padding(bottom = 6.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                if (installed.isEmpty() && frozen.isEmpty()) {
                    item { Text("Nothing here yet. Use Add to bring apps in.", color = Color(0xB3FFFFFF)) }
                }
                items(installed, key = { it.pkg }) { a ->
                    AppRow(a, onClick = { open(a.pkg) }) {
                        TextButton(onClick = { setHidden(a.pkg, true) }) { Text("Freeze") }
                        TextButton(onClick = {
                            act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                        }) { Text("Remove") }
                    }
                }
                items(frozen, key = { "f" + it.pkg }) { a ->
                    AppRow(a, onClick = null) {
                        Text("Frozen", color = Color(0x80FFFFFF), fontSize = 12.sp)
                        TextButton(onClick = { setHidden(a.pkg, false) }) { Text("Unfreeze") }
                    }
                }
            } else {
                item {
                    GradButton("Add Google Play Store") {
                        enableStore(act)
                        tick++
                        msg = "Play Store enabled. Open it from Apps, then sign in with a second account."
                    }
                }
                items(candidates, key = { it.pkg }) { a ->
                    AppRow(a, onClick = null) {
                        TextButton(onClick = {
                            val ok = try {
                                dpm.installExistingPackage(admin, a.pkg)
                            } catch (e: Exception) {
                                false
                            }
                            msg = if (ok) "${a.label} added." else "Couldn't add ${a.label}."
                            tick++
                        }) { Text("Add") }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Remove second space and everything in it",
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.fillMaxWidth().clickable { confirmRemove = true }.padding(12.dp)
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove second space?") },
            text = { Text("All apps and data inside the space will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        dpm.wipeData(0)
                    } catch (e: Exception) {
                        msg = "Couldn't remove the space."
                    }
                    confirmRemove = false
                }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}
