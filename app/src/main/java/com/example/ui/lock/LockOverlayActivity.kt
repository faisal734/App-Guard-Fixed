package com.example.ui.lock

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.AppGuardApplication
import com.example.R
import com.example.data.model.IntruderLogEntity
import com.example.data.repository.ProtectionRepository
import com.example.data.security.ShuffleKeypad
import com.example.ui.theme.AppGuardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class LockOverlayActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_IS_RECENTS_LOCK = "extra_is_recents_lock"
        const val EXTRA_WIDGET_ACTION = "extra_widget_action"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val WIDGET_ACTION_UNLOCK_ALL = "unlock_all"
        const val ACTION_ACTIVATE_PROFILE = "action_activate_profile"

        @Volatile
        var isOverlayActive: Boolean = false
            private set

        @Volatile
        var activePackage: String = ""
            private set

        @Volatile
        var activeIsRecents: Boolean = false
            private set
    }

    private var targetPackage: String = ""
    private var appName: String = ""
    private var isRecentsLock: Boolean = false
    private var widgetAction: String? = null
    private var isBiometricActive by mutableStateOf(false)
    private var triggerKey by mutableStateOf(0L)
    private var isDeflecting = false

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences("app_guard_settings", android.content.Context.MODE_PRIVATE)
        val language = prefs.getString("language", "ar") ?: "ar"
        super.attachBaseContext(com.example.util.LocaleHelper.wrapContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("app_guard_settings", android.content.Context.MODE_PRIVATE)
        val language = prefs.getString("language", "ar") ?: "ar"
        com.example.util.LocaleHelper.applyLocale(this, language)

        super.onCreate(savedInstanceState)

        // Dismiss lock overlay if device Keyguard (screen lock) is active to prevent window flicker
        val km = getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km?.isKeyguardLocked == true) {
            finish()
            return
        }

        // Enforce global back-button deflection to home screen at the Activity root level
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                deflectToHome()
            }
        })

        // Mandatory FLAG_SECURE to prevent screen capture & recents snapshot leakage
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        appName = intent.getStringExtra(EXTRA_APP_NAME) ?: getString(R.string.lock_screen_title)
        isRecentsLock = intent.getBooleanExtra(EXTRA_IS_RECENTS_LOCK, false)
        widgetAction = intent.getStringExtra(EXTRA_WIDGET_ACTION)
        triggerKey = System.currentTimeMillis()
        isDeflecting = false

        isOverlayActive = true
        activePackage = targetPackage
        activeIsRecents = isRecentsLock

        val app = application as? AppGuardApplication
        val repository = app?.repository

        // Load app icon
        val appIconDrawable = try {
            if (isRecentsLock || widgetAction != null) {
                ContextCompat.getDrawable(this, R.drawable.ic_shield_lock_f)
            } else {
                packageManager.getApplicationIcon(targetPackage)
            }
        } catch (_: Exception) {
            ContextCompat.getDrawable(this, R.drawable.ic_shield_lock_f)
        }

        setContent {
            AppGuardTheme {
                LockOverlayScreen(
                    targetPackage = targetPackage,
                    appName = appName,
                    isRecentsLock = isRecentsLock,
                    appIcon = appIconDrawable,
                    isBiometricShowing = isBiometricActive,
                    triggerKey = triggerKey,
                    onUnlockSuccess = {
                        handleUnlockSuccess()
                    },
                    onTriggerBiometric = {
                        launchBiometricPrompt()
                    },
                    onCancel = {
                        deflectToHome()
                    }
                )
            }
        }

        // Auto trigger biometric if enabled and not already active (postponed if crash screen is enabled)
        val isCrashEnabled = repository?.settingsFlow?.value?.isCrashScreenEnabled == true
        if (repository?.settingsFlow?.value?.isBiometricEnabled == true && !isBiometricActive && !isCrashEnabled) {
            launchBiometricPrompt()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        appName = intent.getStringExtra(EXTRA_APP_NAME) ?: getString(R.string.lock_screen_title)
        isRecentsLock = intent.getBooleanExtra(EXTRA_IS_RECENTS_LOCK, false)
        widgetAction = intent.getStringExtra(EXTRA_WIDGET_ACTION)
        triggerKey = System.currentTimeMillis()
        isDeflecting = false

        isOverlayActive = true
        activePackage = targetPackage
        activeIsRecents = isRecentsLock

        val appIconDrawable = try {
            if (isRecentsLock || widgetAction != null) {
                ContextCompat.getDrawable(this, R.drawable.ic_shield_lock_f)
            } else {
                packageManager.getApplicationIcon(targetPackage)
            }
        } catch (_: Exception) {
            ContextCompat.getDrawable(this, R.drawable.ic_shield_lock_f)
        }

        setContent {
            AppGuardTheme {
                LockOverlayScreen(
                    targetPackage = targetPackage,
                    appName = appName,
                    isRecentsLock = isRecentsLock,
                    appIcon = appIconDrawable,
                    isBiometricShowing = isBiometricActive,
                    triggerKey = triggerKey,
                    onUnlockSuccess = {
                        handleUnlockSuccess()
                    },
                    onTriggerBiometric = {
                        launchBiometricPrompt()
                    },
                    onCancel = {
                        deflectToHome()
                    }
                )
            }
        }

        val app = application as? AppGuardApplication
        val isCrashEnabled = app?.repository?.settingsFlow?.value?.isCrashScreenEnabled == true
        if (app?.repository?.settingsFlow?.value?.isBiometricEnabled == true && !isBiometricActive && !isCrashEnabled) {
            launchBiometricPrompt()
        }
    }

    private fun launchBiometricPrompt() {
        if (isBiometricActive) return

        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            isBiometricActive = false
            return
        }

        isBiometricActive = true

        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isBiometricActive = false
                val app = application as? AppGuardApplication
                app?.repository?.pinSecurity?.resetFailedAttempts()
                handleUnlockSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isBiometricActive = false
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_screen_biometric_prompt))
            .setSubtitle(appName)
            .setNegativeButtonText(getString(R.string.cancel))
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (_: Exception) {
            isBiometricActive = false
        }
    }

    private fun handleUnlockSuccess() {
        val app = application as? AppGuardApplication ?: return finish()
        val repository = app.repository

        if (widgetAction == WIDGET_ACTION_UNLOCK_ALL) {
            app.applicationScope.launch(Dispatchers.IO) {
                repository.setGlobalLock(false)
                repository.setAllAppsLocked(false)
                repository.clearAllTemporaryUnlocks()
            }
        } else if (widgetAction == ACTION_ACTIVATE_PROFILE) {
            val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
            if (profileId != -1L) {
                app.applicationScope.launch(Dispatchers.IO) {
                    repository.activateProfile(profileId)
                }
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.profile_activated_toast, appName),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else if (isRecentsLock) {
            val settings = repository.settingsFlow.value
            if (settings.isRecentAppsAlwaysLocked) {
                // Option 2: Always locked (request auth on every attempt, but grant a very brief 3-second session so it doesn't loop)
                repository.grantRecentAuthSessionExplicit(3000L)
            } else {
                // Option 1: Track upper choices
                val durationMillis = when (settings.unlockRelockMode) {
                    com.example.data.model.UnlockRelockMode.TIMEOUT -> settings.timeoutMinutes * 60 * 1000L
                    com.example.data.model.UnlockRelockMode.UNLOCK_ONCE -> 3000L // Brief session, locks when exiting
                    com.example.data.model.UnlockRelockMode.OPEN_COUNT -> 5000L // Brief session
                    com.example.data.model.UnlockRelockMode.SCREEN_OFF -> Long.MAX_VALUE // Locks only when screen turns off
                }
                repository.grantRecentAuthSessionExplicit(durationMillis)
            }
        } else {
            repository.grantTemporaryUnlock(targetPackage)
            // Increment persistent app open count
            app.applicationScope.launch(Dispatchers.IO) {
                repository.protectedAppDao.incrementOpenCount(targetPackage)
            }
        }
        finish()
    }

    private fun deflectToHome() {
        if (isDeflecting) return
        isDeflecting = true
        if (widgetAction != null) {
            finish()
            return
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        // CRITICAL: Do NOT call finish() here! Calling finish() instantly removes the lock shield,
        // which exposes the protected app for a split second before the Home screen appears.
        // By leaving it alive, it stays on top blocking the app until the Home screen fully covers them both.
        // It will be cleaned up safely in the background due to android:noHistory="true".
    }

    override fun onDestroy() {
        super.onDestroy()
        isOverlayActive = false
        activePackage = ""
        activeIsRecents = false
    }
}

@Composable
fun LockOverlayScreen(
    targetPackage: String,
    appName: String,
    isRecentsLock: Boolean,
    appIcon: Drawable?,
    isBiometricShowing: Boolean = false,
    triggerKey: Long = 0L,
    onUnlockSuccess: () -> Unit,
    onTriggerBiometric: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val validationScope = rememberCoroutineScope()
    val app = context.applicationContext as? AppGuardApplication
    val repository = app?.repository
    val settings by repository?.settingsFlow?.collectAsState() ?: remember { mutableStateOf(null) }

    var enteredPin by remember(triggerKey) { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cooldownRemaining by remember { mutableStateOf(0L) }
    var isCrashModeActive by remember { mutableStateOf(settings?.isCrashScreenEnabled == true) }

    BackHandler {
        onCancel()
    }

    val isShuffle = settings?.isShufflePinEnabled == true
    val keypadNumbers = remember(triggerKey) {
        val shuffle = settings?.isShufflePinEnabled == true
        ShuffleKeypad.generateKeypad(shuffle)
    }

    // Cooldown countdown timer effect
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = repository?.pinSecurity?.getRemainingCooldownSeconds() ?: 0L
            cooldownRemaining = remaining
            delay(1000)
        }
    }

    if (isCrashModeActive) {
        // Crash Screen Simulation Mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                isCrashModeActive = false
                                onTriggerBiometric()
                            }
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        isCrashModeActive = false
                                        onTriggerBiometric()
                                    }
                                )
                            }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.crash_screen_message),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel, // Simulate real crash by exiting
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.crash_screen_report))
                        }
                        Button(
                            onClick = onCancel, // Simulate real crash by exiting
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.crash_screen_close))
                        }
                    }
                }
            }
        }
        return
    }

    // Standard Lock Screen UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // App Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val appImageBitmap = remember(appIcon) {
                    appIcon?.toBitmap(120, 120)?.asImageBitmap()
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (appImageBitmap != null) {
                        Image(
                            bitmap = appImageBitmap,
                            contentDescription = appName,
                            modifier = Modifier.size(56.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.lock_screen_title),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isShuffle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.lock_screen_shuffle_notice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // PIN dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Status / Error / Cooldown / Biometric message
                if (isBiometricShowing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "جاري المصادقة البيومترية...",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (cooldownRemaining > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.lock_screen_cooldown, cooldownRemaining),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                } else if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Numeric Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isKeypadDisabled = cooldownRemaining > 0 || isBiometricShowing

                // Row 1 (digits 0..2 in layout)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (i in 0..2) {
                        val digit = keypadNumbers[i]
                        KeypadButton(digit = digit, enabled = !isKeypadDisabled) {
                            if (enteredPin.length < 4) {
                                enteredPin += digit
                                errorMessage = null
                                if (enteredPin.length == 4) {
                                    validatePin(validationScope, enteredPin, repository, targetPackage, appName, context, onUnlockSuccess, {
                                        errorMessage = context.getString(R.string.lock_screen_wrong_pin)
                                        enteredPin = ""
                                    })
                                }
                            }
                        }
                    }
                }

                // Row 2 (digits 3..5 in layout)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (i in 3..5) {
                        val digit = keypadNumbers[i]
                        KeypadButton(digit = digit, enabled = !isKeypadDisabled) {
                            if (enteredPin.length < 4) {
                                enteredPin += digit
                                errorMessage = null
                                if (enteredPin.length == 4) {
                                    validatePin(validationScope, enteredPin, repository, targetPackage, appName, context, onUnlockSuccess, {
                                        errorMessage = context.getString(R.string.lock_screen_wrong_pin)
                                        enteredPin = ""
                                    })
                                }
                            }
                        }
                    }
                }

                // Row 3 (digits 6..8 in layout)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (i in 6..8) {
                        val digit = keypadNumbers[i]
                        KeypadButton(digit = digit, enabled = !isKeypadDisabled) {
                            if (enteredPin.length < 4) {
                                enteredPin += digit
                                errorMessage = null
                                if (enteredPin.length == 4) {
                                    validatePin(validationScope, enteredPin, repository, targetPackage, appName, context, onUnlockSuccess, {
                                        errorMessage = context.getString(R.string.lock_screen_wrong_pin)
                                        enteredPin = ""
                                    })
                                }
                            }
                        }
                    }
                }

                // Row 4 (Biometric, digit 9, Backspace)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Biometric button
                    IconButton(
                        onClick = {
                            if (!isBiometricShowing) {
                                onTriggerBiometric()
                            }
                        },
                        enabled = !isBiometricShowing,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBiometricShowing) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = stringResource(R.string.settings_biometric),
                            tint = if (isBiometricShowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 10th digit
                    val lastDigit = keypadNumbers[9]
                    KeypadButton(digit = lastDigit, enabled = !isKeypadDisabled) {
                        if (enteredPin.length < 4) {
                            enteredPin += lastDigit
                            errorMessage = null
                            if (enteredPin.length == 4) {
                                validatePin(validationScope, enteredPin, repository, targetPackage, appName, context, onUnlockSuccess, {
                                    errorMessage = context.getString(R.string.lock_screen_wrong_pin)
                                    enteredPin = ""
                                })
                            }
                        }
                    }

                    // Backspace button
                    IconButton(
                        onClick = {
                            if (!isBiometricShowing && enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                                errorMessage = null
                            }
                        },
                        enabled = !isBiometricShowing && enteredPin.isNotEmpty(),
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Backspace",
                            tint = if (isBiometricShowing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer attribution banner
            Text(
                text = stringResource(R.string.dev_name),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun KeypadButton(
    digit: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        modifier = Modifier.size(68.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

private fun validatePin(
    scope: CoroutineScope,
    pin: String,
    repository: ProtectionRepository?,
    targetPackage: String,
    appName: String,
    context: android.content.Context,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    if (repository == null) return
    val pinSecurity = repository.pinSecurity

    // Hash derivation is intentionally off the main thread to avoid lock-screen jank.
    scope.launch(Dispatchers.IO) {
        // Never initialize a missing PIN from the lock screen: that would let the first
        // person who reaches an uninitialized lock define the credential.
        val isValid = pinSecurity.isPinSet() && pinSecurity.verifyPin(pin)

        if (!isValid) {
            val settings = repository.settingsFlow.value
            if (settings.isIntruderDetectionEnabled) {
                try {
                    repository.intruderLogDao.insertLog(
                        IntruderLogEntity(
                            targetPackage = targetPackage,
                            targetAppName = appName,
                            attemptCount = pinSecurity.getFailedAttempts()
                        )
                    )
                } catch (_: Exception) {
                    // A logging failure must not turn a failed authentication into success.
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (isValid) onSuccess() else onFailure()
        }
    }
}
