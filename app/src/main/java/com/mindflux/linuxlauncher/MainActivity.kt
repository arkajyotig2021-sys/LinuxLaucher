package com.mindflux.linuxlauncher

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import com.mindflux.linuxlauncher.runtime.LauncherErrorHistory
import com.mindflux.linuxlauncher.runtime.LauncherNotifications
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mindflux.linuxlauncher.ui.LauncherApp
import com.mindflux.linuxlauncher.ui.theme.LinuxLauncherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LauncherErrorHistory.record(this, "Application crash", throwable.stackTraceToString())
            LauncherNotifications.error(this, "Linux Launcher crashed", throwable.message ?: throwable.javaClass.simpleName)
            previousHandler?.uncaughtException(thread, throwable)
        }
        setContent {
            LinuxLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherApp()
                }
            }
        }
        // Let the first screen render before showing the Android 13+ permission dialog.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val permissionPrefs = getSharedPreferences("linux_launcher_preferences", MODE_PRIVATE)
            if (!permissionPrefs.getBoolean("notification_permission_asked", false)) {
                permissionPrefs.edit().putBoolean("notification_permission_asked", true).apply()
                window.decorView.post {
                    if (!isFinishing && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
                    }
                }
            }
        }
    }
}
