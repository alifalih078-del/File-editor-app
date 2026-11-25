package com.example.data.ai

import android.util.Log
import com.example.model.DesignPreset
import com.example.model.RedesignedDocument
import com.example.ui.i18n.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "DocumentRedesignRepo"

class DocumentRedesignRepository {

    private val blockSchema = Schema.obj(
        properties = mapOf(
            "blockType" to Schema.enumeration(
                values = listOf(
                    "heading",
                    "subheading",
                    "paragraph",
                    "bullet_list",
                    "numbered_list",
                    "callout",
                    "metrics_grid",
                    "table",
                    "bilingual_list",
                    "divider"
                ),
                description = "Structural element type for the redesigned page"
            ),
            "title" to Schema.string(
                description = "Heading text, subheading text, callout title, or optional section title for bilingual_list"
            ),
            "body" to Schema.string(
                description = "Paragraph text or callout body text. May use ==text== to highlight grammar rules/keywords and **text** for bold emphasis."
            ),
            "emphasis" to Schema.boolean(
                description = "True for lead/highlighted paragraphs"
            ),
            "tone" to Schema.enumeration(
                values = listOf("info", "highlight", "warning"),
                description = "Visual tone for callout blocks"
            ),
            "items" to Schema.array(
                items = Schema.string(),
                description = "List items for bullet_list or numbered_list. May use ==text== to highlight grammar rules."
            ),
            "metrics" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "label" to Schema.string(),
                        "value" to Schema.string()
                    )
                ),
                description = "Key-value pairs for metrics_grid (2-6 concise KPI or summary cards)"
            ),
            "tableHeaders" to Schema.array(
                items = Schema.string(),
                description = "Column header labels for table blocks"
            ),
            "tableRows" to Schema.array(
                items = Schema.array(items = Schema.string()),
                description = "Matrix of row cells matching tableHeaders count"
            ),
            "bilingualEntries" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "english" to Schema.string(description = "English word, phrase, or sentence (LTR). Use ==...== to highlight target grammar parts when helpful."),
                        "arabic" to Schema.string(description = "Accurate, clear Arabic translation or meaning (RTL)"),
                        "note" to Schema.string(description = "Optional short part-of-speech, tense, or grammar tag e.g. 'Verb • Past Simple'")
                    ),
                    optionalProperties = listOf("note")
                ),
                description = "Aligned English-Arabic vocabulary or sentence pairs for bilingual_list"
            )
        ),
        optionalProperties = listOf(
            "title",
            "body",
            "emphasis",
            "tone",
            "items",
            "metrics",
            "tableHeaders",
            "tableRows",
            "bilingualEntries"
        )
    )

    private val documentSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Main redesigned document title"),
            "subtitle" to Schema.string(description = "Concise document subtitle or tagline"),
            "headerBadge" to Schema.string(description = "Short uppercase/category badge at top of cover banner"),
            "footerText" to Schema.string(description = "Short running footer text"),
            "direction" to Schema.enumeration(
                values = listOf("rtl", "ltr"),
                description = "Use rtl if the primary document prose is Arabic, or ltr if primary prose is English (even when containing Arabic translations in bilingual_list/tables)."
            ),
            "designThemeName" to Schema.string(description = "Name of the applied visual design style in the requested UI language"),
            "changeSummary" to Schema.string(description = "Clear explanation of all design, translation, grammar highlighting, and formatting improvements made"),
            "primaryColorHex" to Schema.string(description = "Dark dominant hex color e.g. #1E3A8A"),
            "accentColorHex" to Schema.string(description = "Crisp accent hex color e.g. #0D9488"),
            "surfaceTintHex" to Schema.string(description = "Very light background tint hex color e.g. #EFF6FF"),
            "blocks" to Schema.array(
                items = blockSchema,
                description = "Ordered list of visually structured blocks forming the redesigned document"
            )
        ),
        optionalProperties = listOf("subtitle", "headerBadge", "footerText")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = documentSchema
                temperature = 0.35f
            }
        )
    }

    suspend fun redesignPdf(
        pdfBytes: ByteArray,
        fileName: String,
        userInstruction: String,
        preset: DesignPreset,
        uiLanguage: AppLanguage
    ): RedesignedDocument = withContext(Dispatchers.IO) {
        val systemInstruction = buildSystemPrompt(
            fileName = fileName,
            sourceType = "PDF",
            userInstruction = userInstruction,
            preset = preset,
            uiLanguage = uiLanguage
        )
        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        inlineData(pdfBytes, "application/pdf")
                        text(systemInstruction)
                    }
                )
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Empty AI response.")
            RedesignedDocument.fromJsonString(rawJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PDF redesign failed", e)
            throw e
        }
    }

    suspend fun redesignDocxText(
        structuredDocxText: String,
        fileName: String,
        userInstruction: String,
        preset: DesignPreset,
        uiLanguage: AppLanguage
    ): RedesignedDocument = withContext(Dispatchers.IO) {
        val systemInstruction = buildSystemPrompt(
            fileName = fileName,
            sourceType = "Word (.docx)",
            userInstruction = userInstruction,
            preset = preset,
            uiLanguage = uiLanguage
        )
        val trimmedContent = structuredDocxText.take(65_000)
        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(systemInstruction)
                        text("=== SOURCE WORD DOCUMENT CONTENT ===\n$trimmedContent")
                    }
                )
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Empty AI response.")
            RedesignedDocument.fromJsonString(rawJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "DOCX redesign failed", e)
            throw e
        }
    }

    suspend fun redesignRawText(
        rawText: String,
        documentTitleHint: String,
        userInstruction: String,
        preset: DesignPreset,
        uiLanguage: AppLanguage
    ): RedesignedDocument = withContext(Dispatchers.IO) {
        val label = documentTitleHint.ifBlank { "Custom Study Document" }
        val systemInstruction = buildSystemPrompt(
            fileName = label,
            sourceType = "Text / Educational Draft",
            userInstruction = userInstruction,
            preset = preset,
            uiLanguage = uiLanguage
        )
        val trimmedContent = rawText.take(65_000)
        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(systemInstruction)
                        text("=== SOURCE CONTENT ===\n$trimmedContent")
                    }
                )
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Empty AI response.")
            RedesignedDocument.fromJsonString(rawJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Raw text redesign failed", e)
            throw e
        }
    }

    suspend fun refineDocument(
        currentDocument: RedesignedDocument,
        refinementInstruction: String,
        uiLanguage: AppLanguage
    ): RedesignedDocument = withContext(Dispatchers.IO) {
        val currentJson = currentDocument.toJsonString()
        val summaryLang = if (uiLanguage == AppLanguage.AR) "Arabic" else "English"
        val prompt = """
            You are an expert editorial document designer, English-Arabic educational formatter, and precision typographer.
            Below is the current JSON representation of a redesigned document.
            Apply the user's requested modifications accurately without introducing typos, broken tables, or missing data.
            Keep English and Arabic aligned cleanly (use `bilingual_list` for English-Arabic word/sentence pairs, and `==...==` to highlight grammar rules where relevant).
            Preserve the document's language(s) unless the user asks to translate.
            Return the complete updated document in the exact JSON schema, and write `changeSummary` and `designThemeName` in $summaryLang.

            USER REFINEMENT REQUEST:
            $refinementInstruction

            CURRENT DOCUMENT JSON:
            $currentJson
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Empty AI response.")
            RedesignedDocument.fromJsonString(rawJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document refinement failed", e)
            throw e
        }
    }

    private fun buildSystemPrompt(
        fileName: String,
        sourceType: String,
        userInstruction: String,
        preset: DesignPreset,
        uiLanguage: AppLanguage
    ): String {
        val summaryLang = if (uiLanguage == AppLanguage.AR) "Arabic" else "English"
        return """
            You are an elite editorial document architect, typographer, and bilingual English–Arabic educational designer.
            Your task is to read the attached $sourceType content ("$fileName") completely and accurately,
            and redesign it into a modern, ultra-consistent, error-free, publication-grade structured document.

            CRITICAL RULES FOR HIGH PRECISION & ZERO ERRORS:
            1. Preserve 100% of important facts, vocabulary, exercises, sentences, numbers, and meaning from the source unless the user explicitly asks to summarize or omit parts.
            2. Fix any broken line wraps, spelling typos, messy spacing, scrambled RTL/LTR mixing, or inconsistent punctuation from the original file.
            3. BILINGUAL ENGLISH-ARABIC & EDUCATIONAL MASTERY:
               - Whenever the document contains English words/sentences paired with Arabic translations (or the user asks to translate or align sentences for students):
                 ALWAYS use `bilingual_list` blocks (with `english`, `arabic`, and optional `note` like "Noun", "Verb • Past", "Idiom") OR clean structured `table` blocks!
                 Never jam English sentences and Arabic translations randomly in the same messy line. Separating them in `bilingual_list` guarantees crisp alignment for middle & high school students to study and memorize.
               - Whenever grammar rules are present or the user asks to highlight/mark grammar ("تأشير القواعد"):
                 1) Wrap key grammar structures/patterns inside example sentences or notes with `==...==` (e.g., `She ==has been studying== since 8 AM.`) and bold terms with `**...**`.
                 2) Add dedicated `callout` blocks (`tone = "highlight"`) clearly stating the Grammar Rule name, formula/structure, and annotated examples.
               - Format dense reading passages into clean, well-spaced `paragraph` blocks with clear `heading`/`subheading` hierarchy.
            4. Transform messy data into clean visual hierarchy using:
               - `heading` & `subheading` blocks
               - `bilingual_list` blocks for aligned English–Arabic vocabulary, phrases, or sentence-by-sentence translations
               - `callout` blocks for grammar rules, study tips, or executive summaries
               - `metrics_grid` blocks for lesson overview cards (e.g. Level, Unit, Word Count) or key stats
               - `table` blocks with matching column counts across `tableHeaders` and every row in `tableRows`
               - `bullet_list` or `numbered_list` for exercises, questions, and scannable points
               - `paragraph` blocks with crisp prose
            5. Language & Direction:
               - Set `direction` to "ltr" if the main prose/paragraphs are English (even if it includes Arabic translations in `bilingual_list`), or "rtl" if the main prose is Arabic.
               - Write `designThemeName` and `changeSummary` in clear, natural $summaryLang.
            6. Color Harmony:
               - ${preset.promptHint}
               - Ensure `primaryColorHex` is a rich dark color (high contrast against white text), `accentColorHex` is vibrant and complementary, and `surfaceTintHex` is a very light pastel tint (e.g., #F8FAFC, #EFF6FF, #F0FDFA, #FFF7ED).
               - Unless the user specifies custom colors in their prompt, prefer: primary=${preset.primaryHex}, accent=${preset.accentHex}, surfaceTint=${preset.surfaceTintHex}.
            7. User Request Priority:
               - Follow every specific change requested by the user below with precision:
               USER INSTRUCTIONS:
               ${userInstruction.ifBlank { "أعد تصميم الملف وتنسيقه بأسلوب حديث ومتناسق جداً، مع ترتيب الجمل والترجمة وتأشير القواعد والمفاهيم الأساسية بوضوح." }}
        """.trimIndent()
    }
}
