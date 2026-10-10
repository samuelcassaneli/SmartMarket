package com.smartmarket.ui.screens.ocr

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmarket.data.entity.Product
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(
    viewModel: OcrViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val extractedItems by viewModel.extractedItems.collectAsState()
    val supermarkets by viewModel.supermarkets.collectAsState()
    val products by viewModel.products.collectAsState()
    val selectedSupermarketId by viewModel.selectedSupermarketId.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    var supermarketExpanded by remember { mutableStateOf(false) }
    var editDialogItemIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(supermarkets) {
        if (selectedSupermarketId == null && supermarkets.isNotEmpty()) {
            viewModel.setSelectedSupermarket(supermarkets.first().id)
        }
    }

    // Launcher para selecionar Imagens da Galeria/Câmera
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.let { b -> viewModel.processImageBitmap(b) }
        }
    }

    // Launcher para selecionar Encarte em PDF
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processPdf(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Encartes Inteligente", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                actions = {
                    if (extractedItems.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (selectedSupermarketId == null) {
                                    Toast.makeText(context, "Selecione o supermercado primeiro!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.savePricesToDatabase { count ->
                                        Toast.makeText(context, "✅ $count preços salvos com sucesso!", Toast.LENGTH_LONG).show()
                                        onNavigateBack()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salvar Preços", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Seleção de Supermercado e Mês
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Destino da Atualização de Preços",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = supermarketExpanded,
                        onExpandedChange = { supermarketExpanded = !supermarketExpanded }
                    ) {
                        val selectedName = supermarkets.find { it.id == selectedSupermarketId }?.name ?: "Selecione o Supermercado"
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Supermercado") },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supermarketExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = supermarketExpanded,
                            onDismissRequest = { supermarketExpanded = false }
                        ) {
                            supermarkets.forEach { market ->
                                DropdownMenuItem(
                                    text = { Text(market.name, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        viewModel.setSelectedSupermarket(market.id)
                                        supermarketExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mês de Referência: ${String.format(Locale.ROOT, "%02d/%d", selectedMonth, selectedYear)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botões de Ação para Carregar Foto ou PDF
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { imageLauncher.launch("image/*") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Foto Encarte")
                }

                Button(
                    onClick = { pdfLauncher.launch("application/pdf") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Encarte PDF")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Estados de Processamento ou Lista
            when (val state = uiState) {
                is OcrUiState.Processing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.statusMessage, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is OcrUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    if (extractedItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Carregue o PDF ou foto do encarte para o algoritmo inteligente filtrar e casar com seus produtos cadastrados.",
                                color = Color.Gray,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Itens Identificados (${extractedItems.size}):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Toque no lápis para ajustar",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(extractedItems) { index, item ->
                                val hasMatch = item.matchedProduct != null
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.isConfirmed && hasMatch) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.matchedProduct?.name ?: "Produto não identificado",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (hasMatch) Color(0xFF166534) else Color(0xFF991B1B)
                                                )
                                                if (hasMatch) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "(${String.format(Locale.ROOT, "%.0f", item.matchConfidence * 100)}%)",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF166534),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "Texto encarte: \"${item.detectedText}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )

                                            Text(
                                                text = "R$ ${String.format(Locale.ROOT, "%.2f", item.detectedPrice)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(onClick = { editDialogItemIndex = index }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(onClick = { viewModel.removeItem(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Descartar", tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Edição de Preço ou Produto Correspondente
    editDialogItemIndex?.let { index ->
        val currentItem = extractedItems.getOrNull(index)
        if (currentItem != null) {
            EditOcrItemDialog(
                item = currentItem,
                products = products,
                onDismiss = { editDialogItemIndex = null },
                onSave = { updatedPrice, updatedProduct ->
                    viewModel.updateItemPrice(index, updatedPrice)
                    updatedProduct?.let { viewModel.updateMatchedProduct(index, it) }
                    editDialogItemIndex = null
                }
            )
        }
    }
}

@Composable
fun EditOcrItemDialog(
    item: com.smartmarket.domain.ocr.ExtractedOcrItem,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (price: Double, product: Product?) -> Unit
) {
    var priceText by remember { mutableStateOf(String.format(Locale.ROOT, "%.2f", item.detectedPrice)) }
    var selectedProduct by remember { mutableStateOf(item.matchedProduct) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar Item do Encarte") },
        text = {
            Column {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preço Unitário (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Vincular a qual produto cadastrado:", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    itemsIndexed(products) { _, prod ->
                        val isSel = selectedProduct?.id == prod.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProduct = prod }
                                .padding(8.dp)
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                prod.name,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val price = priceText.replace(",", ".").toDoubleOrNull() ?: item.detectedPrice
                onSave(price, selectedProduct)
            }) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
