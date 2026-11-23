package com.xcluice.thelauncherwearemaking.ui

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.compose.ui.geometry.Rect
import com.xcluice.thelauncherwearemaking.AppInfo

fun launchApp(ctx: Context, view: View, app: AppInfo, bounds: Rect?) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = ComponentName(app.pkg, app.cls)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    val options = if (bounds != null && bounds.width > 1f) {
        ActivityOptions.makeScaleUpAnimation(
            view,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt(),
            bounds.height.toInt(),
        )
    } else {
        ActivityOptions.makeBasic()
    }
    try {
        ctx.startActivity(intent, options.toBundle())
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

fun openAppInfo(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

fun uninstallApp(ctx: Context, pkg: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

fun openClock(ctx: Context) {
    try {
        ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(sb)
    } catch (_: Throwable) {
    }
}
