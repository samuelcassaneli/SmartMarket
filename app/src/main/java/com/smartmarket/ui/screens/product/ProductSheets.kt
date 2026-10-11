package com.smartmarket.ui.screens.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartmarket.data.dao.ListSummary
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.theme.Spacing

// ---------------------------------------------------------------- Base

@Composable
internal fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/** Campo numérico decimal; aceita vírgula ou ponto. */
@Composable
internal fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    supporting: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' }.take(10)) },
        label = { Text(label) },
        prefix = if (prefix != null) { { Text(prefix) } } else null,
        supportingText = if (supporting != null) { { Text(supporting) } } else null,
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}

/** Campo de data só leitura que abre o DatePicker do M3 ao tocar. */
@Composable
internal fun DateField(
    label: String,
    millis: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    endOfDay: Boolean = false,
    allowClear: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = millis?.let { Fmt.date(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Sem data") },
        trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Escolher data") },
        interactionSource = interaction,
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = PickerDates.toPicker(millis ?: System.currentTimeMillis())
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(if (endOfDay) PickerDates.endOfDayFromPicker(it) else PickerDates.fromPicker(it))
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (allowClear) TextButton(onClick = { onChange(null); open = false }) { Text("Limpar") }
                    TextButton(onClick = { open = false }) { Text("Cancelar") }
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

// ---------------------------------------------------------------- Registrar preço

@Composable
internal fun RecordPriceSheet(
    markets: List<Supermarket>,
    initialMarketId: Long?,
    onDismiss: () -> Unit,
    onSave: (supermarketId: Long, price: Double, observedAt: Long) -> Unit
) {
    var marketId by rememberSaveable { mutableStateOf(initialMarketId ?: markets.firstOrNull()?.id) }
    var priceText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val price = Fmt.parseMoney(priceText)
    val priceInvalid = priceText.isNotBlank() && (price == null || price <= 0.0)

    FormSheet("Registrar preço", onDismiss) {
        Text("Mercado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (markets.isEmpty()) {
            Text("Cadastre um mercado primeiro.", style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                markets.forEach { m ->
                    FilterChip(
                        selected = m.id == marketId,
                        onClick = { marketId = m.id },
                        label = { Text(m.name) },
                        leadingIcon = {
                            if (m.id == marketId) Icon(Icons.Filled.Check, contentDescription = null)
                            else MarketAvatar(m.name, m.colorHex, size = 18.dp)
                        }
                    )
                }
            }
        }
        DecimalField(
            value = priceText,
            onValueChange = { priceText = it },
            label = "Preço",
            prefix = "R$ ",
            isError = priceInvalid,
            supporting = if (priceInvalid) "Digite um valor como 4,99" else null
        )
        DateField(label = "Data", millis = date, onChange = { it?.let { d -> date = d } })
        Button(
            onClick = {
                val id = marketId ?: return@Button
                val p = price ?: return@Button
                onSave(id, p, date)
                onDismiss()
            },
            enabled = marketId != null && price != null && price > 0.0,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Salvar preço") }
    }
}

// ---------------------------------------------------------------- Adicionar à lista

@Composable
internal fun AddToListSheet(
    lists: List<ListSummary>,
    onDismiss: () -> Unit,
    onPick: (ListSummary) -> Unit,
    onCreate: () -> Unit
) {
    FormSheet("Adicionar à lista", onDismiss) {
        if (lists.isEmpty()) {
            Text(
                "Você ainda não tem listas abertas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column {
            lists.forEach { l ->
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = {
                        Text("${Fmt.monthYear(l.referenceMonth, l.referenceYear)} · ${l.itemCount} itens")
                    },
                    leadingContent = { IconBadge(Icons.Filled.ShoppingCart, size = 36.dp) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onPick(l); onDismiss() }
                )
            }
            ListItem(
                headlineContent = { Text("Criar lista do mês") },
                supportingContent = { Text("Nova lista já com este produto") },
                leadingContent = {
                    IconBadge(
                        Icons.Filled.Add, size = 36.dp,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onCreate(); onDismiss() }
            )
        }
    }
}

// ---------------------------------------------------------------- Editar produto

private val UNITS = listOf("un", "kg", "g", "L", "ml", "pct", "cx", "dz")

@Composable
internal fun EditProductSheet(
    product: Product,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, unit: String, monthly: Double, barcode: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(product.name) }
    var category by rememberSaveable { mutableStateOf(product.category) }
    var unit by rememberSaveable { mutableStateOf(product.unit) }
    var monthly by rememberSaveable { mutableStateOf(Fmt.quantity(product.defaultMonthlyQuantity)) }
    var barcode by rememberSaveable { mutableStateOf(product.barcode) }
    var categoryOpen by remember { mutableStateOf(false) }

    FormSheet("Editar produto", onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome") },
            singleLine = true,
            isError = name.isBlank(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )

        val suggestions = categories.filter { it.contains(category.trim(), ignoreCase = true) && !it.equals(category.trim(), true) }
        ExposedDropdownMenuBox(
            expanded = categoryOpen && suggestions.isNotEmpty(),
            onExpandedChange = { categoryOpen = it }
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it; categoryOpen = true },
                label = { Text("Categoria") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryOpen && suggestions.isNotEmpty()) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = categoryOpen && suggestions.isNotEmpty(),
                onDismissRequest = { categoryOpen = false }
            ) {
                suggestions.take(8).forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { category = c; categoryOpen = false })
                }
            }
        }

        Text("Unidade", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (if (unit in UNITS || unit.isBlank()) UNITS else UNITS + unit).forEach { u ->
                FilterChip(selected = u == unit, onClick = { unit = u }, label = { Text(u) })
            }
        }

        Row {
            DecimalField(
                value = monthly,
                onValueChange = { monthly = it },
                label = "Consumo por mês",
                supporting = "Quanto você usa num mês ($unit)",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.md))
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.filter(Char::isDigit).take(14) },
                label = { Text("Código de barras") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                onSave(name, category, unit, Fmt.parseMoney(monthly) ?: product.defaultMonthlyQuantity, barcode)
                onDismiss()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Salvar alterações") }
    }
}
