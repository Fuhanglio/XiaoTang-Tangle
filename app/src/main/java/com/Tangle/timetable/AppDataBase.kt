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
         * 老库（PRAGMA user_version 在 1~6）备份（B1 引入，W7-05 加固）。
         * 在 Room 打开/重建数据库之前探测 wakeup 库文件版本，命中老版本就把
         * wakeup / wakeup-wal / wakeup-shm 三个文件复制到
         * getExternalFilesDir(null)/db_backup/wakeup_v<旧版本>_<时间戳>.db（含 -wal/-shm）。
         *
         * W7-05：旧实现只有一条探测通道（只读打开），且 `oldVersion !in 1..6` 就 return——
         * 而 Room 默认 WAL 模式下只读打开 WAL 库在部分机型/权限下会抛
         * SQLiteCantOpenDatabaseException，异常被吞成 -1 后正好落进这个 return，
         * 于是变成"探测失败 → 静默跳过备份 → 紧接着 fallback 破坏性重建 →
         * 数据既丢了又没有备份"（v161 想防的恰恰是这一场景，属于回归隐患）。
         * 现在：① 探测改成两级（只读打开 → 直接解析文件头）；
         *      ② 只有明确读出 7 以上版本才跳过，读不出/读异常一律照样备份，绝不静默跳过。
         * 任何一步失败都只记日志，绝不阻断启动；fallback 重建兜底保持不变。
         */
        private fun backupLegacyDatabaseIfNeeded(context: Context) {
            try {
                val dbFile = context.getDatabasePath("wakeup")
                if (!dbFile.exists() || dbFile.length() <= 0L) return
                val oldVersion = readLegacyDbVersion(dbFile)
                val isLegacy = oldVersion in 1..6
                // 明确读出版本号 ≥7（含当前 v8）才说明不是老库；-1 与任何异常值都进入备份分支
                if (oldVersion in 7..1000) return
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "db_backup")
                if (!dir.exists()) dir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                // 老版本用「版本号 + 时间戳」留档；版本未知时用固定文件名且不覆盖，
                // 避免每次冷启动都堆一个文件、也避免后一次的空库覆盖掉前一次的完整老库
                val saved = if (isLegacy) File(dir, "wakeup_v${oldVersion}_${stamp}.db")
                        else File(dir, "wakeup_unknown.db")
                if (!isLegacy && saved.exists()) {
                    Log.w("AppDatabase", "已存在未知版本备份，跳过重复备份")
                    return
                }
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
                // 记录待提示标记：主界面首次启动据此弹一次性 AlertDialog。
                // 版本未知时同样置位：宁可多弹一次信息，也不能让"数据丢了且用户毫不知情"重演
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

        /**
         * W7-05：读老库 PRAGMA user_version，两级探测。
         * ① 首选只读打开（会连带读 -wal，语义最准）；
         * ② 打不开时（WAL 只读 / 权限 / 锁 → SQLiteCantOpenDatabaseException）退回解析文件头，
         *    完全绕开数据库层，天然免疫 WAL 与锁的问题。
         * 两级都失败返回 -1，调用方把 -1 当"版本未知"照样备份（见上），不许静默跳过。
         */
        private fun readLegacyDbVersion(file: File): Int {
            try {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                        .use { return it.version }
            } catch (e: Exception) {
                Log.w("AppDatabase", "只读打开老库探测版本失败，改用文件头解析", e)
            }
            return readVersionFromHeader(file)
        }

        /**
         * 直接解析 SQLite 文件头取 user_version，不打开数据库：
         * 偏移 0~15 = "SQLite format 3\0"（头部魔数，用来确认这确实是个 SQLite 库）；
         * 偏移 60~63 = user_version，**大端**存储。
         * 大端这点已用真实库实测确认：user_version=6 的文件头是 00 00 00 06，按小端解析会得到
         * 100663296，直接落进"非老版本"分支 → 又变回静默跳过，所以字节序不能想当然。
         * 另注：WAL 模式下若 -wal 尚未 checkpoint，文件头里的 user_version 可能是陈旧值（实测读到 0），
         * 因此这条路只作为通道② 的兜底，不作为唯一依据。
         */
        private fun readVersionFromHeader(file: File): Int {
            return try {
                if (file.length() < 64L) return -1
                val head = ByteArray(64)
                file.inputStream().use { ins ->
                    var read = 0
                    while (read < 64) {
                        val n = ins.read(head, read, 64 - read)
                        if (n <= 0) return -1
                        read += n
                    }
                }
                if (String(head, 0, 15, Charsets.US_ASCII) != "SQLite format 3") return -1
                ((head[60].toInt() and 0xff) shl 24) or
                        ((head[61].toInt() and 0xff) shl 16) or
                        ((head[62].toInt() and 0xff) shl 8) or
                        (head[63].toInt() and 0xff)
            } catch (e: Exception) {
                Log.w("AppDatabase", "解析 SQLite 文件头失败", e)
                -1
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

