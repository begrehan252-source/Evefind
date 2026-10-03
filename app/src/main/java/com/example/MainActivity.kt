package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.EveFindWebViewContainer
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraAction: (() -> Unit)? = null
    private var pendingLocationAction: (() -> Unit)? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register Activity Result Launchers
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val results: Array<Uri>? = when {
                    data?.data != null -> arrayOf(data.data!!)
                    data?.clipData != null -> {
                        val clip = data.clipData!!
                        Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    }
                    else -> null
                }
                filePathCallback?.onReceiveValue(results)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ ->
            pendingCameraAction?.invoke()
            pendingCameraAction = null
        }

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            pendingLocationAction?.invoke()
            pendingLocationAction = null
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    EveFindWebViewContainer(
                        modifier = Modifier.padding(innerPadding),
                        onFileChooserRequested = { intent, callback ->
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = callback
                            try {
                                fileChooserLauncher.launch(intent)
                            } catch (e: Exception) {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            }
                        },
                        onRequestCameraPermission = { action ->
                            if (hasCameraPermission()) {
                                action()
                            } else {
                                pendingCameraAction = action
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onRequestLocationPermission = { action ->
                            if (hasLocationPermission()) {
                                action()
                            } else {
                                pendingLocationAction = action
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        hasCameraPermission = { hasCameraPermission() },
                        hasLocationPermission = { hasLocationPermission() }
                    )
                }
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
