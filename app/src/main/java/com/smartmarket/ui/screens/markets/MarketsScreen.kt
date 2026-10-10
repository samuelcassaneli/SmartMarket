package com.smartmarket.ui.screens.markets

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.smartmarket.data.entity.Supermarket

@Composable
fun MarketsScreen(viewModel: MarketsViewModel) {
    val supermarkets by viewModel.supermarkets.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedForEdit by remember { mutableStateOf<Supermarket?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedForEdit = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Supermercado")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Supermercados Cadastrados",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Cadastre e gerencie os estabelecimentos da sua região",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (supermarkets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhum supermercado cadastrado ainda. Clique em + para cadastrar!")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(supermarkets, key = { it.id }) { market ->
                        val marketColor = runCatching { Color(android.graphics.Color.parseColor(market.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Círculo com cor do mercado
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(marketColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Store,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = market.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (market.isFavorite) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Favorito",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (market.address.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(market.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }

                                    if (market.phone.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(market.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                }

                                IconButton(onClick = { viewModel.toggleFavorite(market) }) {
                                    Icon(
                                        imageVector = if (market.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (market.isFavorite) Color(0xFFFFB300) else Color.Gray
                                    )
                                }

                                IconButton(onClick = {
                                    selectedForEdit = market
                                    showDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(onClick = { viewModel.deleteMarket(market) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        SupermarketEditDialog(
            market = selectedForEdit,
            onDismiss = { showDialog = false },
            onSave = { name, address, phone, notes, colorHex, isFavorite ->
                viewModel.saveMarket(
                    id = selectedForEdit?.id ?: 0L,
                    name = name,
                    address = address,
                    phone = phone,
                    notes = notes,
                    colorHex = colorHex,
                    isFavorite = isFavorite
                )
                showDialog = false
            }
        )
    }
}

@Composable
fun SupermarketEditDialog(
    market: Supermarket?,
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, phone: String, notes: String, colorHex: String, isFavorite: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(market?.name ?: "") }
    var address by remember { mutableStateOf(market?.address ?: "") }
    var phone by remember { mutableStateOf(market?.phone ?: "") }
    var notes by remember { mutableStateOf(market?.notes ?: "") }
    var selectedColor by remember { mutableStateOf(market?.colorHex ?: "#0F766E") }
    var isFavorite by remember { mutableStateOf(market?.isFavorite ?: false) }

    val presetColors = listOf(
        "#0F766E", "#E53935", "#1E88E5", "#2E7D32", "#8E24AA", "#FB8C00", "#00897B", "#546E7A"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (market == null) "Novo Supermercado" else "Editar Supermercado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Mercado *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Endereço / Bairro") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone / WhatsApp") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (ex: dia de ofertas)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Cor do Mercado:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetColors.forEach { hex ->
                        val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .then(
                                    if (selectedColor == hex) Modifier.border(3.dp, Color.Black, CircleShape) else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, address, phone, notes, selectedColor, isFavorite)
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
