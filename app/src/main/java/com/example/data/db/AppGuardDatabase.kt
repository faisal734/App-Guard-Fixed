package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*

@Database(
    entities = [
        ProtectedAppEntity::class,
        ProfileEntity::class,
        ProfileAppCrossRef::class,
        ScheduleEntity::class,
        IntruderLogEntity::class,
        HiddenNotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppGuardDatabase : RoomDatabase() {
    abstract fun protectedAppDao(): ProtectedAppDao
    abstract fun profileDao(): ProfileDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun intruderLogDao(): IntruderLogDao
    abstract fun hiddenNotificationDao(): HiddenNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppGuardDatabase? = null

        fun getInstance(context: Context): AppGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppGuardDatabase::class.java,
                    "app_guard_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Room invokes onCreate before getInstance() necessarily publishes INSTANCE.
                // Insert defaults directly into the already-created table to avoid that race.
                val defaults = listOf(
                    Triple(1, "المنزل", "🏠"),
                    Triple(2, "العمل", "💼"),
                    Triple(3, "الدراسة", "📚"),
                    Triple(4, "النوم", "🌙"),
                    Triple(5, "الألعاب", "🎮")
                )
                defaults.forEach { (id, name, icon) ->
                    val statement = db.compileStatement(
                        "INSERT OR IGNORE INTO profiles (id, name, icon, isActive, isPreset) VALUES (?, ?, ?, 0, 1)"
                    )
                    statement.bindLong(1, id.toLong())
                    statement.bindString(2, name)
                    statement.bindString(3, icon)
                    statement.executeInsert()
                    statement.close()
                }
            }
        }
    }
}
