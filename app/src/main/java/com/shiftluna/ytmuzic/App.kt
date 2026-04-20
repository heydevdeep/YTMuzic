package com.shiftluna.ytmuzic

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
