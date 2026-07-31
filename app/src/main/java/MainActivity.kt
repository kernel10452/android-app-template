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

    // 1. Launcher for Android 11+ "All Files Access" settings page
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Full disc access granted!", Toast.LENGTH_SHORT).show()
                // Proceed with full file operations
            } else {
                Toast.makeText(this, "Full disc access denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Launcher for Android 10 and below standard permissions
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        
        if (readGranted && writeGranted) {
            Toast.makeText(this, "Legacy storage access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Legacy storage access denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestFullDiscAccess()
    }

    private fun requestFullDiscAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Devices running Android 11+ (API 30+)
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Disc access already granted!", Toast.LENGTH_SHORT).show()
            } else {
                // Redirect user to settings to grant "All Files Access"
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        } else {
            // Devices running Android 10 and below
            val hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val hasWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

            if (hasRead && hasWrite) {
                Toast.makeText(this, "Disc access already granted!", Toast.LENGTH_SHORT).show()
            } else {
                legacyPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
}
