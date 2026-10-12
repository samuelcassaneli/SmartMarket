@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.products

import androidx.compose.foundation.clickable
import com.smartmarket.ui.components.ProductAvatar
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.EmptyState
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.ui.screens.lists.FormSheet
import com.smartmarket.ui.theme.Spacing

@Composable
fun ProductsScreen(navController: NavController) {
    val vm = appViewModel { c -> ProductsViewModel(c.db.productDao(), c.db.productPriceDao()) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = "Produtos",
                subtitle = if (state.totalCount > 0) "${state.totalCount} cadastrados" else null,
                onBack = { navController.popBackStack() },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Nome (A–Z)", fontWeight = if (state.sort == ProductSort.NAME) FontWeight.Bold else null) },
                                onClick = { vm.setSort(ProductSort.NAME); sortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Menor preço", fontWeight = if (state.sort == ProductSort.PRICE) FontWeight.Bold else null) },
                                onClick = { vm.setSort(ProductSort.PRICE); sortMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo produto") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
        } else Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = "Buscar produto ou código",
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            )
            if (state.categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        FilterChip(selected = state.category == null, onClick = { vm.setCategory(null) }, label = { Text("Todos") })
                    }
                    items(state.categories) { cat ->
                        FilterChip(
                            selected = state.category == cat,
                            onClick = { vm.setCategory(if (state.category == cat) null else cat) },
                            label = { Text(cat) }
                        )
                    }
                }
            }
            when {
                state.totalCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Default.Category,
                        "Nenhum produto ainda",
                        "Cadastre os produtos que você costuma comprar para montar listas e comparar preços.",
                        actionLabel = "Cadastrar produto",
                        onAction = { showNew = true }
                    )
                }
                state.rows.isEmpty() -> EmptyState(
                    Icons.Default.Category,
                    "Nada encontrado",
                    "Tente outro nome ou limpe os filtros."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(state.rows, key = { it.product.id }) { row ->
                        ProductRow(row) { navController.navigate(Routes.productDetail(row.product.id)) }
                    }
                }
            }
        }
    }

    if (showNew) {
        val form = remember { ProductFormState() }
        FormSheet(title = "Novo produto", onDismiss = { showNew = false }) {
            ProductFormFields(form, state.categories)
            Button(
                onClick = {
                    vm.create(form.toProduct())
                    showNew = false
                },
                enabled = form.isValid,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Salvar produto") }
        }
    }
}

@Composable
private fun ProductRow(row: ProductRowUi, onClick: () -> Unit) {
    val p = row.product
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProductAvatar(p.category, p.imageUrl, size = 48.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${p.category} · ${p.unit}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        val best = row.best
        if (best != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(best.price), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    best.supermarketName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text("sem preço", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
