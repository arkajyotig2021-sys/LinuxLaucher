package app.nova.desktop.runtime

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect

data class ExternalAppInfo(val packageName: String, val label: String)

/**
 * Launches Android apps as normal system-owned tasks. Android's public APIs do not let
 * an ordinary launcher re-parent arbitrary third-party Activities into its own view tree.
 * setLaunchBounds is only a request; the OS/OEM may ignore it when freeform windows are disabled.
 */
class ExternalAppLauncher(private val context: Context) {
    fun installedLaunchableApps(): List<ExternalAppInfo> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .mapNotNull { result ->
                val activity = result.activityInfo ?: return@mapNotNull null
                ExternalAppInfo(activity.packageName, result.loadLabel(context.packageManager).toString())
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun launch(packageName: String): Boolean = launch(packageName, false)

    /** Requests an Android-managed freeform task where the device firmware supports it. */
    fun launchInSystemWindow(packageName: String): Boolean = launch(packageName, true)

    private fun launch(packageName: String, requestWindow: Boolean): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (requestWindow) {
            val metrics = context.resources.displayMetrics
            val bounds = Rect(
                (metrics.widthPixels * 0.04f).toInt(),
                (metrics.heightPixels * 0.08f).toInt(),
                (metrics.widthPixels * 0.96f).toInt(),
                (metrics.heightPixels * 0.84f).toInt()
            )
            val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
            context.startActivity(intent, options.toBundle())
        } else {
            context.startActivity(intent)
        }
        return true
    }
}
