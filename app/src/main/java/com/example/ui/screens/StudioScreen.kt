package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DesignPreset
import com.example.model.InputFileType
import com.example.model.OutputFormat
import com.example.model.SelectedSourceFile
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.viewmodel.InputMode
import com.example.viewmodel.ProcessingState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    uiLanguage: AppLanguage,
    strings: AppStrings,
    inputMode: InputMode,
    selectedFile: SelectedSourceFile?,
    rawTextTitle: String,
    rawTextBody: String,
    userInstruction: String,
    selectedPreset: DesignPreset,
    outputFormat: OutputFormat,
    processingState: ProcessingState,
    onSelectInputMode: (InputMode) -> Unit,
    onRawTextTitleChange: (String) -> Unit,
    onRawTextBodyChange: (String) -> Unit,
    onPickFileClick: () -> Unit,
    onInstructionChange: (String) -> Unit,
    onQuickSuggestionClick: (String) -> Unit,
    onPresetSelected: (DesignPreset) -> Unit,
    onOutputFormatSelected: (OutputFormat) -> Unit,
    onStartRedesignClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isRunning = processingState is ProcessingState.Running
    val canStart = if (inputMode == InputMode.FILE) selectedFile != null else rawTextBody.isNotBlank()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Studio Banner
            HeroStudioCard(strings = strings)

            // Error Alert Banner
            AnimatedVisibility(
                visible = processingState is ProcessingState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (processingState is ProcessingState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = processingState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onDismissError,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(strings.closeError)
                            }
                        }
                    }
                }
            }

            // Step 1: Pick PDF/Word File OR Paste Text
            StepSectionHeader(
                stepNumber = if (uiLanguage == AppLanguage.AR) "١" else "1",
                title = strings.step1Title,
                subtitle = strings.step1Subtitle
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = inputMode == InputMode.FILE,
                    onClick = { if (!isRunning) onSelectInputMode(InputMode.FILE) },
                    label = {
                        Text(
                            text = strings.modeUploadFile,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_upload_file")
                )
                FilterChip(
                    selected = inputMode == InputMode.PASTE_TEXT,
                    onClick = { if (!isRunning) onSelectInputMode(InputMode.PASTE_TEXT) },
                    label = {
                        Text(
                            text = strings.modePasteText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_paste_text")
                )
            }

            if (inputMode == InputMode.FILE) {
                FilePickerCard(
                    uiLanguage = uiLanguage,
                    strings = strings,
                    selectedFile = selectedFile,
                    enabled = !isRunning,
                    onPickFileClick = onPickFileClick
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = rawTextTitle,
                            onValueChange = onRawTextTitleChange,
                            enabled = !isRunning,
                            singleLine = true,
                            label = { Text(strings.pasteTitleLabel) },
                            placeholder = { Text(strings.pasteTitlePlaceholder) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("paste_title_input")
                        )
                        OutlinedTextField(
                            value = rawTextBody,
                            onValueChange = onRawTextBodyChange,
                            enabled = !isRunning,
                            minLines = 4,
                            maxLines = 8,
                            label = { Text(strings.pasteTextLabel) },
                            placeholder = { Text(strings.pasteTextPlaceholder) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("paste_body_input")
                        )
                    }
                }
            }

            // Step 2: Instructions & Quick Educational Actions
            StepSectionHeader(
                stepNumber = if (uiLanguage == AppLanguage.AR) "٢" else "2",
                title = strings.step2Title,
                subtitle = strings.step2Subtitle
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = userInstruction,
                        onValueChange = onInstructionChange,
                        enabled = !isRunning,
                        minLines = 3,
                        maxLines = 6,
                        label = { Text(strings.instructionLabel) },
                        placeholder = { Text(strings.instructionPlaceholder) },
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("instruction_input")
                    )

                    Text(
                        text = strings.quickSuggestionsTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        strings.quickSuggestions.forEachIndexed { idx, suggestion ->
                            AssistChip(
                                onClick = { if (!isRunning) onQuickSuggestionClick(suggestion) },
                                label = {
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("suggestion_chip_$idx")
                            )
                        }
                    }
                }
            }

            // Step 3: Design Theme Presets
            StepSectionHeader(
                stepNumber = if (uiLanguage == AppLanguage.AR) "٣" else "3",
                title = strings.step3Title,
                subtitle = strings.step3Subtitle
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DesignPreset.entries.forEach { preset ->
                    PresetCard(
                        uiLanguage = uiLanguage,
                        preset = preset,
                        selected = preset == selectedPreset,
                        enabled = !isRunning,
                        onClick = { onPresetSelected(preset) }
                    )
                }
            }

            // Output Format Selection
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.outputFormatTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutputFormat.entries.forEach { format ->
                            val isSelected = format == outputFormat
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (!isRunning) onOutputFormatSelected(format) },
                                label = {
                                    Text(
                                        text = if (uiLanguage == AppLanguage.AR) format.labelAr else format.labelEn,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("format_${format.name}")
                            )
                        }
                    }
                }
            }

            // Live Processing Stepper OR Primary Action Button
            if (processingState is ProcessingState.Running) {
                ProcessingProgressCard(strings = strings, state = processingState)
            } else {
                Button(
                    onClick = onStartRedesignClick,
                    enabled = canStart,
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("start_redesign_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.startRedesignButton,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroStudioCard(strings: AppStrings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(MaterialTheme.shapes.large)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = strings.heroTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xEB0F172A),
                            Color(0xBF0F172A),
                            Color(0x550F172A)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = Color(0x332DD4BF),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0x662DD4BF))
                ) {
                    Text(
                        text = strings.heroBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF99F6E4),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = strings.heroTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.heroSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StepSectionHeader(
    stepNumber: String,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilePickerCard(
    uiLanguage: AppLanguage,
    strings: AppStrings,
    selectedFile: SelectedSourceFile?,
    enabled: Boolean,
    onPickFileClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selectedFile != null) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            width = if (selectedFile != null) 1.5.dp else 1.dp,
            color = if (selectedFile != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onPickFileClick)
            .testTag("pick_file_card")
    ) {
        if (selectedFile == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = strings.pickFileTitle,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = strings.pickFileTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.pickFileSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedFile.pdfThumbnail != null) {
                    Image(
                        bitmap = selectedFile.pdfThumbnail.asImageBitmap(),
                        contentDescription = selectedFile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 58.dp, height = 76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp)
                            )
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 58.dp, height = 76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = if (selectedFile.fileType == InputFileType.PDF) {
                                Icons.Default.PictureAsPdf
                            } else {
                                Icons.Default.Description
                            },
                            contentDescription = selectedFile.fileType.displayLabel,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = selectedFile.fileType.displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = formatFileSize(selectedFile.sizeBytes, uiLanguage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = selectedFile.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedFile.extractedPreview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onPickFileClick,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("change_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = strings.changeFileDesc,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    uiLanguage: AppLanguage,
    preset: DesignPreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = Color(android.graphics.Color.parseColor(preset.primaryHex))
    val accentColor = Color(android.graphics.Color.parseColor(preset.accentHex))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .width(164.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("preset_${preset.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = if (uiLanguage == AppLanguage.AR) preset.titleAr else preset.titleEn,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (uiLanguage == AppLanguage.AR) preset.subtitleAr else preset.subtitleEn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProcessingProgressCard(strings: AppStrings, state: ProcessingState.Running) {
    val progress = ((state.stepIndex + 1) / 3f).coerceIn(0.15f, 1f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.workingTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.stepMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatFileSize(bytes: Long, uiLanguage: AppLanguage): String {
    if (bytes <= 0) return if (uiLanguage == AppLanguage.AR) "جاهز" else "Ready"
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        if (uiLanguage == AppLanguage.AR) "${kb.toInt()} كيلوبايت" else "${kb.toInt()} KB"
    } else {
        if (uiLanguage == AppLanguage.AR) {
            String.format("%.1f ميجابايت", kb / 1024.0)
        } else {
            String.format("%.1f MB", kb / 1024.0)
        }
    }
}
