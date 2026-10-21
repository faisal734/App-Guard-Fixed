package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.*
import com.example.ui.theme.AppGuardTheme
import com.example.ui.viewmodel.MainViewModel

import android.content.Context
import com.example.util.LocaleHelper
import android.content.Intent
import com.example.ui.lock.LockOverlayActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavigationTab {
    APPS,
    PROFILES,
    SCHEDULES,
    NOTIFICATIONS,
    DIAGNOSTICS,
    SETTINGS,
    SELF_TEST
}

class MainActivity : ComponentActivity() {

    private var handingOffToFlutter = false
    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_guard_settings", Context.MODE_PRIVATE)
        val language = prefs.getString("language", "ar") ?: "ar"
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("app_guard_settings", Context.MODE_PRIVATE)
        val language = prefs.getString("language", "ar") ?: "ar"
        LocaleHelper.applyLocale(this, language)

        super.onCreate(savedInstanceState)
        // Enforce FLAG_SECURE against Recents peek and screenshot leakage
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settingsFlow.collectAsState()
            var isPinConfigured by remember { mutableStateOf(viewModel.repository.pinSecurity.isPinSet()) }
            var isOnboardingComplete by remember { mutableStateOf(viewModel.isOnboardingComplete()) }
            var flutterLaunchRequested by remember { mutableStateOf(false) }
            var resumeSignal by remember { mutableIntStateOf(0) }

            DisposableEffect(this@MainActivity.lifecycle) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeSignal++
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(isPinConfigured, isOnboardingComplete, resumeSignal) {
                val unlockSession = viewModel.repository.getTemporaryUnlockSession(packageName)
                val isAuthenticated = unlockSession != null && !unlockSession.isExpired()
                if (BuildConfig.FLUTTER_HOST_ENABLED && isPinConfigured && isOnboardingComplete && isAuthenticated && !flutterLaunchRequested) {
                    flutterLaunchRequested = true
                    try {
                        handingOffToFlutter = true
                        startActivity(Intent().setClassName(this@MainActivity, "com.example.flutterbridge.AppGuardFlutterActivity"))
                        finish()
                    } catch (_: android.content.ActivityNotFoundException) {
                        handingOffToFlutter = false
                        flutterLaunchRequested = false
                    }
                }
            }

            AppGuardTheme(darkTheme = settings.isDarkTheme) {
                var showExitDialog by remember { mutableStateOf(false) }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("تأكيد الخروج", fontWeight = FontWeight.Bold) },
                        text = { Text("هل أنت متأكد أنك تريد الخروج من التطبيق؟") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showExitDialog = false
                                    this@MainActivity.finish()
                                }
                            ) {
                                Text("خروج")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text("إلغاء")
                            }
                        }
                    )
                }

                if (!isPinConfigured) {
                    com.example.ui.screens.SetPinDialog(
                        onDismiss = {},
                        onPinSaved = { newPin ->
                            lifecycleScope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    viewModel.repository.pinSecurity.setPin(newPin)
                                }
                                if (saved) {
                                    viewModel.repository.grantTemporaryUnlock(packageName)
                                    isPinConfigured = true
                                }
                            }
                        }
                    )
                } else if (!isOnboardingComplete) {
                    com.example.ui.screens.OnboardingPermissionsScreen(
                        viewModel = viewModel,
                        onComplete = {
                            viewModel.setOnboardingComplete(true)
                            isOnboardingComplete = true
                        }
                    )
                } else if (BuildConfig.FLUTTER_HOST_ENABLED && flutterLaunchRequested) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    androidx.activity.compose.BackHandler(enabled = true) {
                        showExitDialog = true
                    }
                    MainAppScaffold(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.repository.pinSecurity.isPinSet()) {
            checkSelfProtection()
        }
    }

    override fun onStop() {
        super.onStop()
        // Preserve the authenticated session only during the deliberate handoff to Flutter.
        if (!handingOffToFlutter) viewModel.repository.revokeTemporaryUnlock(packageName)
    }

    private fun checkSelfProtection() {
        // App Guard is ALWAYS protected by SELF_PROTECTION policy
        val session = viewModel.repository.getTemporaryUnlockSession(packageName)
        if (session == null || session.isExpired()) {
            val intent = Intent(this, LockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
                putExtra(LockOverlayActivity.EXTRA_APP_NAME, getString(R.string.app_name))
                putExtra(LockOverlayActivity.EXTRA_IS_RECENTS_LOCK, false)
            }
            startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(NavigationTab.APPS) }
    val settings by viewModel.settingsFlow.collectAsState()

    val hiddenNotifications by viewModel.hiddenNotificationsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = stringResource(R.string.dev_name),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Master Protection Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Switch(
                            checked = settings.isProtectionEnabled,
                            onCheckedChange = { isEnabled ->
                                viewModel.repository.updateSettings { it.copy(isProtectionEnabled = isEnabled) }
                            },
                            thumbContent = {
                                Icon(
                                    imageVector = if (settings.isProtectionEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    }

                    // Hidden Notifications Vault Shortcut
                    IconButton(onClick = { selectedTab = NavigationTab.NOTIFICATIONS }) {
                        BadgedBox(
                            badge = {
                                if (hiddenNotifications.isNotEmpty()) {
                                    Badge {
                                        Text(hiddenNotifications.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = stringResource(R.string.nav_hidden_notifications),
                                tint = if (selectedTab == NavigationTab.NOTIFICATIONS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Diagnostics & Self Test Shortcut
                    IconButton(onClick = { selectedTab = NavigationTab.DIAGNOSTICS }) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = stringResource(R.string.nav_diagnostics),
                            tint = if (selectedTab == NavigationTab.DIAGNOSTICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Dark Theme Toggle
                    IconButton(onClick = {
                        viewModel.repository.updateSettings { it.copy(isDarkTheme = !it.isDarkTheme) }
                    }) {
                        Icon(
                            imageVector = if (settings.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.APPS,
                    onClick = { selectedTab = NavigationTab.APPS },
                    icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_apps), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.PROFILES,
                    onClick = { selectedTab = NavigationTab.PROFILES },
                    icon = { Icon(Icons.Default.GroupWork, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_profiles), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.SCHEDULES,
                    onClick = { selectedTab = NavigationTab.SCHEDULES },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_schedules), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.NOTIFICATIONS,
                    onClick = { selectedTab = NavigationTab.NOTIFICATIONS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (hiddenNotifications.isNotEmpty()) {
                                    Badge {
                                        Text(hiddenNotifications.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null)
                        }
                    },
                    label = { Text(stringResource(R.string.nav_hidden_notifications), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.SETTINGS,
                    onClick = { selectedTab = NavigationTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings), fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.APPS -> AppsScreen(viewModel = viewModel)
                NavigationTab.PROFILES -> ProfilesScreen(viewModel = viewModel)
                NavigationTab.SCHEDULES -> SchedulesScreen(viewModel = viewModel)
                NavigationTab.NOTIFICATIONS -> HiddenNotificationsScreen(viewModel = viewModel)
                NavigationTab.DIAGNOSTICS -> DiagnosticsScreen(viewModel = viewModel)
                NavigationTab.SETTINGS -> SecuritySettingsScreen(
                    viewModel = viewModel,
                    onNavigateToNotifications = { selectedTab = NavigationTab.NOTIFICATIONS }
                )
                NavigationTab.SELF_TEST -> SelfTestScreen(viewModel = viewModel)
            }
        }
    }
}
