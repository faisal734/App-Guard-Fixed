package com.example.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.service.AppGuardDeviceAdminReceiver
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onNavigateToNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.settingsFlow.collectAsState()
    val intruderLogs by viewModel.intruderLogsFlow.collectAsState(initial = emptyList())
    val hiddenNotifications by viewModel.hiddenNotificationsFlow.collectAsState(initial = emptyList())
    var isNotificationAccessGranted by remember { mutableStateOf(viewModel.isNotificationAccessGranted()) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showIntruderLogsDialog by remember { mutableStateOf(false) }
    var isDeviceAdminActive by remember { mutableStateOf(viewModel.isDeviceAdminActive()) }

    var customTimeoutText by remember(settings.timeoutMinutes) {
        val isCustom = settings.timeoutMinutes !in listOf(1, 5, 10, 15, 30, 60)
        val initialValue = if (isCustom) {
            if (settings.timeoutMinutes % 60 == 0 && settings.timeoutMinutes >= 60) {
                (settings.timeoutMinutes / 60).toString()
            } else {
                settings.timeoutMinutes.toString()
            }
        } else {
            "12"
        }
        mutableStateOf(initialValue)
    }
    var customTimeoutUnit by remember(settings.timeoutMinutes) {
        val isCustom = settings.timeoutMinutes !in listOf(1, 5, 10, 15, 30, 60)
        val initialUnit = if (isCustom && settings.timeoutMinutes % 60 == 0 && settings.timeoutMinutes >= 60) "hours" else "minutes"
        mutableStateOf(initialUnit)
    }
    var customOpenCountText by remember(settings.openCountLimit) {
        val isCustom = settings.openCountLimit !in listOf(1, 2, 3, 5, 10)
        val initialValue = if (isCustom) settings.openCountLimit.toString() else "4"
        mutableStateOf(initialValue)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section: General Settings & Language
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_general), icon = Icons.Default.Language)
        }

        item {
            val context = LocalContext.current
            val activity = context as? android.app.Activity
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_language_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_language_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = settings.language == "ar",
                                onClick = {
                                    if (settings.language != "ar") {
                                        viewModel.repository.updateSettings { it.copy(language = "ar") }
                                        com.example.util.LocaleHelper.applyLocale(context, "ar")
                                        activity?.recreate()
                                    }
                                },
                                label = { Text("العربية", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = settings.language == "en",
                                onClick = {
                                    if (settings.language != "en") {
                                        viewModel.repository.updateSettings { it.copy(language = "en") }
                                        com.example.util.LocaleHelper.applyLocale(context, "en")
                                        activity?.recreate()
                                    }
                                },
                                label = { Text("English", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Security & PIN
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_security), icon = Icons.Default.Security)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Set / Change PIN Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (viewModel.repository.pinSecurity.isPinSet()) stringResource(R.string.settings_change_pin) else stringResource(R.string.settings_set_pin),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "حماية مشفرة عبر Android Keystore و SHA-256",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { showPinDialog = true }) {
                            Text("تعديل", fontSize = 12.sp)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Biometric Switch
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_biometric),
                        subtitle = stringResource(R.string.settings_biometric_desc),
                        checked = settings.isBiometricEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isBiometricEnabled = checked) }
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Shuffle PIN Switch
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_shuffle_pin),
                        subtitle = stringResource(R.string.settings_shuffle_pin_desc),
                        checked = settings.isShufflePinEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isShufflePinEnabled = checked) }
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))


                }
            }
        }

        // Section: Relock Method
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_relock), icon = Icons.Default.LockReset)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.relock_mode_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Option 2: TIMEOUT
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.TIMEOUT) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.TIMEOUT) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.TIMEOUT,
                                    onClick = {
                                        viewModel.repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.TIMEOUT) }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.relock_mode_timeout),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.relock_mode_timeout_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.TIMEOUT) {
                                val isCustomTimeout = settings.timeoutMinutes !in listOf(1, 5, 10, 15, 30, 60)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timeouts = listOf(1 to "1 دقيقة", 5 to "5 دقائق", 10 to "10 دقائق", 15 to "15 دقيقة", 30 to "30 دقيقة", 60 to "60 دقيقة")
                                    timeouts.forEach { (mins, label) ->
                                        FilterChip(
                                            selected = settings.timeoutMinutes == mins,
                                            onClick = {
                                                viewModel.repository.updateSettings { it.copy(timeoutMinutes = mins) }
                                            },
                                            label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                        )
                                    }
                                    FilterChip(
                                        selected = isCustomTimeout,
                                        onClick = {
                                            if (!isCustomTimeout) {
                                                viewModel.repository.updateSettings { it.copy(timeoutMinutes = 12) } // default custom minutes
                                            }
                                        },
                                        label = { Text(stringResource(R.string.custom_option), fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                    )
                                }

                                if (isCustomTimeout) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customTimeoutText,
                                            onValueChange = { newValue ->
                                                if (newValue.all { it.isDigit() }) {
                                                    customTimeoutText = newValue
                                                    val num = newValue.toIntOrNull() ?: 1
                                                    val calculatedMins = if (customTimeoutUnit == "hours") num * 60 else num
                                                    viewModel.repository.updateSettings { it.copy(timeoutMinutes = calculatedMins) }
                                                }
                                            },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                            ),
                                            label = { Text(stringResource(R.string.custom_timeout_label), fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        
                                        Row(
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilterChip(
                                                selected = customTimeoutUnit == "minutes",
                                                onClick = {
                                                    customTimeoutUnit = "minutes"
                                                    val num = customTimeoutText.toIntOrNull() ?: 1
                                                    viewModel.repository.updateSettings { it.copy(timeoutMinutes = num) }
                                                },
                                                label = { Text(stringResource(R.string.minutes_label), fontSize = 11.sp) },
                                                border = null
                                            )
                                            FilterChip(
                                                selected = customTimeoutUnit == "hours",
                                                onClick = {
                                                    customTimeoutUnit = "hours"
                                                    val num = customTimeoutText.toIntOrNull() ?: 1
                                                    viewModel.repository.updateSettings { it.copy(timeoutMinutes = num * 60) }
                                                },
                                                label = { Text(stringResource(R.string.hours_label), fontSize = 11.sp) },
                                                border = null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Option 3: OPEN_COUNT
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.OPEN_COUNT) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.OPEN_COUNT) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.OPEN_COUNT,
                                    onClick = {
                                        viewModel.repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.OPEN_COUNT) }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.relock_mode_open_count),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.relock_mode_open_count_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.OPEN_COUNT) {
                                val isCustomOpenCount = settings.openCountLimit !in listOf(1, 2, 3, 5, 10)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val openCounts = listOf(1 to "1 فتح", 2 to "2 فتح", 3 to "3 فتح", 5 to "5 فتح", 10 to "10 فتح")
                                    openCounts.forEach { (count, label) ->
                                        FilterChip(
                                            selected = settings.openCountLimit == count,
                                            onClick = {
                                                viewModel.repository.updateSettings { it.copy(openCountLimit = count) }
                                            },
                                            label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                        )
                                    }
                                    FilterChip(
                                        selected = isCustomOpenCount,
                                        onClick = {
                                            if (!isCustomOpenCount) {
                                                viewModel.repository.updateSettings { it.copy(openCountLimit = 4) } // default custom open count
                                            }
                                        },
                                        label = { Text(stringResource(R.string.custom_option), fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                    )
                                }

                                if (isCustomOpenCount) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = customOpenCountText,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() }) {
                                                customOpenCountText = newValue
                                                val num = newValue.toIntOrNull() ?: 1
                                                viewModel.repository.updateSettings { it.copy(openCountLimit = num) }
                                            }
                                        },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        ),
                                        label = { Text(stringResource(R.string.custom_open_count_label), fontSize = 12.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    // Option 4: SCREEN_OFF
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.SCREEN_OFF) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.SCREEN_OFF) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.unlockRelockMode == com.example.data.model.UnlockRelockMode.SCREEN_OFF,
                                onClick = {
                                    viewModel.repository.updateSettings { it.copy(unlockRelockMode = com.example.data.model.UnlockRelockMode.SCREEN_OFF) }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.relock_mode_screen_off),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = stringResource(R.string.relock_mode_screen_off_desc),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Keyboard Lock Behavior Option
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("keyboard_lock_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_keyboard_lock_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_keyboard_lock_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: ALWAYS_UNLOCKED
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings { it.copy(keyboardLockBehavior = com.example.data.model.KeyboardLockBehavior.ALWAYS_UNLOCKED) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.keyboardLockBehavior == com.example.data.model.KeyboardLockBehavior.ALWAYS_UNLOCKED) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().testTag("keyboard_always_unlocked_option")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.keyboardLockBehavior == com.example.data.model.KeyboardLockBehavior.ALWAYS_UNLOCKED,
                                onClick = {
                                    viewModel.repository.updateSettings { it.copy(keyboardLockBehavior = com.example.data.model.KeyboardLockBehavior.ALWAYS_UNLOCKED) }
                                },
                                modifier = Modifier.testTag("keyboard_always_unlocked_radio")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_keyboard_lock_always_unlocked),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Option 2: FOLLOW_RELOCK
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings { it.copy(keyboardLockBehavior = com.example.data.model.KeyboardLockBehavior.FOLLOW_RELOCK) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.keyboardLockBehavior == com.example.data.model.KeyboardLockBehavior.FOLLOW_RELOCK) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().testTag("keyboard_follow_relock_option")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.keyboardLockBehavior == com.example.data.model.KeyboardLockBehavior.FOLLOW_RELOCK,
                                onClick = {
                                    viewModel.repository.updateSettings { it.copy(keyboardLockBehavior = com.example.data.model.KeyboardLockBehavior.FOLLOW_RELOCK) }
                                },
                                modifier = Modifier.testTag("keyboard_follow_relock_radio")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_keyboard_lock_follow),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Notifications Protection
        item {
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_notifications),
                icon = Icons.Default.NotificationsActive
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Notification Hiding for Protected Apps Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_notification_hiding),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_notification_hiding_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Follow Relock Option
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings {
                                it.copy(
                                    notificationHidingMode = com.example.data.model.NotificationHidingMode.FOLLOW_RELOCK_STATE,
                                    isNotificationHidingEnabled = true
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.notificationHidingMode == com.example.data.model.NotificationHidingMode.FOLLOW_RELOCK_STATE)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.notificationHidingMode == com.example.data.model.NotificationHidingMode.FOLLOW_RELOCK_STATE,
                                onClick = {
                                    viewModel.repository.updateSettings {
                                        it.copy(
                                            notificationHidingMode = com.example.data.model.NotificationHidingMode.FOLLOW_RELOCK_STATE,
                                            isNotificationHidingEnabled = true
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "1. " + stringResource(R.string.settings_notif_mode_relock),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_notif_mode_relock_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Always Active Option
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings {
                                it.copy(
                                    notificationHidingMode = com.example.data.model.NotificationHidingMode.ALWAYS_ACTIVE,
                                    isNotificationHidingEnabled = true
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.notificationHidingMode == com.example.data.model.NotificationHidingMode.ALWAYS_ACTIVE)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.notificationHidingMode == com.example.data.model.NotificationHidingMode.ALWAYS_ACTIVE,
                                onClick = {
                                    viewModel.repository.updateSettings {
                                        it.copy(
                                            notificationHidingMode = com.example.data.model.NotificationHidingMode.ALWAYS_ACTIVE,
                                            isNotificationHidingEnabled = true
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "2. " + stringResource(R.string.settings_notif_mode_always_active),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_notif_mode_always_active_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Always Off Option
                    Surface(
                        onClick = {
                            viewModel.repository.updateSettings {
                                it.copy(
                                    notificationHidingMode = com.example.data.model.NotificationHidingMode.ALWAYS_OFF,
                                    isNotificationHidingEnabled = false
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (settings.notificationHidingMode == com.example.data.model.NotificationHidingMode.ALWAYS_OFF)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.notificationHidingMode == com.example.data.model.NotificationHidingMode.ALWAYS_OFF,
                                onClick = {
                                    viewModel.repository.updateSettings {
                                        it.copy(
                                            notificationHidingMode = com.example.data.model.NotificationHidingMode.ALWAYS_OFF,
                                            isNotificationHidingEnabled = false
                                        )
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "3. " + stringResource(R.string.settings_notif_mode_always_off),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_notif_mode_always_off_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (settings.notificationHidingMode != com.example.data.model.NotificationHidingMode.ALWAYS_OFF && !isNotificationAccessGranted) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_notification_permission_prompt),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("تفعيل", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Open Hidden Notifications Vault
                    FilledTonalButton(
                        onClick = { onNavigateToNotifications() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_notification_vault, hiddenNotifications.size))
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Protect System Settings and Package Installer
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_protect_settings_app),
                        subtitle = stringResource(R.string.settings_protect_settings_app_desc),
                        checked = settings.isSettingsProtectionEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isSettingsProtectionEnabled = checked) }
                        }
                    )
                }
            }
        }

        // Section: Recent Apps & Advanced Protection
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_recent), icon = Icons.Default.ViewAgenda)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Recent Apps Lock
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_recent_lock),
                        subtitle = stringResource(R.string.settings_recent_lock_desc),
                        checked = settings.isRecentAppsLockEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isRecentAppsLockEnabled = checked) }
                        }
                    )

                    if (settings.isRecentAppsLockEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_recent_apps_policy),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Policy 1: Follow Global Policy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.repository.updateSettings { it.copy(isRecentAppsAlwaysLocked = false) }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !settings.isRecentAppsAlwaysLocked,
                                    onClick = {
                                        viewModel.repository.updateSettings { it.copy(isRecentAppsAlwaysLocked = false) }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_recent_apps_policy_follow),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // Policy 2: Always Locked
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.repository.updateSettings { it.copy(isRecentAppsAlwaysLocked = true) }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.isRecentAppsAlwaysLocked,
                                    onClick = {
                                        viewModel.repository.updateSettings { it.copy(isRecentAppsAlwaysLocked = true) }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_recent_apps_policy_always),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Auto-lock new apps
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_auto_lock_new),
                        subtitle = stringResource(R.string.settings_auto_lock_new_desc),
                        checked = settings.isAutoLockNewAppsEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isAutoLockNewAppsEnabled = checked) }
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Crash Screen Mode
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_crash_screen),
                        subtitle = stringResource(R.string.settings_crash_screen_desc),
                        checked = settings.isCrashScreenEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isCrashScreenEnabled = checked) }
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Anti-Uninstall Device Administrator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_anti_uninstall),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_anti_uninstall_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val component = ComponentName(context, AppGuardDeviceAdminReceiver::class.java)
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "تفعيل حماية مسؤول الجهاز لحارس التطبيقات لمنع إزالة التثبيت العفوي.")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDeviceAdminActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isDeviceAdminActive) "مفعّل" else "تفعيل", fontSize = 12.sp)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Intruder Detection
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_intruder),
                        subtitle = stringResource(R.string.settings_intruder_desc),
                        checked = settings.isIntruderDetectionEnabled,
                        onCheckedChange = { checked ->
                            viewModel.repository.updateSettings { it.copy(isIntruderDetectionEnabled = checked) }
                        }
                    )

                    if (intruderLogs.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showIntruderLogsDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_intruder_logs, intruderLogs.size))
                        }
                    }
                }
            }
        }

        // Developer & App Identity Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.app_title_full),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dev_name),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الإصدار 1.0 — Native Kotlin & Jetpack Compose",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    // Set PIN Dialog
    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onPinSaved = { newPin ->
                coroutineScope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        viewModel.repository.pinSecurity.setPin(newPin)
                    }
                    if (saved) showPinDialog = false
                }
            }
        )
    }

    // Intruder Logs Dialog
    if (showIntruderLogsDialog) {
        IntruderLogsDialog(
            logs = intruderLogs,
            onDismiss = { showIntruderLogsDialog = false },
            onClearLogs = {
                viewModel.clearIntruderLogs()
                showIntruderLogsDialog = false
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onPinSaved: (String) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Enter PIN, 2: Confirm PIN
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val targetLength = 4

    fun handleDigitInput(digit: String) {
        if (currentPin.length < targetLength) {
            val newPin = currentPin + digit
            currentPin = newPin
            errorMessage = null

            if (newPin.length == targetLength) {
                if (step == 1) {
                    firstPin = newPin
                    currentPin = ""
                    step = 2
                } else {
                    if (newPin == firstPin) {
                        onPinSaved(newPin)
                    } else {
                        errorMessage = "رمزا PIN غير متطابقين، يرجى المحاولة مرة أخرى"
                        currentPin = ""
                        step = 1
                        firstPin = ""
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            errorMessage = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (step == 1) Icons.Default.Lock else Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Title & Description
                Text(
                    text = if (step == 1) "أنشئ رمز PIN للتطبيق" else "تأكيد رمز PIN",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (step == 1) "أدخل 4 أرقام لحماية التطبيقات" else "أعد إدخال الرمز لتأكيده",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Visual PIN Indicators (Dots)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    for (i in 0 until targetLength) {
                        val isFilled = i < currentPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                // Error Message Display
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Custom Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val digits = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    digits.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { digit ->
                                KeypadButton(text = digit, onClick = { handleDigitInput(digit) })
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.size(64.dp)) // Spacer placeholder
                        KeypadButton(text = "0", onClick = { handleDigitInput("0") })
                        IconButton(
                            onClick = { handleBackspace() },
                            enabled = currentPin.isNotEmpty(),
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "مسح",
                                tint = if (currentPin.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("إلغاء", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun IntruderLogsDialog(
    logs: List<com.example.data.model.IntruderLogEntity>,
    onDismiss: () -> Unit,
    onClearLogs: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل محاولات التسلل") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد محاولات تسلل مسجلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(logs, key = { it.id }) { log ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "التطبيق: ${log.targetAppName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "الوقت: ${dateFormat.format(Date(log.timestamp))}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "عدد المحاولات الخاطئة: ${log.attemptCount}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (logs.isNotEmpty()) {
                Button(
                    onClick = onClearLogs,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("مسح السجل")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
