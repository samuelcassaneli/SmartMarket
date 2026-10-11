package com.smartmarket.ui.screens.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.smartmarket.domain.nfce.NfceException
import com.smartmarket.domain.nfce.QrPayloadParser
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.screens.receipts.ManualKeyDialog
import com.smartmarket.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Leitor do QR Code da NFC-e: câmera ao vivo, foto da galeria (QR ou chave lida no texto) e chave digitada.
 * Com [openGallery] = true, abre o seletor de fotos direto.
 */
@Composable
fun QrScannerScreen(openGallery: Boolean, navController: NavController) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val torch = rememberTorchState()

    var navigated by remember { mutableStateOf(false) }
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
    var readingImage by remember { mutableStateOf(false) }
    var galleryLaunched by rememberSaveable { mutableStateOf(false) }
    var lastRejected by remember { mutableStateOf<String?>(null) }

    fun goToReview(payload: String) {
        if (navigated) return
        navigated = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        navController.navigate(Routes.receiptReview(payload)) {
            popUpTo(Routes.SCAN_QR) { inclusive = true }
        }
    }

    /** Valida o texto lido; se não for NFC-e, avisa (uma vez por código) e segue lendo. */
    fun handleScanned(value: String) {
        if (navigated) return
        val result = QrPayloadParser.parse(value)
        if (result.isSuccess) {
            goToReview(value)
        } else if (lastRejected != value) {
            lastRejected = value
            val msg = (result.exceptionOrNull() as? NfceException)?.message ?: "Este QR Code não é de uma nota fiscal."
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        readingImage = true
        scope.launch {
            val result = runCatching { ImageCodeReader.readReceiptImage(context, uri) }.getOrNull()
            readingImage = false
            when (result) {
                is ImageCodeReader.Result.Qr -> {
                    val parsed = QrPayloadParser.parse(result.value)
                    if (parsed.isSuccess) goToReview(result.value)
                    else snackbar.showSnackbar(parsed.exceptionOrNull()?.message ?: "Este QR Code não é de uma nota fiscal.")
                }
                is ImageCodeReader.Result.Key -> {
                    val parsed = QrPayloadParser.parse(result.key.digits)
                    if (parsed.isSuccess) goToReview(result.key.digits)
                    else snackbar.showSnackbar(parsed.exceptionOrNull()?.message ?: "Chave inválida.")
                }
                else -> snackbar.showSnackbar("Não encontramos o QR Code nem a chave nesta foto. Tente uma foto mais nítida ou digite a chave.")
            }
        }
    }

    fun launchGallery() {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(openGallery) {
        if (openGallery && !galleryLaunched) {
            galleryLaunched = true
            launchGallery()
        }
    }

    if (showKeyDialog) {
        ManualKeyDialog(
            onConfirm = { digits ->
                showKeyDialog = false
                goToReview(digits)
            },
            onDismiss = { showKeyDialog = false }
        )
    }

    ScreenScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CameraPermissionAwareTopBar(onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraPermissionGate(
                rationale = "Usamos a câmera só para ler o QR Code da nota fiscal. Nada é gravado ou enviado.",
                secondaryActions = {
                    ScanActions(
                        onGallery = { launchGallery() },
                        onKey = { showKeyDialog = true }
                    )
                }
            ) {
                Box(Modifier.fillMaxSize()) {
                    BarcodeCameraView(
                        formats = intArrayOf(Barcode.FORMAT_QR_CODE),
                        onDetected = { handleScanned(it) },
                        paused = navigated || showKeyDialog || readingImage,
                        frame = ScanFrame.SQUARE,
                        torch = torch,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Dica e controles sobre a imagem da câmera
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                "Aponte para o QR Code da nota fiscal",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            )
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (torch.available) {
                                FilledTonalIconButton(
                                    onClick = { torch.toggle() },
                                    modifier = Modifier.padding(end = Spacing.sm).height(48.dp).width(48.dp)
                                ) {
                                    Icon(
                                        if (torch.enabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                                        contentDescription = if (torch.enabled) "Desligar lanterna" else "Ligar lanterna"
                                    )
                                }
                            }
                            ScanActions(onGallery = { launchGallery() }, onKey = { showKeyDialog = true })
                        }
                    }
                }
            }

            if (readingImage) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.md))
                        Text("Lendo a foto…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionAwareTopBar(onBack: () -> Unit) {
    AppTopBar(title = "Ler nota fiscal", subtitle = "QR Code da NFC-e", onBack = onBack)
}

@Composable
private fun ScanActions(onGallery: () -> Unit, onKey: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilledTonalButton(onClick = onGallery, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Galeria")
        }
        FilledTonalButton(onClick = onKey, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Default.Keyboard, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Digitar chave")
        }
    }
}
