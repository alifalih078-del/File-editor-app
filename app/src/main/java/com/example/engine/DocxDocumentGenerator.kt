package com.example.engine

import com.example.model.DocBlock
import com.example.model.RedesignedDocument
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxDocumentGenerator {

    fun generateDocx(document: RedesignedDocument, outputFile: File) {
        outputFile.parentFile?.mkdirs()

        val primaryHex = document.primaryColorHex.removePrefix("#").uppercase()
        val accentHex = document.accentColorHex.removePrefix("#").uppercase()
        val surfaceHex = document.surfaceTintHex.removePrefix("#").uppercase()
        val isRtl = document.direction.equals("rtl", ignoreCase = true)

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", contentTypesXml())
            writeZipEntry(zos, "_rels/.rels", rootRelsXml())
            writeZipEntry(zos, "word/_rels/document.xml.rels", documentRelsXml())
            writeZipEntry(zos, "word/styles.xml", stylesXml(primaryHex, accentHex, isRtl))
            writeZipEntry(
                zos,
                "word/document.xml",
                buildDocumentXml(document, primaryHex, accentHex, surfaceHex, isRtl)
            )
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun contentTypesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun documentRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml(primaryHex: String, accentHex: String, isRtl: Boolean): String {
        val bidiTag = if (isRtl) "<w:bidi/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Arial"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="22"/>
        <w:color w:val="1E293B"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        $bidiTag
        <w:spacing w:after="140" w:line="300" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:pPr>$bidiTag<w:spacing w:before="240" w:after="100"/></w:pPr>
    <w:rPr><w:b/><w:bCs/><w:color w:val="$primaryHex"/><w:sz w:val="30"/><w:szCs w:val="30"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:pPr>$bidiTag<w:spacing w:before="180" w:after="80"/></w:pPr>
    <w:rPr><w:b/><w:bCs/><w:color w:val="$accentHex"/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>
  </w:style>
</w:styles>"""
    }

    private fun buildDocumentXml(
        document: RedesignedDocument,
        primaryHex: String,
        accentHex: String,
        surfaceHex: String,
        isRtl: Boolean
    ): String {
        val bidiTag = if (isRtl) "<w:bidi/>" else ""
        val rtlRunTag = if (isRtl) "<w:rtl/>" else ""
        val bidiTblTag = if (isRtl) "<w:bidiVisual/>" else ""

        val body = StringBuilder()

        // Cover Header Card Table
        body.append("""
            <w:tbl>
              <w:tblPr>
                $bidiTblTag
                <w:tblW w:w="9200" w:type="dxa"/>
                <w:tblCellMar>
                  <w:top w:w="200" w:type="dxa"/>
                  <w:left w:w="240" w:type="dxa"/>
                  <w:bottom w:w="200" w:type="dxa"/>
                  <w:right w:w="240" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tr>
                <w:tc>
                  <w:tcPr>
                    <w:shd w:val="clear" w:color="auto" w:fill="$primaryHex"/>
                  </w:tcPr>
        """.trimIndent())

        document.headerBadge?.let { badge ->
            body.append(paragraphXml(badge, bold = true, colorHex = accentHex, sizeHalfPts = 20, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
        }
        body.append(paragraphXml(document.title, bold = true, colorHex = "FFFFFF", sizeHalfPts = 38, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
        document.subtitle?.let { sub ->
            body.append(paragraphXml(sub, bold = false, colorHex = "E2E8F0", sizeHalfPts = 22, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
        }

        body.append("</w:tc></w:tr></w:tbl>")
        body.append(paragraphXml("", bold = false, colorHex = "1E293B", sizeHalfPts = 12, bidiTag = bidiTag, rtlRunTag = rtlRunTag))

        // Document Blocks
        document.blocks.forEach { block ->
            when (block) {
                is DocBlock.Heading -> {
                    val styleId = if (block.level <= 1) "Heading1" else "Heading2"
                    val color = if (block.level <= 1) primaryHex else accentHex
                    val sz = if (block.level <= 1) 30 else 24
                    body.append("""
                        <w:p>
                          <w:pPr><w:pStyle w:val="$styleId"/>$bidiTag</w:pPr>
                          ${runsXml(block.text, bold = true, colorHex = color, sizeHalfPts = sz, rtlRunTag = rtlRunTag, accentHex = accentHex)}
                        </w:p>
                    """.trimIndent())
                }

                is DocBlock.Paragraph -> {
                    body.append(
                        paragraphXml(
                            text = block.text,
                            bold = block.emphasis,
                            colorHex = if (block.emphasis) primaryHex else "1E293B",
                            sizeHalfPts = 22,
                            bidiTag = bidiTag,
                            rtlRunTag = rtlRunTag
                        )
                    )
                }

                is DocBlock.BulletList -> {
                    block.items.forEachIndexed { idx, item ->
                        val prefix = if (block.numbered) "${idx + 1}. " else "• "
                        body.append(
                            paragraphXml(
                                text = "$prefix$item",
                                bold = false,
                                colorHex = "1E293B",
                                sizeHalfPts = 21,
                                bidiTag = bidiTag,
                                rtlRunTag = rtlRunTag
                            )
                        )
                    }
                }

                is DocBlock.Callout -> {
                    val fill = if (block.tone == "warning") "FEF3C7" else surfaceHex
                    body.append("""
                        <w:tbl>
                          <w:tblPr>
                            $bidiTblTag
                            <w:tblW w:w="9200" w:type="dxa"/>
                            <w:tblBorders>
                              <w:top w:val="none"/><w:bottom w:val="none"/>
                              <w:left w:val="single" w:sz="18" w:space="0" w:color="$accentHex"/>
                              <w:right w:val="single" w:sz="18" w:space="0" w:color="$accentHex"/>
                            </w:tblBorders>
                            <w:tblCellMar>
                              <w:top w:w="140" w:type="dxa"/><w:bottom w:w="140" w:type="dxa"/>
                              <w:left w:w="180" w:type="dxa"/><w:right w:w="180" w:type="dxa"/>
                            </w:tblCellMar>
                          </w:tblPr>
                          <w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:color="auto" w:fill="$fill"/></w:tcPr>
                    """.trimIndent())
                    if (block.title.isNotBlank()) {
                        body.append(paragraphXml(block.title, bold = true, colorHex = primaryHex, sizeHalfPts = 22, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                    }
                    if (block.body.isNotBlank()) {
                        body.append(paragraphXml(block.body, bold = false, colorHex = "1E293B", sizeHalfPts = 20, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                    }
                    body.append("</w:tc></w:tr></w:tbl>")
                    body.append(paragraphXml("", bold = false, colorHex = "1E293B", sizeHalfPts = 10, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                }

                is DocBlock.MetricsGrid -> {
                    val cols = block.items.size.coerceIn(1, 3)
                    val colW = 9200 / cols
                    body.append("""
                        <w:tbl>
                          <w:tblPr>
                            $bidiTblTag
                            <w:tblW w:w="9200" w:type="dxa"/>
                            <w:tblBorders>
                              <w:top w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:bottom w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:left w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:right w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:insideH w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:insideV w:val="single" w:sz="6" w:color="CBD5E1"/>
                            </w:tblBorders>
                            <w:tblCellMar>
                              <w:top w:w="120" w:type="dxa"/><w:bottom w:w="120" w:type="dxa"/>
                              <w:left w:w="140" w:type="dxa"/><w:right w:w="140" w:type="dxa"/>
                            </w:tblCellMar>
                          </w:tblPr>
                    """.trimIndent())
                    block.items.chunked(cols).forEach { row ->
                        body.append("<w:tr>")
                        row.forEach { metric ->
                            body.append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$surfaceHex\"/></w:tcPr>")
                            body.append(paragraphXml(metric.label, bold = false, colorHex = "64748B", sizeHalfPts = 18, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                            body.append(paragraphXml(metric.value, bold = true, colorHex = primaryHex, sizeHalfPts = 26, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                            body.append("</w:tc>")
                        }
                        body.append("</w:tr>")
                    }
                    body.append("</w:tbl>")
                    body.append(paragraphXml("", bold = false, colorHex = "1E293B", sizeHalfPts = 10, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                }

                is DocBlock.TableBlock -> {
                    val colCount = maxOf(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
                    val colW = 9200 / colCount
                    body.append("""
                        <w:tbl>
                          <w:tblPr>
                            $bidiTblTag
                            <w:tblW w:w="9200" w:type="dxa"/>
                            <w:tblBorders>
                              <w:top w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:bottom w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:left w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:right w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:insideH w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:insideV w:val="single" w:sz="6" w:color="CBD5E1"/>
                            </w:tblBorders>
                            <w:tblCellMar>
                              <w:top w:w="100" w:type="dxa"/><w:bottom w:w="100" w:type="dxa"/>
                              <w:left w:w="120" w:type="dxa"/><w:right w:w="120" w:type="dxa"/>
                            </w:tblCellMar>
                          </w:tblPr>
                    """.trimIndent())
                    if (block.headers.isNotEmpty()) {
                        body.append("<w:tr>")
                        for (c in 0 until colCount) {
                            val h = block.headers.getOrElse(c) { "" }
                            body.append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$primaryHex\"/></w:tcPr>")
                            body.append(paragraphXml(h, bold = true, colorHex = "FFFFFF", sizeHalfPts = 20, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                            body.append("</w:tc>")
                        }
                        body.append("</w:tr>")
                    }
                    block.rows.forEachIndexed { rIdx, row ->
                        val fill = if (rIdx % 2 == 0) "FFFFFF" else surfaceHex
                        body.append("<w:tr>")
                        for (c in 0 until colCount) {
                            val cell = row.getOrElse(c) { "" }
                            body.append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$fill\"/></w:tcPr>")
                            body.append(paragraphXml(cell, bold = false, colorHex = "1E293B", sizeHalfPts = 19, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                            body.append("</w:tc>")
                        }
                        body.append("</w:tr>")
                    }
                    body.append("</w:tbl>")
                    body.append(paragraphXml("", bold = false, colorHex = "1E293B", sizeHalfPts = 10, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                }

                is DocBlock.BilingualList -> {
                    block.title?.takeIf { it.isNotBlank() }?.let { titleText ->
                        body.append("""
                            <w:p>
                              <w:pPr><w:pStyle w:val="Heading2"/>$bidiTag</w:pPr>
                              ${runsXml(titleText, bold = true, colorHex = primaryHex, sizeHalfPts = 24, rtlRunTag = rtlRunTag, accentHex = accentHex)}
                            </w:p>
                        """.trimIndent())
                    }
                    body.append("""
                        <w:tbl>
                          <w:tblPr>
                            <w:tblW w:w="9200" w:type="dxa"/>
                            <w:tblBorders>
                              <w:top w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:bottom w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:left w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:right w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:insideH w:val="single" w:sz="6" w:color="CBD5E1"/>
                              <w:insideV w:val="single" w:sz="6" w:color="$accentHex"/>
                            </w:tblBorders>
                            <w:tblCellMar>
                              <w:top w:w="110" w:type="dxa"/><w:bottom w:w="110" w:type="dxa"/>
                              <w:left w:w="140" w:type="dxa"/><w:right w:w="140" w:type="dxa"/>
                            </w:tblCellMar>
                          </w:tblPr>
                          <w:tr>
                            <w:tc><w:tcPr><w:tcW w:w="4600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$primaryHex"/></w:tcPr>${paragraphXml("English", bold = true, colorHex = "FFFFFF", sizeHalfPts = 20, bidiTag = "", rtlRunTag = "")}</w:tc>
                            <w:tc><w:tcPr><w:tcW w:w="4600" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="$primaryHex"/></w:tcPr>${paragraphXml("المعنى / الترجمة", bold = true, colorHex = "FFFFFF", sizeHalfPts = 20, bidiTag = "<w:bidi/>", rtlRunTag = "<w:rtl/>")}</w:tc>
                          </w:tr>
                    """.trimIndent())

                    block.entries.forEachIndexed { rIdx, entry ->
                        val fill = if (rIdx % 2 == 0) "FFFFFF" else surfaceHex
                        body.append("<w:tr>")
                        // Left cell: English LTR
                        body.append("<w:tc><w:tcPr><w:tcW w:w=\"4600\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$fill\"/></w:tcPr>")
                        body.append(paragraphXml(entry.english, bold = false, colorHex = "1E293B", sizeHalfPts = 20, bidiTag = "", rtlRunTag = "", accentHex = accentHex))
                        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                            body.append(paragraphXml(note, bold = true, colorHex = accentHex, sizeHalfPts = 17, bidiTag = "", rtlRunTag = "", accentHex = accentHex))
                        }
                        body.append("</w:tc>")
                        // Right cell: Arabic RTL
                        body.append("<w:tc><w:tcPr><w:tcW w:w=\"4600\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$fill\"/></w:tcPr>")
                        body.append(paragraphXml(entry.arabic, bold = false, colorHex = "1E293B", sizeHalfPts = 20, bidiTag = "<w:bidi/>", rtlRunTag = "<w:rtl/>", accentHex = accentHex))
                        body.append("</w:tc>")
                        body.append("</w:tr>")
                    }
                    body.append("</w:tbl>")
                    body.append(paragraphXml("", bold = false, colorHex = "1E293B", sizeHalfPts = 10, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
                }

                DocBlock.Divider -> {
                    body.append("""
                        <w:p>
                          <w:pPr>
                            $bidiTag
                            <w:pBdr><w:bottom w:val="single" w:sz="6" w:space="4" w:color="CBD5E1"/></w:pBdr>
                          </w:pPr>
                        </w:p>
                    """.trimIndent())
                }
            }
        }

        document.footerText?.let { footer ->
            body.append(paragraphXml(footer, bold = false, colorHex = "64748B", sizeHalfPts = 17, bidiTag = bidiTag, rtlRunTag = rtlRunTag))
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $body
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1080" w:right="1080" w:bottom="1080" w:left="1080"/>
      $bidiTag
    </w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun paragraphXml(
        text: String,
        bold: Boolean,
        colorHex: String,
        sizeHalfPts: Int,
        bidiTag: String,
        rtlRunTag: String,
        accentHex: String = "0D9488"
    ): String {
        return """<w:p><w:pPr>$bidiTag</w:pPr>${runsXml(text, bold, colorHex, sizeHalfPts, rtlRunTag, accentHex)}</w:p>"""
    }

    private fun runsXml(
        text: String,
        bold: Boolean,
        colorHex: String,
        sizeHalfPts: Int,
        rtlRunTag: String,
        accentHex: String
    ): String {
        val segments = InlineSpanParser.parse(text)
        if (segments.isEmpty()) {
            val boldTag = if (bold) "<w:b/><w:bCs/>" else ""
            return """<w:r><w:rPr>$rtlRunTag$boldTag<w:color w:val="$colorHex"/><w:sz w:val="$sizeHalfPts"/><w:szCs w:val="$sizeHalfPts"/></w:rPr><w:t xml:space="preserve"></w:t></w:r>"""
        }
        return segments.joinToString("") { seg ->
            val effectiveBold = bold || seg.isBold
            val boldTag = if (effectiveBold) "<w:b/><w:bCs/>" else ""
            val runColor = if (seg.isHighlight) accentHex else colorHex
            val highlightTag = if (seg.isHighlight) """<w:highlight w:val="yellow"/>""" else ""
            """<w:r><w:rPr>$rtlRunTag$boldTag<w:color w:val="$runColor"/><w:sz w:val="$sizeHalfPts"/><w:szCs w:val="$sizeHalfPts"/>$highlightTag</w:rPr><w:t xml:space="preserve">${escapeXml(seg.text)}</w:t></w:r>"""
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
