package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ProfileEntity
import com.example.data.model.ScheduleEntity
import com.example.data.model.ScheduleTargetType
import com.example.engine.ScheduleEngine
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SchedulesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val schedules by viewModel.schedulesFlow.collectAsState(initial = emptyList())
    val profiles by viewModel.profilesFlow.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }

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
                        text = stringResource(R.string.schedules_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.schedules_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Add Schedule Button
        item {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddAlarm, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_schedule))
            }
        }

        // Schedules List Items
        items(schedules, key = { it.id }) { schedule ->
            val isCurrentlyActive = ScheduleEngine.isScheduleActive(schedule)
            val isOvernight = (schedule.endHour * 60 + schedule.endMinute) < (schedule.startHour * 60 + schedule.startMinute)
            val targetProfileName = profiles.find { it.id == schedule.targetProfileId }?.name ?: ""

            ScheduleCard(
                schedule = schedule,
                isCurrentlyActive = isCurrentlyActive,
                isOvernight = isOvernight,
                targetProfileName = targetProfileName,
                onToggleEnabled = { enabled -> viewModel.toggleScheduleEnabled(schedule.id, enabled) },
                onDelete = { viewModel.deleteSchedule(schedule) }
            )
        }
    }

    if (showAddDialog) {
        ScheduleAddDialog(
            profiles = profiles,
            onDismiss = { showAddDialog = false },
            onConfirm = { newSchedule ->
                viewModel.addSchedule(newSchedule)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ScheduleCard(
    schedule: ScheduleEntity,
    isCurrentlyActive: Boolean,
    isOvernight: Boolean,
    targetProfileName: String,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyActive && schedule.isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isOvernight) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.schedule_overnight_badge),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val timeFormatted = String.format(
                        "%02d:%02d ➔ %02d:%02d",
                        schedule.startHour, schedule.startMinute,
                        schedule.endHour, schedule.endMinute
                    )
                    Text(
                        text = timeFormatted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val targetDescription = when (schedule.targetType) {
                        ScheduleTargetType.GLOBAL_LOCK -> "قفل الكل (Global Lock)"
                        ScheduleTargetType.GLOBAL_UNLOCK -> "فتح الكل (Global Unlock)"
                        ScheduleTargetType.ACTIVATE_PROFILE -> "تفعيل ملف: $targetProfileName"
                        ScheduleTargetType.DEACTIVATE_PROFILE -> "تعطيل ملف: $targetProfileName"
                    }
                    Text(
                        text = targetDescription,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isCurrentlyActive && schedule.isEnabled) "نشط حالياً (Active)" else "غير نشط الآن",
                    fontSize = 11.sp,
                    color = if (isCurrentlyActive && schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleAddDialog(
    profiles: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf(23) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(7) }
    var endMinute by remember { mutableStateOf(0) }
    var targetType by remember { mutableStateOf(ScheduleTargetType.GLOBAL_LOCK) }
    var selectedProfileId by remember { mutableStateOf(profiles.firstOrNull()?.id) }
    var selectedDaysMask by remember { mutableStateOf(127) } // All 7 days

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_schedule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الجدول (مثال: القفل الليلي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Time Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                startHour = h; startMinute = m
                            }, startHour, startMinute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(String.format("البدء: %02d:%02d", startHour, startMinute), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                endHour = h; endMinute = m
                            }, endHour, endMinute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(String.format("الانتهاء: %02d:%02d", endHour, endMinute), fontSize = 12.sp)
                    }
                }

                // Target Type selector
                Text("نوع الإجراء:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = targetType == ScheduleTargetType.GLOBAL_LOCK,
                        onClick = { targetType = ScheduleTargetType.GLOBAL_LOCK },
                        label = { Text("قفل الكل", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetType == ScheduleTargetType.GLOBAL_UNLOCK,
                        onClick = { targetType = ScheduleTargetType.GLOBAL_UNLOCK },
                        label = { Text("فتح الكل", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetType == ScheduleTargetType.ACTIVATE_PROFILE,
                        onClick = { targetType = ScheduleTargetType.ACTIVATE_PROFILE },
                        label = { Text("ملف شخصي", fontSize = 11.sp) }
                    )
                }

                // Profile Selector if Profile mode
                if (targetType == ScheduleTargetType.ACTIVATE_PROFILE && profiles.isNotEmpty()) {
                    Text("اختر الملف:", fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profiles.forEach { p ->
                            FilterChip(
                                selected = selectedProfileId == p.id,
                                onClick = { selectedProfileId = p.id },
                                label = { Text("${p.icon} ${p.name}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val schedule = ScheduleEntity(
                            name = name,
                            targetType = targetType,
                            targetProfileId = if (targetType == ScheduleTargetType.ACTIVATE_PROFILE) selectedProfileId else null,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            daysOfWeek = selectedDaysMask,
                            isEnabled = true
                        )
                        onConfirm(schedule)
                    }
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
