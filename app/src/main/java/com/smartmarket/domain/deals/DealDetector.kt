package com.smartmarket.domain.deals

import kotlinx.serialization.Serializable

/** Preço encontrado na verificação (encarte novo ou site do mercado). */
data class FoundPrice(
    val productId: Long,
    val productName: String,
    val supermarketId: Long,
    val marketName: String,
    val price: Double,
    /** "Encarte", "Site do mercado"... */
    val origin: String,
    val note: String = ""
)

/** Referência de comparação de um produto. */
data class Baseline(
    /** Último preço pago (nota fiscal), se houver. */
    val lastPaid: Double? = null,
    val lastPaidAt: Long? = null,
    /** Último preço visto antes desta verificação (semana/mês passado). */
    val lastSeen: Double? = null,
    val lastSeenAt: Long? = null
)

@Serializable
data class Deal(
    val productId: Long,
    val productName: String,
    val supermarketId: Long,
    val marketName: String,
    val newPrice: Double,
    val oldPrice: Double,
    /** "última compra" ou "última verificação" */
    val comparedWith: String,
    val oldDate: Long? = null,
    val origin: String,
    val note: String = ""
) {
    val savings: Double get() = oldPrice - newPrice
    val savingsPercent: Double get() = if (oldPrice > 0) savings / oldPrice * 100.0 else 0.0
}

@Serializable
data class DealReport(
    val checkedAt: Long,
    val deals: List<Deal>,
    val flyersRead: Int = 0,
    val pricesChecked: Int = 0,
    val seen: Boolean = false
)

/**
 * Decide o que é promoção de verdade: preço novo menor que o último pago (prioridade) ou,
 * sem compra registrada, menor que o último preço visto. Ignora quedas pequenas.
 * Puro Kotlin, testável na JVM.
 */
object DealDetector {

    /** Queda mínima para avisar: 3% e R$ 0,20. */
    const val MIN_DROP_PERCENT = 3.0
    const val MIN_DROP_VALUE = 0.20

    fun detect(found: List<FoundPrice>, baselines: Map<Long, Baseline>): List<Deal> {
        // melhor preço novo por produto
        val best = found.filter { it.price > 0 }.groupBy { it.productId }.mapValues { (_, v) -> v.minBy { it.price } }
        return best.values.mapNotNull { f ->
            val b = baselines[f.productId] ?: return@mapNotNull null
            val (ref, label, date) = when {
                b.lastPaid != null -> Triple(b.lastPaid, "última compra", b.lastPaidAt)
                b.lastSeen != null -> Triple(b.lastSeen, "última verificação", b.lastSeenAt)
                else -> return@mapNotNull null
            }
            val drop = ref - f.price
            if (drop < MIN_DROP_VALUE || drop / ref * 100.0 < MIN_DROP_PERCENT) return@mapNotNull null
            // queda implausível (>70%) costuma ser produto/embalagem diferente
            if (f.price < ref * 0.3) return@mapNotNull null
            Deal(
                productId = f.productId,
                productName = f.productName,
                supermarketId = f.supermarketId,
                marketName = f.marketName,
                newPrice = f.price,
                oldPrice = ref,
                comparedWith = label,
                oldDate = date,
                origin = f.origin,
                note = f.note
            )
        }.sortedByDescending { it.savingsPercent }
    }

    /** Texto da notificação: título e linhas ("Amaciante: R$ 29,90 → R$ 20,99 (Spani)"). */
    fun notificationLines(deals: List<Deal>, max: Int = 5): Pair<String, List<String>> {
        val title = when (deals.size) {
            1 -> "1 produto mais barato que antes"
            else -> "${deals.size} produtos mais baratos que antes"
        }
        val lines = deals.take(max).map { d ->
            "${d.productName}: ${money(d.oldPrice)} → ${money(d.newPrice)} (${d.marketName})"
        }
        return title to lines
    }

    fun money(v: Double): String = "R$ " + String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',')
}
