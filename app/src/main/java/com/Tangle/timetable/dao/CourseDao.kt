package com.Tangle.timetable.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.Tangle.timetable.bean.CourseBaseBean
import com.Tangle.timetable.bean.CourseBean
import com.Tangle.timetable.bean.CourseDetailBean

@Dao
interface CourseDao {

    @Transaction
    suspend fun insertSingleCourse(courseBaseBean: CourseBaseBean, courseDetailList: List<CourseDetailBean>) {
        insertCourseBase(courseBaseBean)
        insertDetailList(courseDetailList)
    }

    @Transaction
    suspend fun updateSingleCourse(courseBaseBean: CourseBaseBean, courseDetailList: List<CourseDetailBean>) {
        updateCourseBaseBean(courseBaseBean)
        deleteDetailByIdOfTable(courseBaseBean.id, courseBaseBean.tableId)
        insertDetailList(courseDetailList)
    }

    @Transaction
    suspend fun updateSameCourse(courseBaseBean: CourseBaseBean, courseDetailList: List<CourseDetailBean>) {
        updateCourseBaseBean(courseBaseBean)
        insertDetailList(courseDetailList)
    }

    @Transaction
    suspend fun insertCourses(courseBaseList: List<CourseBaseBean>, courseDetailList: List<CourseDetailBean>) {
        insertBaseList(courseBaseList)
        insertDetailList(courseDetailList)
    }

    @Transaction
    suspend fun coverImport(courseBaseList: List<CourseBaseBean>, courseDetailList: List<CourseDetailBean>) {
        removeCourseBaseBeanOfTable(courseBaseList[0].tableId)
        insertBaseList(courseBaseList)
        insertDetailList(courseDetailList)
    }

    @Delete
    suspend fun deleteCourseDetail(courseDetailBean: CourseDetailBean)

    /**
     * 把若干条课程明细"搬家"：先按旧主键删掉原记录，再插入新记录，全程一个事务。
     *
     * 为什么不能直接用 @Update：CourseDetailBean 的主键是
     * (day, startNode, startWeek, type, tableId, id)，改了 day/startNode 就等于换了主键，
     * @Update 会按新主键去找行、必然找不到（更新 0 行）；而用 REPLACE 插入又会因为主键不同
     * 而留下旧记录，等于拖一次多一条。所以必须"删旧 + 插新"。
     *
     * 也正因如此，调用方必须保证 oldList 里的 bean 保留着**原始**的 day/startNode 值。
     */
    @Transaction
    suspend fun moveCourseDetails(oldList: List<CourseDetailBean>, newList: List<CourseDetailBean>) {
        for (old in oldList) {
            deleteCourseDetail(old)
        }
        insertDetailList(newList)
    }

    @Query("select * from coursebasebean where tableId = :tableId")
    suspend fun getCourseBaseBeanOfTable(tableId: Int): List<CourseBaseBean>

    @Query("select * from coursebasebean natural join coursedetailbean where day = :day and tableId = :tableId")
    fun getCourseByDayOfTableLiveData(day: Int, tableId: Int): LiveData<List<CourseBean>>

    @Query("select * from coursebasebean natural join coursedetailbean where day = :day and tableId = :tableId")
    suspend fun getCourseByDayOfTable(day: Int, tableId: Int): List<CourseBean>

    @Query("select * from coursebasebean natural join coursedetailbean where day = :day and tableId = :tableId")
    fun getCourseByDayOfTableSync(day: Int, tableId: Int): List<CourseBean>

    @Query("select * from coursebasebean natural join coursedetailbean where day = :day and tableId = :tableId and startWeek <= :week and endWeek >= :week and (type = 0 or type = :type)")
    suspend fun getCourseByDayOfTable(day: Int, week: Int, type: Int, tableId: Int): List<CourseBean>

    @Query("select * from coursebasebean natural join coursedetailbean where day = :day and tableId = :tableId and startWeek <= :week and endWeek >= :week and (type = 0 or type = :type)")
    fun getCourseByDayOfTableSync(day: Int, week: Int, type: Int, tableId: Int): List<CourseBean>

    @Query("select * from coursebasebean where id = :id and tableId = :tableId")
    suspend fun getCourseByIdOfTable(id: Int, tableId: Int): CourseBaseBean

    @Query("select max(id) from coursebasebean where tableId = :tableId")
    suspend fun getLastIdOfTable(tableId: Int): Int?

    @Query("delete from coursebasebean where id = :id and tableId = :tableId")
    suspend fun deleteCourseBaseBeanOfTable(id: Int, tableId: Int)

    @Query("select * from coursebasebean natural join coursedetailbean where courseName = :name and tableId = :tableId")
    suspend fun checkSameNameInTable(name: String, tableId: Int): CourseBaseBean?

    @Query("delete from coursebasebean where tableId = :tableId")
    suspend fun removeCourseBaseBeanOfTable(tableId: Int)

    @Query("delete from coursedetailbean where id = :id and tableId = :tableId")
    suspend fun deleteDetailByIdOfTable(id: Int, tableId: Int)

    @Query("select * from coursedetailbean where id = :id and tableId = :tableId")
    suspend fun getDetailByIdOfTable(id: Int, tableId: Int): List<CourseDetailBean>

    @Query("select * from coursedetailbean where tableId = :tableId")
    suspend fun getDetailOfTable(tableId: Int): List<CourseDetailBean>

    @Query("select distinct teacher from coursedetailbean where tableId = :tableId order by length(teacher)")
    suspend fun getExistedTeachers(tableId: Int): List<String>

    @Query("select distinct room from coursedetailbean where tableId = :tableId order by length(room)")
    suspend fun getExistedRooms(tableId: Int): List<String>

    @Query("SELECT COUNT(*) FROM coursedetailbean WHERE tableId=:tableId AND ((startWeek<=:week AND endWeek>=:week) AND (type=0 OR (:week % 2=0 AND type=2) OR (:week % 2=1 AND type=1)))")
    fun getShowCourseNumber(tableId: Int, week: Int): LiveData<Int>

    @Query("SELECT COUNT(*) FROM coursedetailbean WHERE tableId=:tableId AND endWeek>=:week")
    fun getShowCourseNumberWithOtherWeek(tableId: Int, week: Int): LiveData<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseList(courseBaseList: List<CourseBaseBean>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseBase(courseBaseBean: CourseBaseBean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetailList(courseDetailList: List<CourseDetailBean>)

    @Update
    suspend fun updateCourseBaseBean(course: CourseBaseBean)

}