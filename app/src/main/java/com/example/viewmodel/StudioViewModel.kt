package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.DocumentRedesignRepository
import com.example.data.local.AppDatabase
import com.example.data.local.RedesignHistoryEntity
import com.example.engine.DocxDocumentGenerator
import com.example.engine.DocxParser
import com.example.engine.PdfDocumentGenerator
import com.example.engine.PdfInspector
import com.example.model.DesignPreset
import com.example.model.InputFileType
import com.example.model.OutputFormat
import com.example.model.RedesignedDocument
import com.example.model.SelectedSourceFile
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "StudioViewModel"
private const val PREFS_NAME = "munasik_prefs"
private const val KEY_LANGUAGE = "app_language"

enum class AppTab {
    STUDIO,
    HISTORY
}

enum class InputMode {
    FILE,
    PASTE_TEXT
}

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Running(val stepIndex: Int, val stepMessage: String) : ProcessingState
    data class Error(val message: String) : ProcessingState
}

data class RedesignedResult(
    val historyId: Long,
    val originalFileName: String,
    val document: RedesignedDocument,
    val pdfFile: File?,
    val docxFile: File?,
    val pageCount: Int
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = AppDatabase.getInstance(application).redesignHistoryDao()
    private val aiRepository = DocumentRedesignRepository()

    val historyList: StateFlow<List<RedesignHistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _uiLanguage = MutableStateFlow(loadSavedLanguage())
    val uiLanguage: StateFlow<AppLanguage> = _uiLanguage.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.STUDIO)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _inputMode = MutableStateFlow(InputMode.FILE)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _selectedFile = MutableStateFlow<SelectedSourceFile?>(null)
    val selectedFile: StateFlow<SelectedSourceFile?> = _selectedFile.asStateFlow()

    private val _rawTextTitle = MutableStateFlow("")
    val rawTextTitle: StateFlow<String> = _rawTextTitle.asStateFlow()

    private val _rawTextBody = MutableStateFlow("")
    val rawTextBody: StateFlow<String> = _rawTextBody.asStateFlow()

    private val _userInstruction = MutableStateFlow("")
    val userInstruction: StateFlow<String> = _userInstruction.asStateFlow()

    private val _selectedPreset = MutableStateFlow(DesignPreset.BILINGUAL_EDU)
    val selectedPreset: StateFlow<DesignPreset> = _selectedPreset.asStateFlow()

    private val _outputFormat = MutableStateFlow(OutputFormat.PDF_AND_DOCX)
    val outputFormat: StateFlow<OutputFormat> = _outputFormat.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _activeResult = MutableStateFlow<RedesignedResult?>(null)
    val activeResult: StateFlow<RedesignedResult?> = _activeResult.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private fun loadSavedLanguage(): AppLanguage {
        val code = prefs.getString(KEY_LANGUAGE, AppLanguage.AR.code)
        return if (code == AppLanguage.EN.code) AppLanguage.EN else AppLanguage.AR
    }

    fun toggleLanguage() {
        val next = if (_uiLanguage.value == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
        _uiLanguage.value = next
        prefs.edit().putString(KEY_LANGUAGE, next.code).apply()
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectInputMode(mode: InputMode) {
        _inputMode.value = mode
    }

    fun updateRawTextTitle(title: String) {
        _rawTextTitle.value = title
    }

    fun updateRawTextBody(body: String) {
        _rawTextBody.value = body
    }

    fun updateInstruction(text: String) {
        _userInstruction.value = text
    }

    fun applyQuickSuggestion(suggestion: String) {
        val current = _userInstruction.value.trim()
        _userInstruction.value = if (current.isEmpty()) {
            suggestion
        } else if (!current.contains(suggestion)) {
            "$current\n$suggestion"
        } else {
            current
        }
    }

    fun selectPreset(preset: DesignPreset) {
        _selectedPreset.value = preset
    }

    fun selectOutputFormat(format: OutputFormat) {
        _outputFormat.value = format
    }

    fun clearError() {
        if (_processingState.value is ProcessingState.Error) {
            _processingState.value = ProcessingState.Idle
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun closeResultPreview() {
        _activeResult.value = null
    }

    fun onFilePicked(uri: Uri) {
        val context = getApplication<Application>()
        val strings = stringsFor(_uiLanguage.value)
        viewModelScope.launch {
            _processingState.value = ProcessingState.Running(0, strings.stepInspectingFile)
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Not all providers support persistable permissions; safe to ignore
                }

                val meta = withContext(Dispatchers.IO) {
                    readFileMetadataAndInspect(context, uri)
                }
                _selectedFile.value = meta
                _inputMode.value = InputMode.FILE
                _processingState.value = ProcessingState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect picked file", e)
                _processingState.value = ProcessingState.Error(
                    e.localizedMessage ?: "تعذر قراءة الملف المختار. تأكد من أنه PDF أو Word (.docx) صالح."
                )
            }
        }
    }

    fun startAutomaticRedesign() {
        val lang = _uiLanguage.value
        val strings = stringsFor(lang)
        val mode = _inputMode.value
        val source = _selectedFile.value
        val pastedBody = _rawTextBody.value.trim()
        val pastedTitle = _rawTextTitle.value.trim()

        if (mode == InputMode.FILE && source == null) {
            _snackbarMessage.value = strings.snackPickFileOrText
            return
        }
        if (mode == InputMode.PASTE_TEXT && pastedBody.isBlank()) {
            _snackbarMessage.value = strings.snackPickFileOrText
            return
        }

        val context = getApplication<Application>()
        val instruction = _userInstruction.value.trim()
        val preset = _selectedPreset.value
        val format = _outputFormat.value

        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Running(0, strings.stepAnalyzingFile)
                val redesignedDoc: RedesignedDocument
                val displayFileName: String
                val inputLabel: String

                if (mode == InputMode.PASTE_TEXT) {
                    displayFileName = pastedTitle.ifBlank {
                        if (lang == AppLanguage.AR) "مستند_نصي.docx" else "study_notes.docx"
                    }
                    inputLabel = if (lang == AppLanguage.AR) "نص مباشر" else "Text"
                    _processingState.value = ProcessingState.Running(1, strings.stepAiRedesigning)
                    redesignedDoc = aiRepository.redesignRawText(
                        rawText = pastedBody,
                        documentTitleHint = pastedTitle,
                        userInstruction = instruction,
                        preset = preset,
                        uiLanguage = lang
                    )
                } else {
                    val fileSource = source!!
                    displayFileName = fileSource.displayName
                    inputLabel = fileSource.fileType.displayLabel
                    redesignedDoc = when (fileSource.fileType) {
                        InputFileType.PDF -> {
                            val pdfBytes = withContext(Dispatchers.IO) {
                                PdfInspector.inspectPdfUri(context, Uri.parse(fileSource.uriString)).bytes
                            }
                            _processingState.value = ProcessingState.Running(1, strings.stepAiRedesigning)
                            aiRepository.redesignPdf(
                                pdfBytes = pdfBytes,
                                fileName = fileSource.displayName,
                                userInstruction = instruction,
                                preset = preset,
                                uiLanguage = lang
                            )
                        }
                        InputFileType.DOCX -> {
                            val text = fileSource.extractedStructuredText
                                ?: withContext(Dispatchers.IO) {
                                    DocxParser.parseFromUri(context, Uri.parse(fileSource.uriString)).fullStructuredText
                                }
                            _processingState.value = ProcessingState.Running(1, strings.stepAiRedesigning)
                            aiRepository.redesignDocxText(
                                structuredDocxText = text,
                                fileName = fileSource.displayName,
                                userInstruction = instruction,
                                preset = preset,
                                uiLanguage = lang
                            )
                        }
                    }
                }

                _processingState.value = ProcessingState.Running(2, strings.stepGeneratingOutputs)

                val result = withContext(Dispatchers.IO) {
                    buildAndPersistOutputs(
                        context = context,
                        originalFileName = displayFileName,
                        inputType = inputLabel,
                        userInstruction = instruction.ifBlank { strings.defaultInstructionFallback },
                        document = redesignedDoc,
                        format = format
                    )
                }

                _processingState.value = ProcessingState.Idle
                _activeResult.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Automatic redesign failed", e)
                _processingState.value = ProcessingState.Error(
                    e.localizedMessage ?: "حدث خطأ أثناء إعادة تصميم الملف. يرجى المحاولة مرة أخرى."
                )
            }
        }
    }

    fun refineActiveResult(refinementPrompt: String) {
        val current = _activeResult.value ?: return
        val trimmed = refinementPrompt.trim()
        if (trimmed.isBlank()) return

        val context = getApplication<Application>()
        val format = _outputFormat.value
        val lang = _uiLanguage.value
        val strings = stringsFor(lang)

        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Running(1, strings.stepApplyingRefinement)
                val updatedDoc = aiRepository.refineDocument(
                    currentDocument = current.document,
                    refinementInstruction = trimmed,
                    uiLanguage = lang
                )

                _processingState.value = ProcessingState.Running(2, strings.stepUpdatingExports)
                val updatedResult = withContext(Dispatchers.IO) {
                    buildAndPersistOutputs(
                        context = context,
                        originalFileName = current.originalFileName,
                        inputType = if (lang == AppLanguage.AR) "تعديل" else "Refined",
                        userInstruction = trimmed,
                        document = updatedDoc,
                        format = format
                    )
                }
                _processingState.value = ProcessingState.Idle
                _activeResult.value = updatedResult
                _snackbarMessage.value = strings.snackRefinedSuccess
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Refine failed", e)
                _processingState.value = ProcessingState.Idle
                _snackbarMessage.value = e.localizedMessage ?: "Failed to apply refinement."
            }
        }
    }

    fun openHistoryItem(entity: RedesignHistoryEntity) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val doc = RedesignedDocument.fromJsonString(entity.documentJson)
                val pdfFile = entity.pdfFilePath?.let { File(it) }?.takeIf { it.exists() }
                    ?: withContext(Dispatchers.IO) {
                        val fallback = File(outputDir(context), "redesign_${entity.id}.pdf")
                        PdfDocumentGenerator.generatePdf(context, doc, fallback)
                        fallback
                    }
                val docxFile = entity.docxFilePath?.let { File(it) }?.takeIf { it.exists() }
                val pages = withContext(Dispatchers.IO) {
                    PdfInspector.getPdfPageCount(pdfFile).coerceAtLeast(entity.pageCount)
                }
                _activeResult.value = RedesignedResult(
                    historyId = entity.id,
                    originalFileName = entity.originalFileName,
                    document = doc,
                    pdfFile = pdfFile,
                    docxFile = docxFile,
                    pageCount = pages
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open history item", e)
                _snackbarMessage.value = if (_uiLanguage.value == AppLanguage.AR) {
                    "تعذر فتح المستند المحفوظ"
                } else {
                    "Could not open saved document"
                }
            }
        }
    }

    fun deleteHistoryItem(entity: RedesignHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            entity.pdfFilePath?.let { runCatching { File(it).delete() } }
            entity.docxFilePath?.let { runCatching { File(it).delete() } }
            dao.deleteById(entity.id)
        }
    }

    fun exportFileToUri(sourceFile: File?, destinationUri: Uri) {
        val strings = stringsFor(_uiLanguage.value)
        if (sourceFile == null || !sourceFile.exists()) {
            _snackbarMessage.value = "File unavailable for export"
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Unable to write to destination")
                }
                _snackbarMessage.value = strings.snackSavedSuccess
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _snackbarMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun shareFile(context: Context, file: File?, mimeType: String) {
        if (file == null || !file.exists()) {
            _snackbarMessage.value = "File unavailable to share"
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserTitle = if (_uiLanguage.value == AppLanguage.AR) {
                "مشاركة الملف المعاد تصميمه"
            } else {
                "Share Redesigned Document"
            }
            context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
            _snackbarMessage.value = "Could not open share sheet"
        }
    }

    private fun buildAndPersistOutputs(
        context: Context,
        originalFileName: String,
        inputType: String,
        userInstruction: String,
        document: RedesignedDocument,
        format: OutputFormat
    ): RedesignedResult {
        val dir = outputDir(context)
        val slug = UUID.randomUUID().toString().take(8)

        var pdfFile: File? = null
        var pageCount = 1
        if (format == OutputFormat.PDF_ONLY || format == OutputFormat.PDF_AND_DOCX) {
            pdfFile = File(dir, "munasik_$slug.pdf")
            pageCount = PdfDocumentGenerator.generatePdf(context, document, pdfFile)
        }

        var docxFile: File? = null
        if (format == OutputFormat.DOCX_ONLY || format == OutputFormat.PDF_AND_DOCX) {
            docxFile = File(dir, "munasik_$slug.docx")
            DocxDocumentGenerator.generateDocx(document, docxFile)
        }

        val entity = RedesignHistoryEntity(
            originalFileName = originalFileName,
            inputType = inputType,
            redesignedTitle = document.title,
            userInstruction = userInstruction,
            designThemeName = document.designThemeName,
            changeSummary = document.changeSummary,
            primaryColorHex = document.primaryColorHex,
            accentColorHex = document.accentColorHex,
            pdfFilePath = pdfFile?.absolutePath,
            docxFilePath = docxFile?.absolutePath,
            pageCount = pageCount,
            documentJson = document.toJsonString(),
            createdAtMillis = System.currentTimeMillis()
        )

        val insertedId = kotlinx.coroutines.runBlocking { dao.insert(entity) }

        return RedesignedResult(
            historyId = insertedId,
            originalFileName = originalFileName,
            document = document,
            pdfFile = pdfFile,
            docxFile = docxFile,
            pageCount = pageCount
        )
    }

    private fun outputDir(context: Context): File {
        return File(context.filesDir, "redesigned_docs").apply { mkdirs() }
    }

    private fun readFileMetadataAndInspect(context: Context, uri: Uri): SelectedSourceFile {
        var displayName = "document"
        var sizeBytes = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
            }
        }

        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
        val lowerName = displayName.lowercase()

        if (lowerName.endsWith(".doc") && !lowerName.endsWith(".docx")) {
            throw IllegalArgumentException("صيغة .doc القديمة غير مدعومة مباشرة. يرجى اختيار ملف .docx أو PDF.")
        }

        val fileType = when {
            mimeType.contains("pdf") || lowerName.endsWith(".pdf") -> InputFileType.PDF
            mimeType.contains("wordprocessingml") || lowerName.endsWith(".docx") -> InputFileType.DOCX
            else -> throw IllegalArgumentException("يرجى اختيار ملف PDF أو Word (.docx) فقط.")
        }

        return when (fileType) {
            InputFileType.PDF -> {
                val info = PdfInspector.inspectPdfUri(context, uri)
                val previewMsg = if (_uiLanguage.value == AppLanguage.AR) {
                    "ملف PDF جاهز للتحليل وإعادة التصميم (${info.pageCount} صفحة)"
                } else {
                    "PDF ready for analysis & redesign (${info.pageCount} pages)"
                }
                SelectedSourceFile(
                    uriString = uri.toString(),
                    displayName = displayName,
                    fileType = InputFileType.PDF,
                    sizeBytes = if (sizeBytes > 0) sizeBytes else info.bytes.size.toLong(),
                    pageOrSectionCount = info.pageCount,
                    extractedPreview = previewMsg,
                    pdfThumbnail = info.thumbnail
                )
            }
            InputFileType.DOCX -> {
                val parsed = DocxParser.parseFromUri(context, uri)
                SelectedSourceFile(
                    uriString = uri.toString(),
                    displayName = displayName,
                    fileType = InputFileType.DOCX,
                    sizeBytes = sizeBytes,
                    pageOrSectionCount = parsed.sectionCount,
                    extractedPreview = parsed.previewSnippet,
                    extractedStructuredText = parsed.fullStructuredText
                )
            }
        }
    }
}
