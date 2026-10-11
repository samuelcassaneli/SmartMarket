package com.smartmarket.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.smartmarket.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer não fornecido") }

/**
 * Cria (ou recupera) um ViewModel escopado ao destino de navegação atual.
 * Exemplo: `val vm = appViewModel { c -> ListsViewModel(c.db.shoppingListDao()) }`
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
