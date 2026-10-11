package com.smartmarket.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartmarket.ui.screens.flyers.FlyerReviewScreen
import com.smartmarket.ui.screens.flyers.FlyerScanScreen
import com.smartmarket.ui.screens.flyers.FlyersScreen
import com.smartmarket.ui.screens.home.HomeScreen
import com.smartmarket.ui.screens.lists.ListDetailScreen
import com.smartmarket.ui.screens.lists.ListsScreen
import com.smartmarket.ui.screens.markets.MarketsScreen
import com.smartmarket.ui.screens.more.MoreScreen
import com.smartmarket.ui.screens.pantry.PantryScreen
import com.smartmarket.ui.screens.product.ProductDetailScreen
import com.smartmarket.ui.screens.products.ProductsScreen
import com.smartmarket.ui.screens.receipts.ReceiptDetailScreen
import com.smartmarket.ui.screens.receipts.ReceiptReviewScreen
import com.smartmarket.ui.screens.receipts.ReceiptsScreen
import com.smartmarket.ui.screens.scan.BarcodeScanScreen
import com.smartmarket.ui.screens.scan.QrScannerScreen
import com.smartmarket.ui.screens.search.SearchScreen
import com.smartmarket.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.LISTS) { ListsScreen(navController) }
        composable(
            Routes.SEARCH,
            arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            SearchScreen(entry.arguments?.getString("q").orEmpty(), navController)
        }
        composable(Routes.MORE) { MoreScreen(navController) }

        composable(
            Routes.SCAN_QR,
            arguments = listOf(navArgument("gallery") { type = NavType.BoolType; defaultValue = false })
        ) { entry ->
            QrScannerScreen(entry.arguments?.getBoolean("gallery") ?: false, navController)
        }
        composable(Routes.SCAN_BARCODE) { BarcodeScanScreen(navController) }
        composable(
            Routes.RECEIPT_REVIEW,
            arguments = listOf(navArgument("payload") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            ReceiptReviewScreen(entry.arguments?.getString("payload").orEmpty(), navController)
        }
        composable(Routes.FLYER_SCAN) { FlyerScanScreen(navController) }

        composable(
            Routes.LIST_DETAIL,
            arguments = listOf(navArgument("listId") { type = NavType.LongType })
        ) { entry -> ListDetailScreen(entry.arguments!!.getLong("listId"), navController) }

        composable(Routes.RECEIPTS) { ReceiptsScreen(navController) }
        composable(
            Routes.RECEIPT_DETAIL,
            arguments = listOf(navArgument("purchaseId") { type = NavType.LongType })
        ) { entry -> ReceiptDetailScreen(entry.arguments!!.getLong("purchaseId"), navController) }

        composable(Routes.FLYERS) { FlyersScreen(navController) }
        composable(
            Routes.FLYER_REVIEW,
            arguments = listOf(navArgument("flyerId") { type = NavType.LongType })
        ) { entry -> FlyerReviewScreen(entry.arguments!!.getLong("flyerId"), navController) }

        composable(Routes.PRODUCTS) { ProductsScreen(navController) }
        composable(
            Routes.PRODUCT_DETAIL,
            arguments = listOf(navArgument("productId") { type = NavType.LongType })
        ) { entry -> ProductDetailScreen(entry.arguments!!.getLong("productId"), navController) }

        composable(Routes.MARKETS) { MarketsScreen(navController) }
        composable(Routes.PANTRY) { PantryScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
    }
}
