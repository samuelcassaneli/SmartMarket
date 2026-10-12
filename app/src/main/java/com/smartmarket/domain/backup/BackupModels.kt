package com.smartmarket.domain.backup

import com.smartmarket.data.entity.Flyer
import com.smartmarket.data.entity.FlyerOffer
import com.smartmarket.data.entity.PantryItem
import com.smartmarket.data.entity.PriceAlert
import com.smartmarket.data.entity.PriceObservation
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductAlias
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Purchase
import com.smartmarket.data.entity.PurchaseItem
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * DTOs do backup. Espelham as entidades Room campo a campo, mas ficam separados
 * para que mudanças no banco não quebrem arquivos antigos sem querer.
 */

@Serializable
data class SupermarketDto(
    val id: Long, val name: String, val address: String = "", val phone: String = "", val notes: String = "",
    val colorHex: String = "#1E88E5", val isFavorite: Boolean = false, val cnpj: String = "", val city: String = "",
    val onlineSource: String = "NONE", val flyerUrl: String = "", val flyerKind: String = "NONE"
)

@Serializable
data class ProductDto(
    val id: Long, val name: String, val normalizedName: String = "", val category: String = "Geral",
    val unit: String = "un", val defaultMonthlyQuantity: Double = 1.0, val barcode: String = "",
    val notes: String = "", val imageUrl: String = "", val isFavorite: Boolean = false, val createdAt: Long = 0
)

@Serializable
data class ProductAliasDto(
    val id: Long, val productId: Long, val normalizedAlias: String, val source: String = "MANUAL", val createdAt: Long = 0
)

@Serializable
data class ProductPriceDto(
    val id: Long, val productId: Long, val supermarketId: Long, val price: Double,
    val referenceMonth: Int, val referenceYear: Int, val updatedAt: Long = 0, val source: String = "MANUAL"
)

@Serializable
data class PriceObservationDto(
    val id: Long, val productId: Long, val supermarketId: Long, val price: Double,
    val source: String = "MANUAL", val observedAt: Long = 0, val note: String = ""
)

@Serializable
data class ShoppingListDto(
    val id: Long, val name: String, val referenceMonth: Int, val referenceYear: Int, val notes: String = "",
    val budgetTarget: Double = 0.0, val createdAt: Long = 0, val isArchived: Boolean = false
)

@Serializable
data class ShoppingListItemDto(
    val id: Long, val shoppingListId: Long, val productId: Long, val quantity: Double = 1.0, val isChecked: Boolean = false
)

@Serializable
data class PurchaseDto(
    val id: Long, val accessKey: String? = null, val supermarketId: Long? = null, val cnpj: String = "",
    val storeName: String = "", val storeAddress: String = "", val issuedAt: Long, val totalAmount: Double,
    val discount: Double = 0.0, val paymentMethod: String = "", val itemCount: Int = 0, val sourceUrl: String = "",
    val createdAt: Long = 0
)

@Serializable
data class PurchaseItemDto(
    val id: Long, val purchaseId: Long, val productId: Long? = null, val rawDescription: String, val code: String = "",
    val quantity: Double, val unit: String = "UN", val unitPrice: Double, val totalPrice: Double
)

@Serializable
data class PantryItemDto(
    val productId: Long, val quantity: Double = 0.0, val minQuantity: Double = 0.0,
    val expiresAt: Long? = null, val updatedAt: Long = 0
)

@Serializable
data class PriceAlertDto(
    val id: Long, val productId: Long, val targetPrice: Double? = null, val notifyOnAnyDrop: Boolean = true,
    val active: Boolean = true, val lastNotifiedPrice: Double? = null, val createdAt: Long = 0
)

@Serializable
data class FlyerDto(
    val id: Long, val supermarketId: Long, val sourceUrl: String, val contentHash: String, val fetchedAt: Long = 0,
    val pageCount: Int = 0, val offerCount: Int = 0, val status: String = "PROCESSED", val errorMessage: String = ""
)

@Serializable
data class FlyerOfferDto(
    val id: Long, val flyerId: Long, val supermarketId: Long, val productId: Long? = null, val rawText: String,
    val price: Double, val confidence: Double = 0.0, val status: String = "PENDING"
)

