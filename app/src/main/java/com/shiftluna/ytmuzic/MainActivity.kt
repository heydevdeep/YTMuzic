package com.shiftluna.ytmuzic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shiftluna.ytmuzic.ui.AboutScreen
import com.shiftluna.ytmuzic.ui.DownloadsScreen
import com.shiftluna.ytmuzic.ui.theme.YTMuzicTheme
import com.shiftluna.ytmuzic.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: DownloadViewModel by viewModels()

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setDownloadFolder(uri)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle initial SHARE intent if any
        handleIntent(intent)

        setContent {
            YTMuzicTheme {
                val navController = rememberNavController()
                var showSettingsSheet by remember { mutableStateOf(false) }

                if (showSettingsSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSettingsSheet = false }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Settings", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            ListItem(
                                headlineContent = { Text("Download Folder") },
                                supportingContent = {
                                    val uri by viewModel.downloadFolderUri.collectAsState()
                                    Text(uri?.toString() ?: "Default (Downloads)")
                                },
                                trailingContent = {
                                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                        Icon(Icons.Default.Folder, contentDescription = "Pick Folder")
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            
                            Text("Storage", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = { 
                                    viewModel.clearHistory()
                                    showSettingsSheet = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear Download History")
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("YTMuzic DL") },
                            actions = {
                                IconButton(onClick = { showSettingsSheet = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                                label = { Text("Downloads") },
                                selected = currentDestination?.hierarchy?.any { it.route == "downloads" } == true,
                                onClick = {
                                    navController.navigate("downloads") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                label = { Text("About") },
                                selected = currentDestination?.hierarchy?.any { it.route == "about" } == true,
                                onClick = {
                                    navController.navigate("about") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "downloads",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("downloads") {
                            DownloadsScreen(viewModel)
                        }
                        composable("about") {
                            AboutScreen()
                        }
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                viewModel.urlInput.value = sharedText
                viewModel.startDownload(sharedText)
            }
        }
    }
}