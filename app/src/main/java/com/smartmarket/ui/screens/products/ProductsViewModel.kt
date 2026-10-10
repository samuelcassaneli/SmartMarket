package com.smartmarket.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.entity.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductsViewModel(
    private val productDao: ProductDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>("Todos")
    val selectedCategory = _selectedCategory.asStateFlow()

    val categories: StateFlow<List<String>> = productDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = combine(
        productDao.getAllProducts(),
        _searchQuery,
        _selectedCategory
    ) { all, query, category ->
        all.filter { prod ->
            val matchQuery = query.isBlank() || prod.name.contains(query, ignoreCase = true)
            val matchCat = category == null || category == "Todos" || prod.category.equals(category, ignoreCase = true)
            matchQuery && matchCat
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String?) {
        _selectedCategory.value = cat
    }

    fun saveProduct(
        id: Long = 0,
        name: String,
        category: String,
        unit: String,
        defaultMonthlyQuantity: Double,
        barcode: String,
        notes: String
    ) {
        viewModelScope.launch {
            val product = Product(
                id = id,
                name = name.trim(),
                category = category.ifBlank { "Geral" }.trim(),
                unit = unit.ifBlank { "un" }.trim(),
                defaultMonthlyQuantity = defaultMonthlyQuantity,
                barcode = barcode.trim(),
                notes = notes.trim()
            )
            if (id == 0L) {
                productDao.insertProduct(product)
            } else {
                productDao.updateProduct(product)
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            productDao.deleteProduct(product)
        }
    }
}

class ProductsViewModelFactory(
    private val productDao: ProductDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProductsViewModel(productDao) as T
    }
}
