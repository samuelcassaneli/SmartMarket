@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartmarket.ui.screens.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.entity.Product
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.QuantityStepper
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.screens.products.ProductFormFields
import com.smartmarket.ui.screens.products.ProductFormState
import com.smartmarket.ui.theme.Spacing

/** Passo da quantidade conforme a unidade (meio quilo/litro para granel). */
fun quantityStep(unit: String): Double = when (unit.lowercase()) {
    "kg", "l" -> 0.5
    "g", "ml" -> 100.0
    else -> 1.0
}

/** Busca no catálogo: primeiro quem contém o texto, depois os mais parecidos (fuzzy). */
fun searchCatalog(query: String, products: List<Product>, limit: Int = 12): List<Product> {
    val q = query.trim()
    if (q.isEmpty()) return products.sortedWith(compareByDescending<Product> { it.isFavorite }.thenBy { it.name.lowercase() }).take(limit)
    val nq = FuzzyMatcher.normalize(q)
    val contains = products.filter { FuzzyMatcher.normalize(it.name).contains(nq) }
        .sortedWith(compareBy<Product> { !FuzzyMatcher.normalize(it.name).startsWith(nq) }.thenBy { it.name.length })
    val fuzzy = FuzzyMatcher.rank(q, products, { it.name }, limit = limit).map { it.item }
    return (contains + fuzzy).distinctBy { it.id }.take(limit)
}

@Composable
fun AddItemSheet(
    products: List<Product>,
    categories: List<String>,
    inList: List<ShoppingItemWithProduct>,
    onDismiss: () -> Unit,
    onAdd: (productId: Long, quantity: Double) -> Unit,
    onCreate: (product: Product, quantity: Double) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Product?>(null) }
    var quantity by remember { mutableStateOf(1.0) }
    var creating by remember { mutableStateOf<ProductFormState?>(null) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    val inListById = remember(inList) { inList.associateBy { it.productId } }
    val results = remember(query, products) { searchCatalog(query, products) }
    val exactExists = remember(query, products) {
        val nq = FuzzyMatcher.normalize(query)
        products.any { FuzzyMatcher.normalize(it.name) == nq }
    }

    FormSheet(title = "Adicionar à lista", onDismiss = onDismiss) {
        val form = creating
        if (form != null) {
            ProductFormFields(form, categories, showMonthly = false, showBarcode = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantidade", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                QuantityStepper(quantity, { quantity = it }, step = quantityStep(form.unit), min = quantityStep(form.unit), unit = form.unit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { creating = null }, modifier = Modifier.weight(1f)) { Text("Voltar") }
                Button(
                    onClick = {
                        onCreate(form.toProduct(), quantity)
                        lastAdded = form.name.trim()
                        creating = null
                        query = ""
                        quantity = 1.0
                    },
                    enabled = form.isValid,
                    modifier = Modifier.weight(2f)
                ) { Text("Criar e adicionar") }
            }
        } else {
        SearchField(
            value = query,
            onValueChange = { query = it; selected = null },
            placeholder = "Buscar produto",
            autoFocus = true
        )
        lastAdded?.let {
            Tag("\"$it\" adicionado", icon = Icons.Default.CheckCircle)
        }

        if (query.isNotBlank() && !exactExists) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        creating = ProductFormState(initialName = query.trim().replaceFirstChar { it.uppercase() })
                        quantity = 1.0
                    }
                    .padding(vertical = Spacing.md, horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    "Criar \"${query.trim()}\"",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (results.isEmpty() && query.isBlank()) {
            Text(
                "Nenhum produto cadastrado. Digite um nome para criar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            results.forEach { product ->
                val isSelected = selected?.id == product.id
                val already = inListById[product.id]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            if (isSelected) selected = null else {
                                selected = product
                                val step = quantityStep(product.unit)
                                quantity = product.defaultMonthlyQuantity.takeIf { it > 0 } ?: if (step < 1.0) 1.0 else step
                            }
                        }
                        .padding(vertical = Spacing.sm, horizontal = Spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                        CategoryAvatar(product.category, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (already != null) "Na lista: ${Fmt.quantity(already.quantity)} ${already.productUnit}"
                                else "${product.category} · ${product.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (already != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isSelected) {
                        Spacer(Modifier.height(Spacing.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QuantityStepper(
                                quantity, { quantity = it },
                                step = quantityStep(product.unit),
                                min = quantityStep(product.unit),
                                unit = product.unit,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                onAdd(product.id, quantity)
                                lastAdded = product.name
                                selected = null
                                query = ""
                            }) { Text(if (already != null) "Somar" else "Adicionar", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
    }
}
