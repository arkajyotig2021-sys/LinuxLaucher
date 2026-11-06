package com.mindflux.linuxlauncher.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.mindflux.linuxlauncher.runtime.LinuxRuntimeSession
import com.mindflux.linuxlauncher.runtime.RuntimeBinaryLocator
import com.mindflux.linuxlauncher.runtime.RuntimeCommandBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LinuxEnvironmentChooserDialog(distroId:String,onDismiss:()->Unit,onTerminal:()->Unit,onDesktop:()->Unit) {
 AlertDialog(onDismissRequest=onDismiss,title={Text("Open $distroId")},
  text={Text("Terminal works with general OCI Linux images. One-click XFCE desktop setup currently supports Debian/Ubuntu package layouts.")},
  confirmButton={Column { TextButton(onClick=onTerminal){Text("Open terminal")}; TextButton(onClick=onDesktop){Text("Install / open XFCE desktop")}}},
  dismissButton={TextButton(onClick=onDismiss){Text("Close")}})
}

@Composable
fun LinuxDesktopDialog(distroId:String,onDismiss:()->Unit) {
 val context= LocalContext.current; val scope= rememberCoroutineScope()
 val rootfs= remember(distroId){RuntimeCommandBuilder.rootfsDirectory(context.filesDir,distroId)}
 var installed by remember(rootfs){mutableStateOf(File(rootfs,"usr/bin/startxfce4").exists() && File(rootfs,"usr/bin/vncserver").exists())}
 var working by remember{mutableStateOf(false)}; var ready by remember{mutableStateOf(false)}; var viewer by remember{mutableStateOf(false)}
 var errorMessage by remember{mutableStateOf<String?>(null)}; val log= remember{mutableStateListOf<String>()}
 suspend fun execute(command:String):Int {
  val binary=RuntimeBinaryLocator(context).prootExecutable() ?: error("PRoot engine is missing from this APK build.")
  val process=withContext(Dispatchers.IO){LinuxRuntimeSession(binary,rootfs).startCommand(command)}
  return withContext(Dispatchers.IO){val reader=process.inputStream.bufferedReader(); while(true){val line=reader.readLine()?:break; withContext(Dispatchers.Main){if(log.size>200)log.removeAt(0);log.add(line)}};process.waitFor()}
 }
 val setup="""
  set -e
  if command -v apt-get >/dev/null 2>&1; then export DEBIAN_FRONTEND=noninteractive; apt-get update; apt-get install -y xfce4 dbus-x11 tigervnc-standalone-server novnc websockify;
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
  nohup vncserver :1 -localhost yes -geometry 1280x720 -depth 24 -SecurityTypes None >/tmp/linux-vnc.log 2>&1 </dev/null &
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
   errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(log.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().height(130.dp)){items(log){Text(it,color=Color(0xFFB7C6D6),style=MaterialTheme.typography.bodySmall)}}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    Button(enabled=!working&&!installed,onClick={working=true;errorMessage=null;log.clear();scope.launch{try{val code=execute(setup);if(code!=0)errorMessage="Desktop installation failed (exit $code)." else installed=true}catch(t:Throwable){errorMessage=t.message?:"Setup failed."}finally{working=false}}}){Text(if(installed)"Installed" else "Install XFCE")}
    Button(enabled=installed&&!working,onClick={working=true;errorMessage=null;log.clear();scope.launch{try{val code=execute(start);if(code==0){ready=true;viewer=true}else errorMessage="Desktop start failed (exit $code)."}catch(t:Throwable){errorMessage=t.message?:"Launch failed."}finally{working=false}}}){Text("Launch desktop")}
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
