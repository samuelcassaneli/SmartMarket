package com.smartmarket.domain.alerts

import com.smartmarket.ui.components.Fmt
import kotlin.math.abs

/**
 * Regras de alerta de preço, sem dependência de Android (testável na JVM).
 *
 * Um alerta dispara quando o preço mais barato visto recentemente:
 * - fica menor ou igual ao preço-alvo; ou
 * - com [AlertRule.notifyOnAnyDrop], fica abaixo do menor preço registrado antes dele.
 *
 * Nunca avisa duas vezes o mesmo preço ([AlertRule.lastNotifiedPrice]).
 */
object PriceAlertChecker {

    private const val EPS = 0.005

    data class AlertRule(
        val alertId: Long,
        val productId: Long,
        val targetPrice: Double?,
        val notifyOnAnyDrop: Boolean,
        val lastNotifiedPrice: Double?
    )

    data class Observation(
        val productId: Long,
        val supermarketId: Long,
        val price: Double,
        val observedAt: Long
    )

    enum class Reason { TARGET, DROP }

    data class Trigger(
        val alertId: Long,
        val productId: Long,
        val supermarketId: Long,
        val price: Double,
        /** Menor preço registrado antes desta observação (null se não houver histórico). */
        val previousPrice: Double?,
        val targetPrice: Double?,
        val reason: Reason
    )

    /**
     * Escolhe, entre as observações recentes do produto, a mais barata
     * (empate: a mais recente).
     */
    fun pickCandidate(productId: Long, recent: List<Observation>): Observation? =
        recent.filter { it.productId == productId && it.price > 0.0 }
            .minWithOrNull(compareBy<Observation> { it.price }.thenByDescending { it.observedAt })

    /** Decide se uma regra deve notificar para a observação [candidate]. */
    fun evaluate(rule: AlertRule, candidate: Observation, previousMin: Double?): Trigger? {
        val price = candidate.price
        val last = rule.lastNotifiedPrice
        if (last != null && abs(price - last) < EPS) return null

        val target = rule.targetPrice?.takeIf { it > 0.0 }
        val reason = when {
            target != null && price <= target + EPS / 10 -> Reason.TARGET
            rule.notifyOnAnyDrop && previousMin != null && price < previousMin - EPS -> Reason.DROP
            else -> return null
        }
        return Trigger(
            alertId = rule.alertId,
            productId = rule.productId,
            supermarketId = candidate.supermarketId,
            price = price,
            previousPrice = previousMin,
            targetPrice = target,
            reason = reason
        )
    }

    /**
     * Avalia todas as regras ativas.
     * [previousMin] devolve o menor preço do produto observado antes do instante informado.
     */
    fun check(
        rules: List<AlertRule>,
        recent: List<Observation>,
        previousMin: (productId: Long, before: Long) -> Double?
    ): List<Trigger> = rules.mapNotNull { rule ->
        val candidate = pickCandidate(rule.productId, recent) ?: return@mapNotNull null
        evaluate(rule, candidate, previousMin(rule.productId, candidate.observedAt))
    }

    /** "Leite Condensado está R$ 4,99 no Spani (antes R$ 5,99)" */
    fun message(productName: String, marketName: String, trigger: Trigger): String {
        val base = "$productName está ${Fmt.money(trigger.price)} no $marketName"
        val before = trigger.previousPrice?.takeIf { it > trigger.price + EPS }
        return when {
            before != null -> "$base (antes ${Fmt.money(before)})"
            trigger.reason == Reason.TARGET && trigger.targetPrice != null ->
                "$base (seu alvo: ${Fmt.money(trigger.targetPrice)})"
            else -> base
        }
    }

    fun title(trigger: Trigger): String = when (trigger.reason) {
        Reason.TARGET -> "Preço-alvo atingido"
        Reason.DROP -> "O preço caiu"
    }
}
