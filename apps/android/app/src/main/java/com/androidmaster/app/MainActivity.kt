package com.androidmaster.app

import android.os.Bundle
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
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        bridge=DeviceBridge(applicationContext)
        setContent {
            MaterialTheme {
                val state by bridge.state.collectAsState()
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    CompanionScreen(state,{url,room->bridge.connect(url,room)},{bridge.disconnect()})
                }
            }
        }
    }
    override fun onDestroy(){bridge.disconnect();super.onDestroy()}
}

@Composable
private fun CompanionScreen(state:BridgeState,onConnect:(String,String)->Unit,onDisconnect:()->Unit){
    var relayUrl by remember{mutableStateOf("wss://YOUR-RELAY-DOMAIN")}
    var room by remember{mutableStateOf("")}
    var showApps by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("AndroidMaster",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Personal Android companion · pair only with your own dashboard",style=MaterialTheme.typography.bodyMedium)
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
        OutlinedTextField(room,{room=it.trim()},modifier=Modifier.fillMaxWidth(),label={Text("Pairing secret from web dashboard")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={onConnect(relayUrl,room)},enabled=!state.connected&&!state.connecting){Text("Connect")}
            TextButton(onClick=onDisconnect,enabled=state.connected||state.connecting){Text("Disconnect")}
            TextButton(onClick={showApps=!showApps}) { Text(if(showApps)"Hide apps" else "Apps on device") }
        }
        state.error?.let{Text("Error: $it",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Divider()
        Text("Permissions & features",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text("This build uses internet access and installed-app visibility to list and launch apps. Screen capture, notifications, files and other sensitive permissions will be added with their own visible consent flows.",style=MaterialTheme.typography.bodySmall)
        if(showApps){
            Text("Launchable apps visible to AndroidMaster: ${state.apps.size}",fontWeight=FontWeight.Medium)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
                items(state.apps,key={it.packageName}){app->
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
            Text("Keep this app open while paired. The dashboard can request the real app list and launch apps on this phone.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
