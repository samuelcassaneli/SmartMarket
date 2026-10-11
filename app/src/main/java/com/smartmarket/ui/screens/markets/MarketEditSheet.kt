@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.smartmarket.ui.screens.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smartmarket.data.entity.FlyerKind
import com.smartmarket.data.entity.OnlineSource
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.ui.components.colorFromHex
import com.smartmarket.ui.screens.lists.FormSheet
import com.smartmarket.ui.theme.Spacing

/** Paleta de identificação dos mercados (dado do usuário, não cor de interface). */
val MARKET_COLORS = listOf(
    "#1E88E5", "#E53935", "#43A047", "#FB8C00", "#8E24AA",
    "#00ACC1", "#F4511E", "#3949AB", "#C0CA33", "#6D4C41"
)

@Composable
fun MarketEditSheet(
    initial: Supermarket?,
    onDismiss: () -> Unit,
    onSave: (Supermarket) -> Unit,
    onDelete: (Supermarket) -> Unit
) {
    val base = initial ?: Supermarket(name = "", colorHex = MARKET_COLORS.first())
    var name by remember { mutableStateOf(base.name) }
    var address by remember { mutableStateOf(base.address) }
    var city by remember { mutableStateOf(base.city) }
    var phone by remember { mutableStateOf(base.phone) }
    var cnpj by remember { mutableStateOf(base.cnpj.filter { it.isDigit() }.take(14)) }
    var color by remember { mutableStateOf(base.colorHex) }
    var favorite by remember { mutableStateOf(base.isFavorite) }
    var flyerUrl by remember { mutableStateOf(base.flyerUrl) }
    var flyerKind by remember { mutableStateOf(if (base.flyerKind == FlyerKind.NONE) FlyerKind.PDF_LINK else base.flyerKind) }
    var online by remember { mutableStateOf(base.onlineSource) }

    FormSheet(title = if (initial == null) "Novo mercado" else "Editar mercado", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Nome") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Endereço") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = city, onValueChange = { city = it },
                label = { Text("Cidade") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Telefone") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = cnpj,
            onValueChange = { cnpj = it.filter { c -> c.isDigit() }.take(14) },
            label = { Text("CNPJ (opcional)") },
            supportingText = { Text("Usado para reconhecer as notas fiscais deste mercado") },
            singleLine = true,
            visualTransformation = CnpjVisualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Cor", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MARKET_COLORS.forEach { hex ->
                val selected = hex.equals(color, ignoreCase = true)
                val swatch = colorFromHex(hex)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                        )
                        .clickable { color = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Cor selecionada", tint = Color.White)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Favorito", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = favorite, onCheckedChange = { favorite = it })
        }

        Text("Encarte automático", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = flyerUrl, onValueChange = { flyerUrl = it.trim() },
            label = { Text("Página do encarte (opcional)") },
            placeholder = { Text("https://…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        if (flyerUrl.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = flyerKind == FlyerKind.PDF_LINK, onClick = { flyerKind = FlyerKind.PDF_LINK }, label = { Text("PDF") })
                FilterChip(selected = flyerKind == FlyerKind.IMAGE_PAGES, onClick = { flyerKind = FlyerKind.IMAGE_PAGES }, label = { Text("Imagens") })
            }
        }

        Text("Busca online de preços", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                OnlineSource.NONE to "Nenhuma",
                OnlineSource.VTEX_ATACADAO to "Atacadão (site)",
                OnlineSource.SPANI_ONLINE to "Spani online"
            ).forEach { (value, label) ->
                FilterChip(selected = online == value, onClick = { online = value }, label = { Text(label) })
            }
        }

        Button(
            onClick = {
                onSave(
                    base.copy(
                        name = name.trim(),
                        address = address.trim(),
                        city = city.trim(),
                        phone = phone.trim(),
                        cnpj = cnpj,
                        colorHex = color,
                        isFavorite = favorite,
                        flyerUrl = flyerUrl.trim(),
                        flyerKind = if (flyerUrl.isBlank()) FlyerKind.NONE else flyerKind,
                        onlineSource = online
                    )
                )
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Salvar") }

        if (initial != null) {
            OutlinedButton(
                onClick = { onDelete(initial) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  Excluir mercado", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Máscara 00.000.000/0000-00 sobre o texto só com dígitos. */
object CnpjVisualTransformation : VisualTransformation {
    // Separador inserido ANTES do dígito de índice i
    private val separators = mapOf(2 to '.', 5 to '.', 8 to '/', 12 to '-')

    fun format(digits: String): String = buildString {
        digits.forEachIndexed { i, c ->
            separators[i]?.let { append(it) }
            append(c)
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = format(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val extra = separators.keys.count { it < offset }
                return (offset + extra).coerceAtMost(out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var digitsSeen = 0
                var pos = 0
                for (ch in out) {
                    if (pos >= offset) break
                    if (ch.isDigit()) digitsSeen++
                    pos++
                }
                return digitsSeen.coerceAtMost(digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
