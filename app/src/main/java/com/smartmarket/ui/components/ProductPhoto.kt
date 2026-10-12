package com.smartmarket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Foto do produto (fundo branco, como nas lojas); sem foto ou se ela falhar, mostra o ícone da categoria.
 * O Coil guarda a imagem no cache de disco: abrir de novo não baixa outra vez.
 */
@Composable
fun ProductAvatar(category: String, imageUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl.isNullOrBlank() || failed) {
        CategoryAvatar(category, modifier, size)
        return
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize().padding(size / 16)
        )
    }
}

/** Foto da oferta (ou nada, quando não tem). */
@Composable
fun OfferPhoto(imageUrl: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    if (imageUrl.isNullOrBlank()) return
    var failed by remember(imageUrl) { mutableStateOf(false) }
    if (failed) return
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize().padding(4.dp)
        )
    }
}
