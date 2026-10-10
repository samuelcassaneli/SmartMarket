package com.smartmarket.ui.screens.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.ocr.ExtractedOcrItem
import com.smartmarket.domain.ocr.OcrProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class OcrUiState {
    object Idle : OcrUiState()
    data class Processing(val statusMessage: String) : OcrUiState()
    data class Success(val items: List<ExtractedOcrItem>) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}

class OcrViewModel(
    context: Context,
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModel() {

    private val ocrProcessor = OcrProcessor(context)

    val supermarkets: StateFlow<List<Supermarket>> = supermarketDao.getAllSupermarkets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _extractedItems = MutableStateFlow<List<ExtractedOcrItem>>(emptyList())
    val extractedItems = _extractedItems.asStateFlow()

    private val _selectedSupermarketId = MutableStateFlow<Long?>(null)
    val selectedSupermarketId = _selectedSupermarketId.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    fun setSelectedSupermarket(id: Long) {
        _selectedSupermarketId.value = id
    }

    fun setMonthYear(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
    }

    fun processImageBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = OcrUiState.Processing("Reconhecendo texto e itens do encarte offline...")
            try {
                val currentProducts = products.value
                val results = ocrProcessor.processBitmap(bitmap, currentProducts)
                _extractedItems.value = results
                _uiState.value = OcrUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = OcrUiState.Error("Erro ao processar imagem: ${e.localizedMessage}")
            }
        }
    }

    fun processPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = OcrUiState.Processing("Convertendo PDF e executando OCR offline...")
            try {
                val currentProducts = products.value
                val results = ocrProcessor.processPdfUri(uri, currentProducts)
                _extractedItems.value = results
                _uiState.value = OcrUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = OcrUiState.Error("Erro ao processar PDF: ${e.localizedMessage}")
            }
        }
    }

    fun updateItemPrice(index: Int, newPrice: Double) {
        val list = _extractedItems.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(detectedPrice = newPrice)
            _extractedItems.value = list
        }
    }

    fun updateMatchedProduct(index: Int, product: Product) {
        val list = _extractedItems.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(matchedProduct = product, matchConfidence = 1.0)
            _extractedItems.value = list
        }
    }

    fun toggleItemConfirmed(index: Int) {
        val list = _extractedItems.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(isConfirmed = !list[index].isConfirmed)
            _extractedItems.value = list
        }
    }

    fun removeItem(index: Int) {
        val list = _extractedItems.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _extractedItems.value = list
        }
    }

    fun savePricesToDatabase(onSuccess: (Int) -> Unit) {
        val marketId = _selectedSupermarketId.value ?: return
        val month = _selectedMonth.value
        val year = _selectedYear.value

        viewModelScope.launch {
            val confirmed = _extractedItems.value.filter { it.isConfirmed && it.matchedProduct != null }
            val pricesToInsert = confirmed.map { item ->
                ProductPrice(
                    productId = item.matchedProduct!!.id,
                    supermarketId = marketId,
                    price = item.detectedPrice,
                    referenceMonth = month,
                    referenceYear = year
                )
            }
            priceDao.insertPrices(pricesToInsert)
            _uiState.value = OcrUiState.Idle
            _extractedItems.value = emptyList()
            onSuccess(pricesToInsert.size)
        }
    }
}

class OcrViewModelFactory(
    private val context: Context,
    private val productDao: ProductDao,
    private val supermarketDao: SupermarketDao,
    private val priceDao: ProductPriceDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OcrViewModel(context, productDao, supermarketDao, priceDao) as T
    }
}
