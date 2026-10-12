package com.smartmarket

import com.smartmarket.domain.ai.AiModelResolver
import com.smartmarket.domain.images.ImageCache
import com.smartmarket.domain.images.OpenFoodFacts
import com.smartmarket.domain.images.ProductImageFinder
import com.smartmarket.domain.online.MarketSourceRegistry

import android.content.Context
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.prefs.SettingsRepository
import com.smartmarket.data.repo.PriceRepository
import com.smartmarket.domain.ai.GeminiClient
import com.smartmarket.domain.deals.DealReportStore
import com.smartmarket.domain.flyers.FlyerInterpreter
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.ocr.OcrProcessor
import com.smartmarket.work.PriceAlertWorker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Dependências compartilhadas do app (DI manual). Criado uma vez em [SmartMarketApplication]. */
class AppContainer(val context: Context) {

    val db: AppDatabase = AppDatabase.getDatabase(context)

    val settings = SettingsRepository(context)

    /** Cliente HTTP único: SEFAZ, sites dos mercados e download de encartes. */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.header("User-Agent") == null) builder.header("User-Agent", USER_AGENT)
            if (request.header("Accept-Language") == null) builder.header("Accept-Language", "pt-BR,pt;q=0.9")
            chain.proceed(builder.build())
        }
        .build()

    val priceRepository = PriceRepository(db) { PriceAlertWorker.runNow(context) }

    val productMatcher = ProductMatcher(db.productDao())

    val ocrProcessor: OcrProcessor by lazy { OcrProcessor(context) }

    val gemini: GeminiClient by lazy { GeminiClient(httpClient) }

    val flyerInterpreter: FlyerInterpreter by lazy { FlyerInterpreter(settings, gemini) }

    val aiModels: AiModelResolver by lazy { AiModelResolver(settings, gemini) }

    val imageCache: ImageCache by lazy { ImageCache(context) }

    /** Dicas da IA na tela inicial. */
    val aiInsights: com.smartmarket.domain.ai.AiInsights by lazy {
        com.smartmarket.domain.ai.AiInsights(context, settings, gemini, aiModels)
    }

    /** Fotos dos produtos (sites dos mercados, Open Food Facts e conferência opcional pela IA). */
    val imageFinder: ProductImageFinder by lazy {
        ProductImageFinder(
            settings = settings,
            registry = MarketSourceRegistry.get(httpClient, settings),
            supermarketDao = db.supermarketDao(),
            off = OpenFoodFacts(httpClient),
            gemini = gemini,
            ai = aiModels,
            cache = imageCache
        )
    }

    /** Último relatório de promoções da verificação periódica. */
    val dealReports: DealReportStore by lazy { DealReportStore(context) }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
