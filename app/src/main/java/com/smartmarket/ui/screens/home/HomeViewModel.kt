package com.smartmarket.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartmarket.data.dao.FlyerDao
import com.smartmarket.data.dao.LabeledSpend
import com.smartmarket.data.dao.MonthlySpend
import com.smartmarket.data.dao.PantryDao
import com.smartmarket.data.dao.PantryItemWithProduct
import com.smartmarket.data.dao.PurchaseDao
import com.smartmarket.data.dao.PurchaseWithMarket
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.prefs.SettingsRepository
import com.smartmarket.ui.components.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class MonthTotal(val ym: YearMonth, val total: Double)

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val spentThisMonth: Double = 0.0,
    val purchasesThisMonth: Int = 0,
    val spentLastMonth: Double = 0.0,
    /** Orçamento efetivo (configurações ou soma das listas do mês); 0 = sem orçamento. */
    val budget: Double = 0.0,
    val budgetFromLists: Boolean = false,
    val lastSixMonths: List<MonthTotal> = emptyList(),
    val byCategory: List<LabeledSpend> = emptyList(),
    val byMarket: List<LabeledSpend> = emptyList(),
    val recentPurchases: List<PurchaseWithMarket> = emptyList(),
    val hasAnyPurchase: Boolean = false,
    val lowStock: List<PantryItemWithProduct> = emptyList(),
    val pendingOffers: Int = 0
) {
    /** Variação contra o mês anterior, em %; null sem base de comparação. */
    val changeVsLastMonth: Double?
        get() = if (spentLastMonth > 0.0) (spentThisMonth - spentLastMonth) / spentLastMonth * 100.0 else null

    val budgetRatio: Float get() = if (budget > 0) (spentThisMonth / budget).toFloat() else 0f
}

class HomeViewModel(
    purchaseDao: PurchaseDao,
    pantryDao: PantryDao,
    flyerDao: FlyerDao,
    shoppingListDao: ShoppingListDao,
    settings: SettingsRepository
) : ViewModel() {

    private val month = YearMonth.now()
    private val months: List<YearMonth> = generateSequence(month) { it.previous() }.take(6).toList().reversed()
    private val range = month.range()

    private val spendFlow = purchaseDao.getMonthlySpend(since = months.first().range().first)
    private val breakdown = combine(
        purchaseDao.getSpendByCategory(range.first, range.second),
        purchaseDao.getSpendByMarket(range.first, range.second)
    ) { cat, market -> cat to market }
    private val budgetFlow = combine(
        settings.settings,
        shoppingListDao.getListsByMonthYear(month.month, month.year)
    ) { s, lists ->
        if (s.monthlyBudget > 0) s.monthlyBudget to false
        else lists.sumOf { it.budgetTarget } to true
    }
    private val extras = combine(pantryDao.getLowStock(), flyerDao.observePendingCount()) { low, pending -> low to pending }

    val state: StateFlow<HomeUiState> = combine(
        spendFlow,
        breakdown,
        budgetFlow,
        purchaseDao.getAllPurchases(),
        extras
    ) { spend, (byCategory, byMarket), (budget, fromLists), purchases, (low, pending) ->
        val byKey = spend.associateBy(MonthlySpend::yearMonth)
        val totals = months.map { MonthTotal(it, byKey[key(it)]?.total ?: 0.0) }
        val current = byKey[key(month)]
        HomeUiState(
            loading = false,
            month = month,
            spentThisMonth = current?.total ?: 0.0,
            purchasesThisMonth = current?.purchaseCount ?: 0,
            spentLastMonth = byKey[key(month.previous())]?.total ?: 0.0,
            budget = budget,
            budgetFromLists = fromLists && budget > 0,
            lastSixMonths = totals,
            byCategory = byCategory.filter { it.total > 0 },
            byMarket = byMarket.filter { it.total > 0 },
            recentPurchases = purchases.take(3),
            hasAnyPurchase = purchases.isNotEmpty(),
            lowStock = low,
            pendingOffers = pending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun key(ym: YearMonth) = String.format(Locale.ROOT, "%04d-%02d", ym.year, ym.month)
}
