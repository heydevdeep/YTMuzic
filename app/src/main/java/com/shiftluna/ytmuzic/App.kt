package com.shiftluna.ytmuzic

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class App : Application() {

    companion object {
        var initError: String? = null
        var isInitialized = false
    }

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            isInitialized = true
            Log.i("App", "YoutubeDL + FFmpeg initialized successfully")
            
            // Update yt-dlp in background
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@App)
                    Log.i("App", "YoutubeDL updated successfully")
                } catch (e: Exception) {
                    Log.e("App", "YoutubeDL update failed", e)
                }
            }
        } catch (e: Exception) {
            initError = e.message ?: e.javaClass.simpleName
            Log.e("App", "YoutubeDL init failed: ${e.message}", e)
        }
    }
}
