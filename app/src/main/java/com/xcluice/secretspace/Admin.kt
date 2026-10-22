package com.xcluice.secretspace

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle

/** Profile owner of the second space. Runs inside the space right after it is created. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = getComponentName(context)
        val pm = context.packageManager

        try {
            dpm.setProfileName(admin, "Notes")
        } catch (e: Exception) {
        }

        // Let the main side reach RemoteActivity inside the space.
        try {
            val filter = IntentFilter(ACTION_REMOTE)
            filter.addCategory(Intent.CATEGORY_DEFAULT)
            dpm.addCrossProfileIntentFilter(
                admin, filter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
            )
        } catch (e: Exception) {
        }

        pm.setComponentEnabledSetting(
            ComponentName(context, RemoteActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        // No Notes icon inside the space: the only way in is the secret title.
        pm.setComponentEnabledSetting(
            ComponentName(context, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        enableStore(context)

        try {
            dpm.setProfileEnabled(admin)
        } catch (e: Exception) {
        }
    }
}

class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            RESULT_OK,
            Intent().putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            )
        )
        finish()
    }
}

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}

/** Entry point inside the space for commands sent from the main side. */
class RemoteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra("cmd")) {
            "home" -> startActivity(Intent(this, SpaceHomeActivity::class.java))
            "store" -> {
                enableStore(this)
                val launch = packageManager.getLaunchIntentForPackage("com.android.vending")
                if (launch != null) startActivity(launch)
                else startActivity(Intent(this, SpaceHomeActivity::class.java))
            }
        }
        finish()
    }
}
