package app.nova.desktop.runtime
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DownloadEntry(val id:Long,val url:String,val fileName:String,val sha256:String,val status:VerifiedDownloadStatus)
private data class PendingDownload(val url:String,val host:String,val fileName:String,val sha256:String)

@Composable
fun DownloadCenterScreen(){
 val context=LocalContext.current
 val manager=remember(context){VerifiedDownloadManager(context)}
 val scope=rememberCoroutineScope()
 var url by remember{mutableStateOf("")}
 var fileName by remember{mutableStateOf("")}
 var sha256 by remember{mutableStateOf("")}
 var entries by remember{mutableStateOf(emptyList<DownloadEntry>())}
 var pending by remember{mutableStateOf<PendingDownload?>(null)}
 var message by remember{mutableStateOf("Review every download before it starts. A SHA-256 checksum is required.")}

 LaunchedEffect(Unit){
  while(true){
   val snapshot=entries
   val updated=withContext(Dispatchers.IO){
    snapshot.map{entry->
     val inspected=manager.inspect(entry.id)
     val checked=when{
      entry.status.state==VerifiedDownloadState.VERIFIED->entry.status
      inspected.state==VerifiedDownloadState.VERIFYING->manager.verify(entry.id,entry.sha256)
      else->inspected
     }
     entry.copy(status=checked)
    }
   }
   entries=updated
   delay(1200)
  }
 }

 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("Download manager",style=MaterialTheme.typography.titleLarge)
  Text("NOVA checks the URL, asks for approval, then verifies the downloaded file.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(value=url,onValueChange={url=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("HTTPS download URL")})
  OutlinedTextField(value=fileName,onValueChange={fileName=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Save as (filename)")})
  OutlinedTextField(value=sha256,onValueChange={sha256=it.trim()},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Expected SHA-256 (64 hex characters)")})
  Button(onClick={
   val parsed=Uri.parse(url.trim());val host=parsed.host
   when{
    parsed.scheme!="https"||host.isNullOrBlank()->message="Enter a valid HTTPS URL."
    !fileName.matches(Regex("^[A-Za-z0-9._-]{1,120}$"))->message="Use a simple filename with letters, numbers, dots, underscores or hyphens."
    !sha256.matches(Regex("^[a-fA-F0-9]{64}$"))->message="Enter the expected 64-character SHA-256 checksum."
    else->pending=PendingDownload(url.trim(),host,fileName.trim(),sha256.trim().lowercase())
   }
  },modifier=Modifier.fillMaxWidth()){Text("Review download")}
  Text(message,style=MaterialTheme.typography.bodySmall)
  HorizontalDivider()
  Text("Download queue",style=MaterialTheme.typography.titleMedium)
  if(entries.isEmpty())Text("No downloads in this session.",style=MaterialTheme.typography.bodySmall)
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
   items(entries,key={it.id}){entry->
    Card(Modifier.fillMaxWidth()){
     Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
      Text(entry.fileName,fontWeight=FontWeight.Bold)
      Text(entry.status.state.name,style=MaterialTheme.typography.labelMedium)
      if(entry.status.totalBytes>0){
       LinearProgressIndicator(progress={entry.status.progressPercent/100f},modifier=Modifier.fillMaxWidth())
       Text("${entry.status.progressPercent}%")
      }
      if(entry.status.detail.isNotBlank())Text(entry.status.detail,style=MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
       if(entry.status.state==VerifiedDownloadState.QUEUED||entry.status.state==VerifiedDownloadState.RUNNING){
        TextButton(onClick={
         scope.launch(Dispatchers.IO){manager.cancel(entry.id)}
         entries=entries.filterNot{it.id==entry.id}
         message="Download cancelled."
        }){Text("Cancel")}
       }
       if(entry.status.state==VerifiedDownloadState.FAILED||entry.status.state==VerifiedDownloadState.MISSING){
        TextButton(onClick={pending=PendingDownload(entry.url,Uri.parse(entry.url).host.orEmpty(),entry.fileName,entry.sha256)}){Text("Retry")}
       }
      }
     }
    }
   }
  }
 }
 pending?.let{request->
  AlertDialog(
   onDismissRequest={pending=null},
   title={Text("Download this file?")},
   text={Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(request.fileName,fontWeight=FontWeight.Bold)
    Text("Source: ${request.host}")
    Text("Connection: HTTPS")
    Text("Destination: NOVA app-specific verified-downloads folder")
    Text("Integrity check: SHA-256")
    Text("Publisher and file safety are not verified by the checksum.")
    Text("Only download files from sources you trust.")
   }},
   confirmButton={TextButton(onClick={
    pending=null
    scope.launch{
     message="Adding download to Android's download service…"
     runCatching{
      withContext(Dispatchers.IO){
       val id=manager.enqueue(request.url,request.fileName,request.sha256,setOf(request.host))
       id to manager.inspect(id)
      }
     }.onSuccess{(id,status)->
      entries=entries.filterNot{it.id==id}+DownloadEntry(id,request.url,request.fileName,request.sha256,status)
      message="Download queued. NOVA will verify the checksum when it finishes."
     }.onFailure{error->message=error.message?:"Could not start the download."}
    }
   }){Text("Download")}},
   dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}}
  )
 }
}
