package com.androidmaster.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.Size
import androidx.core.app.NotificationCompat

class CameraPreviewService:Service(){
    private lateinit var thread:HandlerThread
    private lateinit var worker:Handler
    private lateinit var bridge:DeviceBridge
    private var camera:CameraDevice?=null
    private var session:CameraCaptureSession?=null
    private var reader:ImageReader?=null
    private var lastFrameAt=0L

    override fun onCreate(){super.onCreate();bridge=DeviceBridgeHolder.get(applicationContext);createChannel();isRunning=true}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
        if(::thread.isInitialized)return START_STICKY
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) else startForeground(NOTIFICATION_ID,notification())
        thread=HandlerThread("AndroidMasterCamera").apply{start()};worker=Handler(thread.looper)
        try{openCamera()}catch(e:Exception){bridge.reportDeviceEvent("camera.error",false,e.message?:"Unable to open camera");stopSelf()}
        return START_NOT_STICKY
    }
    private fun openCamera(){
        val manager=getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)==android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK}
            ?:manager.cameraIdList.firstOrNull()?:throw IllegalStateException("No camera is available")
        val map=manager.getCameraCharacteristics(cameraId).get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size=map?.getOutputSizes(ImageFormat.JPEG)?.filter{it.width<=1280&&it.height<=960}?.maxByOrNull{it.width*it.height}?:Size(640,480)
        reader=ImageReader.newInstance(size.width,size.height,ImageFormat.JPEG,2)
        reader?.setOnImageAvailableListener({source->
            val now=System.currentTimeMillis()
            if(now-lastFrameAt<500L){source.acquireLatestImage()?.close();return@setOnImageAvailableListener}
            val image=source.acquireLatestImage()?:return@setOnImageAvailableListener
            try{val buffer=image.planes[0].buffer;val bytes=ByteArray(buffer.remaining());buffer.get(bytes);bridge.sendCameraFrame(Base64.encodeToString(bytes,Base64.NO_WRAP));lastFrameAt=now}finally{image.close()}
        },worker)
        manager.openCamera(cameraId,object:CameraDevice.StateCallback(){
            override fun onOpened(device:CameraDevice){camera=device;createSession(device)}
            override fun onDisconnected(device:CameraDevice){device.close();camera=null;bridge.reportDeviceEvent("camera.error",false,"Camera disconnected")}
            override fun onError(device:CameraDevice,error:Int){device.close();camera=null;bridge.reportDeviceEvent("camera.error",false,"Camera error $error");stopSelf()}
        },worker)
    }
    private fun createSession(device:CameraDevice){
        val surface=reader?.surface?:throw IllegalStateException("Camera image surface is unavailable")
        device.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
            override fun onConfigured(configured:CameraCaptureSession){session=configured;try{val request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{addTarget(surface);set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)}.build();configured.setRepeatingRequest(request,null,worker);bridge.reportDeviceEvent("camera.started",true,"Camera preview streaming")}catch(e:Exception){bridge.reportDeviceEvent("camera.error",false,e.message?:"Unable to stream camera");stopSelf()}}
            override fun onConfigureFailed(configured:CameraCaptureSession){bridge.reportDeviceEvent("camera.error",false,"Camera capture session configuration failed");stopSelf()}
        },worker)
    }
    private fun closeCamera(){try{session?.stopRepeating()}catch(_:Exception){};try{session?.close()}catch(_:Exception){};session=null;try{camera?.close()}catch(_:Exception){};camera=null;try{reader?.close()}catch(_:Exception){};reader=null;if(::thread.isInitialized)thread.quitSafely();isRunning=false}
    override fun onDestroy(){closeCamera();if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true);super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID,"AndroidMaster camera preview",NotificationManager.IMPORTANCE_LOW))}
    private fun notification():Notification=NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("AndroidMaster camera preview").setContentText("Camera is active and sending preview frames to your paired dashboard").setOngoing(true).setOnlyAlertOnce(true).build()
    companion object{
        const val ACTION_STOP="com.androidmaster.app.STOP_CAMERA_PREVIEW"
        private const val CHANNEL_ID="androidmaster_camera_preview"
        private const val NOTIFICATION_ID=7303
        @Volatile var isRunning=false
            private set
        fun start(context:Context){val i=Intent(context,CameraPreviewService::class.java);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i) else context.startService(i)}
        fun stop(context:Context){context.stopService(Intent(context,CameraPreviewService::class.java))}
    }
}
