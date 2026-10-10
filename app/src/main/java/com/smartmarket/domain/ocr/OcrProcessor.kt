package com.smartmarket.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartmarket.data.entity.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrProcessor(private val context: Context) {

    // Inicializa o TextRecognizer nativo offline do ML Kit
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processa um Bitmap e retorna os itens encontrados
     */
    suspend fun processBitmap(
        bitmap: Bitmap,
        registeredProducts: List<Product>
    ): List<ExtractedOcrItem> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(result)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

        val rawElements = mutableListOf<EncarteParser.RawTextElement>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                rawElements.add(
                    EncarteParser.RawTextElement(
                        text = line.text,
                        boundingBox = line.boundingBox
                    )
                )
            }
        }

        EncarteParser.processDetectedElements(rawElements, registeredProducts)
    }

    /**
     * Processa um arquivo PDF (renderizando página a página em alta definição)
     */
    suspend fun processPdfUri(
        pdfUri: Uri,
        registeredProducts: List<Product>
    ): List<ExtractedOcrItem> = withContext(Dispatchers.IO) {
        val allExtracted = mutableListOf<ExtractedOcrItem>()
        val tempFile = File(context.cacheDir, "temp_ocr_encarte.pdf")

        // Copia do ContentResolver para arquivo temporário acessível pelo PdfRenderer
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)

        try {
            val pageCount = pdfRenderer.pageCount
            for (pageIndex in 0 until pageCount) {
                val page = pdfRenderer.openPage(pageIndex)
                // Renderiza em 2x de escala para obter alta nitidez no OCR
                val bitmapWidth = page.width * 2
                val bitmapHeight = page.height * 2
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val itemsForPage = processBitmap(bitmap, registeredProducts)
                allExtracted.addAll(itemsForPage)
                bitmap.recycle()
            }
        } finally {
            pdfRenderer.close()
            fileDescriptor.close()
            tempFile.delete()
        }

        allExtracted
    }
}
