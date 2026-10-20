package com.androidmaster.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity:ComponentActivity(){
    private lateinit var bridge:DeviceBridge
    private val notificationPermissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        bridge=DeviceBridgeHolder.get(applicationContext)
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if(bridge.hasSavedConnection())DeviceConnectionService.startSaved(this)
        setContent {
            MaterialTheme {
                val state by bridge.state.collectAsState()
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    CompanionScreen(state,{url,room->DeviceConnectionService.start(this,url,room)},{DeviceConnectionService.stop(this)})
                }
            }
        }
    }
}

@Composable
private fun CompanionScreen(state:BridgeState,onConnect:(String,String)->Unit,onDisconnect:()->Unit){
    var relayUrl by remember{mutableStateOf("wss://androidmaster-relay.onrender.com")}
    var room by remember{mutableStateOf("")}
    var showApps by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("AndroidMaster",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Personal Android companion · your private device workspace",style=MaterialTheme.typography.bodyMedium)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(if(state.connected)"CONNECTED" else if(state.connecting)"CONNECTING…" else "NOT CONNECTED",fontWeight=FontWeight.Bold)
                Text(state.lastMessage,style=MaterialTheme.typography.bodySmall)
                state.device?.let{
                    Text(it.model,style=MaterialTheme.typography.titleMedium)
                    Text("Android ${it.androidVersion} · Battery ${if(it.batteryPercent>=0)"${it.batteryPercent}%" else "Unknown"} ${if(it.charging)"· Charging" else ""}")
                }
            }
        }
        OutlinedTextField(relayUrl,{relayUrl=it},modifier=Modifier.fillMaxWidth(),label={Text("Relay URL")},supportingText={Text("Use wss:// for deployment; ws:// only for local testing.")},singleLine=true)
        OutlinedTextField(room,{room=it.trim()},modifier=Modifier.fillMaxWidth(),label={Text("Device access code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={onConnect(relayUrl,room)},enabled=!state.connected&&!state.connecting){Text("Connect")}
            TextButton(onClick=onDisconnect,enabled=state.connected||state.connecting){Text("Disconnect")}
            TextButton(onClick={showApps=!showApps}) { Text(if(showApps)"Hide apps" else "Apps on device") }
        }
        state.error?.let{Text("Error: $it",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Divider()
        Text("Permissions & features",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text("AndroidMaster requests notification permission for its persistent connection notification. Screen capture, remote touch, files, camera and notification-reading controls are not implemented in this build yet.",style=MaterialTheme.typography.bodySmall)
        if(showApps){
            Text("Launchable apps visible to AndroidMaster: ${state.apps.size}",fontWeight=FontWeight.Medium)
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                state.apps.forEach{app->
                    Card(shape=RoundedCornerShape(10.dp)){
                        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(app.label,fontWeight=FontWeight.Medium)
                                Text(app.packageName,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }else{
            Spacer(Modifier.height(4.dp))
            Text("The foreground service can maintain the relay connection while this screen is closed. Android may still stop it if you force-stop the app, reboot the phone, or restrict background activity.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
