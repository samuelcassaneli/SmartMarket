package com.smartmarket.domain.nfce

/** Nota fiscal de consumidor lida da página da SEFAZ. Valores em reais; datas em epoch millis. */
data class NfceReceipt(
    val storeName: String,
    val cnpj: String,             // só dígitos
    val address: String,
    val issuedAt: Long,
    val items: List<NfceItem>,
    val total: Double,            // valor a pagar
    val grossTotal: Double,       // valor total antes de descontos
    val discount: Double,
    val paymentMethod: String,
    val accessKey: String,        // 44 dígitos ("" se a página não trouxe)
    val sourceUrl: String,
    val number: String = "",
    val series: String = "",
    val declaredItemCount: Int? = null
) {
    val itemCount: Int get() = items.size
    val city: String get() = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }.let { parts ->
        if (parts.size >= 2) parts[parts.size - 2] else ""
    }
}

data class NfceItem(
    val description: String,
    val code: String,
    val quantity: Double,
    val unit: String,       // como veio na nota: UN, KG, PC...
    val unitPrice: Double,
    val totalPrice: Double
) {
    /** Item vendido por peso (quantidade fracionada em kg/g). */
    val isWeighed: Boolean get() = NfceText.isWeightUnit(unit)
}

enum class NfceErrorKind {
    INVALID_PAYLOAD,
    INVALID_KEY,
    NOT_NFCE,
    NETWORK,
    /** A SEFAZ pediu captcha ou verificação anti-robô. */
    CAPTCHA,
    NOT_FOUND,
    /** A página depende de JavaScript para mostrar os itens. */
    JS_REQUIRED,
    UNKNOWN_LAYOUT;

    /** Erros que se resolvem abrindo a página num WebView visível. */
    val needsWebView: Boolean get() = this == CAPTCHA || this == JS_REQUIRED || this == UNKNOWN_LAYOUT
}

class NfceException(val kind: NfceErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)
