package com.example.sageflashlight

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.sageflashlight.ui.theme.SageFlashlightTheme
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    companion object {
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "flashlight_channel"
        private const val REQUEST_NOTIFICATION_PERMISSION = 123
    }

    // Use MutableState for the flashlight states so Compose will recompose when they change
    private val _isFlashlightOn = mutableStateOf(false)
    private val isFlashlightOn: Boolean
        get() = _isFlashlightOn.value
    
    private val _isScreenFlashlightOn = mutableStateOf(false)
    private val isScreenFlashlightOn: Boolean
        get() = _isScreenFlashlightOn.value
    
    // Flashlight intensity (0.0f to 1.0f)
    private val _flashlightIntensity = mutableStateOf(1.0f)
    private val flashlightIntensity: Float
        get() = _flashlightIntensity.value
    
    // Whether device supports adjustable torch intensity
    private val _supportsIntensity = mutableStateOf(false)
    private val supportsIntensity: Boolean
        get() = _supportsIntensity.value
    
    // App settings
    private val _settings = mutableStateOf(Settings())
    private val settings: Settings
        get() = _settings.value
    
    // Sound effect for flashlight toggle
    private var toggleSound: MediaPlayer? = null
    
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private lateinit var notificationManager: NotificationManagerCompat
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted, flashlight can be used
            checkTorchIntensitySupport()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted, notifications can be shown
            if (isFlashlightOn) {
                showFlashlightNotification()
            }
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification click
        if (intent.action == "NOTIFICATION_CLICKED") {
            // Just bring the activity to foreground, don't change flashlight state
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load settings
        _settings.value = Settings.load(this)
        
        // Apply status bar setting
        applyStatusBarSetting()
        
        // Initialize sound effect
        toggleSound = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)

        // Initialize notification manager
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        
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
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                checkTorchIntensitySupport()
                
                // Turn on flashlight at startup if enabled in settings
                if (settings.turnOnStartup && hasFlash()) {
                    _isFlashlightOn.value = true
                    updateFlashlight()
                }
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        setContent {
            SageFlashlightTheme {
                var selectedTab by remember { mutableStateOf(0) }
                var showMenu by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var showAboutDialog by remember { mutableStateOf(false) }
                var showFollowUsDialog by remember { mutableStateOf(false) }
                
                // Current settings state in the UI
                var currentSettings by remember { mutableStateOf(settings) }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.app_name)) },
                                actions = {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.menu)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.remove_ads)) },
                                            onClick = {
                                                showMenu = false
                                                removeAds()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.more_apps)) },
                                            onClick = {
                                                showMenu = false
                                                moreApps()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.send_feedback)) },
                                            onClick = {
                                                showMenu = false
                                                sendFeedback()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.follow_us)) },
                                            onClick = {
                                                showMenu = false
                                                showFollowUsDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.invite_friends)) },
                                            onClick = {
                                                showMenu = false
                                                shareApp()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.settings)) },
                                            onClick = {
                                                showMenu = false
                                                currentSettings = settings
                                                showSettingsDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.about)) },
                                            onClick = {
                                                showMenu = false
                                                showAboutDialog = true
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
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
                                    onToggleFlashlight = { toggleFlashlight() },
                                    flashlightIntensity = flashlightIntensity,
                                    supportsIntensity = supportsIntensity,
                                    onIntensityChange = { setFlashlightIntensity(it) }
                                )
                                1 -> ScreenBrightnessFlashlight(
                                    isScreenFlashlightOn = isScreenFlashlightOn,
                                    onToggleScreenFlashlight = { toggleScreenBrightness() }
                                )
                            }
                        }
                    }
                    
                    // Settings Dialog
                    if (showSettingsDialog) {
                        Dialog(onDismissRequest = { showSettingsDialog = false }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_title),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.show_status_bar),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = currentSettings.showStatusBar,
                                            onCheckedChange = { 
                                                currentSettings = currentSettings.copy(showStatusBar = it)
                                            }
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sound_effects),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = currentSettings.soundEffects,
                                            onCheckedChange = { 
                                                currentSettings = currentSettings.copy(soundEffects = it)
                                            }
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.turn_on_startup),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = currentSettings.turnOnStartup,
                                            onCheckedChange = { 
                                                currentSettings = currentSettings.copy(turnOnStartup = it)
                                            }
                                        )
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        TextButton(
                                            onClick = { showSettingsDialog = false }
                                        ) {
                                            Text(stringResource(R.string.close))
                                        }
                                        
                                        Button(
                                            onClick = {
                                                updateSettings(currentSettings)
                                                showSettingsDialog = false
                                            },
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(stringResource(R.string.save))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // About Dialog
                    if (showAboutDialog) {
                        Dialog(onDismissRequest = { showAboutDialog = false }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.about_title),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
                                    Text(
                                        text = stringResource(R.string.about_text),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
                                    Button(
                                        onClick = { showAboutDialog = false }
                                    ) {
                                        Text(stringResource(R.string.close))
                                    }
                                }
                            }
                        }
                    }
                    
                    // Follow Us Dialog
                    if (showFollowUsDialog) {
                        Dialog(onDismissRequest = { showFollowUsDialog = false }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.follow_us_title),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
                                    Text(
                                        text = stringResource(R.string.follow_us_text),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    
                                    Button(
                                        onClick = { 
                                            showFollowUsDialog = false
                                            followUs()
                                        }
                                    ) {
                                        Text(stringResource(R.string.follow_us))
                                    }
                                    
                                    TextButton(
                                        onClick = { showFollowUsDialog = false }
                                    ) {
                                        Text(stringResource(R.string.close))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasFlash(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private fun checkTorchIntensitySupport() {
        if (cameraId != null) {
            try {
                // Check if device supports adjustable torch intensity (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                    val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                    _supportsIntensity.value = maxLevel != null && maxLevel > 1
                } else {
                    _supportsIntensity.value = false
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                _supportsIntensity.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _supportsIntensity.value = false
            }
        }
    }
    
    private fun toggleFlashlight() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            _isFlashlightOn.value = !_isFlashlightOn.value
            updateFlashlight()
            
            // Play sound effect if enabled
            if (settings.soundEffects) {
                playToggleSound()
            }
            
            // Show or hide notification based on flashlight state
            if (isFlashlightOn) {
                showFlashlightNotification()
            } else {
                hideFlashlightNotification()
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun updateFlashlight() {
        // Check if we're on API 23+ which supports setTorchMode
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Flashlight requires Android 6.0 or higher", Toast.LENGTH_SHORT).show()
            _isFlashlightOn.value = false
            return
        }
        
        try {
            if (isFlashlightOn) {
                if (supportsIntensity) {
                    // Try to set torch with intensity (Android 13+)
                    try {
                        val intensityLevel = (flashlightIntensity * 10).toInt().coerceAtLeast(1)
                        val method = cameraManager.javaClass.getMethod(
                            "turnOnTorchWithStrengthLevel",
                            String::class.java,
                            Int::class.java
                        )
                        method.invoke(cameraManager, cameraId, intensityLevel)
                    } catch (e: Exception) {
                        // Fall back to regular torch mode if reflection fails
                        cameraManager.setTorchMode(cameraId!!, true)
                    }
                } else {
                    // Regular torch mode
                    cameraManager.setTorchMode(cameraId!!, true)
                }
            } else {
                cameraManager.setTorchMode(cameraId!!, false)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Error accessing flashlight", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setFlashlightIntensity(intensity: Float) {
        _flashlightIntensity.value = intensity
        if (isFlashlightOn) {
            updateFlashlight()
        }
    }
    
    private fun toggleScreenBrightness() {
        _isScreenFlashlightOn.value = !_isScreenFlashlightOn.value
        val layoutParams = window.attributes
        if (isScreenFlashlightOn) {
            // Set screen to maximum brightness
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = layoutParams
            
            // Play sound effect if enabled
            if (settings.soundEffects) {
                playToggleSound()
            }
        } else {
            // Reset to system brightness
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
    }

    private fun applyStatusBarSetting() {
        if (settings.showStatusBar) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }
    
    private fun playToggleSound() {
        try {
            toggleSound?.seekTo(0)
            toggleSound?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateSettings(newSettings: Settings) {
        _settings.value = newSettings
        Settings.save(this, newSettings)
        applyStatusBarSetting()
    }
    
    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Check out SageFlashlight App")
            putExtra(Intent.EXTRA_TEXT, "I'm using SageFlashlight - a powerful flashlight app with adjustable brightness. Check it out!")
        }
        startActivity(Intent.createChooser(intent, "Invite Friends"))
    }
    
    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:feedback@sagecode.com")
            putExtra(Intent.EXTRA_SUBJECT, "SageFlashlight Feedback")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun followUs() {
        // This would typically open a social media page
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/sagecode"))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun moreApps() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=SageCode"))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun removeAds() {
        // This would typically launch an in-app purchase
        Toast.makeText(this, "Premium version activated!", Toast.LENGTH_SHORT).show()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showFlashlightNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            // Use these flags to bring existing activity to front without recreating it
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add an action to identify this intent in onNewIntent
            action = "NOTIFICATION_CLICKED"
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.flashlight_on)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
    
    private fun hideFlashlightNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Turn off flashlight when app is destroyed
        if (isFlashlightOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                cameraManager.setTorchMode(cameraId!!, false)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
        
        // Hide notification
        hideFlashlightNotification()
        
        // Reset screen brightness
        if (isScreenFlashlightOn) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
        
        // Release media player resources
        toggleSound?.release()
        toggleSound = null
    }
}

@Composable
fun FlashlightScreen(
    isFlashlightOn: Boolean,
    hasFlash: Boolean,
    onToggleFlashlight: () -> Unit,
    flashlightIntensity: Float = 1.0f,
    supportsIntensity: Boolean = false,
    onIntensityChange: (Float) -> Unit = {}
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
            
            // Intensity slider (only shown if device supports intensity and flashlight is on)
            if (supportsIntensity && isFlashlightOn) {
                Text(
                    text = stringResource(R.string.adjust_intensity),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(text = stringResource(R.string.intensity_low), fontSize = 14.sp)
                    
                    Slider(
                        value = flashlightIntensity,
                        onValueChange = { onIntensityChange(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 9,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    
                    Text(text = stringResource(R.string.intensity_high), fontSize = 14.sp)
                }
                
                Text(
                    text = stringResource(R.string.current_intensity, (flashlightIntensity * 100).toInt()),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (supportsIntensity) {
                Text(
                    text = stringResource(R.string.turn_on_to_adjust),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
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
                    id = if (isScreenFlashlightOn) R.string.screen_off else R.string.screen_on
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