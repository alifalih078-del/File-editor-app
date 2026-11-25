package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.configureAppCheck
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ResultPreviewScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.MunasikTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val viewModel: StudioViewModel = viewModel()
            val uiLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
            val layoutDirection = if (uiLanguage == AppLanguage.AR) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MunasikTheme {
                    MunasikAppRoot(viewModel = viewModel, uiLanguage = uiLanguage)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MunasikAppRoot(
    viewModel: StudioViewModel,
    uiLanguage: AppLanguage
) {
    val context = LocalContext.current
    val strings = remember(uiLanguage) { stringsFor(uiLanguage) }

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val inputMode by viewModel.inputMode.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val rawTextTitle by viewModel.rawTextTitle.collectAsStateWithLifecycle()
    val rawTextBody by viewModel.rawTextBody.collectAsStateWithLifecycle()
    val userInstruction by viewModel.userInstruction.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val outputFormat by viewModel.outputFormat.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val activeResult by viewModel.activeResult.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    // Zero-permission Storage Access Framework file picker for PDF & Word (.docx)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFilePicked(it) }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFileToUri(activeResult?.pdfFile, uri)
        }
    }

    val createDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    ) { uri ->
        if (uri != null) {
            viewModel.exportFileToUri(activeResult?.docxFile, uri)
        }
    }

    val currentResult = activeResult
    if (currentResult != null) {
        ResultPreviewScreen(
            result = currentResult,
            strings = strings,
            processingState = processingState,
            onClose = { viewModel.closeResultPreview() },
            onSavePdf = {
                val baseName = currentResult.originalFileName.substringBeforeLast(".")
                createPdfLauncher.launch("${baseName}_redesigned.pdf")
            },
            onSaveDocx = {
                val baseName = currentResult.originalFileName.substringBeforeLast(".")
                createDocxLauncher.launch("${baseName}_redesigned.docx")
            },
            onSharePdf = {
                viewModel.shareFile(context, currentResult.pdfFile, "application/pdf")
            },
            onShareDocx = {
                viewModel.shareFile(
                    context,
                    currentResult.docxFile,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
            },
            onRefineSubmit = { prompt ->
                viewModel.refineActiveResult(prompt)
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (currentTab == AppTab.STUDIO) strings.appTitleStudio else strings.appTitleHistory,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        OutlinedButton(
                            onClick = { viewModel.toggleLanguage() },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("language_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Switch language",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiLanguage.toggleLabel,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentTab == AppTab.STUDIO,
                            onClick = { viewModel.selectTab(AppTab.STUDIO) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.STUDIO) {
                                        Icons.Filled.AutoFixHigh
                                    } else {
                                        Icons.Outlined.AutoFixHigh
                                    },
                                    contentDescription = strings.navStudio
                                )
                            },
                            label = { Text(strings.navStudio) },
                            modifier = Modifier.testTag("nav_studio")
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.HISTORY,
                            onClick = { viewModel.selectTab(AppTab.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.HISTORY) {
                                        Icons.Filled.Folder
                                    } else {
                                        Icons.Outlined.Folder
                                    },
                                    contentDescription = strings.navHistory
                                )
                            },
                            label = { Text(strings.navHistory) },
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentTab == AppTab.STUDIO,
                            onClick = { viewModel.selectTab(AppTab.STUDIO) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.STUDIO) {
                                        Icons.Filled.AutoFixHigh
                                    } else {
                                        Icons.Outlined.AutoFixHigh
                                    },
                                    contentDescription = strings.navStudio
                                )
                            },
                            label = { Text(strings.navStudio) }
                        )
                        NavigationRailItem(
                            selected = currentTab == AppTab.HISTORY,
                            onClick = { viewModel.selectTab(AppTab.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.HISTORY) {
                                        Icons.Filled.Folder
                                    } else {
                                        Icons.Outlined.Folder
                                    },
                                    contentDescription = strings.navHistory
                                )
                            },
                            label = { Text(strings.navHistory) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        AppTab.STUDIO -> {
                            StudioScreen(
                                uiLanguage = uiLanguage,
                                strings = strings,
                                inputMode = inputMode,
                                selectedFile = selectedFile,
                                rawTextTitle = rawTextTitle,
                                rawTextBody = rawTextBody,
                                userInstruction = userInstruction,
                                selectedPreset = selectedPreset,
                                outputFormat = outputFormat,
                                processingState = processingState,
                                onSelectInputMode = viewModel::selectInputMode,
                                onRawTextTitleChange = viewModel::updateRawTextTitle,
                                onRawTextBodyChange = viewModel::updateRawTextBody,
                                onPickFileClick = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        )
                                    )
                                },
                                onInstructionChange = viewModel::updateInstruction,
                                onQuickSuggestionClick = viewModel::applyQuickSuggestion,
                                onPresetSelected = viewModel::selectPreset,
                                onOutputFormatSelected = viewModel::selectOutputFormat,
                                onStartRedesignClick = viewModel::startAutomaticRedesign,
                                onDismissError = viewModel::clearError
                            )
                        }
                        AppTab.HISTORY -> {
                            HistoryScreen(
                                items = historyList,
                                strings = strings,
                                onOpenItem = viewModel::openHistoryItem,
                                onDeleteItem = viewModel::deleteHistoryItem,
                                onGoToStudio = { viewModel.selectTab(AppTab.STUDIO) }
                            )
                        }
                    }
                }
            }
        }
    }
}
