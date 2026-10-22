package com.xcluice.secretspace

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import org.json.JSONArray
import org.json.JSONObject

const val ACTION_REMOTE = "com.xcluice.secretspace.REMOTE"

/** Stops the auto-lock while we deliberately leave the app (provisioning, settings, installer). */
object Lock {
    @Volatile
    var suppress = false
}

data class Note(val id: String, val title: String, val body: String, val updated: Long)

object Store {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    fun load(c: Context): List<Note> = try {
        val arr = JSONArray(sp(c).getString("notes", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Note(o.getString("id"), o.getString("title"), o.getString("body"), o.getLong("updated"))
        }.sortedByDescending { it.updated }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(c: Context, notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title)
                    .put("body", it.body).put("updated", it.updated)
            )
        }
        sp(c).edit().putString("notes", arr.toString()).apply()
    }

    fun secret(c: Context): String = sp(c).getString("secret", ".space") ?: ".space"

    fun setSecret(c: Context, s: String) {
        sp(c).edit().putString("secret", s.trim()).apply()
    }
}

/** True when the phone has another profile for this user (our work-profile space). */
fun spaceExists(c: Context): Boolean = try {
    (c.getSystemService(Context.USER_SERVICE) as UserManager).userProfiles.size > 1
} catch (e: Exception) {
    false
}

/** Sends a command to the copy of this app inside the second space. */
fun remote(c: Context, cmd: String): Boolean = try {
    Lock.suppress = true
    c.startActivity(
        Intent(ACTION_REMOTE).putExtra("cmd", cmd).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    Lock.suppress = false
    false
}

val STORE_PACKAGES = listOf(
    "com.android.vending",
    "com.google.android.gms",
    "com.google.android.gsf"
)

/** Runs inside the space: turns on Google Play Store and its services there. */
fun enableStore(c: Context) {
    val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(c, AdminReceiver::class.java)
    STORE_PACKAGES.forEach {
        try {
            dpm.enableSystemApp(admin, it)
        } catch (e: Exception) {
        }
    }
}
