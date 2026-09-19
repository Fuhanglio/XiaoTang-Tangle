package com.Tangle.timetable

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.edit
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.Tangle.timetable.bean.*
import com.Tangle.timetable.dao.*
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(entities = [CourseBaseBean::class, CourseDetailBean::class, AppWidgetBean::class, TimeDetailBean::class,
    TimeTableBean::class, TableBean::class],
        version = 8, exportSchema = false)

abstract class AppDatabase : RoomDatabase() {

    companion object {
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            if (INSTANCE == null) {
                synchronized(AppDatabase::class.java) {
                    if (INSTANCE == null) {
                        // B1：极老库（DB 1~6）在 fallback 重建前先做文件级备份，保留恢复机会
                        backupLegacyDatabaseIfNeeded(context.applicationContext)
                        INSTANCE = Room.databaseBuilder(context.applicationContext,
                                AppDatabase::class.java, "wakeup")
                                .allowMainThreadQueries()
                                .addMigrations(migration7to8)
                                // 极老版本（DB 1~6）无逐级迁移：宁可重建数据库也不"打开即崩"
                                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                                .build()
                    }
                }
            }
            return INSTANCE ?: throw IllegalStateException(
                    "AppDatabase.getDatabase() 未初始化，请先以非空 context 调用")
        }

