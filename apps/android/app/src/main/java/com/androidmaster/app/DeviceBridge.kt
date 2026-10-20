package com.androidmaster.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class InstalledApp(val label: String, val packageName: String)
data class DeviceSnapshot(val model: String, val androidVersion: String, val batteryPercent: Int, val charging: Boolean)
data class BridgeState(val connected: Boolean=false,val connecting: Boolean=false,val error: String?=null,val apps: List<InstalledApp> = emptyList(),val device: DeviceSnapshot?=null,val lastMessage: String="Not connected")

class DeviceBridge(private val context: Context) {
    private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).pingInterval(25,TimeUnit.SECONDS).build()
    private var socket: WebSocket?=null
    private val prefs=context.getSharedPreferences("androidmaster_device",Context.MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private val connectivityManager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network){
            handler.post {
                if(reconnectWanted && lastUrl.isNotBlank() && lastRoom.isNotBlank() && !_state.value.connected && !_state.value.connecting){
                    handler.removeCallbacks(reconnectTask)
                    connect(lastUrl,lastRoom)
                }
            }
        }
    }
    @Volatile private var reconnectWanted=false
    private var reconnectDelayMs=3000L
    private var lastUrl=""
    private var lastRoom=""
    private val reconnectTask=Runnable { if(reconnectWanted&&lastUrl.isNotBlank()&&lastRoom.isNotBlank()) connect(lastUrl,lastRoom) }
    private val _state=MutableStateFlow(BridgeState())
    val state=_state.asStateFlow()

    init { try { connectivityManager.registerDefaultNetworkCallback(networkCallback) } catch (_:Exception) { } }

    fun hasSavedConnection():Boolean=prefs.getString("relay_url",null)!=null&&prefs.getString("device_code",null)!=null

    fun autoConnectSaved(){
        val savedUrl=prefs.getString("relay_url","").orEmpty()
        val savedRoom=prefs.getString("device_code","").orEmpty()
        if(savedUrl.isNotBlank()&&savedRoom.isNotBlank())connect(savedUrl,savedRoom)
    }

    fun connect(relayBaseUrl: String, room: String) {
        val base=relayBaseUrl.trim().trimEnd('/')
        val secret=room.trim()
        if(!base.startsWith("ws://")&&!base.startsWith("wss://")){_state.value=_state.value.copy(error="Relay URL must start with ws:// or wss://");return}
        if(!Regex("^[a-fA-F0-9]{48}$").matches(secret)){_state.value=_state.value.copy(error="Pairing secret must be the 48-character value shown by the dashboard");return}
        if(reconnectWanted && lastUrl==base && lastRoom==secret && (_state.value.connected || _state.value.connecting)) return
        handler.removeCallbacks(reconnectTask)
        reconnectWanted=true
        lastUrl=base;lastRoom=secret;reconnectDelayMs=3000L
        prefs.edit().putString("relay_url",base).putString("device_code",secret).apply()
        socket?.cancel();socket=null
        val request=Request.Builder().url("$base/ws?role=device&room=$secret").build()
        _state.value=_state.value.copy(connecting=true,error=null,lastMessage="Connecting to relay…")
        socket=client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){
                if(socket!==webSocket)return
                reconnectDelayMs=3000L
                _state.value=_state.value.copy(connected=true,connecting=false,error=null,lastMessage="Connected to relay")
            }
            override fun onMessage(webSocket:WebSocket,text:String){
                if(socket!==webSocket)return
                try {
                    val msg=JSONObject(text);val type=msg.optString("type");val payload=msg.optJSONObject("payload")?:JSONObject()
                    when(type){
                        "controller.connected","relay.ready"->{_state.value=_state.value.copy(lastMessage="Paired. Waiting for dashboard commands");send("device.status.result",statusPayload());sendApps()}
                        "device.status.get"->send("device.status.result",statusPayload())
                        "apps.list"->sendApps()
                        "app.launch"->launchApp(payload.optString("packageName"))
                        "remote.gesture"->performRemoteSwipe(payload)
                        "remote.global_action"->performGlobalAction(payload.optString("action"))
                    }
                }catch(e:Exception){_state.value=_state.value.copy(error="Invalid relay message: ${e.message}")}
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){
                if(socket!==webSocket)return
                socket=null
                _state.value=_state.value.copy(connected=false,connecting=false,error=t.message?:"Relay connection failed",lastMessage="Connection failed")
                scheduleReconnect()
            }
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){
                if(socket!==webSocket)return
                socket=null
                _state.value=_state.value.copy(connected=false,connecting=false,lastMessage="Disconnected: $reason")
                scheduleReconnect()
            }
        })
    }
    private fun scheduleReconnect(){if(!reconnectWanted)return;handler.removeCallbacks(reconnectTask);handler.postDelayed(reconnectTask,reconnectDelayMs);reconnectDelayMs=(reconnectDelayMs*2).coerceAtMost(30000L)}
    fun disconnect(clearSaved:Boolean=true){reconnectWanted=false;handler.removeCallbacks(reconnectTask);socket?.close(1000,"user_disconnected");socket=null;if(clearSaved){prefs.edit().remove("relay_url").remove("device_code").apply();lastUrl="";lastRoom=""};_state.value=_state.value.copy(connected=false,connecting=false,lastMessage="Disconnected")}
    private fun send(type:String,payload:JSONObject){socket?.send(JSONObject().put("type",type).put("payload",payload).toString())}
    private fun statusPayload():JSONObject{
        val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100
        val percent=if(level>=0&&scale>0)level*100/scale else -1
        val batteryStatus=battery?.getIntExtra(BatteryManager.EXTRA_STATUS,-1)?:-1
        val charging=batteryStatus==BatteryManager.BATTERY_STATUS_CHARGING||batteryStatus==BatteryManager.BATTERY_STATUS_FULL
        val model="${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val version=Build.VERSION.RELEASE?:Build.VERSION.SDK_INT.toString()
        _state.value=_state.value.copy(device=DeviceSnapshot(model,version,percent,charging))
        return JSONObject().put("model",model).put("androidVersion",version).put("batteryPercent",percent).put("charging",charging).put("uptimeMs",SystemClock.elapsedRealtime())
    }
    @Suppress("DEPRECATION")
    private fun sendApps(){
        val pm=context.packageManager
        val installed=if(Build.VERSION.SDK_INT>=33)pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0)) else pm.getInstalledApplications(0)
        val apps=installed.mapNotNull{info->
            val launch=pm.getLaunchIntentForPackage(info.packageName)?:return@mapNotNull null
            val label=pm.getApplicationLabel(info).toString()
            if(label.isBlank()||launch.component==null)null else InstalledApp(label,info.packageName)
        }.distinctBy{it.packageName}.sortedBy{it.label.lowercase()}
        val array=JSONArray()
        apps.forEach{array.put(JSONObject().put("label",it.label).put("packageName",it.packageName))}
        _state.value=_state.value.copy(apps=apps,lastMessage="Sent ${apps.size} launchable apps")
        send("apps.list.result",JSONObject().put("apps",array))
    }
    private fun performRemoteSwipe(payload:JSONObject){
        val service=RemoteAccessibilityService.instance
        if(service==null){
            send("device.event",JSONObject().put("event","gesture.result").put("ok",false).put("message","Enable AndroidMaster Accessibility service in Android Settings first"))
            return
        }
        service.swipe(payload.optDouble("x1",0.5).toFloat(),payload.optDouble("y1",0.8).toFloat(),payload.optDouble("x2",0.5).toFloat(),payload.optDouble("y2",0.2).toFloat(),payload.optLong("durationMs",350L)){ok->
            send("device.event",JSONObject().put("event","gesture.result").put("ok",ok).put("message",if(ok)"Swipe dispatched" else "Android cancelled the gesture"))
        }
    }
    private fun performGlobalAction(action:String){
        val service=RemoteAccessibilityService.instance
        val ok=service?.globalAction(action)?:false
        send("device.event",JSONObject().put("event","navigation.result").put("ok",ok).put("action",action).put("message",if(ok)"Navigation action dispatched" else "Enable AndroidMaster Accessibility service in Android Settings first"))
    }
    private fun launchApp(packageName:String){
        if(packageName.isBlank()||packageName.length>255){send("app.launch.result",JSONObject().put("ok",false).put("message","Invalid package name"));return}
        try{
            val service=RemoteAccessibilityService.instance?:throw IllegalStateException("Enable AndroidMaster Accessibility service in Android Settings to launch apps remotely")
            val failure=service.launchPackage(packageName)
            if(failure!=null)throw IllegalStateException(failure)
            send("app.launch.result",JSONObject().put("ok",true).put("message","Launch requested: $packageName"))
            _state.value=_state.value.copy(lastMessage="Launched $packageName")
        }catch(e:Exception){send("app.launch.result",JSONObject().put("ok",false).put("message",e.message?:"Launch failed"));_state.value=_state.value.copy(error=e.message?:"Launch failed")}
    }
}


object DeviceBridgeHolder {
    @Volatile private var instance: DeviceBridge? = null
    fun get(context: Context): DeviceBridge = instance ?: synchronized(this) {
        instance ?: DeviceBridge(context.applicationContext).also { instance = it }
    }
}
