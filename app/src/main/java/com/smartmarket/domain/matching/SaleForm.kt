package com.smartmarket.domain.matching

import kotlin.math.abs
import kotlin.math.max

/**
 * Como um produto é vendido. Serve para não comparar "Alho kg" (a granel, preço por quilo) com
 * "Alho 60g" (pacotinho), nem "Arroz 5kg" com "Arroz 1kg".
 */
sealed class SaleForm {
    /** Vendido por peso: o preço é por kg. */
    object Bulk : SaleForm() { override fun toString() = "Bulk" }

    /** Embalagem com tamanho ([size], em g ou ml) e/ou quantidade de unidades ([count], 1 se avulsa). */
    data class Pack(val size: FuzzyMatcher.PackageSize?, val count: Int) : SaleForm()

    /** Nome não diz tamanho nem forma de venda. */
    object Unknown : SaleForm() { override fun toString() = "Unknown" }

    val isKnown: Boolean get() = this !is Unknown

    fun describe(): String = when (this) {
        Bulk -> "vendido por kg (a granel)"
        is Pack -> buildString {
            size?.let { s ->
                val amount = if (s.amount >= 1000) trim(s.amount / 1000) + if (s.dimension == 'm') " kg" else " L"
                else trim(s.amount) + if (s.dimension == 'm') " g" else " ml"
                append("embalagem de ").append(amount)
            }
            if (count > 1) append(if (isEmpty()) "pacote com $count unidades" else ", $count unidades")
        }
        Unknown -> "tamanho não informado"
    }

    private fun trim(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd(',', '.')

    companion object {
        private val BULK_WORDS = setOf("kg", "kilo", "quilo", "quilos", "granel")

        /** Lê a forma de venda só pelo texto ("Alho kg" = granel; "Alho 60g" = pacote de 60 g). */
        fun ofText(text: String): SaleForm {
            val norm = FuzzyMatcher.normalize(text)
            if (norm.isEmpty()) return Unknown
            val size = FuzzyMatcher.extractSize(text)
            val count = FuzzyMatcher.extractPackCount(text) ?: 1
            val words = SIZE_TOKEN.replace(norm, " ").split(' ', '/', ',', '.').filter { it.isNotBlank() }
            // "granel" vence o tamanho ("Alho granel 1kg" continua sendo a granel)
            if ("granel" in words) return Bulk
            if (size != null) return Pack(size, count)
            if (words.any { it in BULK_WORDS }) return Bulk
            return if (count > 1) Pack(null, count) else Unknown
        }

        /**
         * Forma de venda de um produto cadastrado: pelo nome; senão pela unidade do cadastro;
         * senão pela última nota fiscal (unidade "KG" ou tamanho na descrição).
         */
        fun ofProduct(name: String, unit: String?, lastPaidDescription: String? = null, lastPaidUnit: String? = null): SaleForm {
            ofText(name).takeIf { it.isKnown }?.let { return it }
            if (unit?.trim()?.lowercase() == "kg") return Bulk
            if (lastPaidUnit?.trim()?.lowercase()?.trimEnd('.', 's') == "kg") return Bulk
            return lastPaidDescription?.let { ofText(it) } ?: Unknown
        }

        /** Forma de venda de uma oferta de site: o nome manda; sem tamanho no nome, vale a unidade de venda do site. */
        fun ofOffer(name: String, measurementUnit: String?, unitMultiplier: Double?): SaleForm {
            val byName = ofText(name)
            if (byName is Pack && byName.size != null) return byName
            if (byName == Bulk) return Bulk
            val unit = measurementUnit?.trim()?.lowercase()
            if ((unit == "kg" || unit == "g") && (unitMultiplier ?: 0.0) > 0.0) return Bulk
            return byName
        }

        /**
         * Preço comparável com o do produto cadastrado, ou null se a oferta é outra coisa
         * (granel x pacote, tamanho diferente, unidade x fardo).
         * Para produto a granel devolve o preço por kg.
         */
        fun comparablePrice(
            product: SaleForm,
            offer: SaleForm,
            price: Double,
            measurementUnit: String? = null,
            unitMultiplier: Double? = null
        ): Double? {
            if (price <= 0.0) return null
            return when (product) {
                Bulk -> when (offer) {
                    Bulk -> perKg(price, measurementUnit, unitMultiplier)
                    is Pack -> price.takeIf { offer.count <= 1 && offer.size?.let { it.dimension == 'm' && abs(it.amount - 1000) <= 50 } == true }
                    Unknown -> null
                }
                is Pack -> when (offer) {
                    is Pack -> price.takeIf { sameSize(product.size, offer.size) && product.count == offer.count }
                    else -> null
                }
                Unknown -> when (offer) {
                    Bulk -> perKg(price, measurementUnit, unitMultiplier)
                    else -> price
                }
            }
        }

        private fun perKg(price: Double, unit: String?, multiplier: Double?): Double {
            val m = multiplier?.takeIf { it > 0.0 } ?: return price
            return when (unit?.trim()?.lowercase()) {
                "kg" -> price / m
                "g" -> price / (m / 1000.0)
                else -> price
            }
        }

        private fun sameSize(a: FuzzyMatcher.PackageSize?, b: FuzzyMatcher.PackageSize?): Boolean {
            if (a == null) return true // produto só diz a quantidade ("c/ 12"): o tamanho não importa
            if (b == null) return false
            if (a.dimension != b.dimension) return false
            return abs(a.amount - b.amount) / max(a.amount, b.amount) <= 0.10
        }

        private val SIZE_TOKEN = Regex("""\d+(?:[.,]\d+)?\s*(?:kg|g|gr|grs|mg|ml|l|lt|lts|litros?)\b""")
    }
}
