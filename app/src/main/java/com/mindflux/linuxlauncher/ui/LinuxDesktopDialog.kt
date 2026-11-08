package com.mindflux.linuxlauncher.ui

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.mindflux.linuxlauncher.runtime.LinuxBackupManager
import com.mindflux.linuxlauncher.runtime.LinuxRuntimeSession
import com.mindflux.linuxlauncher.runtime.RuntimeBinaryLocator
import com.mindflux.linuxlauncher.runtime.RuntimeCommandBuilder
import com.mindflux.linuxlauncher.runtime.PerformancePolicy
import com.mindflux.linuxlauncher.runtime.LauncherNotifications
import com.mindflux.linuxlauncher.runtime.LauncherErrorHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LinuxEnvironmentChooserDialog(distroId:String,onDismiss:()->Unit,onTerminal:()->Unit,onDesktop:()->Unit) {
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 var backupBusy by remember { mutableStateOf(false) }
 var backupStatus by remember { mutableStateOf("") }
 var backupError by remember { mutableStateOf<String?>(null) }
 var restoreUri by remember { mutableStateOf<Uri?>(null) }
 val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
  if (uri != null) scope.launch {
   backupBusy = true; backupError = null; backupStatus = "Creating full rootfs backup…"
   try {
    val output = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) } ?: error("Could not open the selected backup file")
    LinuxBackupManager.backup(context, distroId, output) { progress -> scope.launch { backupStatus = progress } }
    backupStatus = "Backup saved successfully."
   } catch (t: Throwable) { backupError = t.message ?: "Backup failed." }
   finally { backupBusy = false }
  }
 }
 val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
 AlertDialog(onDismissRequest=onDismiss,title={Text("Open $distroId")},
  text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
   Text("Terminal works with general OCI Linux images. One-click XFCE desktop setup currently supports Debian/Ubuntu package layouts.")
   Text("Full backup includes the Linux root filesystem. Restore replaces the currently installed root filesystem.", style=MaterialTheme.typography.bodySmall)
   if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
   if (backupStatus.isNotBlank()) Text(backupStatus, style=MaterialTheme.typography.bodySmall)
   backupError?.let { Text(it, color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall) }
  }},
  confirmButton={Column {
   TextButton(enabled=!backupBusy,onClick=onTerminal){Text("Open terminal")}
   TextButton(enabled=!backupBusy,onClick=onDesktop){Text("Install / open XFCE desktop")}
   TextButton(enabled=!backupBusy,onClick={exportPicker.launch("$distroId-full-backup.tar.gz")}){Text("Back up Linux")}
   TextButton(enabled=!backupBusy,onClick={importPicker.launch(arrayOf("application/gzip","application/x-gzip","application/octet-stream"))}){Text("Restore Linux backup")}
  }},
  dismissButton={TextButton(onClick=onDismiss,enabled=!backupBusy){Text("Close")}})
 restoreUri?.let { selectedUri ->
  AlertDialog(
   onDismissRequest = { restoreUri = null },
   title = { Text("Replace $distroId?") },
   text = { Text("Restoring this archive will replace the current Linux root filesystem. Make a backup first if you need to keep the current installation.") },
   confirmButton = {
    TextButton(onClick = {
     restoreUri = null
     backupBusy = true
     backupError = null
     backupStatus = "Restoring Linux rootfs…"
     scope.launch {
      try {
       val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(selectedUri) }
        ?: error("Could not open the selected backup")
       LinuxBackupManager.restore(context, distroId, input) { progress ->
        scope.launch { backupStatus = progress }
       }
       backupStatus = "Restore completed successfully."
      } catch (t: Throwable) {
       backupError = t.message ?: "Restore failed."
      } finally {
       backupBusy = false
      }
     }
    }) { Text("Replace Linux") }
   },
   dismissButton = {
    TextButton(onClick = { restoreUri = null }) { Text("Cancel") }
   }
  )
 }

}

