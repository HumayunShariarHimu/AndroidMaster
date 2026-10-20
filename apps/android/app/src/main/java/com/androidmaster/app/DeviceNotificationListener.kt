package com.androidmaster.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class DeviceNotificationListener:NotificationListenerService(){
    override fun onListenerConnected(){instance=this;publishSnapshot()}
    override fun onListenerDisconnected(){if(instance===this)instance=null}
    override fun onNotificationPosted(sbn:StatusBarNotification?){sbn?.let{publishOne(it,"notification.posted")}}
    override fun onNotificationRemoved(sbn:StatusBarNotification?){sbn?.let{publishOne(it,"notification.removed")}}
    private fun publishOne(sbn:StatusBarNotification,event:String){
        val extras=sbn.notification.extras
        val data=JSONObject().put("packageName",sbn.packageName).put("key",sbn.key)
            .put("title",extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(300))
            .put("text",extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(1200))
            .put("postedAt",sbn.postTime)
        DeviceBridgeHolder.get(applicationContext).reportDeviceEvent(event,true,data.toString())
    }
    fun sendSnapshot(){publishSnapshot()}
    private fun publishSnapshot(){
        try{
            val array=JSONArray()
            activeNotifications.orEmpty().take(100).forEach{sbn->
                val extras=sbn.notification.extras
                array.put(JSONObject().put("packageName",sbn.packageName).put("key",sbn.key)
                    .put("title",extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(300))
                    .put("text",extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(1200))
                    .put("postedAt",sbn.postTime))
            }
            DeviceBridgeHolder.get(applicationContext).reportDeviceEvent("notification.snapshot",true,array.toString())
        }catch(e:Exception){DeviceBridgeHolder.get(applicationContext).reportDeviceEvent("notification.error",false,e.message?:"Unable to read notifications")}
    }
    companion object{@Volatile var instance:DeviceNotificationListener?=null;private set}
}
