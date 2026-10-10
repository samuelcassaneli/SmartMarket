package com.smartmarket.ui.screens.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Supermarket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarketsViewModel(
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    val supermarkets: StateFlow<List<Supermarket>> = supermarketDao.getAllSupermarkets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingMarket = MutableStateFlow<Supermarket?>(null)
    val editingMarket = _editingMarket.asStateFlow()

    fun setEditingMarket(market: Supermarket?) {
        _editingMarket.value = market
    }

    fun saveMarket(
        id: Long = 0,
        name: String,
        address: String,
        phone: String,
        notes: String,
        colorHex: String,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            val market = Supermarket(
                id = id,
                name = name.trim(),
                address = address.trim(),
                phone = phone.trim(),
                notes = notes.trim(),
                colorHex = colorHex,
                isFavorite = isFavorite
            )
            if (id == 0L) {
                supermarketDao.insertSupermarket(market)
            } else {
                supermarketDao.updateSupermarket(market)
            }
            _editingMarket.value = null
        }
    }

    fun toggleFavorite(market: Supermarket) {
        viewModelScope.launch {
            supermarketDao.updateSupermarket(market.copy(isFavorite = !market.isFavorite))
        }
    }

    fun deleteMarket(market: Supermarket) {
        viewModelScope.launch {
            priceDao.deletePricesBySupermarket(market.id)
            supermarketDao.deleteSupermarket(market)
        }
    }
}

class MarketsViewModelFactory(
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MarketsViewModel(supermarketDao, priceDao) as T
    }
}
