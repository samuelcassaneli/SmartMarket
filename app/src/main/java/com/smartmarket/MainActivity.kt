package com.smartmarket

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.smartmarket.ui.navigation.Routes
import com.smartmarket.work.FlyerSyncWorker
import com.smartmarket.work.PriceAlertWorker
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartmarket.data.prefs.AppSettings
import com.smartmarket.data.prefs.ThemeMode
import com.smartmarket.ui.LocalAppContainer
import com.smartmarket.ui.navigation.AppShell
import com.smartmarket.ui.theme.SmartMarketTheme

class MainActivity : ComponentActivity() {

    /** Rota pedida por uma notificação (produto do alerta, encartes novos). */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = routeFromIntent(intent)
    }

    private fun routeFromIntent(intent: Intent?): String? {
        intent ?: return null
        val productId = intent.getLongExtra(PriceAlertWorker.EXTRA_PRODUCT_ID, -1L)
        return when {
            productId > 0 -> Routes.productDetail(productId)
            intent.getStringExtra(FlyerSyncWorker.EXTRA_OPEN) == "flyers" -> Routes.FLYERS
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as SmartMarketApplication).container
        if (savedInstanceState == null) pendingRoute.value = routeFromIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                SmartMarketTheme(darkTheme = dark) {
                    val navController = rememberNavController()
                    val route by pendingRoute.collectAsStateWithLifecycle()
                    LaunchedEffect(route) {
                        route?.let {
                            navController.navigate(it) { launchSingleTop = true }
                            pendingRoute.value = null
                        }
                    }
                    AppShell(navController)
                }
            }
        }
    }
}
