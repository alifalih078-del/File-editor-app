package com.example.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.DocBlock
import com.example.model.RedesignedDocument
import java.io.File
import java.io.FileOutputStream

object PdfDocumentGenerator {
    private const val PAGE_WIDTH = 595 // A4 width in PostScript points
    private const val PAGE_HEIGHT = 842 // A4 height in PostScript points
    private const val MARGIN_X = 42f
    private const val MARGIN_TOP = 44f
    private const val MARGIN_BOTTOM = 48f
    private val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

    fun generatePdf(
        context: Context,
        document: RedesignedDocument,
        outputFile: File
    ): Int {
        outputFile.parentFile?.mkdirs()

        val displayBase = ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.DEFAULT_BOLD
        val bodyBase = ResourcesCompat.getFont(context, R.font.tajawal) ?: Typeface.DEFAULT
        val displayBold = Typeface.create(displayBase, Typeface.BOLD)
        val bodyRegular = Typeface.create(bodyBase, Typeface.NORMAL)
        val bodyBold = Typeface.create(bodyBase, Typeface.BOLD)

        val primaryColor = parseColorSafe(document.primaryColorHex, Color.parseColor("#0F172A"))
        val accentColor = parseColorSafe(document.accentColorHex, Color.parseColor("#0D9488"))
        val surfaceTint = parseColorSafe(document.surfaceTintHex, Color.parseColor("#F8FAFC"))
        val textDark = Color.parseColor("#1E293B")
        val textMuted = Color.parseColor("#64748B")
        val borderLight = Color.parseColor("#CBD5E1")

        val isRtl = document.direction.equals("rtl", ignoreCase = true)
        val textDir = if (isRtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR

        val pdf = PdfDocument()
        var currentPageNum = 0
        lateinit var currentPage: PdfDocument.Page
        lateinit var canvas: Canvas
        var cursorY = MARGIN_TOP

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            typeface = bodyRegular
            textSize = 8.5f
        }
        val footerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderLight
            strokeWidth = 0.6f
        }

        fun finishCurrentPage() {
            if (currentPageNum > 0) {
                val footerY = PAGE_HEIGHT - 28f
                canvas.drawLine(MARGIN_X, footerY - 8f, PAGE_WIDTH - MARGIN_X, footerY - 8f, footerLinePaint)
                val pageLabel = if (isRtl) "صفحة $currentPageNum" else "Page $currentPageNum"
                val labelW = footerPaint.measureText(pageLabel)
                if (isRtl) {
                    canvas.drawText(pageLabel, MARGIN_X, footerY + 4f, footerPaint)
                    document.footerText?.take(55)?.let { fText ->
                        val fw = footerPaint.measureText(fText)
                        canvas.drawText(fText, PAGE_WIDTH - MARGIN_X - fw, footerY + 4f, footerPaint)
                    }
                } else {
                    document.footerText?.take(55)?.let { fText ->
                        canvas.drawText(fText, MARGIN_X, footerY + 4f, footerPaint)
                    }
                    canvas.drawText(pageLabel, PAGE_WIDTH - MARGIN_X - labelW, footerY + 4f, footerPaint)
                }
                pdf.finishPage(currentPage)
            }
        }

