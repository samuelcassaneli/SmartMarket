package com.smartmarket.ui.screens.flyers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartmarket.data.entity.Product
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.SearchField
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing

/** Busca no catálogo: primeiro quem contém o texto, depois os mais parecidos. */
internal fun searchProducts(query: String, products: List<Product>, limit: Int = 30): List<Product> {
    val q = query.trim()
    if (q.isEmpty()) return products.sortedBy { it.name.lowercase() }.take(limit)
    val nq = FuzzyMatcher.normalize(q)
    val contains = products.filter { FuzzyMatcher.normalize(it.name).contains(nq) }
        .sortedBy { it.name.length }
    val fuzzy = FuzzyMatcher.rank(q, products, { it.name }, limit = limit).map { it.item }
    return (contains + fuzzy).distinctBy { it.id }.take(limit)
}

/** Nome sugerido para um produto criado a partir do texto do encarte. */
internal fun suggestedProductName(text: String): String =
    text.replace(Regex("""\s+(?:un|und|unid)\.?$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
        .split(" ").filter { it.isNotEmpty() }
        .joinToString(" ") { w ->
            when {
                w in SMALL_WORDS -> w
                w.first().isDigit() -> w.replace(Regex("""(\d)l$"""), "$1L")
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }
        .replaceFirstChar { it.uppercase() }

private val SMALL_WORDS = setOf("de", "da", "do", "das", "dos", "com", "e", "em", "c/", "p/", "ou")

/**
 * Sheet para vincular um texto do encarte a um produto: sugestões, busca e "Criar produto".
 */
@Composable
internal fun ProductPickerSheet(
    sourceText: String,
    products: List<Product>,
    suggestions: List<Product>,
    currentProductId: Long?,
    onDismiss: () -> Unit,
    onPick: (Product) -> Unit,
    onUnlink: (() -> Unit)?,
    onCreate: (name: String, category: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val results = remember(query, products, suggestions) {
        if (query.isBlank()) (suggestions + searchProducts("", products)).distinctBy { it.id }
        else searchProducts(query, products)
    }
    val suggestionIds = remember(suggestions) { suggestions.map { it.id }.toSet() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text("Vincular produto", style = MaterialTheme.typography.titleLarge)
            Text(
                "No encarte: \"$sourceText\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Buscar produto")

            SheetAction(Icons.Default.Add, "Criar produto \"${suggestedProductName(query.ifBlank { sourceText })}\"") { creating = true }
            if (onUnlink != null && currentProductId != null) {
                SheetAction(Icons.Default.LinkOff, "Remover vínculo") { onUnlink() }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(results, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onPick(p) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryAvatar(p.category, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.id == currentProductId) {
                            Icon(Icons.Default.Check, contentDescription = "Vinculado", tint = MaterialTheme.colorScheme.primary)
                        } else if (p.id in suggestionIds && query.isBlank()) {
                            Tag("Sugestão")
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = Spacing.lg))
        }
    }

    if (creating) {
        CreateProductDialog(
            initialName = suggestedProductName(query.ifBlank { sourceText }),
            onDismiss = { creating = false },
            onConfirm = { name, category -> creating = false; onCreate(name, category) }
        )
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CreateProductDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf("Geral") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar produto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoria") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), category.trim().ifBlank { "Geral" }) }, enabled = name.isNotBlank()) {
                Text("Criar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
