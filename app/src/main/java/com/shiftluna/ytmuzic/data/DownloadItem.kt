package com.shiftluna.ytmuzic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadItem(
    @PrimaryKey val videoId: String,
    val title: String,
    val originalUrl: String,
    val filePath: String,
    val thumbnailPath: String,
    val fileSize: String,
    val timestamp: Long = System.currentTimeMillis()
)