/** Configurações do app no backup (a chave da IA fica de fora de propósito). */
@Serializable
data class SettingsDto(
    val postalCode: String? = null,
    val monthlyBudget: Double? = null,
    val themeMode: String? = null,
    val checkIntervalDays: Int? = null,
    val priceAlertsEnabled: Boolean? = null,
    val aiEnabled: Boolean? = null,
    val photosEnabled: Boolean? = null,
    val photoAiCheck: Boolean? = null,
    val aiMatchEnabled: Boolean? = null,
    val aiInsightsEnabled: Boolean? = null
)

/** Arquivo de backup completo. */
@Serializable
data class BackupFile(
    @SerialName("app") val app: String = BackupCodec.APP_ID,
    val schemaVersion: Int,
    val exportedAt: Long,
    val counts: Map<String, Int> = emptyMap(),
    val supermarkets: List<SupermarketDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val productAliases: List<ProductAliasDto> = emptyList(),
    val productPrices: List<ProductPriceDto> = emptyList(),
    val priceObservations: List<PriceObservationDto> = emptyList(),
    val shoppingLists: List<ShoppingListDto> = emptyList(),
    val shoppingListItems: List<ShoppingListItemDto> = emptyList(),
    val purchases: List<PurchaseDto> = emptyList(),
    val purchaseItems: List<PurchaseItemDto> = emptyList(),
    val pantryItems: List<PantryItemDto> = emptyList(),
    val priceAlerts: List<PriceAlertDto> = emptyList(),
    val flyers: List<FlyerDto> = emptyList(),
    val flyerOffers: List<FlyerOfferDto> = emptyList(),
    val settings: SettingsDto? = null
) {
    /** Contagem real das listas (independe do campo [counts] gravado). */
    fun computeCounts(): Map<String, Int> = linkedMapOf(
        "supermarkets" to supermarkets.size,
        "products" to products.size,
        "productAliases" to productAliases.size,
        "productPrices" to productPrices.size,
        "priceObservations" to priceObservations.size,
        "shoppingLists" to shoppingLists.size,
        "shoppingListItems" to shoppingListItems.size,
        "purchases" to purchases.size,
        "purchaseItems" to purchaseItems.size,
        "pantryItems" to pantryItems.size,
        "priceAlerts" to priceAlerts.size,
        "flyers" to flyers.size,
        "flyerOffers" to flyerOffers.size
    )
}

class BackupFormatException(message: String) : Exception(message)

/** Serialização e validação do backup (JVM pura). */
object BackupCodec {
    const val APP_ID = "SmartMarket"
    const val SCHEMA_VERSION = 3

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Lê e valida o JSON. Lança [BackupFormatException] com mensagem amigável. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("O arquivo não é um backup válido do SmartMarket.")
        }
        if (file.app != APP_ID) throw BackupFormatException("O arquivo não é um backup do SmartMarket.")
        if (file.schemaVersion > SCHEMA_VERSION) {
            throw BackupFormatException("Este backup foi feito por uma versão mais nova do app. Atualize o SmartMarket.")
        }
        if (file.schemaVersion < SCHEMA_VERSION) {
            throw BackupFormatException("Versão de backup não suportada (${file.schemaVersion}).")
        }
        return file
    }
}

// ---------------------------------------------------------------- Conversões entidade <-> DTO

fun Supermarket.toDto() = SupermarketDto(id, name, address, phone, notes, colorHex, isFavorite, cnpj, city, onlineSource, flyerUrl, flyerKind)
fun SupermarketDto.toEntity() = Supermarket(
    id = id, name = name, address = address, phone = phone, notes = notes, colorHex = colorHex, isFavorite = isFavorite,
    cnpj = cnpj, city = city, onlineSource = onlineSource, flyerUrl = flyerUrl, flyerKind = flyerKind
)

fun Product.toDto() = ProductDto(id, name, normalizedName, category, unit, defaultMonthlyQuantity, barcode, notes, imageUrl, isFavorite, createdAt)
fun ProductDto.toEntity() = Product(
    id = id, name = name, normalizedName = normalizedName.ifBlank { name.lowercase().trim() }, category = category,
    unit = unit, defaultMonthlyQuantity = defaultMonthlyQuantity, barcode = barcode, notes = notes, imageUrl = imageUrl,
    isFavorite = isFavorite, createdAt = createdAt
)

