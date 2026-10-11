package com.smartmarket.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Soap
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.KebabDining
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmarket.ui.theme.Spacing
import com.smartmarket.ui.theme.extended

/** Cor a partir de "#RRGGBB"; usa o primário se inválida. */
@Composable
fun colorFromHex(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() ?: MaterialTheme.colorScheme.primary

// ---------------------------------------------------------------- Barras de topo

/**
 * Barra de topo padrão. Use [large] nas telas raiz (encolhe ao rolar com [scrollBehavior]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    val titleContent: @Composable () -> Unit = {
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    val navIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    if (large) {
        LargeTopAppBar(
            title = titleContent,
            navigationIcon = navIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    } else {
        TopAppBar(
            title = titleContent,
            navigationIcon = navIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors
        )
    }
}

// ---------------------------------------------------------------- Cartões

/** Cartão padrão: superfície elevada sutil com borda fina (correto em claro e escuro). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Cartão de métrica: rótulo pequeno, valor grande, linha de apoio opcional. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconBadge(icon = icon, size = 28.dp)
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = supportingColor, maxLines = 2)
        }
    }
}

/** Ícone dentro de um quadrado arredondado tonal. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.5f))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

// ---------------------------------------------------------------- Texto e rótulos

/** Título de seção com ação opcional à direita ("Ver tudo"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Valor monetário em destaque. */
@Composable
fun MoneyText(
    value: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold
) {
    Text(Fmt.money(value), modifier = modifier, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
}

/** Pílula colorida pequena. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null
) {
    Surface(modifier = modifier, color = container, contentColor = content, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Variação de preço: verde quando caiu, vermelho quando subiu.
 * [changePercent] positivo = mais caro.
 */
@Composable
fun PriceChangeChip(changePercent: Double, modifier: Modifier = Modifier) {
    val ext = MaterialTheme.extended
    when {
        changePercent > 0.05 -> Tag(
            Fmt.percent(changePercent), modifier,
            container = ext.negativeContainer, content = ext.onNegativeContainer, icon = Icons.Default.ArrowUpward
        )
        changePercent < -0.05 -> Tag(
            Fmt.percent(changePercent), modifier,
            container = ext.positiveContainer, content = ext.onPositiveContainer, icon = Icons.Default.ArrowDownward
        )
        else -> Tag("estável", modifier)
    }
}

/** Selo "Mais barato". */
@Composable
fun BestPriceTag(modifier: Modifier = Modifier, text: String = "Mais barato") {
    val ext = MaterialTheme.extended
    Tag(text, modifier, container = ext.positiveContainer, content = ext.onPositiveContainer)
}

// ---------------------------------------------------------------- Avatares

/** Círculo com a inicial do mercado na cor dele. */
@Composable
fun MarketAvatar(name: String, colorHex: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = colorFromHex(colorHex)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

/** Ícone representativo da categoria do produto. */
fun categoryIcon(category: String): ImageVector {
    val c = category.lowercase()
    return when {
        "limp" in c -> Icons.Default.CleaningServices
        "higien" in c || "perfum" in c -> Icons.Default.Soap
        "latic" in c || "frios" in c -> Icons.Default.EggAlt
        "bebid" in c -> Icons.Default.LocalDrink
        "alcool" in c || "cerve" in c || "vinho" in c -> Icons.Default.LocalBar
        "matina" in c || "cafe" in c || "café" in c -> Icons.Default.Coffee
        "padar" in c || "pão" in c || "pao" in c -> Icons.Default.BakeryDining
        "carne" in c || "açoug" in c || "acoug" in c -> Icons.Default.KebabDining
        "peix" in c -> Icons.Default.SetMeal
        "horti" in c || "fruta" in c || "verdur" in c -> Icons.Default.Spa
        "doce" in c || "sobrem" in c || "congel" in c -> Icons.Default.Icecream
        "pet" in c -> Icons.Default.Pets
        "bebê" in c || "bebe" in c || "infant" in c -> Icons.Default.ChildCare
        "aliment" in c || "mercear" in c || "grão" in c -> Icons.Default.RiceBowl
        else -> Icons.Default.Inventory2
    }
}

@Composable
fun CategoryAvatar(category: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    IconBadge(
        icon = categoryIcon(category),
        modifier = modifier,
        size = size,
        container = MaterialTheme.colorScheme.secondaryContainer,
        tint = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

// ---------------------------------------------------------------- Entrada

/** Campo de busca arredondado. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar",
    onSearch: (() -> Unit)? = null,
    autoFocus: Boolean = false
) {
    val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) {
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().then(
            if (autoFocus) Modifier.focusRequester(focusRequester) else Modifier
        ),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, contentDescription = "Limpar") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** Botões − valor + para quantidades. */
@Composable
fun QuantityStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    min: Double = 0.0,
    unit: String? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValueChange((value - step).coerceAtLeast(min)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(18.dp))
        }
        Text(
            Fmt.quantity(value) + (unit?.let { " $it" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp)
        )
        IconButton(onClick = { onValueChange(value + step) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------- Estados

/** Estado vazio com ilustração por ícone e ação opcional. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(icon = icon, size = 64.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        icon = Icons.Default.ErrorOutline,
        title = "Algo deu errado",
        message = message,
        modifier = modifier,
        actionLabel = if (onRetry != null) "Tentar de novo" else null,
        onAction = onRetry
    )
}

/** Diálogo de confirmação; [destructive] pinta o botão de vermelho. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------------------------------------------------------------- Scaffold de tela

/**
 * Scaffold para telas internas. Não aplica insets de sistema no conteúdo:
 * a barra de topo cuida da status bar e o shell do app cuida da barra inferior/navegação.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        content = content
    )
}
