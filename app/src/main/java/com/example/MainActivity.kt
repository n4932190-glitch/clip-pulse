package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.firebase.configureAppCheck
import com.example.media.ShareHelper
import com.example.ui.ClipPulseViewModel
import com.example.ui.screens.ClipEditorScreen
import com.example.ui.screens.ExportProgressSheet
import com.example.ui.screens.SavedClipsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.ClipPulseTheme

const val ROUTE_STUDIO = "studio"
const val ROUTE_EDITOR = "editor"
const val ROUTE_SAVED = "saved"

data class ClipNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val Destinations = listOf(
    ClipNavDestination(
        route = ROUTE_STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    ClipNavDestination(
        route = ROUTE_EDITOR,
        labelRes = R.string.nav_editor,
        selectedIcon = Icons.Filled.ContentCut,
        unselectedIcon = Icons.Outlined.ContentCut
    ),
    ClipNavDestination(
        route = ROUTE_SAVED,
        labelRes = R.string.nav_saved,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary
    )
)

class MainActivity : ComponentActivity() {

    private val viewModel: ClipPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        handleIncomingShareIntent(intent)

        setContent {
            ClipPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClipPulseApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != Intent.ACTION_SEND) return
        val type = incomingIntent.type ?: return
        if (type.startsWith("text/")) {
            val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.extractFromLink(sharedText)
            }
        } else if (type.startsWith("video/")) {
            val videoUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (videoUri != null) {
                viewModel.loadDeviceVideo(videoUri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipPulseApp(viewModel: ClipPulseViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedClips by viewModel.savedClips.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(uiState.navigateToEditorTrigger) {
        if (uiState.navigateToEditorTrigger > 0) {
            navigateTo(ROUTE_EDITOR)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        savedCount = savedClips.size,
                                        hasActiveClip = uiState.activeClip != null
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        savedCount = savedClips.size,
                                        hasActiveClip = uiState.activeClip != null
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_STUDIO,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_STUDIO) {
                        StudioScreen(
                            uiState = uiState,
                            onUrlChanged = viewModel::updateUrlInput,
                            onDurationSelected = viewModel::selectDuration,
                            onExtractLinkClicked = { viewModel.extractFromLink() },
                            onDeviceVideoPicked = { uri -> viewModel.loadDeviceVideo(uri) },
                            onDismissError = viewModel::clearPipelineError,
                            onOpenEditor = { navigateTo(ROUTE_EDITOR) }
                        )
                    }

                    composable(ROUTE_EDITOR) {
                        ClipEditorScreen(
                            uiState = uiState,
                            onSelectClip = viewModel::selectClipIndex,
                            onSelectDuration = viewModel::selectDuration,
                            onSelectFraming = viewModel::selectFramingMode,
                            onSelectSubtitleStyle = viewModel::selectSubtitleStyle,
                            onSelectSubtitlePosition = viewModel::selectSubtitlePosition,
                            onSelectSubtitleSize = viewModel::selectSubtitleSize,
                            onEditSubtitleCue = viewModel::updateSubtitleCueText,
                            onAttachLocalVideo = { uri ->
                                viewModel.loadDeviceVideo(uri, attachToCurrentProjectOnly = true)
                            },
                            onSaveToDevice = viewModel::saveClipToDevice,
                            onGoToStudio = { navigateTo(ROUTE_STUDIO) }
                        )
                    }

                    composable(ROUTE_SAVED) {
                        SavedClipsScreen(
                            savedClips = savedClips,
                            onDeleteClip = viewModel::deleteSavedClip,
                            onGoToStudio = { navigateTo(ROUTE_STUDIO) }
                        )
                    }
                }
            }
        }
    }

    ExportProgressSheet(
        exportState = uiState.exportState,
        onDismiss = viewModel::dismissExportSheet,
        onShareTikTok = { uri ->
            ShareHelper.shareClip(
                context = context,
                videoUri = uri,
                targetPackage = ShareHelper.findInstalledTikTokPackage(context),
                title = uiState.activeClip?.title ?: "ClipPulse Viral Short"
            )
        },
        onShareShorts = { uri ->
            ShareHelper.shareClip(
                context = context,
                videoUri = uri,
                targetPackage = ShareHelper.YOUTUBE_PACKAGE,
                title = uiState.activeClip?.title ?: "ClipPulse Viral Short"
            )
        },
        onShareReels = { uri ->
            ShareHelper.shareClip(
                context = context,
                videoUri = uri,
                targetPackage = ShareHelper.INSTAGRAM_PACKAGE,
                title = uiState.activeClip?.title ?: "ClipPulse Viral Short"
            )
        },
        onShareMore = { uri ->
            ShareHelper.shareClip(
                context = context,
                videoUri = uri,
                title = uiState.activeClip?.title ?: "ClipPulse Viral Short"
            )
        }
    )
}

@Composable
private fun DestinationIcon(
    dest: ClipNavDestination,
    selected: Boolean,
    savedCount: Int,
    hasActiveClip: Boolean
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    when {
        dest.route == ROUTE_SAVED && savedCount > 0 -> {
            BadgedBox(badge = { Badge { Text("$savedCount") } }) {
                Icon(icon, contentDescription = stringResource(dest.labelRes))
            }
        }
        dest.route == ROUTE_EDITOR && hasActiveClip -> {
            BadgedBox(badge = { Badge() }) {
                Icon(icon, contentDescription = stringResource(dest.labelRes))
            }
        }
        else -> {
            Icon(icon, contentDescription = stringResource(dest.labelRes))
        }
    }
}
