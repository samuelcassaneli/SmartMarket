package com.smartmarket.ui.screens.prices

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmarket.data.entity.Product
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPriceScreen(viewModel: ManualPriceViewModel) {
    val supermarkets by viewModel.supermarkets.collectAsState()
    val products by viewModel.products.collectAsState()
    val monthPrices by viewModel.monthPrices.collectAsState()
    val selectedSupermarketId by viewModel.selectedSupermarketId.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var supermarketExpanded by remember { mutableStateOf(false) }
    var editPriceProduct by remember { mutableStateOf<Pair<Product, Double?>?>(null) }

    LaunchedEffect(supermarkets) {
        if (selectedSupermarketId == null && supermarkets.isNotEmpty()) {
            viewModel.selectSupermarket(supermarkets.first().id)
        }
    }

    // Mapa de preços do mercado selecionado: productId -> price
    val priceMap = remember(monthPrices, selectedSupermarketId) {
        monthPrices.filter { it.supermarketId == selectedSupermarketId }
            .associate { it.productId to it.price }
    }

    val filteredProducts = products.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Tabela de Preços Manuais",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Alimente ou corrija os valores dos produtos por mercado e mês",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Seletor de Supermercado
            ExposedDropdownMenuBox(
                expanded = supermarketExpanded,
                onExpandedChange = { supermarketExpanded = !supermarketExpanded }
            ) {
                val currentMarket = supermarkets.find { it.id == selectedSupermarketId }?.name ?: "Selecione um Supermercado"
                OutlinedTextField(
                    value = currentMarket,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Supermercado Ativo") },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supermarketExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = supermarketExpanded,
                    onDismissRequest = { supermarketExpanded = false }
                ) {
                    supermarkets.forEach { market ->
                        DropdownMenuItem(
                            text = { Text(market.name, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                viewModel.selectSupermarket(market.id)
                                supermarketExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Seletor de Mês/Ano
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mês: ${String.format(Locale.ROOT, "%02d/%d", selectedMonth, selectedYear)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val newM = if (selectedMonth == 1) 12 else selectedMonth - 1
                            val newY = if (selectedMonth == 1) selectedYear - 1 else selectedYear
                            viewModel.setMonthYear(newM, newY)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("◀ Mês Ant.")
                    }
                    Button(
                        onClick = {
                            val newM = if (selectedMonth == 12) 1 else selectedMonth + 1
                            val newY = if (selectedMonth == 12) selectedYear + 1 else selectedYear
                            viewModel.setMonthYear(newM, newY)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Próx. Mês ▶")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de busca rápida de produto
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Filtrar produto...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de Produtos com seus preços atuais cadastrados
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val currentPrice = priceMap[product.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editPriceProduct = Pair(product, currentPrice)
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "${product.category} • Unidade: ${product.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            if (currentPrice != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "R$ ${String.format(Locale.ROOT, "%.2f", currentPrice)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Cadastrado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF166534)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Sem preço",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Modal para digitar o preço
    editPriceProduct?.let { (prod, existingPrice) ->
        var priceInput by remember { mutableStateOf(existingPrice?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "") }

        AlertDialog(
            onDismissRequest = { editPriceProduct = null },
            title = { Text("Preço de ${prod.name}") },
            text = {
                Column {
                    Text("Informe o valor praticado no supermercado:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Preço Unitário (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = priceInput.replace(",", ".").toDoubleOrNull()
                    if (p != null && p > 0) {
                        viewModel.updatePrice(prod.id, p)
                    }
                    editPriceProduct = null
                }) {
                    Text("Salvar Preço")
                }
            },
            dismissButton = {
                TextButton(onClick = { editPriceProduct = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
