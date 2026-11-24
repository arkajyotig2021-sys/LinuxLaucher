package app.nova.desktop
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
class MainActivity:ComponentActivity(){
 private var tree:Uri?=null
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){val flags=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION;contentResolver.takePersistableUriPermission(uri,flags);tree=uri;getSharedPreferences("nova",MODE_PRIVATE).edit().putString("tree",uri.toString()).apply();showApp()}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(8,12,28);window.navigationBarColor=android.graphics.Color.rgb(8,12,28);tree=getSharedPreferences("nova",MODE_PRIVATE).getString("tree",null)?.let{Uri.parse(it)};showApp()}
 private fun showApp(){setContent{NovaDesktopApp(tree,chooseFolder={picker.launch(tree)})}}
}