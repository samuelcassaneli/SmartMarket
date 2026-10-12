package com.smartmarket.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartmarket.MainActivity
import com.smartmarket.R
import com.smartmarket.SmartMarketApplication
import com.smartmarket.domain.flyers.FlyerDownloader
import com.smartmarket.domain.flyers.FlyerProcessor
import com.smartmarket.domain.flyers.FlyerSyncResult
import com.smartmarket.domain.deals.DealDetector
import com.smartmarket.domain.deals.DealReport
import com.smartmarket.domain.deals.PriceCheckRunner
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Busca os encartes dos mercados uma vez por dia (com rede e bateria ok), processa os novos
 * e avisa: "Novo encarte do Spani: 37 ofertas, 8 da sua lista".
 */
class FlyerSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SmartMarketApplication).container
        val processor = FlyerProcessor(
            db = container.db,
            downloader = FlyerDownloader(container.httpClient, applicationContext.cacheDir),
            ocr = container.ocrProcessor,
            matcher = container.productMatcher,
            interpreter = container.flyerInterpreter
        )
        return try {
            setProgress(workDataOf(KEY_PROGRESS to "Procurando encartes…"))
            val results = processor.syncAll { msg ->
                // setProgress é suspend; usamos setProgressAsync a partir do callback síncrono
                setProgressAsync(workDataOf(KEY_PROGRESS to msg))
            }
            notifyResults(results)
            // verificação de preços: encartes novos + sites dos mercados x última compra
            val report = runCatching {
                PriceCheckRunner(container).run(results) { msg -> setProgressAsync(workDataOf(KEY_PROGRESS to msg)) }
            }.getOrNull()
            if (report != null) {
                container.dealReports.save(report)
                notifyDeals(report)
            }
            val newCount = results.count { it.isNew }
            // fotos das ofertas novas (usa o cache; só procura o que ainda não conhece)
            if (newCount > 0 && container.settings.current().photosEnabled) ProductPhotoWorker.runNow(applicationContext)
            val failed = results.count { it.error != null }
            Result.success(
                workDataOf(
                    KEY_NEW to newCount,
                    KEY_FAILED to failed,
                    KEY_OFFERS to results.sumOf { it.offerCount },
                    KEY_SUMMARY to summary(results)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf(KEY_SUMMARY to FlyerProcessor.friendlyError(e)))
        }
    }

    private fun summary(results: List<FlyerSyncResult>): String {
        val new = results.filter { it.isNew }
        val failed = results.filter { it.error != null }
        return buildString {
            append(
                when {
                    new.isEmpty() -> "Nenhum encarte novo"
                    new.size == 1 -> "1 encarte novo com ${new.first().offerCount} ofertas"
                    else -> "${new.size} encartes novos com ${new.sumOf { it.offerCount }} ofertas"
                }
            )
            if (failed.isNotEmpty()) append(". Falhou: " + failed.joinToString { it.marketName }.trim())
        }
    }

    private fun notifyResults(results: List<FlyerSyncResult>) {
        val new = results.filter { it.isNew && it.offerCount > 0 }
        if (new.isEmpty()) return
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, "flyers")
        }
        val pending = PendingIntent.getActivity(
            ctx, 4201, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // agrupa por mercado (o Dom publica vários encartes ao mesmo tempo)
        new.groupBy { it.supermarketId }.values.forEachIndexed { i, rs ->
            val market = rs.first().marketName
            val offers = rs.sumOf { it.offerCount }
            val fromList = rs.sumOf { it.listMatches }
            val title = if (rs.size == 1) "Novo encarte do $market" else "${rs.size} encartes novos do $market"
            val text = buildString {
                append("$offers ofertas")
                if (fromList > 0) append(", $fromList da sua lista")
                append(". Toque para revisar.")
            }
            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_BASE + i, notification) }
        }
    }

    /** Notificação em destaque (pop-up) com os produtos que ficaram mais baratos. */
    private fun notifyDeals(report: DealReport) {
        if (report.deals.isEmpty()) return
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureDealsChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, "deals")
        }
        val pending = PendingIntent.getActivity(
            ctx, 4301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, lines) = DealDetector.notificationLines(report.deals)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { style.addLine(it) }
        if (report.deals.size > lines.size) style.setSummaryText("+${report.deals.size - lines.size} na tela Promoções")
        val notification = NotificationCompat.Builder(ctx, DEALS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "")
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(DEALS_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val UNIQUE_PERIODIC = "flyer-sync-periodic"
        const val DEALS_CHANNEL_ID = "promocoes"
        private const val DEALS_NOTIFICATION_ID = 4300

        /** Canal de alta importância: aparece como pop-up (heads-up). */
        fun ensureDealsChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(DEALS_CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(DEALS_CHANNEL_ID, "Promoções", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Produtos que você compra e ficaram mais baratos"
                }
            )
        }
        const val UNIQUE_NOW = "flyer-sync-now"
        const val CHANNEL_ID = "encartes"
        const val KEY_PROGRESS = "progress"
        const val KEY_NEW = "new"
        const val KEY_FAILED = "failed"
        const val KEY_OFFERS = "offers"
        const val KEY_SUMMARY = "summary"
        /** Extra do Intent da notificação; o integrador pode usar para abrir a tela de encartes. */
        const val EXTRA_OPEN = "open"
        private const val NOTIFICATION_BASE = 4200

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Encartes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avisos de encartes novos dos mercados"
                }
            )
        }

        /**
         * Agenda (ou cancela, com 0) a verificação periódica de encartes e preços a cada [intervalDays] dias.
         */
        fun schedule(context: Context, intervalDays: Int) {
            val wm = WorkManager.getInstance(context)
            if (intervalDays <= 0) {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<FlyerSyncWorker>(intervalDays.toLong(), TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .addTag(UNIQUE_PERIODIC)
                .addTag("interval-$intervalDays")
                .build()
            // UPDATE troca o intervalo sem perder o agendamento
            wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Busca agora (expedited; se a cota acabar, roda como trabalho normal). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FlyerSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(UNIQUE_NOW)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }

    /** Expedited exige ForegroundInfo em Android < 12. */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Buscando encartes")
            .setContentText("Lendo as ofertas dos mercados…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return androidx.work.ForegroundInfo(NOTIFICATION_BASE - 1, notification)
    }
}
