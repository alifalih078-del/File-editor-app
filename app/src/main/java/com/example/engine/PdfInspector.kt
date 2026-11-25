package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

data class PdfSourceInfo(
    val pageCount: Int,
    val thumbnail: Bitmap?,
    val bytes: ByteArray
)

object PdfInspector {
    private const val MAX_INLINE_PDF_BYTES = 16 * 1024 * 1024 // 16 MB safe limit under 20 MB

    fun inspectPdfUri(context: Context, uri: Uri): PdfSourceInfo {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("تعذر قراءة ملف PDF المختار.")

        if (bytes.isEmpty()) {
            throw IllegalStateException("ملف PDF المختار فارغ.")
        }
        if (bytes.size > MAX_INLINE_PDF_BYTES) {
            throw IllegalArgumentException("حجم ملف PDF يتجاوز الحد المسموح (16 ميجابايت). يرجى اختيار ملف أصغر.")
        }

        var pageCount = 1
        var thumbnail: Bitmap? = null

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        pfd?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pageCount = renderer.pageCount.coerceAtLeast(1)
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val targetWidth = 420
                        val targetHeight = (page.height.toFloat() / page.width.toFloat() * targetWidth)
                            .toInt()
                            .coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumbnail = bmp
                    }
                }
            }
        }

        return PdfSourceInfo(
            pageCount = pageCount,
            thumbnail = thumbnail,
            bytes = bytes
        )
    }

    fun getPdfPageCount(file: File): Int {
        if (!file.exists()) return 0
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    fun renderPdfPage(file: File, pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? {
        if (!file.exists()) return null
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                    val targetHeight = (targetWidthPx * ratio).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
