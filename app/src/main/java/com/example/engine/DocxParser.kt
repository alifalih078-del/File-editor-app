package com.example.engine

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParsedDocxContent(
    val fullStructuredText: String,
    val previewSnippet: String,
    val sectionCount: Int
)

object DocxParser {

    fun parseFromUri(context: Context, uri: Uri): ParsedDocxContent {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح ملف Word المختار.")

        inputStream.use { stream ->
            return parseFromStream(stream)
        }
    }

    fun parseFromStream(stream: InputStream): ParsedDocxContent {
        var documentXmlBytes: ByteArray? = null
        ZipInputStream(stream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXmlBytes = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val xmlBytes = documentXmlBytes
            ?: throw IllegalArgumentException("ملف Word غير صالح أو تالف (لا يحتوي على document.xml).")

        val blocks = mutableListOf<String>()
        var paragraphCount = 0

        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser().apply {
            setInput(xmlBytes.inputStream(), "UTF-8")
        }

        var inParagraph = false
        var inTable = false
        var inTableRow = false
        var inTableCell = false
        var currentStyle = ""
        var isListItem = false
        val currentParagraphText = StringBuilder()
        val currentCellText = StringBuilder()
        val currentRowCells = mutableListOf<String>()
        val currentTableRows = mutableListOf<List<String>>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "w:tbl" -> {
                            inTable = true
                            currentTableRows.clear()
                        }
                        "w:tr" -> if (inTable) {
                            inTableRow = true
                            currentRowCells.clear()
                        }
                        "w:tc" -> if (inTableRow) {
                            inTableCell = true
                            currentCellText.clear()
                        }
                        "w:p" -> {
                            inParagraph = true
                            currentStyle = ""
                            isListItem = false
                            currentParagraphText.clear()
                        }
                        "w:pStyle" -> if (inParagraph) {
                            currentStyle = parser.getAttributeValue(null, "w:val").orEmpty()
                        }
                        "w:numPr" -> if (inParagraph) {
                            isListItem = true
                        }
                        "w:tab" -> if (inParagraph) {
                            currentParagraphText.append(" ")
                        }
                        "w:br" -> if (inParagraph) {
                            currentParagraphText.append("\n")
                        }
                        "w:t" -> if (inParagraph) {
                            val text = parser.nextText()
                            currentParagraphText.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "w:p" -> {
                            inParagraph = false
                            val text = currentParagraphText.toString().trim()
                            if (text.isNotEmpty()) {
                                paragraphCount++
                                if (inTableCell) {
                                    if (currentCellText.isNotEmpty()) currentCellText.append(" / ")
                                    currentCellText.append(text)
                                } else {
                                    val formatted = when {
                                        currentStyle.contains("Heading1", ignoreCase = true) ||
                                            currentStyle.equals("Title", ignoreCase = true) -> "# $text"
                                        currentStyle.contains("Heading2", ignoreCase = true) ||
                                            currentStyle.equals("Subtitle", ignoreCase = true) -> "## $text"
                                        currentStyle.contains("Heading", ignoreCase = true) -> "### $text"
                                        isListItem -> "- $text"
                                        else -> text
                                    }
                                    blocks.add(formatted)
                                }
                            }
                        }
                        "w:tc" -> if (inTableCell) {
                            inTableCell = false
                            currentRowCells.add(currentCellText.toString().trim())
                        }
                        "w:tr" -> if (inTableRow) {
                            inTableRow = false
                            if (currentRowCells.any { it.isNotBlank() }) {
                                currentTableRows.add(currentRowCells.toList())
                            }
                        }
                        "w:tbl" -> if (inTable) {
                            inTable = false
                            if (currentTableRows.isNotEmpty()) {
                                val tableText = buildString {
                                    append("[TABLE]\n")
                                    currentTableRows.forEach { row ->
                                        append("| ")
                                        append(row.joinToString(" | "))
                                        append(" |\n")
                                    }
                                    append("[/TABLE]")
                                }
                                blocks.add(tableText)
                                paragraphCount += currentTableRows.size
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val fullText = blocks.joinToString("\n\n").trim()
        if (fullText.isBlank()) {
            throw IllegalStateException("ملف Word لا يحتوي على نص قابل للاستخراج.")
        }

        val snippet = fullText.take(260).replace("\n", " ").trim()
        return ParsedDocxContent(
            fullStructuredText = fullText,
            previewSnippet = if (fullText.length > 260) "$snippet..." else snippet,
            sectionCount = paragraphCount.coerceAtLeast(1)
        )
    }
}
