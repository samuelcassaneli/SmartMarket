package com.smartmarket.ui.screens.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartmarket.domain.online.UnitPrice
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.BestPriceTag
import com.smartmarket.ui.components.CategoryAvatar
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.MarketAvatar
import com.smartmarket.ui.components.MoneyText
import com.smartmarket.ui.components.Tag
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

/** "R$ 17,44/kg" */
fun UnitPrice.label(): String = "${Fmt.money(value)}/$unit"

// ---------------------------------------------------------------- Imagem

@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                placeholder = ColorPainter(bg),
                error = ColorPainter(bg),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Ofertas

@Composable
fun OfferCard(
    item: OfferUi,
    isBest: Boolean,
    onSavePrice: () -> Unit,
    onAddToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offer = item.offer
    val uriHandler = LocalUriHandler.current
    val ext = MaterialTheme.extended
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            ProductImage(offer.imageUrl)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    offer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (offer.brand != null) {
                    Text(
                        offer.brand,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    MoneyText(offer.price, style = MaterialTheme.typography.headlineSmall)
                    if (offer.hasDiscount) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            Fmt.money(offer.listPrice ?: 0.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                if (item.unitPrice != null) {
                    Text(
                        item.unitPrice.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isBest || !offer.available || offer.hasDiscount) {
                    Spacer(Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (isBest) BestPriceTag()
                        if (offer.hasDiscount && offer.listPrice != null && offer.listPrice > 0) {
                            val pct = (offer.price / offer.listPrice - 1.0) * 100.0
                            Tag(Fmt.percent(pct), container = ext.positiveContainer, content = ext.onPositiveContainer)
                        }
                        if (!offer.available) {
                            Tag(
                                "Sem estoque",
                                container = MaterialTheme.colorScheme.errorContainer,
                                content = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            val productUrl = offer.productUrl
            if (productUrl != null) {
                IconButton(onClick = { runCatching { uriHandler.openUri(productUrl) } }) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Abrir no site",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = onSavePrice,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Salvar preço", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = onAddToList,
                modifier = Modifier.weight(1.3f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Adicionar à lista", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Cartão "fantasma" pulsando enquanto o site responde. */
@Composable
fun ShimmerOfferCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    AppCard(modifier = modifier.fillMaxWidth()) {
        Row {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(block))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(block))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
                Box(Modifier.fillMaxWidth(0.35f).height(24.dp).clip(RoundedCornerShape(4.dp)).background(block))
            }
        }
    }
}

// ---------------------------------------------------------------- Seções por mercado

@Composable
fun MarketSectionHeader(section: MarketSectionUi, modifier: Modifier = Modifier) {
    val market = section.binding.market
    val status = when (val s = section.state) {
        SectionState.Idle -> "Site do ${section.binding.source.displayName}"
        SectionState.Loading -> "Buscando no site…"
        is SectionState.Success -> if (s.offers.size == 1) "1 resultado no site" else "${s.offers.size} resultados no site"
        SectionState.Empty -> "Nenhum resultado no site"
        is SectionState.Error -> "Falha na busca"
        is SectionState.Unavailable -> "Busca online indisponível"
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarketAvatar(market.name, market.colorHex, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(market.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (section.state == SectionState.Loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/** Mensagem compacta dentro de uma seção (erro, indisponível, vazio). */
@Composable
fun SectionMessage(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AppCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun SectionErrorMessage(message: String, onRetry: () -> Unit) =
    SectionMessage(Icons.Default.CloudOff, message, actionLabel = "Tentar de novo", onAction = onRetry)

@Composable
fun SectionUnavailableMessage(message: String) = SectionMessage(Icons.Default.Info, message)

@Composable
fun SectionEmptyMessage(term: String) =
    SectionMessage(Icons.Default.SearchOff, "Nada encontrado para “$term” neste site.")

// ---------------------------------------------------------------- Meus produtos

@Composable
fun LocalProductRow(result: LocalResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(result.product.category)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    result.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    result.bestPrice?.let { "Melhor do mês · ${it.supermarketName}" } ?: "Sem preço neste mês",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (result.bestPrice != null) {
                Spacer(Modifier.width(Spacing.sm))
                MoneyText(result.bestPrice.price)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FlyerResultRow(result: FlyerResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val h = result.hit
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.smartmarket.ui.components.MarketAvatar(h.marketName, h.colorHex, size = 36.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(result.description, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        h.marketName,
                        "encarte " + com.smartmarket.ui.components.Fmt.dateShort(h.fetchedAt),
                        result.brand?.let { "marca $it" },
                        result.note.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            MoneyText(h.price)
        }
    }
}

// ---------------------------------------------------------------- Histórico

@Composable
fun HistoryRow(term: String, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.md))
        Text(term, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remover “$term” do histórico", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PromptCard(title: String, message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        FilledTonalButton(onClick = onAction) { Text(actionLabel) }
    }
}
