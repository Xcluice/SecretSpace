package com.xcluice.secretspace

import android.app.Activity
import android.content.Intent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Dim = Color(0xB3FFFFFF)

@Composable
fun SpaceScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var hasSpace by remember { mutableStateOf(spaceExists(ctx)) }
    var msg by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf(Store.secret(ctx)) }
    var upd by remember { mutableStateOf("Version ${BuildConfig.VERSION_NAME}") }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasSpace = spaceExists(ctx) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        hasSpace = spaceExists(ctx)
        msg = if (r.resultCode == Activity.RESULT_OK) "Second space created." else "Setup was cancelled or blocked."
    }

    fun create() {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            ComponentName(ctx, AdminReceiver::class.java)
        )
        val allowed = try {
            dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        } catch (e: Exception) {
            true
        }
        if (!allowed) {
            msg = "This phone won't allow a second space (one may already exist, or the maker blocks it)."
            return
        }
        Lock.suppress = true
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Lock.suppress = false
            msg = "This phone can't create a work profile."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hidden space", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        if (msg.isNotEmpty()) Text(msg, color = Dim, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))

        if (Build.VERSION.SDK_INT >= 35) {
            Panel("Private Space (Android 15+)") {
                Text(
                    "Android's own Private Space is locked and fully hidden, with its own Play Store. " +
                        "Apps can't create it, so set it up once in Settings.",
                    color = Dim
                )
                GradButton("Open Private Space settings") {
                    Lock.suppress = true
                    try {
                        ctx.startActivity(
                            Intent("android.settings.PRIVATE_SPACE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e2: Exception) {
                            Lock.suppress = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Panel("Second space (work profile)") {
            if (!hasSpace) {
                Text(
                    "Not created yet. Makes an isolated copy of Android with its own apps, data and Play Store.",
                    color = Dim
                )
                GradButton("Create second space") { create() }
            } else {
                Text("Your space is ready.", color = Dim)
                GradButton("Open space") {
                    if (!remote(ctx, "home")) msg = "Couldn't reach the space."
                }
                GradButton("Play Store in space") {
                    if (!remote(ctx, "store")) msg = "Couldn't reach the space."
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel("Secret title") {
            Text("Type this as a note title to open the hidden space.", color = Dim)
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            GradButton("Save secret title", enabled = secret.trim().length >= 3) {
                Store.setSecret(ctx, secret)
                msg = "Secret title saved."
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel("Updates") {
            Text(upd, color = Dim)
            GradButton("Check for updates") {
                scope.launch {
                    upd = "Checking…"
                    val r = withContext(Dispatchers.IO) { Updater.latest() }
                    if (r == null) upd = "Couldn't reach the update source."
                    else if (r.code > BuildConfig.VERSION_CODE) {
                        release = r
                        upd = "Update ${r.name} is available."
                    } else upd = "You're up to date (${BuildConfig.VERSION_NAME})."
                }
            }
            release?.let { r ->
                GradButton("Download and install ${r.name}") {
                    scope.launch {
                        upd = "Downloading…"
                        val f = withContext(Dispatchers.IO) {
                            try {
                                Updater.download(ctx, r.apkUrl)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (f == null) upd = "Download failed."
                        else {
                            upd = "Ready to install."
                            Updater.install(ctx, f)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