        /**
         * B1：老库（PRAGMA user_version 在 1~6）备份。
         * 在 Room 打开/重建数据库之前，只读探测 wakeup 库文件版本，
         * 命中老版本就把 wakeup / wakeup-wal / wakeup-shm 三个文件复制到
         * getExternalFilesDir(null)/db_backup/wakeup_v<旧版本>_<时间戳>.db（含 -wal/-shm）。
         * 任何一步失败都只记日志，绝不阻断启动；fallback 重建兜底保持不变。
         */
        private fun backupLegacyDatabaseIfNeeded(context: Context) {
            try {
                val dbFile = context.getDatabasePath("wakeup")
                if (!dbFile.exists()) return
                val oldVersion = readLegacyDbVersion(dbFile.absolutePath)
                if (oldVersion !in 1..6) return
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "db_backup")
                if (!dir.exists()) dir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val saved = File(dir, "wakeup_v${oldVersion}_${stamp}.db")
                dbFile.copyTo(saved, overwrite = true)
                // wal/shm 一并备份，缺哪个就跳过哪个
                for (suffix in listOf("-wal", "-shm")) {
                    val src = File(dbFile.parentFile, "wakeup$suffix")
                    if (src.exists()) {
                        try {
                            src.copyTo(File(dir, saved.name + suffix), overwrite = true)
                        } catch (e: Exception) {
                            Log.w("AppDatabase", "备份 wakeup$suffix 失败（不影响主库备份）", e)
                        }
                    }
                }
                // 记录待提示标记：主界面首次启动据此弹一次性 AlertDialog
                context.getPrefer().edit {
                    putBoolean(Const.KEY_DB_OLD_VERSION_DETECTED, true)
                    putString(Const.KEY_DB_BACKUP_PATH, saved.absolutePath)
                }
                Log.w("AppDatabase", "检测到老版本数据库 v$oldVersion，已备份到 ${saved.absolutePath}")
            } catch (e: Exception) {
                // 备份失败只记日志，不阻断启动
                Log.e("AppDatabase", "老库备份失败（不阻断启动）", e)
            }
        }

        /** 只读打开老库读 PRAGMA user_version；任何异常返回 -1（视为无需备份）。调用方负责场景，这里保证 close */
        private fun readLegacyDbVersion(path: String): Int {
            var db: SQLiteDatabase? = null
            return try {
                db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
                db.version
            } catch (e: Exception) {
                -1
            } finally {
                try {
                    db?.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        private val migration7to8: Migration = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE TimeTableBean (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL);")
                database.execSQL("INSERT INTO TimeTableBean VALUES(1, '默认');")
                database.execSQL("CREATE TABLE TableBean (\n" +
                        "    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n" +
                        "    tableName TEXT NOT NULL, \n" +
                        "    nodes INTEGER NOT NULL DEFAULT 11, \n" +
                        "    background TEXT NOT NULL DEFAULT '',\n" +
                        "    timeTable INTEGER NOT NULL DEFAULT 1,\n" +
                        "    startDate TEXT NOT NULL DEFAULT '2019-02-25',\n" +
                        "    maxWeek INTEGER NOT NULL DEFAULT 30,\n" +
                        "    itemHeight INTEGER NOT NULL DEFAULT 56,\n" +
                        "    itemAlpha INTEGER NOT NULL DEFAULT 60,\n" +
                        "    itemTextSize INTEGER NOT NULL DEFAULT 12,\n" +
                        "    widgetItemHeight INTEGER NOT NULL DEFAULT 56,\n" +
                        "    widgetItemAlpha INTEGER NOT NULL DEFAULT 60,\n" +
                        "    widgetItemTextSize INTEGER NOT NULL DEFAULT 12,\n" +
                        "    strokeColor INTEGER NOT NULL DEFAULT 0x80ffffff,\n" +
                        "    widgetStrokeColor INTEGER NOT NULL DEFAULT 0x80ffffff,\n" +
                        "    textColor INTEGER NOT NULL DEFAULT 0xff000000,\n" +
                        "    widgetTextColor INTEGER NOT NULL DEFAULT 0xff000000,\n" +
                        "    courseTextColor INTEGER NOT NULL DEFAULT 0xff000000,\n" +
                        "    widgetCourseTextColor INTEGER NOT NULL DEFAULT 0xff000000,\n" +
                        "    showSat INTEGER NOT NULL DEFAULT 1,\n" +
                        "    showSun INTEGER NOT NULL DEFAULT 1,\n" +
                        "    sundayFirst INTEGER NOT NULL DEFAULT 0,\n" +
                        "    showOtherWeekCourse INTEGER NOT NULL DEFAULT 0,\n" +
                        "    showTime INTEGER NOT NULL DEFAULT 0,\n" +
                        "    type INTEGER NOT NULL DEFAULT 0,\n" +
                        "    FOREIGN KEY (timeTable) REFERENCES TimeTableBean (id) ON DELETE SET DEFAULT ON UPDATE CASCADE\n" +
                        ");")
                // 索引名必须与实体 @Index 推导名一致（index_TableBean_timeTable），
                // 否则 Room 的 TableInfo 校验报 "Migration didn't properly handle tablebean"
                database.execSQL("CREATE INDEX index_TableBean_timeTable ON TableBean (timeTable ASC);")
                database.execSQL("ALTER TABLE CourseBaseBean RENAME TO CourseBaseBean_old;")
                database.execSQL("CREATE TABLE CourseBaseBean(id INTEGER NOT NULL, courseName TEXT NOT NULL, color TEXT NOT NULL, tableId INTEGER NOT NULL, PRIMARY KEY (id, tableId), FOREIGN KEY (tableId) REFERENCES TableBean (id) ON DELETE CASCADE ON UPDATE CASCADE);")
                database.execSQL("INSERT INTO TableBean (tableName) VALUES('');")
                database.execSQL("INSERT INTO TableBean (tableName) VALUES('情侣课表');")
                database.execSQL("INSERT INTO CourseBaseBean (id, courseName, color, tableId) SELECT id, courseName, color, CASE WHEN tableName = '' THEN 1 ELSE 2 END FROM CourseBaseBean_old;")
                database.execSQL("CREATE INDEX index_CourseBaseBean_tableId ON CourseBaseBean (tableId ASC);")
                database.execSQL("DROP TABLE CourseBaseBean_old;")
                database.execSQL("DROP INDEX index_CourseDetailBean_id_tableName;")
                database.execSQL("ALTER TABLE CourseDetailBean RENAME TO CourseDetailBean_old;")
                database.execSQL("CREATE TABLE CourseDetailBean (\n" +
                        "  id INTEGER NOT NULL,\n" +
                        "  day INTEGER NOT NULL,\n" +
                        "  room TEXT,\n" +
                        "  teacher TEXT,\n" +
                        "  startNode INTEGER NOT NULL,\n" +
                        "  step INTEGER NOT NULL,\n" +
                        "  startWeek INTEGER NOT NULL,\n" +
                        "  endWeek INTEGER NOT NULL,\n" +
                        "  type INTEGER NOT NULL,\n" +
                        "  tableId INTEGER NOT NULL,\n" +
                        "  PRIMARY KEY (day, startNode, startWeek, type, tableId, id),\n" +
                        "  FOREIGN KEY (\"id\", \"tableId\") REFERENCES \"CourseBaseBean\" (\"id\", \"tableId\") ON DELETE CASCADE ON UPDATE CASCADE\n" +
                        ");")
                database.execSQL("INSERT INTO CourseDetailBean (id, day, room, teacher, startNode, step, startWeek, endWeek, type, tableId) SELECT id, day, room, teacher, startNode, step, startWeek, endWeek, type, CASE WHEN tableName = '' THEN 1 ELSE 2 END FROM CourseDetailBean_old;")
                database.execSQL("CREATE INDEX index_CourseDetailBean_id_tableId ON CourseDetailBean (id ASC, tableId ASC);")
                database.execSQL("DROP TABLE CourseDetailBean_old")

                database.execSQL("ALTER TABLE TimeDetailBean RENAME TO TimeDetailBean_old;")
                database.execSQL("CREATE TABLE TimeDetailBean (node INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, timeTable INTEGER NOT NULL DEFAULT 1, PRIMARY KEY (node, timeTable), FOREIGN KEY (timeTable) REFERENCES TimeTableBean (id) ON DELETE CASCADE ON UPDATE CASCADE);")
                database.execSQL("INSERT INTO TimeDetailBean (node, startTime, endTime) SELECT node, startTime, endTime FROM TimeDetailBean_old;")
                database.execSQL("CREATE INDEX index_TimeDetailBean_timeTable ON TimeDetailBean(timeTable ASC);")
                database.execSQL("DROP TABLE TimeDetailBean_old;")
                database.execSQL("ALTER TABLE TimeTableBean ADD COLUMN sameLen INTEGER NOT NULL DEFAULT 1;")
                database.execSQL("ALTER TABLE TimeTableBean ADD COLUMN courseLen INTEGER NOT NULL DEFAULT 50;")
                database.execSQL("UPDATE tablebean SET type=1 WHERE id=(SELECT MIN(id) FROM tablebean)")
            }
        }
    }

    abstract fun courseDao(): CourseDao

    abstract fun appWidgetDao(): AppWidgetDao

    abstract fun timeTableDao(): TimeTableDao

    abstract fun timeDetailDao(): TimeDetailDao

    abstract fun tableDao(): TableDao
}

