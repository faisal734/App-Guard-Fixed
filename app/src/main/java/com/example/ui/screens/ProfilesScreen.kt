package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ProfileAppCrossRef
import com.example.data.model.ProfileEntity
import com.example.data.model.ProtectedAppEntity
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ProfileShortcutHelper
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profilesFlow.collectAsState(initial = emptyList())
    val allApps by viewModel.allAppsFlow.collectAsState(initial = emptyList())
    val settings by viewModel.settingsFlow.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var managingAppsProfile by remember { mutableStateOf<ProfileEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Info
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.profiles_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profiles_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Add Profile Button
        item {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_create_profile))
            }
        }

        // Profile Cards List
        items(profiles, key = { it.id }) { profile ->
            val isActive = profile.id == settings.activeProfileId
            ProfileCard(
                profile = profile,
                isActive = isActive,
                onToggleActive = { viewModel.toggleProfileActive(profile.id, isActive) },
                onEditDetails = { editingProfile = profile },
                onEditApps = { managingAppsProfile = profile },
                onDuplicate = { viewModel.duplicateProfile(profile.id) },
                onDelete = { viewModel.deleteProfile(profile) },
                onAddShortcut = { ProfileShortcutHelper.createProfileShortcut(context, profile) }
            )
        }
    }

    // Create Profile Dialog
    if (showCreateDialog) {
        ProfileEditDialog(
            title = stringResource(R.string.action_create_profile),
            initialName = "",
            initialIcon = "🛡",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, icon ->
                viewModel.createProfile(name, icon, emptyList())
                showCreateDialog = false
            }
        )
    }

    // Edit Profile Name & Icon Dialog
    if (editingProfile != null) {
        val prof = editingProfile!!
        ProfileEditDialog(
            title = "تعديل الملف الشخصي",
            initialName = prof.name,
            initialIcon = prof.icon,
            onDismiss = { editingProfile = null },
            onConfirm = { name, icon ->
                viewModel.updateProfileEntityOnly(prof.copy(name = name, icon = icon))
                editingProfile = null
            }
        )
    }

    // Per-Profile Apps Selector Dialog (with Select All / Deselect All)
    if (managingAppsProfile != null) {
        val targetProfile = managingAppsProfile!!
        var lockedPackageNames by remember { mutableStateOf<Set<String>>(emptySet()) }
        var isLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(targetProfile.id) {
            val appsInProfile = viewModel.getProfileAppsSync(targetProfile.id)
            lockedPackageNames = appsInProfile.filter { it.isLockedInProfile }.map { it.packageName }.toSet()
            isLoaded = true
        }

        if (isLoaded) {
            ProfileAppsManagerDialog(
                profile = targetProfile,
                allApps = allApps,
                initialLockedPackages = lockedPackageNames,
                onDismiss = { managingAppsProfile = null },
                onSave = { updatedLockedPkgs ->
                    viewModel.updateProfile(targetProfile, updatedLockedPkgs.toList())
                    managingAppsProfile = null
                }
            )
        }
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    isActive: Boolean,
    onToggleActive: () -> Unit,
    onEditDetails: () -> Unit,
    onEditApps: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onAddShortcut: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = profile.icon, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = profile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                            fontSize = 12.sp,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Activate / Deactivate Button
                FilledTonalButton(
                    onClick = onToggleActive,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isActive) stringResource(R.string.action_deactivate) else stringResource(R.string.action_activate),
                        color = if (isActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEditApps) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تخصيص التطبيقات", fontSize = 12.sp)
                }

                IconButton(onClick = onEditDetails) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل الاسم والأيقونة", modifier = Modifier.size(18.dp))
                }

                var showOptionsMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات الملف الشخصي",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_shortcut)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                onAddShortcut()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate_profile)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                onDuplicate()
                            }
                        )
                        if (!profile.isPreset) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_delete_profile),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileEditDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

    val emojiIcons = listOf("🛡", "🏠", "💼", "📚", "🌙", "🎮", "🔒", "⚡", "✈", "🚗")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الملف الشخصي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("اختر أيقونة الملف:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojiIcons.take(5).forEach { item ->
                        IconButton(
                            onClick = { icon = item },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (icon == item) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(item, fontSize = 18.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojiIcons.drop(5).forEach { item ->
                        IconButton(
                            onClick = { icon = item },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (icon == item) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(item, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name, icon)
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ProfileAppsManagerDialog(
    profile: ProfileEntity,
    allApps: List<ProtectedAppEntity>,
    initialLockedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selectedPackages by remember { mutableStateOf(initialLockedPackages) }
    var filterQuery by remember { mutableStateOf("") }

    val filtered = allApps.filter {
        filterQuery.isBlank() || it.appName.contains(filterQuery, ignoreCase = true) || it.packageName.contains(filterQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("تطبيقات ملف ${profile.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${selectedPackages.size} تطبيق مقفل في هذا الملف", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("بحث...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Select All / Deselect All for profile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        selectedPackages = allApps.map { it.packageName }.toSet()
                    }) {
                        Text(stringResource(R.string.action_select_all), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        selectedPackages = emptySet()
                    }) {
                        Text(stringResource(R.string.action_deselect_all), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { item ->
                        val isLockedInProfile = selectedPackages.contains(item.packageName)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLockedInProfile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.appName,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isLockedInProfile,
                                    onCheckedChange = { checked ->
                                        val updated = selectedPackages.toMutableSet()
                                        if (checked) updated.add(item.packageName) else updated.remove(item.packageName)
                                        selectedPackages = updated
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedPackages) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
