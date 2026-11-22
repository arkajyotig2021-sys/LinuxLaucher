package app.nova.desktop.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class ExternalAppInfo(val packageName: String, val label: String)

/**
 * Launches installed Android apps using their normal Android tasks. Android does not let
 * ordinary third-party apps embed arbitrary other apps' Activities as child windows.
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

    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
