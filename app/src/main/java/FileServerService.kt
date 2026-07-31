package com.example.fileaccessapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class FileServerService : Service() {

    private var fileServer: FileServer? = null
    private var nsdManager: NsdManager? = null
    private val serviceName = "android-files" // This becomes android-files.local

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startServer()
        registerNetworkService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STICKY means if the system absolutely must kill it for memory, 
        // it will auto-restart it as soon as memory is freed up.
        return START_STICKY 
    }

    private fun startForegroundNotification() {
        val channelId = "server_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "File Server Status", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("File Server Running")
            .setContentText("Accessible at http://$serviceName.local:8080")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true) // Cannot be swiped away
            .build()

        startForeground(1, notification)
    }

    private fun startServer() {
        if (fileServer == null) {
            try {
                fileServer = FileServer(8080)
                fileServer?.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun registerNetworkService() {
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@FileServerService.serviceName
            serviceType = "_http._tcp."
            port = 8080
        }

        nsdManager?.registerService(
            serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener
        )
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
    }

    override fun onDestroy() {
        fileServer?.stop()
        try {
            nsdManager?.unregisterService(registrationListener)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // The NanoHTTPD Implementation (Same as before)
    private inner class FileServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val rootDir = Environment.getExternalStorageDirectory()
            val file = File(rootDir, uri)

            return when {
                file.isDirectory -> {
                    val files = file.listFiles() ?: arrayOf()
                    var html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body><h2>Index of $uri</h2><hr><ul>"
                    if (uri != "/") html += "<li><a href='..'>📁 [Parent Directory]</a></li>"
                    files.sortedBy { !it.isDirectory }.forEach { f ->
                        val icon = if (f.isDirectory) "📁" else "📄"
                        val path = if (uri.endsWith("/")) uri + f.name else "$uri/${f.name}"
                        html += "<li><a href='$path'>$icon ${f.name}</a></li>"
                    }
                    html += "</ul><hr></body></html>"
                    newFixedLengthResponse(Response.Status.OK, "text/html", html)
                }
                file.exists() && file.isFile -> {
                    try {
                        newChunkedResponse(Response.Status.OK, "application/octet-stream", FileInputStream(file))
                    } catch (e: Exception) {
                        newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "403 Forbidden")
                    }
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }
        }
    }
}
