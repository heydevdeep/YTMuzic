package com.shiftluna.ytmuzic.viewmodel

import android.app.Application
import android.util.Log
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiftluna.ytmuzic.data.AppDatabase
import com.shiftluna.ytmuzic.data.DownloadItem
import com.shiftluna.ytmuzic.data.SettingsRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val downloadDao = db.downloadDao()
    private val settingsRepository = SettingsRepository(application)

    val downloads: StateFlow<List<DownloadItem>> = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val downloadFolderUri: StateFlow<Uri?> = settingsRepository.downloadFolderUri
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var urlInput = MutableStateFlow("")
    var isDownloading = MutableStateFlow(false)
    var downloadProgress = MutableStateFlow(0f)
    var downloadStatus = MutableStateFlow("")

    fun startDownload(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            // Guard: YoutubeDL must be initialized (fails on x86_64 emulators — ARM device required)
            if (!com.shiftluna.ytmuzic.App.isInitialized) {
                val reason = com.shiftluna.ytmuzic.App.initError ?: "unknown error"
                downloadStatus.value = "Engine not ready: $reason"
                return@launch
            }
            isDownloading.value = true
            downloadProgress.value = 0f
            downloadStatus.value = "Starting download..."
            try {
                // Determine output directory
                val context = getApplication<Application>()
                val cacheDir = File(context.cacheDir, "yt_downloads")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                // Output template
                val template = File(cacheDir, "%(title)s.%(ext)s").absolutePath

                val request = YoutubeDLRequest(url)
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--embed-metadata")
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
                request.addOption("--ppa", "ffmpeg: -c:v mjpeg -vf crop=ih:ih")
                request.addOption("--extractor-args", "youtube:player-client=android_music,web_music")
                request.addOption("-o", template)

                val response = YoutubeDL.getInstance().execute(request, "current_download") { progress, etaInSeconds, _ ->
                    val validProgress = if (progress >= 0) progress / 100f else 0f
                    downloadProgress.value = validProgress
                    
                    val progressStr = if (progress >= 0) "${progress.toInt()}%" else "Preparing..."
                    val etaStr = if (etaInSeconds > 0) " ETA: ${etaInSeconds}s" else ""
                    downloadStatus.value = "Downloading... $progressStr$etaStr"
                }

                downloadStatus.value = "Processing metadata..."
                Log.d("DownloadViewModel", "Starting getInfo for $url")

                // Get real video info
                val info = YoutubeDL.getInstance().getInfo(url)
                val videoId = info.id ?: url.hashCode().toString()
                val title = info.title ?: "Unknown Title"
                Log.d("DownloadViewModel", "Got metadata: $title ($videoId)")

                // Find the downloaded file in cache
                Log.d("DownloadViewModel", "Looking for file in $cacheDir")
                val downloadedFile = cacheDir.listFiles()?.firstOrNull { it.extension == "mp3" }
                if (downloadedFile != null) {
                    val fileSize = "${downloadedFile.length() / (1024 * 1024)} MB"
                    
                    // Copy to destination
                    val destUri = downloadFolderUri.value
                    var finalPath = ""
                    
                    if (destUri != null) {
                        // Copy using SAF
                        val documentFile = DocumentFile.fromTreeUri(context, destUri)
                        val newFile = documentFile?.createFile("audio/mpeg", downloadedFile.name)
                        if (newFile != null) {
                            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                                FileInputStream(downloadedFile).use { input ->
                                    input.copyTo(out)
                                }
                            }
                            finalPath = newFile.uri.toString()
                        }
                    } else {
                        // Default to Downloads folder
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val targetFile = File(downloadsDir, downloadedFile.name)
                        FileOutputStream(targetFile).use { out ->
                            FileInputStream(downloadedFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                        finalPath = targetFile.absolutePath
                    }

                    // Save to Room
                    val item = DownloadItem(
                        videoId = videoId,
                        title = title,
                        originalUrl = url,
                        filePath = finalPath,
                        thumbnailPath = info.thumbnail ?: "",
                        fileSize = fileSize
                    )
                    downloadDao.insertDownload(item)
                    Log.d("DownloadViewModel", "Saved to DB: $title")

                    // Clean up cache
                    downloadedFile.delete()
                    downloadStatus.value = "Download Complete!"
                    urlInput.value = ""
                } else {
                    downloadStatus.value = "Error: File not found after download"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                downloadStatus.value = "Error: ${e.message}"
            } finally {
                isDownloading.value = false
            }
        }
    }

    fun stopDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().destroyProcessById("current_download")
                downloadStatus.value = "Download stopped"
                isDownloading.value = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadDao.deleteAllDownloads()
        }
    }

    fun setDownloadFolder(uri: Uri?) {
        viewModelScope.launch {
            settingsRepository.saveDownloadFolderUri(uri)
        }
    }
}
