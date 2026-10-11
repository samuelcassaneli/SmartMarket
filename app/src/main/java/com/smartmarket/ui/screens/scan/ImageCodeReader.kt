package com.smartmarket.ui.screens.scan

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartmarket.domain.nfce.NfceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lê o QR da NFC-e de uma foto; se não achar, procura a chave de 44 dígitos no texto da foto. */
object ImageCodeReader {

    sealed interface Result {
        data class Qr(val value: String) : Result
        data class Key(val key: NfceKey) : Result
        data object NotFound : Result
    }

    suspend fun readReceiptImage(context: Context, uri: Uri): Result = withContext(Dispatchers.Default) {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }

        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val codes = try {
            runCatching { scanner.process(image).awaitTask() }.getOrDefault(emptyList())
        } finally {
            scanner.close()
        }
        codes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }?.let { return@withContext Result.Qr(it) }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = try {
            runCatching { recognizer.process(image).awaitTask().text }.getOrDefault("")
        } finally {
            recognizer.close()
        }
        NfceKey.findInText(text)?.let { Result.Key(it) } ?: Result.NotFound
    }
}

/** Espera uma Task do Play Services sem depender de kotlinx-coroutines-play-services. */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
    addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}
