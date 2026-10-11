package com.smartmarket.ui.screens.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.entity.Product
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.screens.receipts.ProductForm
import com.smartmarket.ui.screens.receipts.ProductPickerList
import com.smartmarket.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BarcodeScanViewModel(private val productDao: ProductDao) : ViewModel() {

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Código lido sem produto cadastrado (abre o sheet). */
    private val _unknownCode = MutableStateFlow<String?>(null)
    val unknownCode: StateFlow<String?> = _unknownCode.asStateFlow()

    /** Produto a abrir (a tela navega e limpa). */
    private val _openProduct = MutableStateFlow<Long?>(null)
    val openProduct: StateFlow<Long?> = _openProduct.asStateFlow()

    private var busy = false

    fun onCode(code: String) {
        if (busy || _unknownCode.value != null || _openProduct.value != null) return
        busy = true
        viewModelScope.launch {
            val product = productDao.getProductByBarcode(code)
            if (product != null) _openProduct.value = product.id else _unknownCode.value = code
            busy = false
        }
    }

    fun dismissUnknown() {
        _unknownCode.value = null
    }

    fun linkTo(product: Product) {
        val code = _unknownCode.value ?: return
        viewModelScope.launch {
            productDao.updateProduct(product.copy(barcode = code))
            _unknownCode.value = null
            _openProduct.value = product.id
        }
    }

    fun create(name: String, category: String, unit: String) {
        val code = _unknownCode.value ?: return
        viewModelScope.launch {
            val id = productDao.insertProduct(
                Product(name = name, category = category.ifBlank { "Geral" }, unit = unit.ifBlank { "un" }, barcode = code)
            )
            _unknownCode.value = null
            _openProduct.value = id
        }
    }

    fun consumeOpen() {
        _openProduct.value = null
    }
}

/** Lê o código de barras do produto (EAN-13, EAN-8, UPC, Code 128) e abre ou cadastra o produto. */
@Composable
fun BarcodeScanScreen(navController: NavController) {
    val vm = appViewModel { c -> BarcodeScanViewModel(c.db.productDao()) }
    val unknownCode by vm.unknownCode.collectAsStateWithLifecycle()
    val openProduct by vm.openProduct.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val torch = rememberTorchState()

    LaunchedEffect(openProduct) {
        val id = openProduct ?: return@LaunchedEffect
        vm.consumeOpen()
        navController.navigate(Routes.productDetail(id)) {
            popUpTo(Routes.SCAN_BARCODE) { inclusive = true }
        }
    }
    LaunchedEffect(unknownCode) {
        if (unknownCode != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    ScreenScaffold(
        topBar = { AppTopBar(title = "Código de barras", subtitle = "Aponte para o código do produto", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            CameraPermissionGate(
                rationale = "Usamos a câmera só para ler o código de barras dos produtos. Nada é gravado ou enviado."
            ) {
                Box(Modifier.fillMaxSize()) {
                    BarcodeCameraView(
                        formats = intArrayOf(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_CODE_128
                        ),
                        onDetected = { vm.onCode(it) },
                        paused = unknownCode != null || openProduct != null,
                        frame = ScanFrame.WIDE,
                        torch = torch,
                        modifier = Modifier.fillMaxSize()
                    )
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
                                "Centralize o código de barras na moldura",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            )
                        }
                        if (torch.available) {
                            Spacer(Modifier.height(Spacing.lg))
                            FilledTonalIconButton(onClick = { torch.toggle() }, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    if (torch.enabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                                    contentDescription = if (torch.enabled) "Desligar lanterna" else "Ligar lanterna"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    unknownCode?.let { code ->
        UnknownBarcodeSheet(
            code = code,
            products = products,
            onDismiss = vm::dismissUnknown,
            onPick = vm::linkTo,
            onCreate = vm::create
        )
    }
}

@Composable
private fun UnknownBarcodeSheet(
    code: String,
    products: List<Product>,
    onDismiss: () -> Unit,
    onPick: (Product) -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by rememberSaveable(code) { mutableStateOf(false) }
    var query by rememberSaveable(code) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding()) {
            Text("Produto não cadastrado", style = MaterialTheme.typography.titleMedium)
            Text(
                "Código $code. Vincule a um produto existente ou crie um novo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            if (creating) {
                ProductForm(
                    initial = Product(name = "", category = "Geral", unit = "un"),
                    confirmLabel = "Criar produto",
                    onConfirm = onCreate,
                    onCancel = { creating = false }
                )
            } else {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Buscar produto para vincular")
                Spacer(Modifier.height(Spacing.sm))
                ProductPickerList(
                    products = products,
                    query = query,
                    suggestions = emptyList(),
                    selectedId = null,
                    onPick = onPick,
                    modifier = Modifier.heightIn(max = 340.dp)
                )
                Spacer(Modifier.height(Spacing.sm))
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Criar novo produto")
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
