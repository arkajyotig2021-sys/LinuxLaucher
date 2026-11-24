package app.nova.desktop.runtime
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
private data class DEntry(val id:Long,val url:String,val name:String,val sha:String,val status:VerifiedDownloadStatus)
@Composable fun DownloadCenterScreen(){
 val ctx=LocalContext.current;val manager=remember(ctx){VerifiedDownloadManager(ctx)};val scope=rememberCoroutineScope()
 var url by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var sha by remember{mutableStateOf("")};var entries by remember{mutableStateOf(emptyList<DEntry>())}
 var pending by remember{mutableStateOf<Triple<String,String,String>?>(null)};var message by remember{mutableStateOf("Downloads require approval and a SHA-256 checksum.")}
 LaunchedEffect(entries.map{it.id}){while(true){entries=withContext(Dispatchers.IO){entries.map{e->var s=manager.inspect(e.id);if(s.state==VerifiedDownloadState.VERIFYING)s=manager.verify(e.id,e.sha);e.copy(status=s)}};delay(1200)}}
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("Download manager",style=MaterialTheme.typography.titleLarge);Text("No download starts without your confirmation.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(url,{url=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("HTTPS download URL")});OutlinedTextField(name,{name=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Filename")});OutlinedTextField(sha,{sha=it.trim()},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Expected SHA-256")})
  Button(onClick={val u=Uri.parse(url.trim());when{u.scheme!="https"||u.host.isNullOrBlank()->message="Enter a valid HTTPS URL.";!name.matches(Regex("^[A-Za-z0-9._-]{1,120}$"))->message="Use a simple filename.";!sha.matches(Regex("^[a-fA-F0-9]{64}$"))->message="SHA-256 must contain 64 hex characters.";else->pending=Triple(url.trim(),name.trim(),sha.trim())}},modifier=Modifier.fillMaxWidth()){Text("Review download")}
  Text(message,style=MaterialTheme.typography.bodySmall);HorizontalDivider();Text("Queue",style=MaterialTheme.typography.titleMedium);if(entries.isEmpty())Text("No downloads yet.",style=MaterialTheme.typography.bodySmall)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){items(entries,key={it.id}){e->Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(e.name,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold);Text(e.status.state.name,style=MaterialTheme.typography.labelMedium);if(e.status.totalBytes>0){LinearProgressIndicator(progress={e.status.progressPercent/100f},modifier=Modifier.fillMaxWidth());Text(e.status.progressPercent.toString()+"%")};if(e.status.detail.isNotBlank())Text(e.status.detail,style=MaterialTheme.typography.bodySmall);if(e.status.state==VerifiedDownloadState.RUNNING||e.status.state==VerifiedDownloadState.QUEUED)TextButton(onClick={scope.launch(Dispatchers.IO){manager.cancel(e.id)};entries=entries.filterNot{it.id==e.id}){Text("Cancel")};if(e.status.state==VerifiedDownloadState.FAILED||e.status.state==VerifiedDownloadState.MISSING)TextButton(onClick={pending=Triple(e.url,e.name,e.sha)}){Text("Retry")}}}}}
 }
 pending?.let{r->val host=Uri.parse(r.first).host;AlertDialog(onDismissRequest={pending=null},title={Text("Download this file?")},text={Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(r.second);Text("Source: "+host);Text("Transfer: HTTPS");Text("Size: reported during download");Text("Destination: NOVA app storage / verified-downloads");Text("Integrity: SHA-256 checked after download");Text("Proceed only if you trust the source and checksum.")}},confirmButton={TextButton(onClick={pending=null;scope.launch{message="Starting download…";runCatching{withContext(Dispatchers.IO){val id=manager.enqueue(r.first,r.second,r.third,setOf(host!!));id to manager.inspect(id)}}.onSuccess{p->entries=entries.filterNot{it.id==p.first}+DEntry(p.first,r.first,r.second,r.third,p.second);message="Download queued."}.onFailure{message=it.message?:"Could not start download."}}}){Text("Download")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})}
}
