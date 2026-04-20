package com.shiftluna.ytmuzic.viewmodel

import android.app.Application
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
                request.addOption("-f", "ba")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--embed-metadata")
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
                request.addOption("--ppa", "ffmpeg: -c:v mjpeg -vf crop=ih:ih")
                request.addOption("-o", template)

                val response = YoutubeDL.getInstance().execute(request, "ytmuzic") { progress, etaInSeconds, _ ->
                    downloadProgress.value = progress / 100f
                    downloadStatus.value = "Downloading... ETA: ${etaInSeconds}s"
                }

                downloadStatus.value = "Processing metadata..."

                // Find the downloaded file in cache
                val downloadedFile = cacheDir.listFiles()?.firstOrNull { it.extension == "mp3" }
                if (downloadedFile != null) {
                    val title = downloadedFile.nameWithoutExtension
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
                    // Extract Video ID if possible, otherwise use url hash
                    val videoId = response.out?.let { 
                        // attempt to get ID if possible, for simplicity use URL hash or title
                        url.hashCode().toString() + "_" + System.currentTimeMillis()
                    } ?: url.hashCode().toString()

                    val item = DownloadItem(
                        videoId = videoId,
                        title = title,
                        filePath = finalPath,
                        thumbnailPath = "", // Embedded thumbnail, so no separate path needed unless extracted
                        fileSize = fileSize
                    )
                    downloadDao.insertDownload(item)

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

    fun setDownloadFolder(uri: Uri?) {
        viewModelScope.launch {
            settingsRepository.saveDownloadFolderUri(uri)
        }
    }
}
