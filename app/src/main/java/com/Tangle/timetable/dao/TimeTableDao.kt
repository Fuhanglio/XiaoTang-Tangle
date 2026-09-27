package com.Tangle.timetable.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.Tangle.timetable.bean.TimeDetailBean
import com.Tangle.timetable.bean.TimeTableBean

@Dao
interface TimeTableDao {

    @Transaction
    suspend fun initTimeTable(timeTableBean: TimeTableBean) {
        val id = insertTimeTable(timeTableBean).toInt()
        val timeList = listOf(
                TimeDetailBean(1, "08:00", "08:45", id),
                TimeDetailBean(2, "09:00", "09:45", id),
                TimeDetailBean(3, "10:10", "10:55", id),
                TimeDetailBean(4, "11:10", "11:55", id),
                TimeDetailBean(5, "13:30", "14:15", id),
                TimeDetailBean(6, "14:30", "15:15", id),
                TimeDetailBean(7, "15:40", "16:25", id),
                TimeDetailBean(8, "16:40", "17:25", id),
                TimeDetailBean(9, "18:30", "19:15", id),
                TimeDetailBean(10, "19:30", "20:15", id),
                TimeDetailBean(11, "20:30", "21:15", id),
                TimeDetailBean(12, "00:00", "00:00", id),
                TimeDetailBean(13, "00:00", "00:00", id),
                TimeDetailBean(14, "00:00", "00:00", id),
                TimeDetailBean(15, "00:00", "00:00", id),
                TimeDetailBean(16, "00:00", "00:00", id),
                TimeDetailBean(17, "00:00", "00:00", id),
                TimeDetailBean(18, "00:00", "00:00", id),
                TimeDetailBean(19, "00:00", "00:00", id),
                TimeDetailBean(20, "00:00", "00:00", id),
                TimeDetailBean(21, "00:00", "00:00", id),
                TimeDetailBean(22, "00:00", "00:00", id),
                TimeDetailBean(23, "00:00", "00:00", id),
                TimeDetailBean(24, "00:00", "00:00", id),
                TimeDetailBean(25, "00:00", "00:00", id),
                TimeDetailBean(26, "00:00", "00:00", id),
                TimeDetailBean(27, "00:00", "00:00", id),
                TimeDetailBean(28, "00:00", "00:00", id),
                TimeDetailBean(29, "00:00", "00:00", id),
                TimeDetailBean(30, "00:00", "00:00", id)
        )
        insertTimeList(timeList)
    }

    @Insert
    suspend fun insertTimeList(list: List<TimeDetailBean>)

    @Insert
    suspend fun insertTimeTable(timeTableBean: TimeTableBean): Long

    @Query("select * from timetablebean")
    fun getTimeTableList(): LiveData<List<TimeTableBean>>

    @Query("select max(id) from timetablebean")
    suspend fun getMaxId(): Int

    @Query("select * from timetablebean where id = :id")
    suspend fun getTimeTable(id: Int): TimeTableBean?

    @Update
    suspend fun updateTimeTable(timeTableBean: TimeTableBean)

    /**
     * W7-06：删除时间表 —— 先把引用它的课表迁走，再删。
     *
     * TableBean.timeTable 的外键声明是 ON DELETE SET DEFAULT，但 Room 按实体生成的建表语句里
     * `timeTable INTEGER NOT NULL` 并没有列默认值（实体上写的 `= 1` 只是 Kotlin 默认参数，
     * 不会变成 SQL DEFAULT），于是 SET DEFAULT 实际往子键写了 NULL → NOT NULL 违约 →
     * 删除被 SQLiteConstraintException 直接打回。UI 只会把它当成"仍被使用中"，
     * 于是用户永远删不掉任何被引用的时间表（fresh install 的库走 Room 建表路径，尤其如此，
     * 因为这条路径不像 v7→8 迁移那样带 `DEFAULT 1`）。
     *
     * 修法：删除前先把引用者迁到另一张仍然存在的时间表，删除时已无子行引用，SET DEFAULT 不会被触发。
     * 刻意不动实体外键声明：改 onDelete 会改变 Room 校验用的 TableInfo（外键 onDelete 参与比较），
     * 反而必须补一条 8→9 迁移重建表，风险大得多；本改法不碰 schema、不升数据库版本。
     */
    @Transaction
    suspend fun deleteTimeTable(timeTableBean: TimeTableBean) {
        reassignTablesToOtherTimeTable(timeTableBean.id)
        removeTimeTable(timeTableBean.id)
    }

    /**
     * 把引用 :id 的课表改指到另一张仍存在的时间表（取 id 最小的那张）。
     * 若确实没有别的时间表，子查询为 NULL，赋值触发 NOT NULL 约束而抛异常 ——
     * 此时外键会拒绝删除，UI 照旧提示"仍被使用中"，与旧行为一致，不会误删。
     */
    @Query("update tablebean set timeTable = (select min(id) from timetablebean where id <> :id) where timeTable = :id")
    suspend fun reassignTablesToOtherTimeTable(id: Int)

    @Query("delete from timetablebean where id = :id")
    suspend fun removeTimeTable(id: Int)
}