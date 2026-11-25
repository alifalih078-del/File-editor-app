package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.engine.InlineSpanParser
import com.example.engine.PdfInspector
import com.example.model.DocBlock
import com.example.model.RedesignedDocument
import com.example.ui.i18n.AppStrings
import com.example.viewmodel.ProcessingState
import com.example.viewmodel.RedesignedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultPreviewScreen(
    result: RedesignedResult,
    strings: AppStrings,
    processingState: ProcessingState,
    onClose: () -> Unit,
    onSavePdf: () -> Unit,
    onSaveDocx: () -> Unit,
    onSharePdf: () -> Unit,
    onShareDocx: () -> Unit,
    onRefineSubmit: (String) -> Unit
) {
    var isFullscreen by remember { mutableStateOf(false) }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onClose()
        }
    }

    var currentPage by remember(result) { mutableIntStateOf(0) }
    var pageBitmap by remember(result) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember(result) { mutableStateOf(false) }
    var zoomScale by remember(result) { mutableFloatStateOf(1f) }
    var showRefineSheet by remember { mutableStateOf(false) }
    var refineText by remember { mutableStateOf("") }

    val primaryColor = runCatching {
        Color(android.graphics.Color.parseColor(result.document.primaryColorHex))
    }.getOrDefault(MaterialTheme.colorScheme.primary)
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(result.document.accentColorHex))
    }.getOrDefault(MaterialTheme.colorScheme.secondary)

    LaunchedEffect(result, currentPage) {
        val pdfFile = result.pdfFile
        if (pdfFile != null && pdfFile.exists()) {
            isLoadingPage = true
            pageBitmap = withContext(Dispatchers.IO) {
                PdfInspector.renderPdfPage(pdfFile, currentPage, targetWidthPx = 1190)
            }
            isLoadingPage = false
        } else {
            pageBitmap = null
        }
    }

    Scaffold(
        contentWindowInsets = if (isFullscreen) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = result.document.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = result.document.designThemeName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("preview_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.previewBackDesc
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isFullscreen = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("preview_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = strings.previewFullscreenDesc
                            )
                        }
                        if (result.pdfFile != null) {
                            IconButton(
                                onClick = { zoomScale = if (zoomScale > 1.05f) 1f else 1.75f },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (zoomScale > 1.05f) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                                    contentDescription = strings.previewZoomDesc
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (result.pdfFile != null) {
                                Button(
                                    onClick = onSavePdf,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("save_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.savePdf, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (result.docxFile != null) {
                                FilledTonalButton(
                                    onClick = onSaveDocx,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("save_docx_button")
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.saveWord, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (result.pdfFile != null) onSharePdf() else onShareDocx()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_file_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.share, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { showRefineSheet = true },
                                enabled = processingState !is ProcessingState.Running,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("refine_more_button")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.refineMore, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(if (isFullscreen) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (processingState is ProcessingState.Running) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = processingState.stepMessage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // AI Design Summary Card (hidden in full-screen reading mode)
                if (!isFullscreen) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Text(
                                    text = strings.previewAiSummaryTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = result.document.changeSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Visual PDF Page Preview OR Structured Document Preview
                if (result.pdfFile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.previewFinalFileTitle,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = strings.prevPageDesc
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = strings.previewPageIndicator(currentPage + 1, result.pageCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            IconButton(
                                onClick = { if (currentPage < result.pageCount - 1) currentPage++ },
                                enabled = currentPage < result.pageCount - 1,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = strings.nextPageDesc
                                )
                            }
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(595f / 842f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.White)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.medium
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 2.5f)
                                }
                            }
                    ) {
                        val bmp = pageBitmap
                        if (isLoadingPage || bmp == null) {
                            CircularProgressIndicator()
                        } else {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = strings.previewPageIndicator(currentPage + 1, result.pageCount),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale
                                    )
                            )
                        }
                    }
                } else {
                    // Structured preview for DOCX-only output
                    StructuredDocPreviewCard(
                        document = result.document,
                        primaryColor = primaryColor,
                        accentColor = accentColor
                    )
                }

                Spacer(modifier = Modifier.height(if (isFullscreen) 64.dp else 12.dp))
            }

            if (isFullscreen) {
                FilledTonalButton(
                    onClick = { isFullscreen = false },
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("exit_fullscreen_button")
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.exitFullscreen, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showRefineSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRefineSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.refineSheetTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = refineText,
                    onValueChange = { refineText = it },
                    minLines = 3,
                    maxLines = 5,
                    label = { Text(strings.refineInputLabel) },
                    placeholder = { Text(strings.refineInputPlaceholder) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refine_input")
                )
                Button(
                    onClick = {
                        showRefineSheet = false
                        onRefineSubmit(refineText)
                        refineText = ""
                    },
                    enabled = refineText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("submit_refine_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.refineSubmitButton)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun rememberStyledText(
    rawText: String,
    primaryColor: Color,
    accentColor: Color
): AnnotatedString {
    return remember(rawText, primaryColor, accentColor) {
        val segments = InlineSpanParser.parse(rawText)
        buildAnnotatedString {
            segments.forEach { seg ->
                when {
                    seg.isHighlight -> {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                background = accentColor.copy(alpha = 0.20f)
                            )
                        ) {
                            append(seg.text)
                        }
                    }
                    seg.isBold -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(seg.text)
                        }
                    }
                    else -> append(seg.text)
                }
            }
        }
    }
}

@Composable
private fun StructuredDocPreviewCard(
    document: RedesignedDocument,
    primaryColor: Color,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryColor)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    document.headerBadge?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    document.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            document.blocks.forEach { block ->
                when (block) {
                    is DocBlock.Heading -> {
                        Text(
                            text = rememberStyledText(block.text, primaryColor, accentColor),
                            style = if (block.level <= 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                            color = if (block.level <= 1) primaryColor else accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is DocBlock.Paragraph -> {
                        Text(
                            text = rememberStyledText(block.text, primaryColor, accentColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1E293B),
                            fontWeight = if (block.emphasis) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    is DocBlock.BulletList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.items.forEachIndexed { idx, item ->
                                val prefix = if (block.numbered) "${idx + 1}." else "•"
                                Text(
                                    text = rememberStyledText("$prefix $item", primaryColor, accentColor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                    is DocBlock.Callout -> {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (block.title.isNotBlank()) {
                                    Text(
                                        text = rememberStyledText(block.title, primaryColor, accentColor),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = primaryColor
                                    )
                                }
                                if (block.body.isNotBlank()) {
                                    Text(
                                        text = rememberStyledText(block.body, primaryColor, accentColor),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }
                    is DocBlock.MetricsGrid -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            block.items.take(3).forEach { m ->
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(m.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                        Text(m.value, style = MaterialTheme.typography.titleSmall, color = primaryColor)
                                    }
                                }
                            }
                        }
                    }
                    is DocBlock.TableBlock -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                        ) {
                            if (block.headers.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(primaryColor)
                                        .padding(8.dp)
                                ) {
                                    block.headers.forEach { h ->
                                        Text(
                                            text = h,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            block.rows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (idx % 2 == 0) Color.White else Color(0xFFF8FAFC))
                                        .padding(8.dp)
                                ) {
                                    row.forEach { cell ->
                                        Text(
                                            text = rememberStyledText(cell, primaryColor, accentColor),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is DocBlock.BilingualList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            block.title?.takeIf { it.isNotBlank() }?.let { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(primaryColor)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "English",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "المعنى / الترجمة",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    block.entries.forEachIndexed { idx, entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (idx % 2 == 0) Color.White else Color(0xFFF8FAFC))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = rememberStyledText(entry.english, primaryColor, accentColor),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF1E293B)
                                                )
                                                entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                                                    Text(
                                                        text = note,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = accentColor
                                                    )
                                                }
                                            }
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                Text(
                                                    text = rememberStyledText(entry.arabic, primaryColor, accentColor),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF1E293B),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DocBlock.Divider -> HorizontalDivider(color = Color(0xFFE2E8F0))
                }
            }
        }
    }
}
