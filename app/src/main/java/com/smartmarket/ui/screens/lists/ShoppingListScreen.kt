package com.smartmarket.ui.screens.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.domain.comparator.MonthComparisonSummary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel,
    onNavigateToOcr: () -> Unit
) {
    val lists by viewModel.allLists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val items by viewModel.currentListItems.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val compareMonth by viewModel.compareMonth.collectAsState()
    val compareYear by viewModel.compareYear.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Itens da Lista, 1: Comparativo Inteligente

    // Seleciona automaticamente a primeira lista se nenhuma estiver ativa
    LaunchedEffect(lists) {
        if (selectedListId == null && lists.isNotEmpty()) {
            viewModel.selectList(lists.first().id)
        }
    }

    val currentList = lists.find { it.id == selectedListId }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0 && selectedListId != null) {
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar Item")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header: Seletor de Listas e Histórico
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Suas Listas de Compras",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Navegue pelo histórico ou crie uma nova lista",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ Nova Lista")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Carrossel horizontal de listas cadastradas (permite navegar em qualquer mês/ano)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lists) { list ->
                        val isSelected = list.id == selectedListId
                        Card(
                            modifier = Modifier
                                .clickable { viewModel.selectList(list.id) }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    else Modifier
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = list.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "📅 ${String.format(Locale.ROOT, "%02d/%d", list.referenceMonth, list.referenceYear)}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // Barra de detalhes da lista ativa com ações rápidas
            currentList?.let { activeList ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activeList.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Referência: ${String.format(Locale.ROOT, "%02d/%d", activeList.referenceMonth, activeList.referenceYear)} • ${items.size} itens",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Row {
                        IconButton(onClick = { showDuplicateDialog = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar para outro mês", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.deleteList(activeList) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir Lista", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // Abas de navegação interna: Itens x Comparação Inteligente
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🛒 Itens (${items.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.refreshComparison()
                    },
                    text = { Text("📊 Comparar Mercados", fontWeight = FontWeight.SemiBold) }
                )
            }

            if (selectedTab == 0) {
                // Conteúdo da Aba 1: Itens da Lista
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedListId == null) "Selecione uma lista acima ou crie uma nova!" else "Lista vazia. Toque no botão + para adicionar produtos!",
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items, key = { it.itemId }) { item ->
                            ShoppingItemRow(
                                item = item,
                                onToggleCheck = { viewModel.toggleCheck(item.itemId, item.isChecked) },
                                onQuantityChange = { viewModel.updateItemQuantity(item.itemId, it) },
                                onDelete = { viewModel.removeItem(item.itemId) }
                            )
                        }
                    }
                }
            } else {
                // Conteúdo da Aba 2: Comparação Inteligente com Mês Passado
                IntelligentComparisonView(
                    summary = comparison,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    compareMonth = compareMonth,
                    compareYear = compareYear,
                    onCompareMonthChange = { m, y ->
                        viewModel.setCompareMonthYear(m, y)
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateListDialogAdvanced(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, month, year, budget, copyMonthly ->
                viewModel.createList(name, month, year, budget, copyMonthly)
                showCreateDialog = false
            }
        )
    }

    if (showAddItemDialog) {
        val allProds by viewModel.allProducts.collectAsState()
        AddItemDialog(
            products = allProds,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { prodId, qty ->
                viewModel.addItemToList(prodId, qty)
                showAddItemDialog = false
            }
        )
    }

    if (showDuplicateDialog && currentList != null) {
        DuplicateListDialog(
            sourceList = currentList,
            onDismiss = { showDuplicateDialog = false },
            onConfirm = { newName, tMonth, tYear ->
                viewModel.duplicateListToMonth(currentList.id, newName, tMonth, tYear)
                showDuplicateDialog = false
            }
        )
    }
}

