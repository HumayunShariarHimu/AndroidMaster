package com.androidmaster.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.core.app.NotificationCompat

class ScreenCaptureService:Service(){
    private var projection:MediaProjection?=null
    private var virtualDisplay:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private val handler=Handler(Looper.getMainLooper())
    private var lastFrameAt=0L
    private lateinit var bridge:DeviceBridge

    override fun onCreate(){super.onCreate();bridge=DeviceBridgeHolder.get(applicationContext);createChannel()}

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_STOP){stopCapture();stopSelf();return START_NOT_STICKY}
        val resultCode=intent?.getIntExtra(EXTRA_RESULT,0)?:0
        val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra(EXTRA_DATA,Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA) as? Intent
        if(resultCode==0||data==null){stopSelf();return START_NOT_STICKY}
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(NOTIFICATION_ID,notification())
        stopCapture()
        try{
            val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection=manager.getMediaProjection(resultCode,data)
            val metrics=resources.displayMetrics
            val width=metrics.widthPixels.coerceAtMost(1080).coerceAtLeast(320)
            val height=(width.toFloat()*metrics.heightPixels/metrics.widthPixels).toInt().coerceAtLeast(320)
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            reader?.setOnImageAvailableListener({source->
                val now=System.currentTimeMillis()
                if(now-lastFrameAt<250L){source.acquireLatestImage()?.close();return@setOnImageAvailableListener}
                val image=source.acquireLatestImage()?:return@setOnImageAvailableListener
                try{
                    val plane=image.planes[0]
                    val buffer=plane.buffer
                    val pixelStride=plane.pixelStride
                    val rowStride=plane.rowStride
                    val rowPadding=rowStride-pixelStride*image.width
                    val full=Bitmap.createBitmap(image.width+rowPadding/pixelStride,image.height,Bitmap.Config.ARGB_8888)
                    full.copyPixelsFromBuffer(buffer)
                    val scaled=Bitmap.createScaledBitmap(full,540,(image.height.toFloat()*540/image.width).toInt().coerceAtLeast(1),true)
                    val bytes=java.io.ByteArrayOutputStream().use{out->scaled.compress(Bitmap.CompressFormat.JPEG,38,out);out.toByteArray()}
                    bridge.sendScreenFrame(Base64.encodeToString(bytes,Base64.NO_WRAP))
                    lastFrameAt=now
                    if(scaled!==full)scaled.recycle()
                    full.recycle()
                }catch(_:Exception){}finally{image.close()}
            },handler)
            projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopCapture();stopSelf()}},handler)
            virtualDisplay=projection?.createVirtualDisplay("AndroidMasterScreen",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)
        }catch(e:Exception){bridge.reportDeviceEvent("screen.error",false,e.message?:"Unable to start screen capture");stopCapture();stopSelf()}
        return START_NOT_STICKY
    }

    private fun stopCapture(){
        try{virtualDisplay?.release()}catch(_:Exception){}
        virtualDisplay=null
        try{reader?.setOnImageAvailableListener(null,null);reader?.close()}catch(_:Exception){}
        reader=null
        val activeProjection=projection
        projection=null
        try{activeProjection?.stop()}catch(_:Exception){}
    }
    override fun onDestroy(){stopCapture();if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true);super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID,"AndroidMaster screen sharing",NotificationManager.IMPORTANCE_LOW))}
    private fun notification():Notification=NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("AndroidMaster screen sharing").setContentText("Your screen is being shared with the paired dashboard").setOngoing(true).setOnlyAlertOnce(true).build()

    companion object{
        const val ACTION_STOP="com.androidmaster.app.STOP_SCREEN_CAPTURE"
        private const val EXTRA_RESULT="projection_result"
        private const val EXTRA_DATA="projection_data"
        private const val CHANNEL_ID="androidmaster_screen_capture"
        private const val NOTIFICATION_ID=7302
        fun start(context:Context,resultCode:Int,data:Intent){val i=Intent(context,ScreenCaptureService::class.java).putExtra(EXTRA_RESULT,resultCode).putExtra(EXTRA_DATA,data);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i) else context.startService(i)}
        fun stop(context:Context){context.stopService(Intent(context,ScreenCaptureService::class.java))}
    }
}
