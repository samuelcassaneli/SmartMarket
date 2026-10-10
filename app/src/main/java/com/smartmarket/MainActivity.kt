package com.smartmarket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartmarket.ui.screens.lists.ShoppingListScreen
import com.smartmarket.ui.screens.lists.ShoppingListViewModel
import com.smartmarket.ui.screens.lists.ShoppingListViewModelFactory
import com.smartmarket.ui.screens.markets.MarketsScreen
import com.smartmarket.ui.screens.markets.MarketsViewModel
import com.smartmarket.ui.screens.markets.MarketsViewModelFactory
import com.smartmarket.ui.screens.ocr.OcrScannerScreen
import com.smartmarket.ui.screens.ocr.OcrViewModel
import com.smartmarket.ui.screens.ocr.OcrViewModelFactory
import com.smartmarket.ui.screens.prices.ManualPriceScreen
import com.smartmarket.ui.screens.prices.ManualPriceViewModel
import com.smartmarket.ui.screens.prices.ManualPriceViewModelFactory
import com.smartmarket.ui.screens.products.ProductsScreen
import com.smartmarket.ui.screens.products.ProductsViewModel
import com.smartmarket.ui.screens.products.ProductsViewModelFactory
import com.smartmarket.ui.theme.SmartMarketTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SmartMarketApplication
        val db = app.database

        val listViewModelFactory = ShoppingListViewModelFactory(
            listDao = db.shoppingListDao(),
            productDao = db.productDao(),
            supermarketDao = db.supermarketDao(),
            priceDao = db.productPriceDao()
        )

        val ocrViewModelFactory = OcrViewModelFactory(
            context = applicationContext,
            productDao = db.productDao(),
            supermarketDao = db.supermarketDao(),
            priceDao = db.productPriceDao()
        )

        val marketsViewModelFactory = MarketsViewModelFactory(
            supermarketDao = db.supermarketDao(),
            priceDao = db.productPriceDao()
        )

        val productsViewModelFactory = ProductsViewModelFactory(
            productDao = db.productDao()
        )

        val pricesViewModelFactory = ManualPriceViewModelFactory(
            productDao = db.productDao(),
            supermarketDao = db.supermarketDao(),
            priceDao = db.productPriceDao()
        )

        setContent {
            SmartMarketTheme {
                val navController = rememberNavController()

                val shoppingListViewModel: ShoppingListViewModel = viewModels<ShoppingListViewModel> {
                    listViewModelFactory
                }.value

                val ocrViewModel: OcrViewModel = viewModels<OcrViewModel> {
                    ocrViewModelFactory
                }.value

                val marketsViewModel: MarketsViewModel = viewModels<MarketsViewModel> {
                    marketsViewModelFactory
                }.value

                val productsViewModel: ProductsViewModel = viewModels<ProductsViewModel> {
                    productsViewModelFactory
                }.value

                val manualPriceViewModel: ManualPriceViewModel = viewModels<ManualPriceViewModel> {
                    pricesViewModelFactory
                }.value

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentRoute) {
                                        "lists" -> "SmartMarket • Listas"
                                        "ocr" -> "SmartMarket • OCR Encarte"
                                        "prices" -> "SmartMarket • Preços Manuais"
                                        "markets" -> "SmartMarket • Mercados"
                                        "products" -> "SmartMarket • Produtos"
                                        else -> "SmartMarket"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Listas") },
                                label = { Text("Listas") },
                                selected = currentRoute == "lists",
                                onClick = {
                                    navController.navigate("lists") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.CameraAlt, contentDescription = "OCR Encarte") },
                                label = { Text("OCR") },
                                selected = currentRoute == "ocr",
                                onClick = {
                                    navController.navigate("ocr") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Payments, contentDescription = "Preços Manuais") },
                                label = { Text("Preços") },
                                selected = currentRoute == "prices",
                                onClick = {
                                    navController.navigate("prices") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Store, contentDescription = "Mercados") },
                                label = { Text("Mercados") },
                                selected = currentRoute == "markets",
                                onClick = {
                                    navController.navigate("markets") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )

                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Inventory, contentDescription = "Produtos") },
                                label = { Text("Produtos") },
                                selected = currentRoute == "products",
                                onClick = {
                                    navController.navigate("products") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "lists",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("lists") {
                            ShoppingListScreen(
                                viewModel = shoppingListViewModel,
                                onNavigateToOcr = {
                                    navController.navigate("ocr")
                                }
                            )
                        }

                        composable("ocr") {
                            OcrScannerScreen(
                                viewModel = ocrViewModel,
                                onNavigateBack = {
                                    shoppingListViewModel.refreshComparison()
                                    navController.navigate("lists") {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    }
                                }
                            )
                        }

                        composable("prices") {
                            ManualPriceScreen(viewModel = manualPriceViewModel)
                        }

                        composable("markets") {
                            MarketsScreen(viewModel = marketsViewModel)
                        }

                        composable("products") {
                            ProductsScreen(viewModel = productsViewModel)
                        }
                    }
                }
            }
        }
    }
}
