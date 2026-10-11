package com.smartmarket.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Progresso da leitura: página atual (1-based) e total. */
typealias PageProgress = (current: Int, total: Int) -> Unit

/**
 * Lê texto de imagens e PDFs com o ML Kit (modelo latino empacotado, offline) e devolve
 * linhas cruas ([OcrPage]) para o [EncarteParser]. Cuida da memória: reduz bitmaps grandes,
 * renderiza PDF uma página por vez e recicla tudo.
 */
class OcrProcessor(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val pdfText by lazy { PdfTextReader(context) }

    /** Lê um bitmap. Não recicla o bitmap recebido. */
    suspend fun readBitmap(bitmap: Bitmap, pageIndex: Int = 0): OcrPage = withContext(Dispatchers.Default) {
        val scaled = downscale(bitmap)
        try {
            val result = recognize(InputImage.fromBitmap(scaled, 0))
            OcrPage(pageIndex, toLines(result), image = compressForAi(scaled))
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    /** Lê uma imagem (Uri de galeria/câmera). */
    suspend fun readImageUri(uri: Uri, pageIndex: Int = 0): OcrPage = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled({ context.contentResolver.openInputStream(uri) })
            ?: throw IllegalArgumentException("Não foi possível abrir a imagem")
        try {
            readBitmap(bitmap, pageIndex)
        } finally {
            bitmap.recycle()
        }
    }

    /** Lê um arquivo local de imagem. */
    suspend fun readImageFile(file: File, pageIndex: Int = 0): OcrPage = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled({ file.inputStream() })
            ?: throw IllegalArgumentException("Imagem inválida: ${file.name}")
        try {
            readBitmap(bitmap, pageIndex)
        } finally {
            bitmap.recycle()
        }
    }

    /** Lê um PDF vindo de um Uri (copia para o cache, pois o PdfRenderer precisa de arquivo buscável). */
    suspend fun readPdfUri(uri: Uri, onProgress: PageProgress = { _, _ -> }): List<OcrPage> = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "ocr_${System.nanoTime()}.pdf")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Não foi possível abrir o PDF")
            input.use { inp -> FileOutputStream(temp).use { out -> inp.copyTo(out) } }
            readPdfFile(temp, onProgress)
        } finally {
            temp.delete()
        }
    }

    /**
     * Lê um PDF local. Usa o texto embutido (exato) quando a página tem; as páginas escaneadas
     * (só imagem) são renderizadas em 2x e passam pelo OCR.
     */
    suspend fun readPdfFile(file: File, onProgress: PageProgress = { _, _ -> }): List<OcrPage> = withContext(Dispatchers.IO) {
        val textPages = runCatching { pdfText.read(file) }.getOrNull()
        if (textPages != null && textPages.all { it != null }) {
            onProgress(textPages.size, textPages.size)
            return@withContext textPages.mapIndexed { i, lines -> OcrPage(i, lines!!, fromPdfText = true) }
        }
        val pages = mutableListOf<OcrPage>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val total = renderer.pageCount
                for (i in 0 until total) {
                    onProgress(i + 1, total)
                    val textLines = textPages?.getOrNull(i)
                    if (textLines != null) {
                        pages += OcrPage(i, textLines, fromPdfText = true)
                        continue
                    }
                    val bitmap = renderer.openPage(i).use { page ->
                        var w = page.width * 2
                        var h = page.height * 2
                        val longest = max(w, h)
                        if (longest > MAX_SIDE) {
                            val f = MAX_SIDE.toFloat() / longest
                            w = (w * f).toInt(); h = (h * f).toInt()
                        }
                        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE) // PDF com fundo transparente vira preto sem isso
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                    try {
                        pages += readBitmap(bitmap, i)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        pages
    }

    /** Detecta o tipo pelo conteúdo (%PDF) e lê o arquivo. */
    suspend fun readFile(file: File, pageIndex: Int = 0, onProgress: PageProgress = { _, _ -> }): List<OcrPage> =
        if (isPdf(file)) readPdfFile(file, onProgress) else listOf(readImageFile(file, pageIndex))

    /** Lê um Uri escolhido pelo usuário (imagem ou PDF). */
    suspend fun readUri(uri: Uri, onProgress: PageProgress = { _, _ -> }): List<OcrPage> {
        val type = context.contentResolver.getType(uri).orEmpty()
        return if (type == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")) {
            readPdfUri(uri, onProgress)
        } else {
            onProgress(1, 1)
            listOf(readImageUri(uri))
        }
    }

    // ------------------------------------------------------------------ internos

    /** JPEG reduzido (lado maior até 1600px) para enviar à IA, se ativada. */
    private fun compressForAi(bitmap: Bitmap): ByteArray? = runCatching {
        val longest = max(bitmap.width, bitmap.height)
        val small = if (longest > AI_MAX_SIDE) {
            val f = AI_MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
        } else bitmap
        try {
            java.io.ByteArrayOutputStream().use { out ->
                small.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }.getOrNull()

    private suspend fun recognize(image: InputImage): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    private fun toLines(text: Text): List<OcrLine> {
        val out = mutableListOf<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                out += OcrLine(line.text, line.boundingBox?.toBox())
            }
        }
        return out
    }

    private fun Rect.toBox() = Box(left, top, right, bottom)

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE) return bitmap
        val f = MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
    }

    /** Decodifica com inSampleSize para não estourar memória com fotos de 12+ MP. */
    private fun decodeSampled(open: () -> java.io.InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    companion object {
        private const val AI_MAX_SIDE = 1600
        const val MAX_SIDE = 2500

        fun isPdf(file: File): Boolean = runCatching {
            file.inputStream().use { s ->
                val head = ByteArray(4)
                s.read(head) == 4 && String(head, Charsets.US_ASCII) == "%PDF"
            }
        }.getOrDefault(false)
    }
}
