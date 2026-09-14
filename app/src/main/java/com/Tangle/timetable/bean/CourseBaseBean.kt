package com.Tangle.timetable.bean

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(foreignKeys = [(
        ForeignKey(entity = TableBean::class,
                parentColumns = ["id"],
                childColumns = ["tableId"],
                onUpdate = ForeignKey.CASCADE,
                onDelete = ForeignKey.CASCADE
        ))],
        primaryKeys = ["id", "tableId"],
        indices = [Index(value = ["tableId"], unique = false)])
data class CourseBaseBean(
        var id: Int,
        var courseName: String,
        var color: String,
        var tableId: Int
) : Parcelable