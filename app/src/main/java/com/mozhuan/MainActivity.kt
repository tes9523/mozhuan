package com.mozhuan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mozhuan.storage.preferences.AppPreferences
import com.mozhuan.system.notify.AppForeground
import com.mozhuan.system.service.WritingSessionService
import com.mozhuan.ui.MozhuanRoot

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MozhuanApplication).container
        requestNotificationPermissionIfNeeded(container.preferences)
        setContent {
            MozhuanRoot(container)
        }
    }

    override fun onStart() {
        super.onStart()
        AppForeground.onStart()
        val container = (application as MozhuanApplication).container
        if (container.preferences.notificationSettings.value.keepAlive) {
            WritingSessionService.startKeepAlive(this)
        }
    }

    override fun onStop() {
        AppForeground.onStop()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded(preferences: AppPreferences) {
        if (preferences.notificationPermissionAsked) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        preferences.notificationPermissionAsked = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