@Composable
fun ShoppingItemRow(
    item: ShoppingItemWithProduct,
    onToggleCheck: () -> Unit,
    onQuantityChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleCheck) {
                Icon(
                    imageVector = if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Concluir",
                    tint = if (item.isChecked) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (item.isChecked) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${item.productCategory} • Padrão mensal: ${item.defaultMonthlyQuantity} ${item.productUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Controle de Quantidade
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (item.quantity > 1) onQuantityChange(item.quantity - 1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(
                    text = "${item.quantity} ${item.productUnit}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { onQuantityChange(item.quantity + 1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = Color(0xFFEF4444))
            }
        }
    }
}

@Composable
fun IntelligentComparisonView(
    summary: MonthComparisonSummary?,
    selectedMonth: Int,
    selectedYear: Int,
    compareMonth: Int,
    compareYear: Int,
    onCompareMonthChange: (Int, Int) -> Unit
) {
    if (summary == null || summary.marketResults.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhum preço encontrado para os mercados neste mês. Use o OCR ou cadastre preços!", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Seletor de Mês de Comparação
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Comparando com:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = "Mês ${String.format(Locale.ROOT, "%02d/%d", compareMonth, compareYear)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val prevM = if (compareMonth == 1) 12 else compareMonth - 1
                                val prevY = if (compareMonth == 1) compareYear - 1 else compareYear
                                onCompareMonthChange(prevM, prevY)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("◀ Mês Anterior")
                        }
                    }
                }
            }
        }

        // Card do Campeão de Economia no Mês
        item {
            summary.bestSupermarketCurrent?.let { bestMarket ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Savings, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🏆 Mercado Mais Barato de ${String.format(Locale.ROOT, "%02d/%d", selectedMonth, selectedYear)}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = bestMarket.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = Color(0xFF14532D)
                        )

                        if (summary.currentPotentialSavings > 0) {
                            Text(
                                text = "Economia de R$ ${String.format(Locale.ROOT, "%.2f", summary.currentPotentialSavings)} comparado ao mercado mais caro!",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF166534),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Indicador de Inflação / Variação Média Geral
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Variação Geral de Preços", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = "Comparando ${String.format(Locale.ROOT, "%02d/%d", selectedMonth, selectedYear)} vs ${String.format(Locale.ROOT, "%02d/%d", compareMonth, compareYear)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    val varPct = summary.overallMonthVariationPercent
                    val isUp = varPct > 0
                    Row(
                        modifier = Modifier
                            .background(if (isUp) Color(0xFFFEE2E2) else Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isUp) Color(0xFF991B1B) else Color(0xFF166534),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (isUp) "+" else ""}${String.format(Locale.ROOT, "%.1f", varPct)}%",
                            fontWeight = FontWeight.Bold,
                            color = if (isUp) Color(0xFF991B1B) else Color(0xFF166534)
                        )
                    }
                }
            }
        }

        // Lista de Cada Supermercado e Detalhamento
        items(summary.marketResults) { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = res.supermarket.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "R$ ${String.format(Locale.ROOT, "%.2f", res.totalPrice)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = if (res.isBestPrice) Color(0xFF166534) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Itens cotados: ${res.itemsAvailableCount}/${res.totalItemsCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        if (res.previousTotalPrice > 0) {
                            val infl = res.inflationPercent
                            val isHigher = infl > 0
                            Text(
                                text = "Mês anterior: R$ ${String.format(Locale.ROOT, "%.2f", res.previousTotalPrice)} (${if (isHigher) "+" else ""}${String.format(Locale.ROOT, "%.1f", infl)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isHigher) Color(0xFFDC2626) else Color(0xFF166534)
                            )
                        }
                    }

                    if (!res.isBestPrice && res.differenceFromBest > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+R$ ${String.format(Locale.ROOT, "%.2f", res.differenceFromBest)} (+${String.format(Locale.ROOT, "%.1f", res.percentageMoreExpensive)}%) mais caro que o líder",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (res.missingItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ Sem cotação neste mês: ${res.missingItems.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateListDialogAdvanced(
    onDismiss: () -> Unit,
    onConfirm: (name: String, month: Int, year: Int, budget: Double, copyMonthly: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("10") }
    var year by remember { mutableStateOf("2026") }
    var budget by remember { mutableStateOf("") }
    var copyDefaultMonthly by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar Nova Lista Inteligente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Lista (ex: Compras de Outubro)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it },
                        label = { Text("Mês (1-12)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Ano") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Meta de Orçamento R$ (Opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copyDefaultMonthly = !copyDefaultMonthly }
                ) {
                    Checkbox(
                        checked = copyDefaultMonthly,
                        onCheckedChange = { copyDefaultMonthly = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Preencher automaticamente com a quantidade normal mensal de cada produto cadastrado",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            month.toIntOrNull() ?: 10,
                            year.toIntOrNull() ?: 2026,
                            budget.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            copyDefaultMonthly
                        )
                    }
                }
            ) {
                Text("Criar Lista")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun DuplicateListDialog(
    sourceList: ShoppingList,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, targetMonth: Int, targetYear: Int) -> Unit
) {
    var newName by remember { mutableStateOf("${sourceList.name} (Cópia)") }
    var targetMonth by remember { mutableStateOf("${if (sourceList.referenceMonth == 12) 1 else sourceList.referenceMonth + 1}") }
    var targetYear by remember { mutableStateOf("${if (sourceList.referenceMonth == 12) sourceList.referenceYear + 1 else sourceList.referenceYear}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicar Lista para Outro Mês") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copie todos os itens desta lista para planejar as compras de um novo mês.")
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome da Nova Lista") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetMonth,
                        onValueChange = { targetMonth = it },
                        label = { Text("Mês Destino") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetYear,
                        onValueChange = { targetYear = it },
                        label = { Text("Ano Destino") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(
                            newName,
                            targetMonth.toIntOrNull() ?: 11,
                            targetYear.toIntOrNull() ?: 2026
                        )
                    }
                }
            ) {
                Text("Duplicar")
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
    var quantityText by remember { mutableStateOf(selectedProduct?.defaultMonthlyQuantity?.toString() ?: "1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Item à Lista") },
        text = {
            Column {
                Text("Selecione o Produto:", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(products) { prod ->
                        val isSelected = selectedProduct?.id == prod.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProduct = prod
                                    quantityText = prod.defaultMonthlyQuantity.toString()
                                }
                                .padding(8.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    prod.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${prod.category} • Padrão: ${prod.defaultMonthlyQuantity} ${prod.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantidade para esta lista") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedProduct?.let {
                        val qty = quantityText.replace(",", ".").toDoubleOrNull() ?: 1.0
                        onConfirm(it.id, qty)
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

