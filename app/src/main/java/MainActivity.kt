package com.example.fileaccessapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Launcher for Android 11+ "All Files Access"
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAllPermissionsAndStart()
    }

    // Launcher for standard permissions (Legacy Storage + Notifications)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkAllPermissionsAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAllPermissionsAndStart()
    }

    private fun checkAllPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Check Storage Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${applicationContext.packageName}")
                manageStorageLauncher.launch(intent)
                return // Pause here to let user grant Manage Storage
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 3. Request pending standard permissions or start the service
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions granted! Start the background service
            val serviceIntent = Intent(this, FileServerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            
            Toast.makeText(this, "Server running! You can close the app.", Toast.LENGTH_LONG).show()
            finish() // Automatically closes the UI window, leaving only the background service
        }
    }
}
