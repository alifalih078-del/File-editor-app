package com.example.model

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject

enum class InputFileType(val displayLabel: String, val extension: String) {
    PDF("PDF", "pdf"),
    DOCX("Word", "docx")
}

enum class OutputFormat(val labelAr: String, val labelEn: String) {
    PDF_AND_DOCX("PDF و Word", "PDF & Word"),
    PDF_ONLY("PDF فقط", "PDF Only"),
    DOCX_ONLY("Word فقط", "Word Only")
}

enum class DesignPreset(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val subtitleAr: String,
    val subtitleEn: String,
    val primaryHex: String,
    val accentHex: String,
    val surfaceTintHex: String,
    val promptHint: String
) {
    BILINGUAL_EDU(
        id = "bilingual_edu",
        titleAr = "تعليمي ثنائي اللغة",
        titleEn = "Bilingual Study",
        subtitleAr = "مثالي لدروس الإنجليزية والترجمة والقواعد",
        subtitleEn = "Ideal for English lessons, Arabic meanings & grammar",
        primaryHex = "#1E3A8A",
        accentHex = "#0D9488",
        surfaceTintHex = "#EFF6FF",
        promptHint = "Use a Bilingual Academic Study aesthetic: primary #1E3A8A, accent #0D9488, surfaceTint #EFF6FF. Organize English vocabulary/sentences and Arabic translations cleanly using bilingual_list or structured tables, and mark key grammar structures with ==...== inside examples and highlight callouts."
    ),
    EXECUTIVE_NAVY(
        id = "executive_navy",
        titleAr = "تنفيذي فاخر",
        titleEn = "Executive Navy",
        subtitleAr = "كحلي عميق مع لمسات ذهبية",
        subtitleEn = "Deep navy with refined gold accents",
        primaryHex = "#0F172A",
        accentHex = "#D97706",
        surfaceTintHex = "#F8FAFC",
        promptHint = "Use an Executive Luxury aesthetic: primary #0F172A, accent #D97706, surfaceTint #F8FAFC, sharp structured hierarchy, executive summary callout, and clean tables."
    ),
    EDITORIAL_TEAL(
        id = "editorial_teal",
        titleAr = "تحريري عصري",
        titleEn = "Modern Editorial",
        subtitleAr = "أخضر زمردي متوازن وواضح",
        subtitleEn = "Balanced emerald teal with spacious prose",
        primaryHex = "#064E3B",
        accentHex = "#0D9488",
        surfaceTintHex = "#F0FDFA",
        promptHint = "Use a Modern Editorial aesthetic: primary #064E3B, accent #0D9488, surfaceTint #F0FDFA, clean spacious paragraphs, highlight callouts, and refined headings."
    ),
    SWISS_MINIMAL(
        id = "swiss_minimal",
        titleAr = "سويسري أنيق",
        titleEn = "Swiss Minimal",
        subtitleAr = "أسود فحمي مع نيلي حديث",
        subtitleEn = "Crisp charcoal with indigo contrast",
        primaryHex = "#18181B",
        accentHex = "#4F46E5",
        surfaceTintHex = "#F4F4F5",
        promptHint = "Use a Swiss Minimalist Grid aesthetic: primary #18181B, accent #4F46E5, surfaceTint #F4F4F5, bold typographic contrast, crisp metric cards, and zero clutter."
    ),
    WARM_ACADEMIC(
        id = "warm_academic",
        titleAr = "أكاديمي دافئ",
        titleEn = "Warm Academic",
        subtitleAr = "عناوين دافئة وتقسيم واضح",
        subtitleEn = "Warm structured headings for worksheets & notes",
        primaryHex = "#431407",
        accentHex = "#C2410C",
        surfaceTintHex = "#FFF7ED",
        promptHint = "Use a Warm Academic & Report aesthetic: primary #431407, accent #C2410C, surfaceTint #FFF7ED, numbered structure, clear citations/notes, and balanced spacing."
    ),
    AUTO_SMART(
        id = "auto_smart",
        titleAr = "ذكي تلقائي",
        titleEn = "Smart Auto",
        subtitleAr = "يختار الذكاء الأنسب للمحتوى",
        subtitleEn = "AI picks the best palette for your content",
        primaryHex = "#0F172A",
        accentHex = "#0284C7",
        surfaceTintHex = "#F8FAFC",
        promptHint = "Choose the best harmonious hex color palette (primary, accent, surfaceTint) tailored specifically to the document subject and user instructions."
    )
}

data class SelectedSourceFile(
    val uriString: String,
    val displayName: String,
    val fileType: InputFileType,
    val sizeBytes: Long,
    val pageOrSectionCount: Int,
    val extractedPreview: String,
    val extractedStructuredText: String? = null,
    val pdfThumbnail: Bitmap? = null
)

data class MetricItem(
    val label: String,
    val value: String
)

data class BilingualEntry(
    val english: String,
    val arabic: String,
    val note: String? = null
)

