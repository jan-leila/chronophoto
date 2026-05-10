package com.chronophoto.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chronophoto.data.db.dao.PhotoDao
import com.chronophoto.data.db.dao.ProjectDao
import com.chronophoto.data.db.entity.Photo
import com.chronophoto.data.db.entity.Project

@Database(
    entities = [Project::class, Photo::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun photoDao(): PhotoDao

    companion object {
        private const val DB_NAME = "chronophoto.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_4_5)
                .build()

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN reminderIntervalAmount INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE projects ADD COLUMN reminderIntervalUnit TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    UPDATE projects SET
                        reminderIntervalAmount = CASE
                            WHEN reminderIntervalMinutes IS NULL THEN NULL
                            WHEN reminderIntervalMinutes % 10080 = 0 THEN reminderIntervalMinutes / 10080
                            WHEN reminderIntervalMinutes % 1440 = 0 THEN reminderIntervalMinutes / 1440
                            WHEN reminderIntervalMinutes % 60 = 0 THEN reminderIntervalMinutes / 60
                            ELSE reminderIntervalMinutes
                        END,
                        reminderIntervalUnit = CASE
                            WHEN reminderIntervalMinutes IS NULL THEN NULL
                            WHEN reminderIntervalMinutes % 10080 = 0 THEN 'WEEKS'
                            WHEN reminderIntervalMinutes % 1440 = 0 THEN 'DAYS'
                            WHEN reminderIntervalMinutes % 60 = 0 THEN 'HOURS'
                            ELSE 'MINUTES'
                        END
                    """.trimIndent(),
                )
            }
        }
    }
}
