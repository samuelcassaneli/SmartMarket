package com.smartmarket.domain.backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** Resultado de uma exportação ou importação. */
data class BackupSummary(val counts: Map<String, Int>, val exportedAt: Long) {
    val products: Int get() = counts["products"] ?: 0
    val purchases: Int get() = counts["purchases"] ?: 0
    val lists: Int get() = counts["shoppingLists"] ?: 0
    val listItems: Int get() = counts["shoppingListItems"] ?: 0
    val prices: Int get() = (counts["productPrices"] ?: 0) + (counts["priceObservations"] ?: 0)
    val markets: Int get() = counts["supermarkets"] ?: 0
    val pantry: Int get() = counts["pantryItems"] ?: 0

    /** "38 produtos, 120 preços, 3 listas (25 itens) e 5 notas" */
    fun describe(): String = "$products produtos, $prices preços, $lists listas ($listItems itens) e $purchases notas"

    /** Texto detalhado (uma linha por tipo) para a confirmação da importação. */
    fun details(): String = listOf(
        "Produtos: $products",
        "Preços: $prices",
        "Listas de compras: $lists ($listItems itens)",
        "Mercados: $markets",
        "Notas fiscais: $purchases",
        "Despensa: $pantry itens"
    ).joinToString("\n")
}

/** Arquivo lido e conferido, aguardando a confirmação do usuário. */
data class ImportPreview(val uri: Uri, val summary: BackupSummary)

