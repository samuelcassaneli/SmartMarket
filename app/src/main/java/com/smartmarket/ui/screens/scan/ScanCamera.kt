package com.smartmarket.ui.screens.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.theme.Spacing
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Formato da moldura: quadrada para QR, larga para código de barras. */
enum class ScanFrame { SQUARE, WIDE }

/** Controle da lanterna exposto para a tela. */
class TorchState {
    internal var camera: Camera? = null
    var enabled by mutableStateOf(false)
        private set
    var available by mutableStateOf(false)
        internal set

    fun toggle() {
        val cam = camera ?: return
        enabled = !enabled
        cam.cameraControl.enableTorch(enabled)
    }
}

@Composable
fun rememberTorchState(): TorchState = remember { TorchState() }

fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Câmera ao vivo lendo códigos com ML Kit. Reaproveitada pelo leitor de NFC-e (QR) e de código de barras.
 * [onDetected] recebe o valor bruto; [paused] suspende a análise (ex.: enquanto um sheet está aberto).
 */
@Composable
fun BarcodeCameraView(
    formats: IntArray,
    onDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    frame: ScanFrame = ScanFrame.SQUARE,
    torch: TorchState? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDetected by rememberUpdatedState(onDetected)
    val pausedFlag = remember { AtomicBoolean(paused) }
    pausedFlag.set(paused)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val options = if (formats.size > 1) {
            BarcodeScannerOptions.Builder().setBarcodeFormats(formats[0], *formats.copyOfRange(1, formats.size)).build()
        } else {
            BarcodeScannerOptions.Builder().setBarcodeFormats(formats.firstOrNull() ?: Barcode.FORMAT_QR_CODE).build()
        }
        val scanner = BarcodeScanning.getClient(options)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            val cameraProvider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, CodeAnalyzer(scanner, pausedFlag) { value ->
                ContextCompat.getMainExecutor(context).execute { currentOnDetected(value) }
            })
            runCatching {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                torch?.camera = camera
                torch?.available = camera.cameraInfo.hasFlashUnit()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
            torch?.camera = null
            scanner.close()
            executor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanOverlay(frame = frame, modifier = Modifier.fillMaxSize())
    }
}

/** Analisador do ML Kit: processa um quadro por vez e devolve o primeiro código lido. */
private class CodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val paused: AtomicBoolean,
    private val onValue: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || paused.get()) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
                if (value != null && !paused.get()) onValue(value)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** Escurece a tela fora da moldura e desenha os cantos. Usa preto/branco translúcidos sobre a imagem da câmera. */
@Composable
private fun ScanOverlay(frame: ScanFrame, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val w = size.width
        val h = size.height
        val boxW = if (frame == ScanFrame.SQUARE) minOf(w, h) * 0.68f else w * 0.84f
        val boxH = if (frame == ScanFrame.SQUARE) boxW else boxW * 0.45f
        val left = (w - boxW) / 2f
        val top = (h - boxH) / 2.4f
        val radius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
        // Escurecimento da câmera (scrim) — sobre imagem de câmera, independe do tema
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = radius,
            blendMode = BlendMode.Clear
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = radius,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

/**
 * Pede a permissão de câmera explicando o motivo. Quando concedida, mostra [content].
 * Se o usuário negar, oferece abrir as configurações do app.
 */
@Composable
fun CameraPermissionGate(
    rationale: String,
    modifier: Modifier = Modifier,
    secondaryActions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
    }
    if (granted) {
        content()
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(icon = Icons.Default.PhotoCamera, size = 72.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text("Permitir a câmera", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
        if (askedOnce) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Abrir configurações") }
        }
        Spacer(Modifier.height(Spacing.lg))
        secondaryActions()
    }
}