fun ProductAlias.toDto() = ProductAliasDto(id, productId, normalizedAlias, source, createdAt)
fun ProductAliasDto.toEntity() = ProductAlias(id = id, productId = productId, normalizedAlias = normalizedAlias, source = source, createdAt = createdAt)

fun ProductPrice.toDto() = ProductPriceDto(id, productId, supermarketId, price, referenceMonth, referenceYear, updatedAt, source)
fun ProductPriceDto.toEntity() = ProductPrice(
    id = id, productId = productId, supermarketId = supermarketId, price = price, referenceMonth = referenceMonth,
    referenceYear = referenceYear, updatedAt = updatedAt, source = source
)

fun PriceObservation.toDto() = PriceObservationDto(id, productId, supermarketId, price, source, observedAt, note)
fun PriceObservationDto.toEntity() = PriceObservation(
    id = id, productId = productId, supermarketId = supermarketId, price = price, source = source, observedAt = observedAt, note = note
)

fun ShoppingList.toDto() = ShoppingListDto(id, name, referenceMonth, referenceYear, notes, budgetTarget, createdAt, isArchived)
fun ShoppingListDto.toEntity() = ShoppingList(
    id = id, name = name, referenceMonth = referenceMonth, referenceYear = referenceYear, notes = notes,
    budgetTarget = budgetTarget, createdAt = createdAt, isArchived = isArchived
)

fun ShoppingListItem.toDto() = ShoppingListItemDto(id, shoppingListId, productId, quantity, isChecked)
fun ShoppingListItemDto.toEntity() = ShoppingListItem(id = id, shoppingListId = shoppingListId, productId = productId, quantity = quantity, isChecked = isChecked)

fun Purchase.toDto() = PurchaseDto(
    id, accessKey, supermarketId, cnpj, storeName, storeAddress, issuedAt, totalAmount, discount, paymentMethod, itemCount, sourceUrl, createdAt
)
fun PurchaseDto.toEntity() = Purchase(
    id = id, accessKey = accessKey, supermarketId = supermarketId, cnpj = cnpj, storeName = storeName, storeAddress = storeAddress,
    issuedAt = issuedAt, totalAmount = totalAmount, discount = discount, paymentMethod = paymentMethod, itemCount = itemCount,
    sourceUrl = sourceUrl, createdAt = createdAt
)

fun PurchaseItem.toDto() = PurchaseItemDto(id, purchaseId, productId, rawDescription, code, quantity, unit, unitPrice, totalPrice)
fun PurchaseItemDto.toEntity() = PurchaseItem(
    id = id, purchaseId = purchaseId, productId = productId, rawDescription = rawDescription, code = code,
    quantity = quantity, unit = unit, unitPrice = unitPrice, totalPrice = totalPrice
)

fun PantryItem.toDto() = PantryItemDto(productId, quantity, minQuantity, expiresAt, updatedAt)
fun PantryItemDto.toEntity() = PantryItem(productId = productId, quantity = quantity, minQuantity = minQuantity, expiresAt = expiresAt, updatedAt = updatedAt)

fun PriceAlert.toDto() = PriceAlertDto(id, productId, targetPrice, notifyOnAnyDrop, active, lastNotifiedPrice, createdAt)
fun PriceAlertDto.toEntity() = PriceAlert(
    id = id, productId = productId, targetPrice = targetPrice, notifyOnAnyDrop = notifyOnAnyDrop, active = active,
    lastNotifiedPrice = lastNotifiedPrice, createdAt = createdAt
)

fun Flyer.toDto() = FlyerDto(id, supermarketId, sourceUrl, contentHash, fetchedAt, pageCount, offerCount, status, errorMessage)
fun FlyerDto.toEntity() = Flyer(
    id = id, supermarketId = supermarketId, sourceUrl = sourceUrl, contentHash = contentHash, fetchedAt = fetchedAt,
    pageCount = pageCount, offerCount = offerCount, status = status, errorMessage = errorMessage
)

