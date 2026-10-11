package com.smartmarket.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.theme.Spacing

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val target: String = route)

private val LEFT_TABS = listOf(
    Tab(Routes.HOME, "Início", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.LISTS, "Listas", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart)
)
private val RIGHT_TABS = listOf(
    Tab(Routes.SEARCH, "Buscar", Icons.Outlined.Search, Icons.Filled.Search, target = Routes.search()),
    Tab(Routes.MORE, "Mais", Icons.Outlined.Menu, Icons.Filled.Menu)
)

/** Estrutura principal: navegação inferior com botão central "Escanear". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in Routes.TOP_LEVEL
    var showScanSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    LEFT_TABS.forEach { tab -> TabItem(tab, backStack?.destination?.hierarchy?.any { it.route == tab.route } == true, navController) }
                    // Botão central
                    Row(
                        modifier = Modifier.weight(1f).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FloatingActionButton(
                            onClick = { showScanSheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear")
                        }
                    }
                    RIGHT_TABS.forEach { tab -> TabItem(tab, backStack?.destination?.hierarchy?.any { it.route == tab.route } == true, navController) }
                }
            }
        }
    ) { inner ->
        AppNavHost(navController, Modifier.padding(inner))
    }

    if (showScanSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showScanSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            ScanSheetContent { route ->
                showScanSheet = false
                navController.navigate(route)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, selected: Boolean, navController: NavHostController) {
    NavigationBarItem(
        selected = selected,
        onClick = {
            navController.navigate(tab.target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
        label = { Text(tab.label) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun ScanSheetContent(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.lg)) {
        Text(
            "Escanear",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)
        )
        ScanOption(Icons.Filled.QrCode2, "Nota fiscal (QR Code)", "Importa itens, preços e data direto da SEFAZ") { onPick(Routes.scanQr()) }
        ScanOption(Icons.AutoMirrored.Filled.ReceiptLong, "Foto da nota fiscal", "Escolha uma foto da galeria com o QR ou a chave de acesso") { onPick(Routes.scanQr(gallery = true)) }
        ScanOption(Icons.Outlined.Newspaper, "Encarte de ofertas", "Foto ou PDF do encarte; o app lê os preços") { onPick(Routes.FLYER_SCAN) }
        ScanOption(Icons.Filled.ViewWeek, "Código de barras", "Encontre ou cadastre um produto pelo código") { onPick(Routes.SCAN_BARCODE) }
    }
}

@Composable
private fun ScanOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBadge(icon) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .padding(horizontal = Spacing.sm)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    )
}
