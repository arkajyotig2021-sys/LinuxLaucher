package app.nova.desktop
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.nova.desktop.files.FileManagerScreen
import app.nova.desktop.runtime.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
private val NovaBlue=Color(0xFF55C8FF)
private val NovaPurple=Color(0xFF9B75FF)
@Composable fun NovaDesktopApp(tree:Uri?,chooseFolder:()->Unit){
 var windows by remember{mutableStateOf(listOf<DesktopWindowState>())};var start by remember{mutableStateOf(false)}
 var clock by remember{mutableStateOf("")};var profile by remember{mutableStateOf("Smooth")}
 LaunchedEffect(Unit){while(true){clock=SimpleDateFormat("EEE, d MMM · h:mm a",Locale.getDefault()).format(Date());delay(1000)}}
 fun open(name:String){val old=windows.firstOrNull{it.title==name};windows=if(old!=null)windows.filterNot{it.id==old.id}+old.copy(minimized=false)else windows+DesktopWindowState(title=name,x=16f+windows.size*12f,y=50f+windows.size*10f,width=340f,height=470f);start=false}
 MaterialTheme(colorScheme=darkColorScheme(primary=NovaBlue,secondary=NovaPurple,background=Color(0xFF080D1D),surface=Color(0xFF111A30),surfaceVariant=Color(0xFF1A2744),onSurface=Color(0xFFEAF1FF),onSurfaceVariant=Color(0xFFABB9D2))){
  BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF070B19),Color(0xFF101A36),Color(0xFF21143D),Color(0xFF090F24))))){
   Column(Modifier.fillMaxSize().padding(14.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("N O V A",color=NovaBlue,fontSize=12.sp,fontWeight=FontWeight.Black);Text("Workspace",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)};Column(horizontalAlignment=Alignment.End){Text(clock,color=Color(0xFFD9E4FF),fontSize=11.sp);Text("ANDROID DESKTOP",color=NovaPurple,fontSize=9.sp,fontWeight=FontWeight.Bold)}}
    Spacer(Modifier.height(15.dp))
    Card(Modifier.fillMaxWidth().weight(1f),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Color(0x70202D50)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x665C8DDB))){
     Column(Modifier.fillMaxSize().padding(16.dp)){Text("Your space. Your workflow.",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("A touch-first desktop for Android",color=Color(0xFFB6C5E4),fontSize=12.sp);Spacer(Modifier.height(22.dp))
      Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){Shortcut("Files",Icons.Default.Folder,NovaBlue,Modifier.weight(1f)){open("Files")};Shortcut("Apps",Icons.Default.Apps,NovaPurple,Modifier.weight(1f)){open("Apps")};Shortcut("Linux",Icons.Default.Terminal,Color(0xFF70E0C0),Modifier.weight(1f)){open("Linux")}}
      Spacer(Modifier.height(9.dp))
      Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){Shortcut("Downloads",Icons.Default.Download,NovaBlue,Modifier.weight(1f)){open("Downloads")};Shortcut("Monitor",Icons.Default.Speed,NovaPurple,Modifier.weight(1f)){open("Performance")};Shortcut("Settings",Icons.Default.Settings,Color(0xFFFFC879),Modifier.weight(1f)){open("Settings")}}
      Spacer(Modifier.height(22.dp));Text("RECENT WORKSPACE",color=Color(0xFF9EAFD1),fontSize=10.sp,fontWeight=FontWeight.Bold)
      Row(Modifier.padding(top=9.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Memory,null,tint=NovaBlue,modifier=Modifier.size(30.dp));Column(Modifier.weight(1f).padding(start=9.dp)){Text("NOVA runtime centre",color=Color.White,fontSize=12.sp);Text("Linux, Windows compatibility and device tools",color=Color(0xFF9EAFD1),fontSize=10.sp)};TextButton(onClick={open("Apps")}){Text("OPEN")}}
      Spacer(Modifier.weight(1f));Text("NOVA Neon · Performance: $profile",color=Color(0xFF8FA2C8),fontSize=10.sp)
     }
    }
    Spacer(Modifier.height(11.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.horizontalGradient(listOf(Color(0xE51A3154),Color(0xE52A2050)))).padding(horizontal=4.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick={start=!start}){Icon(Icons.Default.GridView,"Start",tint=NovaBlue)}
     listOf("Files","Apps","Linux","Downloads","Performance").forEach{n->IconButton(onClick={open(n)},modifier=Modifier.weight(1f)){Icon(when(n){"Files"->Icons.Default.Folder;"Apps"->Icons.Default.Apps;"Linux"->Icons.Default.Terminal;"Downloads"->Icons.Default.Download;else->Icons.Default.Speed},n,tint=if(windows.any{it.title==n&&!it.minimized})NovaBlue else Color.White)}}
     TextButton(onClick={open("Settings")}){Text("⋮",color=Color.White)}
    }
    val minimized=windows.filter{it.minimized};if(minimized.isNotEmpty())Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){minimized.forEach{w->AssistChip(onClick={windows=windows.map{if(it.id==w.id)it.copy(minimized=false)else it}},label={Text(w.title)})}}
   }
   windows.filterNot{it.minimized}.forEachIndexed{index,w->DesktopWindowFrame(w,index.toFloat()+1f,{u->windows=windows.map{if(it.id==u.id)u else it}},{windows=windows.filterNot{it.id==w.id}+w},{windows=windows.map{if(it.id==w.id)it.copy(minimized=true)else it}},{windows=windows.map{if(it.id==w.id)it.copy(maximized=!it.maximized)else it}},{windows=windows.filterNot{it.id==w.id}}){NovaWindowContent(w.title,tree,chooseFolder,{open(it)},{profile=it})}}
   if(start)Dialog(onDismissRequest={start=false}){Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xF21A2542)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x885C8DDB))){Column(Modifier.padding(16.dp)){Text("NOVA LAUNCHER",color=NovaBlue,fontWeight=FontWeight.Black);Text("Open an app",color=Color.White,fontSize=18.sp);listOf("Files","Apps","Linux","Downloads","Performance","Settings","Notes").forEach{n->TextButton(onClick={open(n)},modifier=Modifier.fillMaxWidth()){Text(n,modifier=Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null)}}}}}
  }
 }
}
@Composable private fun Shortcut(name:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color,modifier:Modifier=Modifier,onClick:()->Unit){Card(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color(0x7A26395E)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0x405C8DDB))){Column(Modifier.fillMaxWidth().padding(vertical=13.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=tint,modifier=Modifier.size(25.dp));Spacer(Modifier.height(6.dp));Text(name,color=Color.White,fontSize=10.sp,fontWeight=FontWeight.SemiBold)}}}
@Composable private fun NovaWindowContent(title:String,tree:Uri?,chooseFolder:()->Unit,onOpen:(String)->Unit,onProfile:(String)->Unit){
 val context=LocalContext.current
 when(title){"Files"->FileManagerScreen(tree,chooseFolder);"Apps"->RuntimeAppsScreen(onOpen);"Linux"->{val m=remember(context){LinuxRuntimeManager(context)};val s=remember{m.status()};Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text("Linux environment",style=MaterialTheme.typography.titleLarge);Text(s.detail,style=MaterialTheme.typography.bodySmall);when(s.state){LinuxInstallState.READY->Button(onClick={onOpen("Linux Terminal")}){Text("Open Alpine terminal")};LinuxInstallState.NOT_INSTALLED->Button(onClick={onOpen("Linux Installer")}){Text("Install Alpine Linux")};else->Text("Linux unavailable for this Android version or ABI.")};Text("Graphical Linux desktops are not integrated yet.",style=MaterialTheme.typography.bodySmall)}};"Linux Terminal"->LinuxTerminalScreen(LinuxTerminalMode.LOGIN);"Linux Installer"->LinuxTerminalScreen(LinuxTerminalMode.INSTALL);"Downloads"->DownloadCenterScreen();"Performance"->NovaPerformance(onProfile);"Settings"->NovaSettings();"Notes"->NovaNotes();"System Monitor"->NovaPerformance(onProfile);else->Text("This NOVA module is not available yet.")}
}
@Composable private fun NovaPerformance(onProfile:(String)->Unit){val ctx=LocalContext.current;var pss by remember{mutableIntStateOf(0)};var ram by remember{mutableIntStateOf(0)};var cpu by remember{mutableIntStateOf(0)}
 LaunchedEffect(Unit){var c=android.os.Process.getElapsedCpuTime();var t=android.os.SystemClock.elapsedRealtime();while(true){delay(2000);val nc=android.os.Process.getElapsedCpuTime();val nt=android.os.SystemClock.elapsedRealtime();cpu=(((nc-c).coerceAtLeast(0)*100)/(nt-t).coerceAtLeast(1)).toInt().coerceIn(0,100);c=nc;t=nt;pss=android.os.Debug.getPss()/1024;val mi=android.app.ActivityManager.MemoryInfo();(ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi);ram=(mi.availMem/1048576L).toInt()}}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Device performance",style=MaterialTheme.typography.titleLarge);Metric("NOVA process CPU","$cpu%");Metric("NOVA process memory","$pss MB");Metric("System available RAM","$ram MB");listOf("Smooth","Balanced","Battery Saver").forEach{OutlinedButton(onClick={onProfile(it)},modifier=Modifier.fillMaxWidth()){Text(it)}};GraphicsInfoCard();Text("CPU is process usage. Hardware clock control is unavailable to ordinary apps.",style=MaterialTheme.typography.bodySmall)}
}
@Composable private fun Metric(label:String,value:String){Card{Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.bodySmall);Text(value,fontWeight=FontWeight.Bold)}}}
@Composable private fun NovaSettings(){var theme by remember{mutableStateOf("NOVA Neon")};var anim by remember{mutableStateOf(true)};Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Desktop settings",style=MaterialTheme.typography.titleLarge);listOf("NOVA Neon","Windows 11 inspired","Windows XP inspired","Linux inspired","macOS inspired").forEach{OutlinedButton(onClick={theme=it},modifier=Modifier.fillMaxWidth()){Text(if(theme==it)"✓  $it" else it)}};Row(verticalAlignment=Alignment.CenterVertically){Text("Animations",modifier=Modifier.weight(1f));Switch(anim,{anim=it})};Text("Themes change NOVA's shell, not the underlying operating system.",style=MaterialTheme.typography.bodySmall)}}
@Composable private fun NovaNotes(){var note by remember{mutableStateOf("")};Column{Text("NOVA Notes",style=MaterialTheme.typography.titleLarge);OutlinedTextField(note,{note=it},modifier=Modifier.fillMaxSize(),placeholder={Text("Write something…")})}}
