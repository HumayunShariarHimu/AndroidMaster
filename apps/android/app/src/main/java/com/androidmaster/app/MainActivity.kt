package com.androidmaster.app

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity:ComponentActivity(){
    private lateinit var bridge:DeviceBridge
    private val notificationPermissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    private val contactsPermissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    private val cameraPermissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)CameraPreviewService.start(this) else bridge.reportDeviceEvent("camera.permission_required",false,"Camera permission was denied")}
    private val screenCaptureLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->val data=result.data;if(result.resultCode==Activity.RESULT_OK&&data!=null)ScreenCaptureService.start(this,result.resultCode,data) else bridge.clearScreenCaptureRequest()}
    private val fileTreePickerLauncher=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);bridge.saveFileTree(uri)}catch(e:Exception){bridge.reportDeviceEvent("files.permission",false,e.message?:"Unable to save folder permission")}}}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        bridge=DeviceBridgeHolder.get(applicationContext)
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if(bridge.hasSavedConnection())DeviceConnectionService.startSaved(this)
        setContent {
            MaterialTheme {
                val state by bridge.state.collectAsState()
                LaunchedEffect(state.screenCaptureRequested) {
                    if(state.screenCaptureRequested) {
                        bridge.clearScreenCaptureRequest()
                        if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                        } else bridge.reportDeviceEvent("screen.permission_required",false,"Open AndroidMaster on the phone and tap Start screen sharing to approve Android capture permission")
                    }
                }
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    CompanionScreen(state,{url,room->DeviceConnectionService.start(this,url,room)},{DeviceConnectionService.stop(this)},{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},{startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},{fileTreePickerLauncher.launch(null)},{if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)CameraPreviewService.start(this) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)},{CameraPreviewService.stop(this)},{val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;screenCaptureLauncher.launch(manager.createScreenCaptureIntent())},{ScreenCaptureService.stop(this)},{startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))},{contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)})
                }
            }
        }
    }
}

@Composable
private fun CompanionScreen(state:BridgeState,onConnect:(String,String)->Unit,onDisconnect:()->Unit,onEnableAccessibility:()->Unit,onEnableNotificationAccess:()->Unit,onChooseFileFolder:()->Unit,onStartCameraPreview:()->Unit,onStopCameraPreview:()->Unit,onStartScreenCapture:()->Unit,onStopScreenCapture:()->Unit,onOpenAppDetails:()->Unit,onRequestContactsPermission:()->Unit){
    var permissionRefresh by remember{mutableStateOf(0)}
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)permissionRefresh++}
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}
    }
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
        val accessibilityContext=androidx.compose.ui.platform.LocalContext.current
        val accessibilityComponent=ComponentName(accessibilityContext,RemoteAccessibilityService::class.java).flattenToString()
        val enabledServices=Settings.Secure.getString(accessibilityContext.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val accessibilityEnabled=enabledServices.split(":").any{it.equals(accessibilityComponent,true)}
        val notificationListeners=Settings.Secure.getString(accessibilityContext.contentResolver,"enabled_notification_listeners").orEmpty()
        val notificationAccessEnabled=notificationListeners.split(":").any{it.contains(accessibilityContext.packageName,true)&&it.contains(DeviceNotificationListener::class.java.simpleName,true)}
        val cameraGranted=androidx.core.content.ContextCompat.checkSelfPermission(accessibilityContext,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        val contactsGranted=androidx.core.content.ContextCompat.checkSelfPermission(accessibilityContext,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
        Text("Permission status",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
        Text("Accessibility: ${if(accessibilityEnabled)"Enabled" else "Not enabled"} · Notification access: ${if(notificationAccessEnabled)"Enabled" else "Not enabled"} · Camera: ${if(cameraGranted)"Allowed" else "Not allowed"} · Contacts: ${if(contactsGranted)"Allowed" else "Not allowed"} · Shared folder: ${if(state.fileAccessEnabled)"Selected" else "Not selected"}",style=MaterialTheme.typography.bodySmall)
        if(permissionRefresh>0)Text("Permission status refreshed after returning from Android Settings.",style=MaterialTheme.typography.bodySmall)
        Button(onClick=onEnableAccessibility,modifier=Modifier.fillMaxWidth()){Text(if(accessibilityEnabled)"Review Accessibility settings" else "Enable remote swipe & navigation")}
        Button(onClick=onEnableNotificationAccess,modifier=Modifier.fillMaxWidth()){Text(if(notificationAccessEnabled)"Review notification access" else "Enable notification reading in Android Settings")}
        Button(onClick=onOpenAppDetails,modifier=Modifier.fillMaxWidth()){Text("Open AndroidMaster app settings")}
        Button(onClick={if(contactsGranted)onOpenAppDetails() else onRequestContactsPermission()},modifier=Modifier.fillMaxWidth()){Text(if(contactsGranted)"Review contacts permission" else "Allow contact access")}
        Text("Remote contacts are only read after you grant Contacts permission here; the dashboard can request a snapshot while this permission remains enabled.",style=MaterialTheme.typography.bodySmall)
        Text("If Android shows a Restricted setting warning (common on Android 13+ for sideloaded APKs), open this app’s App info, tap the three-dot menu, choose Allow restricted settings, then return here and enable Accessibility. AndroidMaster cannot grant this special access itself.",style=MaterialTheme.typography.bodySmall)
        Button(onClick=onChooseFileFolder,modifier=Modifier.fillMaxWidth()){Text(if(state.fileAccessEnabled)"Change shared folder" else "Choose shared folder for file manager")}
        Button(onClick=onStartScreenCapture,modifier=Modifier.fillMaxWidth()){Text("Start screen sharing on this phone")}
        TextButton(onClick=onStopScreenCapture,modifier=Modifier.fillMaxWidth()){Text("Stop screen sharing")}
        Button(onClick=onStartCameraPreview,modifier=Modifier.fillMaxWidth()){Text("Start camera preview on this phone")}
        TextButton(onClick=onStopCameraPreview,modifier=Modifier.fillMaxWidth()){Text("Stop camera preview")}
        Text("Android requires your explicit approval for Accessibility, screen capture, notification access, camera permission and a shared folder. Screen sharing and camera preview display ongoing foreground notifications; file access is limited to the folder you select.",style=MaterialTheme.typography.bodySmall)
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