/**
 * Exporta e importa todas as tabelas do banco em JSON, lendo e escrevendo via
 * Storage Access Framework (qualquer [Uri] do ContentResolver: arquivo local, Drive...).
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository? = null
) {

    private val resolver get() = context.contentResolver

    // ---------------------------------------------------------------- Exportação

    suspend fun buildBackup(): BackupFile = withContext(Dispatchers.IO) {
        val file = BackupFile(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            supermarkets = db.supermarketDao().getAllSupermarketsSync().map { it.toDto() },
            products = db.productDao().getAllProductsSync().map { it.toDto() },
            productAliases = db.productDao().getAllAliases().map { it.toDto() },
            productPrices = readProductPrices(),
            priceObservations = readObservations(),
            shoppingLists = db.shoppingListDao().getAllListsSync().map { it.toDto() },
            shoppingListItems = db.shoppingListDao().getAllItemsSync().map { it.toDto() },
            purchases = db.purchaseDao().getAllPurchasesSync().map { it.toDto() },
            purchaseItems = db.purchaseDao().getAllItemsSync().map { it.toDto() },
            pantryItems = db.pantryDao().getAllSync().map { it.toDto() },
            priceAlerts = db.priceAlertDao().getAllSync().map { it.toDto() },
            flyers = readFlyers(),
            flyerOffers = readFlyerOffers(),
            settings = settings?.current()?.let { s ->
                SettingsDto(
                    postalCode = s.postalCode,
                    monthlyBudget = s.monthlyBudget,
                    themeMode = s.themeMode,
                    checkIntervalDays = s.checkIntervalDays,
                    priceAlertsEnabled = s.priceAlertsEnabled,
                    aiEnabled = s.aiEnabled,
                    photosEnabled = s.photosEnabled,
                    photoAiCheck = s.photoAiCheck,
                    aiMatchEnabled = s.aiMatchEnabled,
                    aiInsightsEnabled = s.aiInsightsEnabled
                )
            }
        )
        file.copy(counts = file.computeCounts())
    }

    /**
     * Grava o backup completo em [uri] (criado por CreateDocument ou pela pasta do backup automático)
     * e lê o arquivo de volta para conferir que foi gravado inteiro.
     */
    suspend fun exportTo(uri: Uri): BackupSummary {
        val file = buildBackup()
        val text = BackupCodec.encode(file)
        withContext(Dispatchers.IO) {
            val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Não foi possível abrir o arquivo para gravar.")
            stream.use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.flush()
            }
        }
        val written = runCatching { readFile(uri) }.getOrElse {
            throw IllegalStateException("O arquivo foi gravado incompleto. Tente salvar em outra pasta.")
        }
        if (written.computeCounts() != file.computeCounts()) {
            throw IllegalStateException("O arquivo gravado não confere com os dados. Tente salvar em outra pasta.")
        }
        return BackupSummary(file.counts, file.exportedAt)
    }

    // ---------------------------------------------------------------- Importação

    /** Lê e valida o arquivo sem mexer nos dados (para mostrar o que tem antes de restaurar). */
    suspend fun preview(uri: Uri): ImportPreview {
        val file = readFile(uri)
        return ImportPreview(uri, BackupSummary(file.computeCounts(), file.exportedAt))
    }

    /**
     * Substitui TODOS os dados pelos do arquivo, numa transação só (se algo falhar, nada muda).
     * Antes, guarda uma cópia dos dados atuais para [undoImport].
     */
    suspend fun importFrom(uri: Uri): BackupSummary {
        val file = readFile(uri)
        saveSnapshot()
        restore(file)
        restoreSettings(file.settings)
        return currentSummary(file.exportedAt)
    }

    /** Há cópia dos dados de antes da última importação? */
    fun hasSnapshot(): Boolean = snapshotFile().exists()

    /** Volta os dados de antes da última importação. */
    suspend fun undoImport(): BackupSummary {
        val f = snapshotFile()
        if (!f.exists()) throw IllegalStateException("Não há importação para desfazer.")
        val file = BackupCodec.decode(withContext(Dispatchers.IO) { f.readText(Charsets.UTF_8) })
        restore(file)
        withContext(Dispatchers.IO) { f.delete() }
        return currentSummary(file.exportedAt)
    }

    private suspend fun readFile(uri: Uri): BackupFile {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("Não foi possível ler o arquivo.")
        }
        return BackupCodec.decode(text)
    }

    private fun snapshotFile() = File(File(context.filesDir, "backups"), "antes-da-importacao.json")

    private suspend fun saveSnapshot() {
        val text = BackupCodec.encode(buildBackup())
        withContext(Dispatchers.IO) {
            val f = snapshotFile()
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        }
    }

    /** Contagem do que está no banco agora (confere que a restauração gravou tudo). */
    private suspend fun currentSummary(exportedAt: Long): BackupSummary =
        BackupSummary(buildBackup().computeCounts(), exportedAt)

    suspend fun restore(original: BackupFile) {
        // linhas órfãs ou duplicadas derrubariam a transação inteira: limpa antes
        val file = original.sanitized()
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            // Filhos antes dos pais (as chaves estrangeiras continuam ativas)
            DELETE_ORDER.forEach { table -> sql.execSQL("DELETE FROM `$table`") }

            db.supermarketDao().let { dao -> file.supermarkets.forEach { dao.insertSupermarket(it.toEntity()) } }
            db.productDao().insertProducts(file.products.map { it.toEntity() })
            db.productDao().let { dao -> file.productAliases.forEach { dao.upsertAlias(it.toEntity()) } }
            db.productPriceDao().insertPrices(file.productPrices.map { it.toEntity() })
            if (file.priceObservations.isNotEmpty()) {
                db.productPriceDao().insertObservations(file.priceObservations.map { it.toEntity() })
            }
            db.shoppingListDao().let { dao -> file.shoppingLists.forEach { dao.insertList(it.toEntity()) } }
            db.shoppingListDao().insertItems(file.shoppingListItems.map { it.toEntity() })
            db.purchaseDao().let { dao -> file.purchases.forEach { dao.insertPurchase(it.toEntity()) } }
            if (file.purchaseItems.isNotEmpty()) {
                db.purchaseDao().insertItems(file.purchaseItems.map { it.toEntity() })
            }
            db.pantryDao().let { dao -> file.pantryItems.forEach { dao.upsert(it.toEntity()) } }
            db.priceAlertDao().let { dao -> file.priceAlerts.forEach { dao.upsert(it.toEntity()) } }
            db.flyerDao().let { dao -> file.flyers.forEach { dao.insertFlyer(it.toEntity()) } }
            if (file.flyerOffers.isNotEmpty()) {
                db.flyerDao().insertOffers(file.flyerOffers.map { it.toEntity() })
            }
        }
    }

    private suspend fun restoreSettings(dto: SettingsDto?) {
        val repo = settings ?: return
        dto ?: return
        dto.postalCode?.takeIf { it.isNotBlank() }?.let { repo.setPostalCode(it) }
        dto.monthlyBudget?.let { repo.setMonthlyBudget(it) }
        dto.themeMode?.let { repo.setThemeMode(it) }
        dto.checkIntervalDays?.let { repo.setCheckIntervalDays(it) }
        dto.priceAlertsEnabled?.let { repo.setPriceAlertsEnabled(it) }
        dto.photosEnabled?.let { repo.setPhotosEnabled(it) }
        dto.photoAiCheck?.let { repo.setPhotoAiCheck(it) }
        dto.aiMatchEnabled?.let { repo.setAiMatchEnabled(it) }
        dto.aiInsightsEnabled?.let { repo.setAiInsightsEnabled(it) }
        // a IA só liga de novo se este aparelho já tiver uma chave
        dto.aiEnabled?.let { if (repo.current().geminiApiKey.isNotBlank()) repo.setAiEnabled(it) }
    }

    // ---------------------------------------------------------------- Backup automático (pasta)

    /**
     * Grava `smartmarket-backup-AAAA-MM-DD.json` dentro da pasta [treeUri] (OpenDocumentTree)
     * e apaga os mais antigos, mantendo [keep].
     */
    suspend fun exportToTree(treeUri: Uri, keep: Int = 4): BackupSummary = withContext(Dispatchers.IO) {
        val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val cal = Calendar.getInstance()
        val name = BackupRetention.fileName(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

        val existing = listChildren(treeUri, parentDocId)
        val target = existing.firstOrNull { it.second == name }?.let {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, it.first)
        } ?: DocumentsContract.createDocument(resolver, parentUri, "application/json", name)
        ?: throw IllegalStateException("Não foi possível criar o arquivo na pasta escolhida.")

        val summary = exportTo(target)

        val names = listChildren(treeUri, parentDocId)
        val toDelete = BackupRetention.toDelete(names.map { it.second }, keep).toSet()
        names.filter { it.second in toDelete }.forEach { (docId, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)) }
        }
        summary
    }

    /** (documentId, nome) dos arquivos da pasta. */
    private fun listChildren(treeUri: Uri, parentDocId: String): List<Pair<String, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val result = mutableListOf<Pair<String, String>>()
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                result += id to name
            }
        }
        return result
    }

    // ---------------------------------------------------------------- Tabelas sem getAll no DAO

    private fun <T> readTable(table: String, mapper: (Cursor) -> T): List<T> {
        val out = mutableListOf<T>()
        db.query(SimpleSQLiteQuery("SELECT * FROM `$table`")).use { c ->
            while (c.moveToNext()) out += mapper(c)
        }
        return out
    }

    private fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.longOrNull(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.double(col: String): Double = getDouble(getColumnIndexOrThrow(col))
    private fun Cursor.string(col: String): String = getColumnIndexOrThrow(col).let { if (isNull(it)) "" else getString(it) }

    private fun readProductPrices() = readTable("product_prices") { c ->
        ProductPriceDto(
            id = c.long("id"), productId = c.long("productId"), supermarketId = c.long("supermarketId"),
            price = c.double("price"), referenceMonth = c.int("referenceMonth"), referenceYear = c.int("referenceYear"),
            updatedAt = c.long("updatedAt"), source = c.string("source")
        )
    }

    private fun readObservations() = readTable("price_observations") { c ->
        PriceObservationDto(
            id = c.long("id"), productId = c.long("productId"), supermarketId = c.long("supermarketId"),
            price = c.double("price"), source = c.string("source"), observedAt = c.long("observedAt"), note = c.string("note")
        )
    }

    private fun readFlyers() = readTable("flyers") { c ->
        FlyerDto(
            id = c.long("id"), supermarketId = c.long("supermarketId"), sourceUrl = c.string("sourceUrl"),
            contentHash = c.string("contentHash"), fetchedAt = c.long("fetchedAt"), pageCount = c.int("pageCount"),
            offerCount = c.int("offerCount"), status = c.string("status"), errorMessage = c.string("errorMessage")
        )
    }

    private fun readFlyerOffers() = readTable("flyer_offers") { c ->
        FlyerOfferDto(
            id = c.long("id"), flyerId = c.long("flyerId"), supermarketId = c.long("supermarketId"),
            productId = c.longOrNull("productId"), rawText = c.string("rawText"), price = c.double("price"),
            confidence = c.double("confidence"), status = c.string("status")
        )
    }

    companion object {
        /** Ordem segura para apagar (dependentes primeiro). */
        val DELETE_ORDER = listOf(
            "flyer_offers", "flyers", "price_alerts", "pantry_items", "product_aliases",
            "purchase_items", "purchases", "shopping_list_items", "shopping_lists",
            "price_observations", "product_prices", "products", "supermarkets"
        )
    }
}
