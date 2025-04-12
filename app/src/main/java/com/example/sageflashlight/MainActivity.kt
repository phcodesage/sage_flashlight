package com.example.sageflashlight

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.sageflashlight.ui.theme.SageFlashlightTheme

class MainActivity : ComponentActivity() {

    // Use MutableState for the flashlight states so Compose will recompose when they change
    private val _isFlashlightOn = mutableStateOf(false)
    private val isFlashlightOn: Boolean
        get() = _isFlashlightOn.value
    
    private val _isScreenFlashlightOn = mutableStateOf(false)
    private val isScreenFlashlightOn: Boolean
        get() = _isScreenFlashlightOn.value
    
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted, flashlight can be used
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize CameraManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0] // Get the first camera (usually rear)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        // Check if device has a flash
        if (!hasFlash()) {
            Toast.makeText(this, "No flashlight available on this device", Toast.LENGTH_LONG).show()
        }

        // Check for camera permission
        when {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> {
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        setContent {
            SageFlashlightTheme {
                var selectedTab by remember { mutableStateOf(0) }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Tab Row
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(id = R.string.flashlight_tab)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(id = R.string.screen_tab)) }
                            )
                        }
                        
                        // Content based on selected tab
                        when (selectedTab) {
                            0 -> FlashlightScreen(
                                isFlashlightOn = isFlashlightOn,
                                hasFlash = hasFlash(),
                                onToggleFlashlight = { toggleFlashlight() }
                            )
                            1 -> ScreenBrightnessFlashlight(
                                isScreenFlashlightOn = isScreenFlashlightOn,
                                onToggleScreenFlashlight = { toggleScreenBrightness() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasFlash(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private fun toggleFlashlight() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            _isFlashlightOn.value = !_isFlashlightOn.value
            try {
                cameraManager.setTorchMode(cameraId!!, isFlashlightOn)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                Toast.makeText(this, "Error accessing flashlight", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    
    private fun toggleScreenBrightness() {
        _isScreenFlashlightOn.value = !_isScreenFlashlightOn.value
        val layoutParams = window.attributes
        if (isScreenFlashlightOn) {
            // Set screen to maximum brightness
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = layoutParams
        } else {
            // Reset to system brightness
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Turn off flashlight when app is destroyed
        if (isFlashlightOn) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
        
        // Reset screen brightness
        if (isScreenFlashlightOn) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
    }
}

@Composable
fun FlashlightScreen(
    isFlashlightOn: Boolean,
    hasFlash: Boolean,
    onToggleFlashlight: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Title
            Text(
                text = "SageFlashlight",
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // Toggle switch image
            Image(
                painter = painterResource(
                    id = if (isFlashlightOn) R.drawable.toggle_on else R.drawable.toggle_off
                ),
                contentDescription = stringResource(
                    id = if (isFlashlightOn) R.string.toggle_flashlight_off else R.string.toggle_flashlight
                ),
                modifier = Modifier
                    .size(width = 120.dp, height = 60.dp)
                    .padding(bottom = 32.dp)
                    .clickable(enabled = hasFlash) { onToggleFlashlight() }
            )
            
            // Status text
            Text(
                text = if (isFlashlightOn) "Flashlight is ON" else "Flashlight is OFF",
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (!hasFlash) {
                Text(
                    text = "No flashlight available on this device",
                    fontSize = 14.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ScreenBrightnessFlashlight(
    isScreenFlashlightOn: Boolean,
    onToggleScreenFlashlight: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isScreenFlashlightOn) Color.White else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // Always visible controls for toggling, even when screen is white
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Title (only shown when screen is not white)
            if (!isScreenFlashlightOn) {
                Text(
                    text = "Screen Light",
                    fontSize = 24.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 48.dp)
                )
            }
            
            // Toggle switch image
            Image(
                painter = painterResource(
                    id = if (isScreenFlashlightOn) R.drawable.toggle_on else R.drawable.toggle_off
                ),
                contentDescription = stringResource(
                    id = if (isScreenFlashlightOn) R.string.screen_brightness_off else R.string.screen_brightness_on
                ),
                modifier = Modifier
                    .size(width = 120.dp, height = 60.dp)
                    .padding(bottom = 32.dp)
                    .clickable { onToggleScreenFlashlight() }
            )
            
            // Status text
            Text(
                text = if (isScreenFlashlightOn) "Screen Light is ON - Tap to turn OFF" else "Screen Light is OFF",
                fontSize = 18.sp,
                color = if (isScreenFlashlightOn) Color.Black else Color.Unspecified,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Make the entire screen clickable when white to turn off easily
            if (isScreenFlashlightOn) {
                Spacer(modifier = Modifier
                    .fillMaxSize()
                    .clickable { onToggleScreenFlashlight() }
                )
            }
        }
    }
}