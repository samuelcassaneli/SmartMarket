package com.smartmarket.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartmarket.SmartMarketApplication
import com.smartmarket.domain.flyers.OfferText
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Preenche as fotos que faltam: produtos cadastrados sem foto e ofertas dos encartes recentes.
 * Usa o cache, então cada produto (pela especificação) é procurado uma vez só.
 */
class ProductPhotoWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as SmartMarketApplication).container
        if (!c.settings.current().photosEnabled) return Result.success()
        val finder = c.imageFinder
        var found = 0
        try {
            val products = c.db.productDao().getAllProductsSync().filter { it.imageUrl.isBlank() }.take(MAX_PRODUCTS)
            for (p in products) {
                setProgress(workDataOf(KEY_PROGRESS to "Foto: ${p.name}"))
                val url = finder.find(p.name, p.barcode) ?: continue
                c.db.productDao().setImageUrl(p.id, url)
                found++
            }
            finder.save()
            val since = System.currentTimeMillis() - 21L * 24 * 60 * 60 * 1000
            val offers = c.db.flyerDao().getRecentOffers(since)
                .map { OfferText.split(it.rawText).first }
                .distinctBy { com.smartmarket.domain.images.ImageMatch.key(it) }
                .filter { !finder.cache.isKnown(it) }
                .take(MAX_OFFERS)
            for (d in offers) {
                setProgress(workDataOf(KEY_PROGRESS to "Foto: $d"))
                if (finder.find(d) != null) found++
                if (found % 10 == 0) finder.save()
            }
        } catch (e: CancellationException) {
            finder.save()
            throw e
        } catch (e: Throwable) {
            // falha de rede: o que já achou fica salvo
        }
        finder.save()
        return Result.success(workDataOf(KEY_FOUND to found))
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_FOUND = "found"
        const val UNIQUE_NOW = "photos-now"
        private const val UNIQUE_PERIODIC = "photos-periodic"
        private const val MAX_PRODUCTS = 30
        private const val MAX_OFFERS = 60

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
                return
            }
            val req = PeriodicWorkRequestBuilder<ProductPhotoWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Busca agora (qualquer rede; o usuário pediu ou acabou de ler encartes). */
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<ProductPhotoWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, req)
        }
    }
}
