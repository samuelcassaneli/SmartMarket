package com.smartmarket.ui.navigation

import android.net.Uri

/** Rotas de navegação. Use as funções para montar rotas com argumentos. */
object Routes {
    // Abas principais (barra inferior)
    const val HOME = "home"
    const val LISTS = "lists"
    const val SEARCH = "search?q={q}"
    const val MORE = "more"

    // Escanear
    const val SCAN_QR = "scan/qr?gallery={gallery}"
    const val SCAN_BARCODE = "scan/barcode"
    const val RECEIPT_REVIEW = "receipt/review?payload={payload}"
    const val FLYER_SCAN = "flyer/scan"

    // Detalhes e telas secundárias
    const val LIST_DETAIL = "list/{listId}"
    const val RECEIPTS = "receipts"
    const val RECEIPT_DETAIL = "receipt/{purchaseId}"
    const val FLYERS = "flyers"
    const val FLYER_REVIEW = "flyer/{flyerId}"
    const val PRODUCTS = "products"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val MARKETS = "markets"
    const val PANTRY = "pantry"
    const val SETTINGS = "settings"

    fun scanQr(gallery: Boolean = false) = "scan/qr?gallery=$gallery"
    fun search(query: String = "") = "search?q=${Uri.encode(query)}"
    fun receiptReview(payload: String) = "receipt/review?payload=${Uri.encode(payload)}"
    fun listDetail(listId: Long) = "list/$listId"
    fun receiptDetail(purchaseId: Long) = "receipt/$purchaseId"
    fun flyerReview(flyerId: Long) = "flyer/$flyerId"
    fun productDetail(productId: Long) = "product/$productId"

    /** Rotas que exibem a barra inferior. */
    val TOP_LEVEL = setOf(HOME, LISTS, SEARCH, MORE)
}
