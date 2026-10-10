package com.smartmarket.ui.screens.lists

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.domain.comparator.ComparisonSummary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel,
    onNavigateToOcr: () -> Unit,
    onNavigateToPrices: () -> Unit
) {
    val lists by viewModel.allLists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val items by viewModel.currentListItems.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Itens da Lista, 1: Comparativo de Mercados

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartMarket - Listas de Compras", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Button(
                        onClick = onNavigateToOcr,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("📷 OCR Encarte")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 && selectedListId != null) {
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar Produto")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Seletor de Lista ou botão para criar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lista Ativa:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = lists.find { it.id == selectedListId }?.name ?: "Nenhuma lista selecionada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mês de Referência: ${String.format(Locale.ROOT, "%02d/%d", selectedMonth, selectedYear)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showCreateDialog = true }) {
                    Text("+ Nova Lista")
                }
            }

            // Abas de visualização (Itens vs Comparação)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🛒 Itens (${items.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.refreshComparison()
                    },
                    text = { Text("📊 Comparar Mercados") }
                )
            }

            if (selectedTab == 0) {
                // Lista de Itens
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedListId == null) "Selecione ou crie uma lista para começar" else "Nenhum item na lista. Toque no + para adicionar!",
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(items, key = { it.itemId }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.toggleCheck(item.itemId, item.isChecked) }) {
                                        Icon(
                                            imageVector = if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Concluir",
                                            tint = if (item.isChecked) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.productName,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Qtd: ${item.quantity} ${item.productUnit} • ${item.productCategory}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeItem(item.itemId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remover", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tela de Comparação de Mercados
                ComparisonView(comparison = comparison)
            }
        }
    }

    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, month, year ->
                viewModel.createList(name, month, year)
                showCreateDialog = false
            }
        )
    }

    if (showAddItemDialog) {
        val products by viewModel.allProducts.collectAsState()
        AddItemDialog(
            products = products,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { prodId, qty ->
                viewModel.addItemToList(prodId, qty)
                showAddItemDialog = false
            }
        )
    }
}

@Composable
fun ComparisonView(comparison: ComparisonSummary?) {
    if (comparison == null || comparison.marketResults.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Adicione itens à lista e cadastre preços para ver o comparativo.", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Card de Destaque do Mais Barato
        item {
            comparison.bestSupermarket?.let { bestMarket ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🏆 Mercado Mais Barato do Mês",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = bestMarket.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = Color(0xFF1B5E20)
                        )
                        if (comparison.potentialSavings > 0) {
                            Text(
                                text = "Economia de até R$ ${String.format(Locale.ROOT, "%.2f", comparison.potentialSavings)} em relação ao mais caro!",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Ranking detalhado de cada mercado
        items(comparison.marketResults) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.supermarket.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "R$ ${String.format(Locale.ROOT, "%.2f", result.totalPrice)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (result.isBestPrice) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Itens cotados: ${result.itemsAvailableCount} de ${result.totalItemsCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    if (!result.isBestPrice && result.differenceFromBest > 0) {
                        Text(
                            text = "+R$ ${String.format(Locale.ROOT, "%.2f", result.differenceFromBest)} (+${String.format(Locale.ROOT, "%.1f", result.percentageMoreExpensive)}%) mais caro",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (result.missingItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ Itens sem preço neste mês: ${result.missingItems.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateListDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, month: Int, year: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("10") }
    var year by remember { mutableStateOf("2026") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar Nova Lista") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Lista (ex: Compras de Outubro)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it },
                        label = { Text("Mês (1-12)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Ano") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, month.toIntOrNull() ?: 10, year.toIntOrNull() ?: 2026)
                    }
                }
            ) {
                Text("Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddItemDialog(
    products: List<com.smartmarket.data.entity.Product>,
    onDismiss: () -> Unit,
    onConfirm: (productId: Long, quantity: Double) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var quantityText by remember { mutableStateOf("1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Item à Lista") },
        text = {
            Column {
                Text("Selecione o Produto:")
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(products) { prod ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProduct = prod }
                                .padding(8.dp)
                                .background(if (selectedProduct?.id == prod.id) Color(0xFFE8F5E9) else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(prod.name, fontWeight = if (selectedProduct?.id == prod.id) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantidade") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedProduct?.let {
                        onConfirm(it.id, quantityText.toDoubleOrNull() ?: 1.0)
                    }
                }
            ) {
                Text("Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
