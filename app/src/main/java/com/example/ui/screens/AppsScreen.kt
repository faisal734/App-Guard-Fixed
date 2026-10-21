package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.ProtectedAppEntity
import com.example.ui.viewmodel.AppFilter
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apps by viewModel.filteredApps.collectAsState()
    val allApps by viewModel.allAppsFlow.collectAsState(initial = emptyList())
    val selectedPkgs by viewModel.selectedPackages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val profiles by viewModel.profilesFlow.collectAsState(initial = emptyList())

    val isAccessibilityActive = viewModel.isAccessibilityEnabled()
    val lockedCount = allApps.count { it.isLocked }
    val activeProfileName = profiles.find { it.id == settings.activeProfileId }?.name ?: stringResource(R.string.no_active_profile)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Protection Status Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessibilityActive && settings.isProtectionEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAccessibilityActive && settings.isProtectionEnabled) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isAccessibilityActive && settings.isProtectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isAccessibilityActive && settings.isProtectionEnabled) {
                                        stringResource(R.string.protection_active)
                                    } else {
                                        stringResource(R.string.protection_disabled)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.active_profile_label, activeProfileName),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // Locked Count Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = stringResource(R.string.locked_apps_count, lockedCount),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (!isAccessibilityActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.protection_disabled_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.permission_accessibility_prompt))
                        }
                    }
                }
            }
        }

        // Quick Global Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.lockAll() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_lock_all), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.unlockAll() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_unlock_all), fontSize = 12.sp)
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_apps_hint), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeFilter == AppFilter.ALL,
                    onClick = { viewModel.activeFilter.value = AppFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all, allApps.size), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = activeFilter == AppFilter.LOCKED,
                    onClick = { viewModel.activeFilter.value = AppFilter.LOCKED },
                    label = { Text(stringResource(R.string.filter_locked, allApps.count { it.isLocked }), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = activeFilter == AppFilter.UNLOCKED,
                    onClick = { viewModel.activeFilter.value = AppFilter.UNLOCKED },
                    label = { Text(stringResource(R.string.filter_unlocked, allApps.count { !it.isLocked }), fontSize = 11.sp) }
                )
                FilterChip(
                    selected = activeFilter == AppFilter.SYSTEM,
                    onClick = { viewModel.activeFilter.value = AppFilter.SYSTEM },
                    label = { Text(stringResource(R.string.filter_system, allApps.count { it.isSystemApp }), fontSize = 11.sp) }
                )
            }
        }

        // Bulk Selection Actions Bar
        item {
            AnimatedVisibility(visible = true) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text(stringResource(R.string.action_select_all), fontSize = 11.sp)
                            }
                            TextButton(onClick = { viewModel.deselectAll() }) {
                                Text(stringResource(R.string.action_deselect_all), fontSize = 11.sp)
                            }
                        }

                        if (selectedPkgs.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton(
                                    onClick = { viewModel.lockSelected() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.action_lock_selected), fontSize = 11.sp)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.unlockSelected() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.action_unlock_selected), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apps List Items
        items(apps, key = { it.packageName }) { appItem ->
            AppListItem(
                app = appItem,
                isSelected = selectedPkgs.contains(appItem.packageName),
                onSelectToggle = { viewModel.toggleSelection(appItem.packageName) },
                onLockToggle = { isLocked -> viewModel.toggleAppLock(appItem.packageName, isLocked) }
            )
        }
    }
}

@Composable
fun AppListItem(
    app: ProtectedAppEntity,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onLockToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
        } catch (_: Exception) {
            null
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.toBitmap(80, 80).asImageBitmap(),
                            contentDescription = app.appName,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = app.isLocked,
                onCheckedChange = { onLockToggle(it) },
                thumbContent = {
                    Icon(
                        imageVector = if (app.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
            )
        }
    }
}