@Composable
fun LinuxDesktopDialog(distroId:String,onDismiss:()->Unit) {
 val context= LocalContext.current; val scope= rememberCoroutineScope()
 val rootfs= remember(distroId){RuntimeCommandBuilder.rootfsDirectory(context.filesDir,distroId)}
 var installed by remember(rootfs){mutableStateOf(File(rootfs,"usr/bin/startxfce4").exists() && File(rootfs,"usr/bin/vncserver").exists())}
 var working by remember{mutableStateOf(false)}; var ready by remember{mutableStateOf(false)}; var viewer by remember{mutableStateOf(false)}
 var errorMessage by remember{mutableStateOf<String?>(null)}; val log= remember{mutableStateListOf<String>()}
  var renderer by remember(distroId) { mutableStateOf("CPU") }
 suspend fun execute(command:String):Int {
  val binary=RuntimeBinaryLocator(context).prootExecutable() ?: error("PRoot engine is missing from this APK build.")
  val process=withContext(Dispatchers.IO){LinuxRuntimeSession(binary,rootfs,PerformancePolicy.resolve(context).threadPriority).startCommand(command)}
  return withContext(Dispatchers.IO){val reader=process.inputStream.bufferedReader(); while(true){val line=reader.readLine()?:break; withContext(Dispatchers.Main){if(log.size>200)log.removeAt(0);log.add(line)}};process.waitFor()}
 }
 val setup="""
  set -e
  if command -v apt-get >/dev/null 2>&1; then
   export DEBIAN_FRONTEND=noninteractive
   if [ ! -e /usr/sbin/policy-rc.d ]; then printf '%s\n' '#!/bin/sh' 'exit 101' > /usr/sbin/policy-rc.d; chmod 755 /usr/sbin/policy-rc.d; _ll_created_policy=1; fi
   trap '[ "${'$'}{_ll_created_policy:-0}" = 1 ] && rm -f /usr/sbin/policy-rc.d' EXIT
   echo 'Refreshing Debian package indexes…'
   apt-get update
   echo 'Repairing Debian D-Bus package dependencies…'
   apt-get install -y --no-install-recommends dbus-daemon dbus-x11
   apt-get -f install -y
   dpkg --configure -a
   echo 'Installing XFCE and VNC desktop packages…'
   apt-get install -y --no-install-recommends xfce4 dbus-x11 tigervnc-standalone-server novnc websockify
   apt-get -f install -y
   dpkg --configure -a
   apt-get check
   if [ "${'$'}{_ll_created_policy:-0}" = 1 ]; then rm -f /usr/sbin/policy-rc.d; fi
  else echo 'Automatic desktop setup currently supports apt-based Debian/Ubuntu images. Use your distro package manager from Terminal on other Linux images.'; exit 42; fi
  mkdir -p /root/.vnc
  printf '%s\n' '#!/bin/sh' 'unset SESSION_MANAGER' 'unset DBUS_SESSION_BUS_ADDRESS' 'exec dbus-launch --exit-with-session startxfce4' > /root/.vnc/xstartup
  chmod 700 /root/.vnc/xstartup
  command -v vncserver; command -v websockify; test -f /usr/share/novnc/vnc.html
  touch /root/.linuxlauncher-xfce-installed
  echo 'XFCE desktop components installed.'
 """.trimIndent()
 val start="""
  set -e
  vncserver -kill :1 >/dev/null 2>&1 || true
  nohup vncserver :1 -localhost yes -geometry ${PerformancePolicy.resolve(context).desktopGeometry} -depth 24 -SecurityTypes None >/tmp/linux-vnc.log 2>&1 </dev/null &
  sleep 2
  nohup websockify --web=/usr/share/novnc 127.0.0.1:6080 127.0.0.1:5901 >/tmp/linux-websockify.log 2>&1 </dev/null &
  sleep 2
  echo 'Desktop services started on localhost.'
 """.trimIndent()
 Dialog(onDismissRequest={if(!working)onDismiss()}) { Surface(color=Color(0xFF101923),shape=MaterialTheme.shapes.extraLarge) {
  Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
   Text("$distroId · XFCE desktop",color=Color(0xFF69E6B7),style=MaterialTheme.typography.titleLarge)
   Text("Lightweight desktop streamed locally to this app. It shares Android's kernel and uses your phone's CPU and RAM.",color=Color(0xFFE1E8EF))
   if(working) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("Rendering backend",color=Color(0xFF69E6B7),style=MaterialTheme.typography.titleSmall)
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     RadioButton(selected=renderer=="CPU",onClick={renderer="CPU"})
     Column { Text("Phone CPU · Available",color=Color(0xFFE1E8EF)); Text("Supported software rendering through the local VNC desktop.",color=Color(0xFF8290A0),style=MaterialTheme.typography.bodySmall) }
    }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     RadioButton(selected=false,onClick=null,enabled=false)
     Column { Text("Phone GPU · Unavailable",color=Color(0xFF8290A0)); Text("The current TigerVNC/noVNC path has no Android GPU passthrough.",color=Color(0xFF8290A0),style=MaterialTheme.typography.bodySmall) }
    }
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     RadioButton(selected=false,onClick=null,enabled=false)
     Column { Text("Kaggle GPU · Unavailable",color=Color(0xFF8290A0)); Text("Kaggle GPU is notebook-session compute, not a supported persistent remote renderer for this local desktop.",color=Color(0xFF8290A0),style=MaterialTheme.typography.bodySmall) }
    }
   errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(log.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().height(130.dp)){items(log){Text(it,color=Color(0xFFB7C6D6),style=MaterialTheme.typography.bodySmall)}}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Button(enabled=!working&&!installed,onClick={working=true;errorMessage=null;log.clear();scope.launch{try{val code=execute(setup);if(code!=0){errorMessage="Desktop installation failed (exit $code).";LauncherErrorHistory.record(context,"Desktop installation","Exit $code\n"+log.joinToString("\n"));LauncherNotifications.error(context,"Desktop installation failed",errorMessage?:"Exit $code")}else { installed=true; LauncherNotifications.finished(context,"XFCE installed","$distroId desktop is ready.") }}catch(t:Throwable){errorMessage=t.message?:"Setup failed.";LauncherErrorHistory.record(context,"Desktop setup",t.stackTraceToString());LauncherNotifications.error(context,"Desktop setup failed",errorMessage?:"Unknown error")}finally{working=false}}}){Text(if(installed)"Installed" else "Install XFCE")}
    Button(enabled=installed&&!working,onClick={working=true;errorMessage=null;log.clear();log.add("Rendering backend: $renderer");scope.launch{try{val code=execute(start);if(code==0){ready=true;viewer=true;LauncherNotifications.finished(context,"Linux desktop started","$distroId XFCE is running.")}else {errorMessage="Desktop start failed (exit $code).";LauncherErrorHistory.record(context,"Desktop launch","Exit $code\n"+log.joinToString("\n"));LauncherNotifications.error(context,"Desktop launch failed",errorMessage?:"Exit $code")}}catch(t:Throwable){errorMessage=t.message?:"Launch failed.";LauncherErrorHistory.record(context,"Desktop launch",t.stackTraceToString());LauncherNotifications.error(context,"Desktop launch failed",errorMessage?:"Unknown error")}finally{working=false}}}){Text("Launch desktop")}
   }
   TextButton(onClick=onDismiss,enabled=!working){Text("Close")}
  }
 }}
 if(viewer&&ready) Dialog(onDismissRequest={viewer=false}) { Surface(color=Color(0xFF101923),shape=MaterialTheme.shapes.large) {
  Column(Modifier.fillMaxWidth().padding(8.dp)) {
   Text("XFCE · $distroId",color=Color(0xFF69E6B7),modifier=Modifier.padding(8.dp))
   AndroidView(modifier=Modifier.fillMaxWidth().height(520.dp),factory={ctx->WebView(ctx).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;webViewClient=WebViewClient();loadUrl("http://127.0.0.1:6080/vnc.html?host=127.0.0.1&port=6080&encrypt=0&path=websockify&autoconnect=1&resize=scale")}})
   TextButton(onClick={viewer=false}){Text("Close viewer")}
  }
 }}
}
