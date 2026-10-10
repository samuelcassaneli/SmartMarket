package com.smartmarket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartmarket.ui.screens.lists.ShoppingListScreen
import com.smartmarket.ui.screens.lists.ShoppingListViewModel
import com.smartmarket.ui.screens.lists.ShoppingListViewModelFactory
import com.smartmarket.ui.screens.ocr.OcrScannerScreen
import com.smartmarket.ui.screens.ocr.OcrViewModel
import com.smartmarket.ui.screens.ocr.OcrViewModelFactory
import com.smartmarket.ui.theme.SmartMarketTheme

class MainActivity : ComponentActivity() {

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

        setContent {
            SmartMarketTheme {
                val navController = rememberNavController()

                val shoppingListViewModel: ShoppingListViewModel = viewModels<ShoppingListViewModel> {
                    listViewModelFactory
                }.value

                val ocrViewModel: OcrViewModel = viewModels<OcrViewModel> {
                    ocrViewModelFactory
                }.value

                NavHost(navController = navController, startDestination = "lists") {
                    composable("lists") {
                        ShoppingListScreen(
                            viewModel = shoppingListViewModel,
                            onNavigateToOcr = {
                                navController.navigate("ocr")
                            },
                            onNavigateToPrices = {
                                // Navegação auxiliar se necessária
                            }
                        )
                    }

                    composable("ocr") {
                        OcrScannerScreen(
                            viewModel = ocrViewModel,
                            onNavigateBack = {
                                shoppingListViewModel.refreshComparison()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
