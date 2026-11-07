package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** One row per package that was the first app opened after at least one unlock on [date]. */
@Entity(tableName = "phone_first_app_day", primaryKeys = ["date", "package_name"])
data class PhoneFirstAppDayEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int,
)
