package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedAppDao {
    @Query("SELECT * FROM protected_apps ORDER BY appName ASC")
    fun getAllApps(): Flow<List<ProtectedAppEntity>>

    @Query("SELECT * FROM protected_apps WHERE isLocked = 1")
    fun getLockedApps(): Flow<List<ProtectedAppEntity>>

    @Query("SELECT * FROM protected_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getApp(packageName: String): ProtectedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: ProtectedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<ProtectedAppEntity>)

    @Query("UPDATE protected_apps SET isLocked = :isLocked WHERE packageName = :packageName")
    suspend fun setLocked(packageName: String, isLocked: Boolean)

    @Query("UPDATE protected_apps SET isLocked = :isLocked WHERE packageName IN (:packageNames)")
    suspend fun setLockedBulk(packageNames: List<String>, isLocked: Boolean)

    @Query("UPDATE protected_apps SET isLocked = :isLocked")
    suspend fun setLockedAll(isLocked: Boolean)

    @Query("UPDATE protected_apps SET openCount = openCount + 1 WHERE packageName = :packageName")
    suspend fun incrementOpenCount(packageName: String)

    @Query("UPDATE protected_apps SET openCount = 0 WHERE packageName = :packageName")
    suspend fun resetOpenCount(packageName: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    @Query("UPDATE profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun activateProfile(profileId: Long)

    @Query("SELECT * FROM profile_apps WHERE profileId = :profileId")
    fun getProfileApps(profileId: Long): Flow<List<ProfileAppCrossRef>>

    @Query("SELECT * FROM profile_apps WHERE profileId = :profileId")
    suspend fun getProfileAppsSync(profileId: Long): List<ProfileAppCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileApp(profileApp: ProfileAppCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileApps(profileApps: List<ProfileAppCrossRef>)

    @Query("DELETE FROM profile_apps WHERE profileId = :profileId")
    suspend fun deleteProfileApps(profileId: Long)

    @Query("DELETE FROM profile_apps WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun removeAppFromProfile(profileId: Long, packageName: String)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY id ASC")
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    fun getEnabledSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedulesSync(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, isEnabled: Boolean)
}

@Dao
interface IntruderLogDao {
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IntruderLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IntruderLogEntity)

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearLogs()
}

@Dao
interface HiddenNotificationDao {
    @Query("SELECT * FROM hidden_notifications ORDER BY timestamp DESC")
    fun getAllHiddenNotifications(): Flow<List<HiddenNotificationEntity>>

    @Query("SELECT * FROM hidden_notifications WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastNotificationForPackage(packageName: String): HiddenNotificationEntity?

    @Query("SELECT * FROM hidden_notifications WHERE packageName = :packageName AND title = :title AND content = :content LIMIT 1")
    suspend fun findNotification(packageName: String, title: String, content: String): HiddenNotificationEntity?

    @Update
    suspend fun updateNotification(notification: HiddenNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: HiddenNotificationEntity): Long

    @Query("DELETE FROM hidden_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM hidden_notifications")
    suspend fun clearAllNotifications()

    @Query("UPDATE hidden_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE hidden_notifications SET isRead = 1")
    suspend fun markAllAsRead()
}