        fun startNewPage() {
            finishCurrentPage()
            currentPageNum++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
            currentPage = pdf.startPage(pageInfo)
            canvas = currentPage.canvas
            canvas.drawColor(Color.WHITE)

            // Subtle top accent hairline on continuation pages
            if (currentPageNum > 1) {
                val topLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    strokeWidth = 2f
                }
                canvas.drawLine(MARGIN_X, 26f, PAGE_WIDTH - MARGIN_X, 26f, topLinePaint)
            }
            cursorY = MARGIN_TOP
        }

        fun ensureSpace(neededHeight: Float) {
            if (cursorY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                startNewPage()
            }
        }

        val highlightBgColor = blendColor(accentColor, Color.WHITE, 0.80f)

        fun toStyledText(rawText: String, highlightTextColor: Int = primaryColor): CharSequence {
            val segments = InlineSpanParser.parse(rawText)
            if (segments.size <= 1 && segments.none { it.isBold || it.isHighlight }) {
                return rawText
            }
            val ssb = SpannableStringBuilder()
            segments.forEach { seg ->
                val start = ssb.length
                ssb.append(seg.text)
                val end = ssb.length
                if (end > start) {
                    if (seg.isBold) {
                        ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (seg.isHighlight) {
                        ssb.setSpan(BackgroundColorSpan(highlightBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        ssb.setSpan(ForegroundColorSpan(highlightTextColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            return ssb
        }

        fun buildStaticLayout(
            text: String,
            paint: TextPaint,
            width: Int,
            lineSpacingMult: Float = 1.28f,
            directionOverride: TextDirectionHeuristic = textDir,
            parseInlineSpans: Boolean = true
        ): StaticLayout {
            val formatted: CharSequence = if (parseInlineSpans) toStyledText(text) else text
            return StaticLayout.Builder
                .obtain(formatted, 0, formatted.length, paint, width.coerceAtLeast(40))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMult)
                .setIncludePad(false)
                .setTextDirection(directionOverride)
                .build()
        }

        fun drawStaticLayout(layout: StaticLayout, x: Float, y: Float) {
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
        }

        // Start Page 1 & Draw Modern Cover Header Banner
        startNewPage()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = displayBold
            textSize = 20f
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            typeface = bodyRegular
            textSize = 11.5f
        }
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            typeface = displayBold
            textSize = 9.5f
        }

        val innerHeaderWidth = (CONTENT_WIDTH - 40f).toInt()
        val titleLayout = buildStaticLayout(document.title, titlePaint, innerHeaderWidth, 1.2f)
        val subtitleLayout = document.subtitle?.let {
            buildStaticLayout(it, subtitlePaint, innerHeaderWidth, 1.25f)
        }
        val badgeLayout = document.headerBadge?.let {
            buildStaticLayout(it, badgePaint, innerHeaderWidth, 1.1f)
        }

        var bannerContentHeight = 20f + titleLayout.height
        if (badgeLayout != null) bannerContentHeight += badgeLayout.height + 8f
        if (subtitleLayout != null) bannerContentHeight += subtitleLayout.height + 8f
        bannerContentHeight += 20f

        val bannerRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + bannerContentHeight)
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor }
        canvas.drawRoundRect(bannerRect, 10f, 10f, bannerPaint)

        // Vertical accent bar on the reading-start edge
        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        val barRect = if (isRtl) {
            RectF(bannerRect.right - 6f, bannerRect.top, bannerRect.right, bannerRect.bottom)
        } else {
            RectF(bannerRect.left, bannerRect.top, bannerRect.left + 6f, bannerRect.bottom)
        }
        canvas.drawRoundRect(barRect, 3f, 3f, accentBarPaint)

        var headerY = cursorY + 18f
        val headerX = MARGIN_X + 20f
        if (badgeLayout != null) {
            drawStaticLayout(badgeLayout, headerX, headerY)
            headerY += badgeLayout.height + 8f
        }
        drawStaticLayout(titleLayout, headerX, headerY)
        headerY += titleLayout.height + 8f
        if (subtitleLayout != null) {
            drawStaticLayout(subtitleLayout, headerX, headerY)
        }

        cursorY += bannerContentHeight + 22f

        // Render Each Block
        document.blocks.forEach { block ->
            when (block) {
                is DocBlock.Heading -> {
                    val isH1 = block.level <= 1
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isH1) primaryColor else accentColor
                        typeface = displayBold
                        textSize = if (isH1) 14.5f else 12f
                    }
                    val padBefore = if (isH1) 10f else 6f
                    val textWidth = (CONTENT_WIDTH - if (isH1) 14f else 0f).toInt()
                    val layout = buildStaticLayout(block.text, paint, textWidth, 1.22f)
                    ensureSpace(padBefore + layout.height + 28f)
                    cursorY += padBefore

                    if (isH1) {
                        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
                        val indRect = if (isRtl) {
                            RectF(PAGE_WIDTH - MARGIN_X - 4f, cursorY + 2f, PAGE_WIDTH - MARGIN_X, cursorY + layout.height - 1f)
                        } else {
                            RectF(MARGIN_X, cursorY + 2f, MARGIN_X + 4f, cursorY + layout.height - 1f)
                        }
                        canvas.drawRoundRect(indRect, 2f, 2f, indicatorPaint)
                        val textX = if (isRtl) MARGIN_X else MARGIN_X + 12f
                        drawStaticLayout(layout, textX, cursorY)
                    } else {
                        drawStaticLayout(layout, MARGIN_X, cursorY)
                    }
                    cursorY += layout.height + if (isH1) 10f else 7f
                }

                is DocBlock.Paragraph -> {
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (block.emphasis) primaryColor else textDark
                        typeface = if (block.emphasis) bodyBold else bodyRegular
                        textSize = 10.8f
                    }
                    val layout = buildStaticLayout(block.text, paint, CONTENT_WIDTH.toInt(), 1.35f)
                    ensureSpace(layout.height + 8f)
                    drawStaticLayout(layout, MARGIN_X, cursorY)
                    cursorY += layout.height + 10f
                }

                is DocBlock.BulletList -> {
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textDark
                        typeface = bodyRegular
                        textSize = 10.5f
                    }
                    val markerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                        typeface = displayBold
                        textSize = 10.5f
                    }
                    block.items.forEachIndexed { idx, item ->
                        val marker = if (block.numbered) "${idx + 1}." else "•"
                        val markerWidth = 18f
                        val itemLayout = buildStaticLayout(item, paint, (CONTENT_WIDTH - markerWidth - 6f).toInt(), 1.32f)
                        ensureSpace(itemLayout.height + 6f)

                        if (isRtl) {
                            val markerX = PAGE_WIDTH - MARGIN_X - markerPaint.measureText(marker) - 2f
                            canvas.drawText(marker, markerX, cursorY + 10f, markerPaint)
                            drawStaticLayout(itemLayout, MARGIN_X, cursorY)
                        } else {
                            canvas.drawText(marker, MARGIN_X + 2f, cursorY + 10f, markerPaint)
                            drawStaticLayout(itemLayout, MARGIN_X + markerWidth, cursorY)
                        }
                        cursorY += itemLayout.height + 6f
                    }
                    cursorY += 6f
                }

                is DocBlock.Callout -> {
                    val bg = when (block.tone) {
                        "warning" -> Color.parseColor("#FEF3C7")
                        "highlight" -> blendColor(accentColor, Color.WHITE, 0.88f)
                        else -> surfaceTint
                    }
                    val barColor = when (block.tone) {
                        "warning" -> Color.parseColor("#D97706")
                        else -> accentColor
                    }
                    val cTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = primaryColor
                        typeface = displayBold
                        textSize = 11f
                    }
                    val cBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textDark
                        typeface = bodyRegular
                        textSize = 10.2f
                    }
                    val innerW = (CONTENT_WIDTH - 28f).toInt()
                    val tLayout = if (block.title.isNotBlank()) buildStaticLayout(block.title, cTitlePaint, innerW, 1.2f) else null
                    val bLayout = if (block.body.isNotBlank()) buildStaticLayout(block.body, cBodyPaint, innerW, 1.32f) else null

                    var boxH = 22f
                    if (tLayout != null) boxH += tLayout.height + 4f
                    if (bLayout != null) boxH += bLayout.height

                    ensureSpace(boxH + 12f)
                    val rect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + boxH)
                    canvas.drawRoundRect(rect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

                    val stripe = if (isRtl) {
                        RectF(rect.right - 4.5f, rect.top, rect.right, rect.bottom)
                    } else {
                        RectF(rect.left, rect.top, rect.left + 4.5f, rect.bottom)
                    }
                    canvas.drawRoundRect(stripe, 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor })

                    var yPos = cursorY + 11f
                    val xPos = MARGIN_X + 14f
                    if (tLayout != null) {
                        drawStaticLayout(tLayout, xPos, yPos)
                        yPos += tLayout.height + 4f
                    }
                    if (bLayout != null) {
                        drawStaticLayout(bLayout, xPos, yPos)
                    }
                    cursorY += boxH + 12f
                }

                is DocBlock.MetricsGrid -> {
                    val items = block.items
                    val cols = items.size.coerceIn(1, 3)
                    val gap = 10f
                    val cardW = (CONTENT_WIDTH - gap * (cols - 1)) / cols
                    val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = primaryColor
                        typeface = displayBold
                        textSize = 13.5f
                    }
                    val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textMuted
                        typeface = bodyRegular
                        textSize = 9.5f
                    }

                    items.chunked(cols).forEach { rowItems ->
                        val cardLayouts = rowItems.map { m ->
                            val inner = (cardW - 18f).toInt()
                            buildStaticLayout(m.value, valPaint, inner, 1.15f) to
                                buildStaticLayout(m.label, lblPaint, inner, 1.2f)
                        }
                        val rowH = (cardLayouts.maxOfOrNull { (v, l) -> v.height + l.height + 24f } ?: 54f)
                        ensureSpace(rowH + 10f)

                        rowItems.indices.forEach { idx ->
                            val visualIdx = if (isRtl) (rowItems.size - 1 - idx) else idx
                            val x = MARGIN_X + visualIdx * (cardW + gap)
                            val cardRect = RectF(x, cursorY, x + cardW, cursorY + rowH)
                            canvas.drawRoundRect(cardRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surfaceTint })
                            canvas.drawRoundRect(cardRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = borderLight
                                style = Paint.Style.STROKE
                                strokeWidth = 0.8f
                            })
                            val (vLayout, lLayout) = cardLayouts[idx]
                            drawStaticLayout(lLayout, x + 9f, cursorY + 10f)
                            drawStaticLayout(vLayout, x + 9f, cursorY + 14f + lLayout.height)
                        }
                        cursorY += rowH + 10f
                    }
                    cursorY += 4f
                }

                is DocBlock.TableBlock -> {
                    val colCount = maxOf(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
                    val colW = CONTENT_WIDTH / colCount
                    val cellPadX = 7f
                    val cellPadY = 7f

                    val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        typeface = displayBold
                        textSize = 9.8f
                    }
                    val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textDark
                        typeface = bodyRegular
                        textSize = 9.5f
                    }

                    fun measureRow(cells: List<String>, paint: TextPaint): Pair<List<StaticLayout>, Float> {
                        val layouts = (0 until colCount).map { col ->
                            val text = cells.getOrElse(col) { "" }
                            buildStaticLayout(text, paint, (colW - cellPadX * 2).toInt(), 1.22f)
                        }
                        val h = (layouts.maxOfOrNull { it.height } ?: 14) + cellPadY * 2
                        return layouts to h
                    }

                    fun drawRow(
                        layouts: List<StaticLayout>,
                        rowH: Float,
                        bgColor: Int
                    ) {
                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
                        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = borderLight
                            style = Paint.Style.STROKE
                            strokeWidth = 0.7f
                        }
                        val rowRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + rowH)
                        canvas.drawRect(rowRect, bgPaint)
                        canvas.drawRect(rowRect, strokePaint)

                        for (col in 0 until colCount) {
                            val visualCol = if (isRtl) (colCount - 1 - col) else col
                            val cellX = MARGIN_X + visualCol * colW
                            if (col > 0) {
                                canvas.drawLine(cellX, cursorY, cellX, cursorY + rowH, strokePaint)
                            }
                            drawStaticLayout(layouts[col], cellX + cellPadX, cursorY + cellPadY)
                        }
                        cursorY += rowH
                    }

                    val headerMeasured = if (block.headers.isNotEmpty()) {
                        measureRow(block.headers, headerPaint)
                    } else null

                    if (headerMeasured != null) {
                        ensureSpace(headerMeasured.second + 26f)
                        drawRow(headerMeasured.first, headerMeasured.second, primaryColor)
                    }

                    block.rows.forEachIndexed { rIdx, row ->
                        val measured = measureRow(row, cellPaint)
                        if (cursorY + measured.second > PAGE_HEIGHT - MARGIN_BOTTOM) {
                            startNewPage()
                            if (headerMeasured != null) {
                                drawRow(headerMeasured.first, headerMeasured.second, primaryColor)
                            }
                        }
                        val rowBg = if (rIdx % 2 == 0) Color.WHITE else surfaceTint
                        drawRow(measured.first, measured.second, rowBg)
                    }
                    cursorY += 12f
                }

                is DocBlock.BilingualList -> {
                    if (!block.title.isNullOrBlank()) {
                        val bTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = primaryColor
                            typeface = displayBold
                            textSize = 12f
                        }
                        val bTitleLayout = buildStaticLayout(block.title, bTitlePaint, CONTENT_WIDTH.toInt(), 1.2f)
                        ensureSpace(bTitleLayout.height + 38f)
                        drawStaticLayout(bTitleLayout, MARGIN_X, cursorY)
                        cursorY += bTitleLayout.height + 6f
                    }

                    val colGap = 1f
                    val enColW = CONTENT_WIDTH * 0.50f
                    val arColW = CONTENT_WIDTH - enColW
                    val cellPadX = 9f
                    val cellPadY = 8f

                    val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textDark
                        typeface = bodyRegular
                        textSize = 10f
                    }
                    val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                        typeface = displayBold
                        textSize = 8.2f
                    }
                    val arPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textDark
                        typeface = bodyRegular
                        textSize = 10.2f
                    }
                    val hdrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        typeface = displayBold
                        textSize = 9.5f
                    }

                    val hdrEn = buildStaticLayout("English", hdrPaint, (enColW - cellPadX * 2).toInt(), 1.15f, TextDirectionHeuristics.LTR, false)
                    val hdrAr = buildStaticLayout("المعنى / الترجمة", hdrPaint, (arColW - cellPadX * 2).toInt(), 1.15f, TextDirectionHeuristics.RTL, false)
                    val hdrH = maxOf(hdrEn.height, hdrAr.height) + cellPadY * 2

                    fun drawBilingualHeader() {
                        val headerRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + hdrH)
                        canvas.drawRoundRect(headerRect, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor })
                        drawStaticLayout(hdrEn, MARGIN_X + cellPadX, cursorY + cellPadY)
                        drawStaticLayout(hdrAr, MARGIN_X + enColW + cellPadX, cursorY + cellPadY)
                        cursorY += hdrH
                    }

                    ensureSpace(hdrH + 34f)
                    drawBilingualHeader()

                    block.entries.forEachIndexed { rIdx, entry ->
                        val enLayout = buildStaticLayout(
                            text = entry.english,
                            paint = enPaint,
                            width = (enColW - cellPadX * 2).toInt(),
                            lineSpacingMult = 1.25f,
                            directionOverride = TextDirectionHeuristics.LTR
                        )
                        val noteLayout = entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                            buildStaticLayout(
                                text = note,
                                paint = notePaint,
                                width = (enColW - cellPadX * 2).toInt(),
                                lineSpacingMult = 1.15f,
                                directionOverride = TextDirectionHeuristics.FIRSTSTRONG_LTR
                            )
                        }
                        val arLayout = buildStaticLayout(
                            text = entry.arabic,
                            paint = arPaint,
                            width = (arColW - cellPadX * 2).toInt(),
                            lineSpacingMult = 1.28f,
                            directionOverride = TextDirectionHeuristics.RTL
                        )

                        val enTotalH = enLayout.height + (noteLayout?.let { it.height + 3f } ?: 0f)
                        val rowH = maxOf(enTotalH, arLayout.height.toFloat()) + cellPadY * 2

                        if (cursorY + rowH > PAGE_HEIGHT - MARGIN_BOTTOM) {
                            startNewPage()
                            drawBilingualHeader()
                        }

                        val rowBg = if (rIdx % 2 == 0) Color.WHITE else surfaceTint
                        val rowRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + rowH)
                        canvas.drawRect(rowRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rowBg })

                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = borderLight
                            style = Paint.Style.STROKE
                            strokeWidth = 0.7f
                        }
                        canvas.drawRect(rowRect, borderPaint)
                        canvas.drawLine(
                            MARGIN_X + enColW,
                            cursorY,
                            MARGIN_X + enColW,
                            cursorY + rowH,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = accentColor
                                strokeWidth = colGap
                                alpha = 90
                            }
                        )

                        drawStaticLayout(enLayout, MARGIN_X + cellPadX, cursorY + cellPadY)
                        if (noteLayout != null) {
                            drawStaticLayout(noteLayout, MARGIN_X + cellPadX, cursorY + cellPadY + enLayout.height + 3f)
                        }
                        drawStaticLayout(arLayout, MARGIN_X + enColW + cellPadX, cursorY + cellPadY)

                        cursorY += rowH
                    }
                    cursorY += 12f
                }

                DocBlock.Divider -> {
                    ensureSpace(14f)
                    val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = borderLight
                        strokeWidth = 0.9f
                    }
                    canvas.drawLine(MARGIN_X, cursorY + 5f, PAGE_WIDTH - MARGIN_X, cursorY + 5f, divPaint)
                    cursorY += 14f
                }
            }
        }

        // Finish last page with footer
        finishCurrentPage()

        // Re-open? PdfDocument pages cannot be edited after finishPage, so let's instead draw footers live!
        // Note: To include accurate total page count, or clean page numbers per page without blank pages,
        // wait: we don't know total pages ahead of time without 2 passes, so let's do a fast 2-pass or draw clean "صفحة N" footer?
        // Actually, finishing and closing is fast — let's check: we can draw page footers right before finishPage!
        // Wait: currentPage was finished above; let's modify startNewPage / finish to draw footer before finishPage!
        // Let's write a helper `drawFooter(canvas, pageNum, document, textMuted, bodyRegular)` called inside finish!
        //Wait—let's update the file cleanly? No, I'm writing it right now! Let's put the footer drawing inside `finishCurrentPage()` before `pdf.finishPage(currentPage)`!
        // Hold on: let's make sure the code I'm passing in write_to_file right now already does `drawFooter()` before `pdf.finishPage`!

        FileOutputStream(outputFile).use { fos ->
            pdf.writeTo(fos)
        }
        pdf.close()
        return currentPageNum
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun blendColor(colorA: Int, colorB: Int, ratioB: Float): Int {
        val ratioA = 1f - ratioB
        val r = (Color.red(colorA) * ratioA + Color.red(colorB) * ratioB).toInt()
        val g = (Color.green(colorA) * ratioA + Color.green(colorB) * ratioB).toInt()
        val b = (Color.blue(colorA) * ratioA + Color.blue(colorB) * ratioB).toInt()
        return Color.rgb(r, g, b)
    }
}