sealed interface DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock
    data class Paragraph(val text: String, val emphasis: Boolean = false) : DocBlock
    data class BulletList(val items: List<String>, val numbered: Boolean = false) : DocBlock
    data class Callout(val title: String, val body: String, val tone: String = "info") : DocBlock
    data class MetricsGrid(val items: List<MetricItem>) : DocBlock
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : DocBlock
    data class BilingualList(
        val title: String? = null,
        val entries: List<BilingualEntry>
    ) : DocBlock
    data object Divider : DocBlock
}

data class RedesignedDocument(
    val title: String,
    val subtitle: String?,
    val headerBadge: String?,
    val footerText: String?,
    val direction: String,
    val designThemeName: String,
    val changeSummary: String,
    val primaryColorHex: String,
    val accentColorHex: String,
    val surfaceTintHex: String,
    val blocks: List<DocBlock>
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("title", title)
        subtitle?.let { root.put("subtitle", it) }
        headerBadge?.let { root.put("headerBadge", it) }
        footerText?.let { root.put("footerText", it) }
        root.put("direction", direction)
        root.put("designThemeName", designThemeName)
        root.put("changeSummary", changeSummary)
        root.put("primaryColorHex", primaryColorHex)
        root.put("accentColorHex", accentColorHex)
        root.put("surfaceTintHex", surfaceTintHex)

        val blocksArray = JSONArray()
        blocks.forEach { block ->
            val obj = JSONObject()
            when (block) {
                is DocBlock.Heading -> {
                    obj.put("blockType", if (block.level <= 1) "heading" else "subheading")
                    obj.put("title", block.text)
                }
                is DocBlock.Paragraph -> {
                    obj.put("blockType", "paragraph")
                    obj.put("body", block.text)
                    obj.put("emphasis", block.emphasis)
                }
                is DocBlock.BulletList -> {
                    obj.put("blockType", if (block.numbered) "numbered_list" else "bullet_list")
                    obj.put("items", JSONArray(block.items))
                }
                is DocBlock.Callout -> {
                    obj.put("blockType", "callout")
                    obj.put("title", block.title)
                    obj.put("body", block.body)
                    obj.put("tone", block.tone)
                }
                is DocBlock.MetricsGrid -> {
                    obj.put("blockType", "metrics_grid")
                    val metricsArr = JSONArray()
                    block.items.forEach { metric ->
                        metricsArr.put(JSONObject().apply {
                            put("label", metric.label)
                            put("value", metric.value)
                        })
                    }
                    obj.put("metrics", metricsArr)
                }
                is DocBlock.TableBlock -> {
                    obj.put("blockType", "table")
                    obj.put("tableHeaders", JSONArray(block.headers))
                    val rowsArr = JSONArray()
                    block.rows.forEach { row -> rowsArr.put(JSONArray(row)) }
                    obj.put("tableRows", rowsArr)
                }
                is DocBlock.BilingualList -> {
                    obj.put("blockType", "bilingual_list")
                    block.title?.let { obj.put("title", it) }
                    val entriesArr = JSONArray()
                    block.entries.forEach { entry ->
                        entriesArr.put(JSONObject().apply {
                            put("english", entry.english)
                            put("arabic", entry.arabic)
                            entry.note?.let { put("note", it) }
                        })
                    }
                    obj.put("bilingualEntries", entriesArr)
                }
                DocBlock.Divider -> {
                    obj.put("blockType", "divider")
                }
            }
            blocksArray.put(obj)
        }
        root.put("blocks", blocksArray)
        return root.toString()
    }

    companion object {
        fun fromJsonString(jsonString: String): RedesignedDocument {
            val root = JSONObject(jsonString)
            val title = root.optString("title", "مستند مُعاد تصميمه").ifBlank { "مستند مُعاد تصميمه" }
            val subtitle = root.optString("subtitle").takeIf { it.isNotBlank() }
            val headerBadge = root.optString("headerBadge").takeIf { it.isNotBlank() }
            val footerText = root.optString("footerText").takeIf { it.isNotBlank() }
            val direction = root.optString("direction", "rtl").lowercase().let {
                if (it == "ltr") "ltr" else "rtl"
            }
            val designThemeName = root.optString("designThemeName", "تصميم حديث متناسق")
            val changeSummary = root.optString("changeSummary", "تمت إعادة تنسيق وهيكلة المستند بشكل حديث ومتناسق.")
            val primaryColorHex = sanitizeHex(root.optString("primaryColorHex"), "#0F172A")
            val accentColorHex = sanitizeHex(root.optString("accentColorHex"), "#0D9488")
            val surfaceTintHex = sanitizeHex(root.optString("surfaceTintHex"), "#F8FAFC")

            val blocks = mutableListOf<DocBlock>()
            val blocksArr = root.optJSONArray("blocks") ?: JSONArray()
            for (i in 0 until blocksArr.length()) {
                val obj = blocksArr.optJSONObject(i) ?: continue
                when (obj.optString("blockType")) {
                    "heading" -> {
                        val t = obj.optString("title").ifBlank { obj.optString("body") }
                        if (t.isNotBlank()) blocks.add(DocBlock.Heading(level = 1, text = t))
                    }
                    "subheading" -> {
                        val t = obj.optString("title").ifBlank { obj.optString("body") }
                        if (t.isNotBlank()) blocks.add(DocBlock.Heading(level = 2, text = t))
                    }
                    "paragraph" -> {
                        val b = obj.optString("body").ifBlank { obj.optString("title") }
                        val emphasis = obj.optBoolean("emphasis", false)
                        if (b.isNotBlank()) blocks.add(DocBlock.Paragraph(text = b, emphasis = emphasis))
                    }
                    "bullet_list", "numbered_list" -> {
                        val numbered = obj.optString("blockType") == "numbered_list"
                        val itemsArr = obj.optJSONArray("items")
                        val items = mutableListOf<String>()
                        if (itemsArr != null) {
                            for (j in 0 until itemsArr.length()) {
                                val item = itemsArr.optString(j)
                                if (item.isNotBlank()) items.add(item)
                            }
                        }
                        if (items.isNotEmpty()) blocks.add(DocBlock.BulletList(items = items, numbered = numbered))
                    }
                    "callout" -> {
                        val t = obj.optString("title")
                        val b = obj.optString("body")
                        val tone = obj.optString("tone", "info")
                        if (t.isNotBlank() || b.isNotBlank()) {
                            blocks.add(DocBlock.Callout(title = t, body = b, tone = tone))
                        }
                    }
                    "metrics_grid" -> {
                        val mArr = obj.optJSONArray("metrics")
                        val metrics = mutableListOf<MetricItem>()
                        if (mArr != null) {
                            for (j in 0 until mArr.length()) {
                                val mObj = mArr.optJSONObject(j) ?: continue
                                val label = mObj.optString("label")
                                val value = mObj.optString("value")
                                if (label.isNotBlank() || value.isNotBlank()) {
                                    metrics.add(MetricItem(label = label, value = value))
                                }
                            }
                        }
                        if (metrics.isNotEmpty()) blocks.add(DocBlock.MetricsGrid(metrics))
                    }
                    "table" -> {
                        val hArr = obj.optJSONArray("tableHeaders")
                        val rArr = obj.optJSONArray("tableRows")
                        val headers = mutableListOf<String>()
                        if (hArr != null) {
                            for (j in 0 until hArr.length()) {
                                headers.add(hArr.optString(j))
                            }
                        }
                        val rows = mutableListOf<List<String>>()
                        if (rArr != null) {
                            for (r in 0 until rArr.length()) {
                                val rowArr = rArr.optJSONArray(r) ?: continue
                                val row = mutableListOf<String>()
                                for (c in 0 until rowArr.length()) {
                                    row.add(rowArr.optString(c))
                                }
                                if (row.isNotEmpty()) rows.add(row)
                            }
                        }
                        if (headers.isNotEmpty() || rows.isNotEmpty()) {
                            blocks.add(DocBlock.TableBlock(headers = headers, rows = rows))
                        }
                    }
                    "bilingual_list" -> {
                        val blockTitle = obj.optString("title").takeIf { it.isNotBlank() }
                        val entriesArr = obj.optJSONArray("bilingualEntries")
                        val entries = mutableListOf<BilingualEntry>()
                        if (entriesArr != null) {
                            for (j in 0 until entriesArr.length()) {
                                val eObj = entriesArr.optJSONObject(j) ?: continue
                                val en = eObj.optString("english")
                                val ar = eObj.optString("arabic")
                                val note = eObj.optString("note").takeIf { it.isNotBlank() }
                                if (en.isNotBlank() || ar.isNotBlank()) {
                                    entries.add(BilingualEntry(english = en, arabic = ar, note = note))
                                }
                            }
                        }
                        if (entries.isNotEmpty()) {
                            blocks.add(DocBlock.BilingualList(title = blockTitle, entries = entries))
                        }
                    }
                    "divider" -> blocks.add(DocBlock.Divider)
                }
            }

            return RedesignedDocument(
                title = title,
                subtitle = subtitle,
                headerBadge = headerBadge,
                footerText = footerText,
                direction = direction,
                designThemeName = designThemeName,
                changeSummary = changeSummary,
                primaryColorHex = primaryColorHex,
                accentColorHex = accentColorHex,
                surfaceTintHex = surfaceTintHex,
                blocks = blocks
            )
        }

        private fun sanitizeHex(raw: String?, fallback: String): String {
            if (raw.isNullOrBlank()) return fallback
            val cleaned = if (raw.startsWith("#")) raw.trim() else "#${raw.trim()}"
            return if (Regex("^#[0-9A-Fa-f]{6}$").matches(cleaned)) cleaned else fallback
        }
    }
}