fun FlyerOffer.toDto() = FlyerOfferDto(id, flyerId, supermarketId, productId, rawText, price, confidence, status)
fun FlyerOfferDto.toEntity() = FlyerOffer(
    id = id, flyerId = flyerId, supermarketId = supermarketId, productId = productId, rawText = rawText,
    price = price, confidence = confidence, status = status
)

/** Retenção dos backups automáticos: nomes a apagar, mantendo os [keep] mais recentes. */
object BackupRetention {
    private val NAME = Regex("""smartmarket-backup-(\d{4}-\d{2}-\d{2})\.json""")

    fun fileName(year: Int, month: Int, day: Int): String =
        String.format(java.util.Locale.ROOT, "smartmarket-backup-%04d-%02d-%02d.json", year, month, day)

    fun isBackupName(name: String): Boolean = NAME.matches(name)

    fun toDelete(names: List<String>, keep: Int = 4): List<String> =
        names.filter(::isBackupName).sortedDescending().drop(keep)
}

// ---------------------------------------------------------------- Limpeza antes de restaurar

/**
 * Remove o que impediria a restauração inteira de passar (o banco recusa a transação toda por uma linha
 * só): itens que apontam para produto, lista, mercado ou nota que não existe no arquivo, duplicatas de
 * chaves únicas e chave de nota em branco. Referências opcionais quebradas viram null em vez de sumir.
 */
fun BackupFile.sanitized(): BackupFile {
    val marketIds = supermarkets.map { it.id }.toSet()
    val productIds = products.map { it.id }.toSet()
    val listIds = shoppingLists.map { it.id }.toSet()

    val cleanPurchases = purchases
        .map { p ->
            p.copy(
                accessKey = p.accessKey?.trim()?.takeIf { it.isNotEmpty() },
                supermarketId = p.supermarketId?.takeIf { it in marketIds }
            )
        }
        .distinctBy { it.id }
        .let { list ->
            val seen = mutableSetOf<String>()
            list.map { p -> if (p.accessKey != null && !seen.add(p.accessKey)) p.copy(accessKey = null) else p }
        }
    val purchaseIds = cleanPurchases.map { it.id }.toSet()
    val cleanFlyers = flyers.filter { it.supermarketId in marketIds }
        .distinctBy { it.id }
        .distinctBy { it.supermarketId to it.contentHash }
    val flyerIds = cleanFlyers.map { it.id }.toSet()

    return copy(
        supermarkets = supermarkets.distinctBy { it.id },
        products = products.distinctBy { it.id },
        productAliases = productAliases.filter { it.productId in productIds }
            .distinctBy { it.id }.distinctBy { it.normalizedAlias },
        productPrices = productPrices.filter { it.productId in productIds && it.supermarketId in marketIds }
            .sortedByDescending { it.updatedAt }
            .distinctBy { listOf(it.productId, it.supermarketId, it.referenceMonth.toLong(), it.referenceYear.toLong()) }
            .distinctBy { it.id },
        priceObservations = priceObservations.filter { it.productId in productIds && it.supermarketId in marketIds }
            .distinctBy { it.id },
        shoppingLists = shoppingLists.distinctBy { it.id },
        shoppingListItems = shoppingListItems.filter { it.shoppingListId in listIds && it.productId in productIds }
            .distinctBy { it.id },
        purchases = cleanPurchases,
        purchaseItems = purchaseItems.filter { it.purchaseId in purchaseIds }
            .map { it.copy(productId = it.productId?.takeIf { pid -> pid in productIds }) }
            .distinctBy { it.id },
        pantryItems = pantryItems.filter { it.productId in productIds }.distinctBy { it.productId },
        priceAlerts = priceAlerts.filter { it.productId in productIds }.distinctBy { it.productId }.distinctBy { it.id },
        flyers = cleanFlyers,
        flyerOffers = flyerOffers.filter { it.flyerId in flyerIds && it.supermarketId in marketIds }
            .map { it.copy(productId = it.productId?.takeIf { pid -> pid in productIds }) }
            .distinctBy { it.id }
    )
}
