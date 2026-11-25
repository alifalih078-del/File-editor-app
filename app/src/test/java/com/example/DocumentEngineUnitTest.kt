package com.example

import com.example.engine.DocxDocumentGenerator
import com.example.engine.InlineSpanParser
import com.example.model.BilingualEntry
import com.example.model.DocBlock
import com.example.model.MetricItem
import com.example.model.RedesignedDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocumentEngineUnitTest {

    @Test
    fun `docx generator produces valid OpenXML archive with well-formed XML entries`() {
        val doc = RedesignedDocument(
            title = "تقرير الأداء السنوي",
            subtitle = "تحليل النتائج المالية والتشغيلية",
            headerBadge = "تقرير تنفيذي",
            footerText = "تم الإنشاء بواسطة مُنسّق",
            direction = "rtl",
            designThemeName = "تنفيذي فاخر",
            changeSummary = "تم تنظيم البيانات في بطاقات وجدول واضح.",
            primaryColorHex = "#0F172A",
            accentColorHex = "#D97706",
            surfaceTintHex = "#F8FAFC",
            blocks = listOf(
                DocBlock.MetricsGrid(
                    items = listOf(
                        MetricItem("الإيرادات", "1.4M"),
                        MetricItem("النمو", "+24%")
                    )
                ),
                DocBlock.Heading(level = 1, text = "الملخص التنفيذي"),
                DocBlock.Paragraph(text = "حقق الفريق نمواً ملحوظاً & قياسياً <2026>.", emphasis = true),
                DocBlock.Callout(
                    title = "ملاحظة هامة",
                    body = "جميع الأرقام مدققة ومعتمدة.",
                    tone = "highlight"
                ),
                DocBlock.BulletList(items = listOf("رفع الكفاءة", "تحسين الجودة"), numbered = true),
                DocBlock.TableBlock(
                    headers = listOf("البند", "القيمة"),
                    rows = listOf(listOf("التشغيل", "400K"), listOf("التطوير", "250K"))
                ),
                DocBlock.Divider
            )
        )

        val tempFile = File.createTempFile("munasik_test_", ".docx")
        try {
            DocxDocumentGenerator.generateDocx(doc, tempFile)
            assertTrue(tempFile.exists() && tempFile.length() > 500)

            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(tempFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries[entry.name] = zis.readBytes()
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val expectedEntries = listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/_rels/document.xml.rels",
                "word/styles.xml",
                "word/document.xml"
            )
            expectedEntries.forEach { name ->
                assertTrue("Missing DOCX entry: $name", entries.containsKey(name))
                val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                val parsedDoc = dbf.newDocumentBuilder().parse(entries[name]!!.inputStream())
                assertTrue(parsedDoc.documentElement != null)
            }

            val docXmlText = String(entries["word/document.xml"]!!, Charsets.UTF_8)
            assertTrue(docXmlText.contains("تقرير الأداء السنوي"))
            assertTrue(docXmlText.contains("الملخص التنفيذي"))
            assertTrue(docXmlText.contains("حقق الفريق نمواً ملحوظاً &amp; قياسياً &lt;2026&gt;."))
            assertTrue(docXmlText.contains("<w:bidi/>"))
            assertEquals(5, entries.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `inline span parser extracts grammar highlights and bold segments accurately`() {
        val segments = InlineSpanParser.parse("She ==has been studying== **English** daily.")
        assertEquals(5, segments.size)
        assertEquals("She ", segments[0].text)
        assertTrue(!segments[0].isBold && !segments[0].isHighlight)
        assertEquals("has been studying", segments[1].text)
        assertTrue(segments[1].isHighlight && segments[1].isBold)
        assertEquals("English", segments[3].text)
        assertTrue(segments[3].isBold && !segments[3].isHighlight)
    }

    @Test
    fun `bilingual list generates valid docx xml with grammar highlights and rtl arabic`() {
        val doc = RedesignedDocument(
            title = "Unit 1: Present Perfect",
            subtitle = "3rd Intermediate English Study Sheet",
            headerBadge = "ENGLISH & ARABIC",
            footerText = "Munasik Study",
            direction = "ltr",
            designThemeName = "Bilingual Study",
            changeSummary = "Aligned vocabulary and highlighted present perfect verbs.",
            primaryColorHex = "#1E3A8A",
            accentColorHex = "#0D9488",
            surfaceTintHex = "#EFF6FF",
            blocks = listOf(
                DocBlock.BilingualList(
                    title = "Key Sentences & Vocabulary",
                    entries = listOf(
                        BilingualEntry(
                            english = "They ==have lived== here since 2020.",
                            arabic = "لقد عاشوا هنا منذ عام 2020.",
                            note = "Present Perfect"
                        ),
                        BilingualEntry(
                            english = "Achievement",
                            arabic = "إنجاز",
                            note = "Noun"
                        )
                    )
                )
            )
        )

        val tempFile = File.createTempFile("munasik_bilingual_", ".docx")
        try {
            DocxDocumentGenerator.generateDocx(doc, tempFile)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(tempFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries[entry.name] = zis.readBytes()
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val docXmlBytes = entries["word/document.xml"]!!
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val parsedDoc = dbf.newDocumentBuilder().parse(docXmlBytes.inputStream())
            assertTrue(parsedDoc.documentElement != null)
            val xml = String(docXmlBytes, Charsets.UTF_8)
            assertTrue(xml.contains("have lived"))
            assertTrue(xml.contains("لقد عاشوا هنا منذ عام 2020."))
            assertTrue(xml.contains("Present Perfect"))
            assertTrue(xml.contains("<w:highlight w:val=\"yellow\"/>"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `bilingual app strings provide both arabic and english labels`() {
        val ar = com.example.ui.i18n.stringsFor(com.example.ui.i18n.AppLanguage.AR)
        val en = com.example.ui.i18n.stringsFor(com.example.ui.i18n.AppLanguage.EN)
        assertEquals("مُنسّق", ar.appTitleStudio)
        assertEquals("Munasik", en.appTitleStudio)
        assertTrue(ar.quickSuggestions.isNotEmpty())
        assertTrue(en.quickSuggestions.isNotEmpty())
    }
}
