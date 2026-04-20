# Product Requirements Document (PRD): YT Muzic Downloader

## 1. Project Overview

A minimal, Material 3-based Android application designed to download YouTube Music tracks with high-fidelity metadata (ID3 tags) and perfectly square, center-cropped album art.

## 2. Target Features

- **One-Click Download:** Intercept YouTube share links or paste manually to trigger a download.
- **Metadata Mastery:** Automatic embedding of Title, Artist, Album, and Year.
- **Visual Fidelity:** Forced center-crop of 16:9 YouTube thumbnails into 1:1 squares using FFmpeg.
- **Persistent History:** A local database (Room) to track downloads and allow one-tap re-downloads.
- **Custom Download Folder:** User-selectable destination folder via SAF (Storage Access Framework). Defaults to the system `Downloads` directory. Persisted across sessions via `DataStore`.
- **Material 3 UI:** Dynamic colors, rounded corners, and a clean two-tab navigation.

## 3. Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Engine:** `youtubedl-android` (wrapper for `yt-dlp`)
- **Processing:** FFmpeg (for thumbnail cropping and metadata injection)
- **Database:** Room (Local SQL storage)
- **Image Loading:** Coil (for thumbnail rendering in lists)

---

# Implementation Blueprint

### 1. The Core Download Command

The app must execute the following logic via the `youtubedl-android` library to ensure metadata and square art:

```bash
yt-dlp -f 'ba' -x --audio-format mp3 --embed-metadata --embed-thumbnail --convert-thumbnails jpg --ppa "ffmpeg: -c:v mjpeg -vf crop=ih:ih" [URL]
```

### 2. Data Layer (Room Entity)

```kotlin
@Entity(tableName = "download_history")
data class DownloadItem(
    @PrimaryKey val videoId: String,
    val title: String,
    val filePath: String,
    val thumbnailPath: String,
    val fileSize: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

### 3. Application Permissions & Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

---

### 4. UI Structure (Jetpack Compose)

#### **Screen A: Downloads (Main)**

- **Top Bar:** A `Column` containing:
    - `OutlinedTextField` for the URL.
    - `TrailingIcon`: IconButton (ContentPaste) to grab from clipboard.
    - `Row`: "Download" `ElevatedButton` to start the process.
- **List Section:** A `LazyColumn` of `DownloadItem` cards.
    - **Thumbnail:** `AsyncImage` with a `clip(RoundedCornerShape(8.dp))` and fixed `size(60.dp)`.
    - **Text:** Title (Max 1 line, Ellipsis) + Subtext (File size).
    - **Action:** `IconButton` (Re-download) on the right edge.

#### **Screen B: About**

- **Layout:** Centered `Column`.
- **Content:** App Icon -> App Name (Aegis DL) -> Version String -> Developer Credit.

#### **Settings Section (on Downloads screen or dedicated Settings sheet)**

- **Download Folder Picker:**
    - `ListItem` / `Row` labeled "Download Folder".
    - Subtext shows current path (default: `Downloads`).
    - Trailing `IconButton` (Folder) launches `ActivityResultContracts.OpenDocumentTree` (SAF).
    - Selected `Uri` persisted via `DataStore<Preferences>` under key `download_folder_uri`.
    - Call `contentResolver.takePersistableUriPermission(...)` on pick to retain access across reboots.
    - "Reset to Default" option clears the stored `Uri`, falling back to `Environment.DIRECTORY_DOWNLOADS`.

---

### 5. Implementation Steps for the LLM

1.  **Project Setup:** Add `youtubedl-android`, `ffmpeg`, `Room`, and `androidx.datastore:datastore-preferences` dependencies to `build.gradle.kts`.
2.  **Engine Init:** Initialize `YoutubeDL.getInstance().init(context)` in a custom `Application` class.
3.  **UI Scaffolding:** Create a `Scaffold` with a `BottomAppBar` containing the two navigation items.
4.  **Settings Store:** Create `SettingsRepository` backed by `DataStore` exposing `downloadFolderUri: Flow<Uri?>`. Default to `null` → resolve to `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` at call site.
5.  **Folder Picker:** Wire `rememberLauncherForActivityResult(OpenDocumentTree())` in the settings UI. On result, persist URI + call `takePersistableUriPermission`.
6.  **Download Logic:** Create a `ViewModel` that handles the download state (Idle, Downloading, Finished). Inject chosen folder `Uri` into `YoutubeDLRequest` via `-o` / `--paths` pointing at the resolved absolute path (or copy from cache dir to `DocumentFile.fromTreeUri(...)` post-download for SAF URIs).
7.  **Thumbnail Magic:** Ensure the `--ppa` argument for FFmpeg cropping is included in every `YoutubeDLRequest`.
8.  **Persistence:** Upon a successful `onFinish`, insert the file details into the Room DB.

---

### Comparison of User Flow

| Action         | Current "Bad" Way                                        | YTMuzic DL Way                                  |
| :------------- | :------------------------------------------------------- | :---------------------------------------------- |
| **Share Link** | Copy -> Open Browser -> Paste -> Wait for Ad -> Download | Share -> YTMuzic DL -> Automatic Processing     |
| **Album Art**  | Stretched or 16:9 with bars                              | Perfectly center-cropped 1:1 Square             |
| **Management** | Lost in "Downloads" folder                               | Visible in-app with size and re-download option |
