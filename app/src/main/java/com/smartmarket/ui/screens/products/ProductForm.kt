@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.smartmarket.ui.screens.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.smartmarket.data.entity.Product
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.theme.Spacing

val PRODUCT_UNITS = listOf("un", "kg", "g", "L", "ml", "pct", "cx")

/** Estado do formulário de produto (criação ou edição). */
@Stable
class ProductFormState(initial: Product? = null, initialName: String = "") {
    private val baseId = initial?.id ?: 0L
    private val base = initial
    var name by mutableStateOf(initial?.name ?: initialName)
    var category by mutableStateOf(initial?.category ?: "")
    var unit by mutableStateOf(initial?.unit ?: "un")
    var monthly by mutableStateOf(initial?.defaultMonthlyQuantity?.takeIf { it > 0 }?.let { Fmt.quantity(it) } ?: "")
    var barcode by mutableStateOf(initial?.barcode ?: "")

    val isValid: Boolean get() = name.isNotBlank()

    fun toProduct(): Product {
        val cleanName = name.trim()
        val qty = Fmt.parseMoney(monthly) ?: 0.0
        val cat = category.trim().ifBlank { "Geral" }
        return base?.copy(
            name = cleanName,
            normalizedName = cleanName.lowercase().trim(),
            category = cat,
            unit = unit,
            defaultMonthlyQuantity = qty,
            barcode = barcode.filter { it.isDigit() }
        ) ?: Product(
            id = baseId,
            name = cleanName,
            category = cat,
            unit = unit,
            defaultMonthlyQuantity = qty,
            barcode = barcode.filter { it.isDigit() }
        )
    }
}

/** Campos do formulário de produto. */
@Composable
fun ProductFormFields(
    state: ProductFormState,
    categories: List<String>,
    modifier: Modifier = Modifier,
    showMonthly: Boolean = true,
    showBarcode: Boolean = true
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            label = { Text("Nome") },
            placeholder = { Text("Ex.: Leite Integral 1L") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.category,
            onValueChange = { state.category = it },
            label = { Text("Categoria") },
            placeholder = { Text("Geral") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        val query = state.category.trim()
        val suggestions = categories
            .filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
            .take(12)
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(suggestions) { cat ->
                    SuggestionChip(onClick = { state.category = cat }, label = { Text(cat) })
                }
            }
        }
        Text("Unidade", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PRODUCT_UNITS.forEach { u ->
                FilterChip(selected = state.unit == u, onClick = { state.unit = u }, label = { Text(u) })
            }
        }
        if (showMonthly) {
            OutlinedTextField(
                value = state.monthly,
                onValueChange = { state.monthly = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text("Consumo mensal (opcional)") },
                supportingText = { Text("Usado em \"Meus itens do mês\" ao criar listas") },
                suffix = { Text(state.unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showBarcode) {
            OutlinedTextField(
                value = state.barcode,
                onValueChange = { state.barcode = it.filter { c -> c.isDigit() } },
                label = { Text("Código de barras (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
