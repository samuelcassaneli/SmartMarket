package com.smartmarket.ui.screens.flyers

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ErrorBox
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended
import kotlinx.coroutines.launch
import java.io.File

/** Leitura manual de encarte: foto, imagens da galeria ou PDF, com revisão antes de salvar. */
@Composable
fun FlyerScanScreen(navController: NavController) {
    val vm = appViewModel { c ->
        FlyerScanViewModel(c.ocrProcessor, c.productMatcher, c.priceRepository, c.db.productDao(), c.db.supermarketDao())
    }
    val context = LocalContext.current
    val markets by vm.markets.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val marketId by vm.marketId.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    // seleciona o primeiro mercado com encarte (ou o primeiro) por padrão
    LaunchedEffect(markets) {
        if (marketId == null && markets.isNotEmpty()) {
            vm.selectMarket((markets.firstOrNull { it.flyerUrl.isNotBlank() } ?: markets.first()).id)
        }
    }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.saved.collect { n ->
            navController.previousBackStackEntry?.savedStateHandle?.set("snackbar", "$n preços salvos")
            scope.launch { snackbar.showSnackbar("$n preços salvos") }
            navController.popBackStack()
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) vm.readImages(listOf(Uri.parse(uri)))
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "encarte_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri.toString()
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else scope.launch { snackbar.showSnackbar("Permita o uso da câmera para fotografar o encarte") }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        vm.readImages(uris)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readPdf(uri)
    }

    val saveCount = items.count { it.canSave }
    val inReview = phase is ScanPhase.Review

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = "Ler encarte",
                subtitle = if (inReview) "${items.size} ofertas encontradas" else null,
                onBack = { navController.popBackStack() },
                actions = {
                    if (inReview) TextButton(onClick = { vm.reset() }) { Text("Recomeçar") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (inReview) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    Button(
                        onClick = { vm.save() },
                        enabled = saveCount > 0 && marketId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                            .height(52.dp)
                    ) {
                        Text(if (saveCount == 1) "Salvar 1 preço" else "Salvar $saveCount preços", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MarketSelector(
                markets = markets,
                selectedId = marketId,
                onSelect = vm::selectMarket,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            )
            when (val p = phase) {
                ScanPhase.Idle -> SourceChooser(
                    onCamera = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    onGallery = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPdf = { pickPdf.launch(arrayOf("application/pdf")) }
                )
                is ScanPhase.Reading -> ReadingState(p)
                is ScanPhase.Error -> ErrorBox(p.message, onRetry = { vm.reset() })
                ScanPhase.Review -> ReviewList(
                    items = items,
                    onPrice = vm::setPrice,
                    onInclude = vm::setInclude,
                    onPickProduct = { pickerFor = it }
                )
            }
        }
    }

    val pickingId = pickerFor
    val picking = items.firstOrNull { it.id == pickingId }
    if (picking != null) {
        ProductPickerSheet(
            sourceText = picking.original,
            products = products,
            suggestions = picking.suggestions,
            currentProductId = picking.product?.id,
            onDismiss = { pickerFor = null },
            onPick = { vm.setProduct(picking.id, it); pickerFor = null },
            onUnlink = { vm.setProduct(picking.id, null); pickerFor = null },
            onCreate = { name, category -> vm.createProduct(picking.id, name, category); pickerFor = null }
        )
    }
}

@Composable
private fun MarketSelector(
    markets: List<com.smartmarket.data.entity.Supermarket>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = markets.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name ?: if (markets.isEmpty()) "Cadastre um mercado primeiro" else "Escolha o mercado",
            onValueChange = {},
            readOnly = true,
            label = { Text("Mercado") },
            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            markets.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name) },
                    leadingIcon = { MarketAvatar(m.name, m.colorHex, size = 28.dp) },
                    onClick = { onSelect(m.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SourceChooser(onCamera: () -> Unit, onGallery: () -> Unit, onPdf: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            "Fotografe o encarte ou escolha as imagens ou o PDF. A leitura é feita no celular, sem internet, e você revisa tudo antes de salvar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SourceCard(Icons.Default.PhotoCamera, "Tirar foto", "Fotografe uma página do encarte impresso", onCamera)
        SourceCard(Icons.Default.Image, "Imagens da galeria", "Uma ou várias páginas salvas no celular", onGallery)
        SourceCard(Icons.Default.PictureAsPdf, "Arquivo PDF", "Encarte baixado do site do mercado", onPdf)
    }
}

@Composable
private fun SourceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReadingState(p: ScanPhase.Reading) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(p.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.lg))
        if (p.total > 0) {
            LinearProgressIndicator(progress = { p.current.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Encartes grandes podem levar alguns minutos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReviewList(
    items: List<ScanItem>,
    onPrice: (Int, String) -> Unit,
    onInclude: (Int, Boolean) -> Unit,
    onPickProduct: (Int) -> Unit
) {
    val linked = items.filter { it.product != null }
    val unlinked = items.filter { it.product == null }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (linked.isNotEmpty()) {
            item(key = "h-linked") { SectionHeader("Vinculados (${linked.size})") }
            items(linked, key = { it.id }) { ScanItemCard(it, onPrice, onInclude, onPickProduct) }
        }
        if (unlinked.isNotEmpty()) {
            item(key = "h-unlinked") {
                Column {
                    SectionHeader("Não vinculados (${unlinked.size})")
                    Text(
                        "Toque em \"Vincular\" para escolher o produto ou criar um novo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(unlinked, key = { it.id }) { ScanItemCard(it, onPrice, onInclude, onPickProduct) }
        }
    }
}

@Composable
private fun ScanItemCard(
    item: ScanItem,
    onPrice: (Int, String) -> Unit,
    onInclude: (Int, Boolean) -> Unit,
    onPickProduct: (Int) -> Unit
) {
    val ext = MaterialTheme.extended
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = item.include,
                onCheckedChange = { onInclude(item.id, it) },
                enabled = item.product != null
            )
            Column(Modifier.weight(1f).padding(top = Spacing.xs)) {
                Text(
                    "\"${item.original}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                if (item.product != null) {
                    Text(item.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Não vinculado", style = MaterialTheme.typography.titleSmall, color = ext.warning)
                    if (item.suggestions.isNotEmpty()) {
                        Text(
                            "Sugestões: " + item.suggestions.take(2).joinToString(" · ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                    if (item.viaAlias) Tag("Já conhecido")
                    else if (item.product != null && item.confidence < 0.8) Tag("Confira", container = ext.warningContainer, content = ext.onWarningContainer)
                    if (item.note.isNotBlank()) Tag(item.note)
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.priceText,
                onValueChange = { onPrice(item.id, it) },
                prefix = { Text("R$ ") },
                singleLine = true,
                isError = item.price == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.width(150.dp)
            )
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { onPickProduct(item.id) }) {
                Icon(
                    if (item.product != null) Icons.Default.Edit else Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(if (item.product != null) "Trocar" else "Vincular")
            }
        }
    }
}
