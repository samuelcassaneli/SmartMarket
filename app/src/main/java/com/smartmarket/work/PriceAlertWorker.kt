package com.smartmarket.work

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartmarket.MainActivity
import com.smartmarket.R
import com.smartmarket.SmartMarketApplication
import com.smartmarket.domain.alerts.PriceAlertChecker
import java.util.concurrent.TimeUnit

/**
 * Confere os alertas de preço ativos contra as observações recentes e notifica
 * pelo canal "Alertas de preço". Roda a cada 12h e sob demanda ([runNow]),
 * por exemplo depois do sync de encartes ou de salvar uma nota.
 */
class PriceAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SmartMarketApplication).container
        if (!container.settings.current().priceAlertsEnabled) return Result.success()

        val db = container.db
        val alertDao = db.priceAlertDao()
        val priceDao = db.productPriceDao()

        val alerts = alertDao.getActiveAlerts()
        if (alerts.isEmpty()) return Result.success()

        val since = System.currentTimeMillis() - LOOKBACK_MS
        val recent = priceDao.getObservationsSince(since).map {
            PriceAlertChecker.Observation(it.productId, it.supermarketId, it.price, it.observedAt)
        }
        if (recent.isEmpty()) return Result.success()

        val rules = alerts.map {
            PriceAlertChecker.AlertRule(it.id, it.productId, it.targetPrice, it.notifyOnAnyDrop, it.lastNotifiedPrice)
        }
        val wanted = rules.mapNotNull { rule ->
            PriceAlertChecker.pickCandidate(rule.productId, recent)?.let { rule to it }
        }
        val triggers = wanted.mapNotNull { (rule, candidate) ->
            val prevMin = priceDao.getMinPriceBefore(rule.productId, candidate.observedAt)
            PriceAlertChecker.evaluate(rule, candidate, prevMin)
        }
        if (triggers.isEmpty()) return Result.success()

        ensureChannel(applicationContext)
        val canNotify = canPostNotifications(applicationContext)
        val markets = db.supermarketDao().getAllSupermarketsSync().associateBy { it.id }
        for (t in triggers) {
            val product = db.productDao().getProductById(t.productId) ?: continue
            val marketName = markets[t.supermarketId]?.name ?: "mercado"
            if (canNotify) notify(applicationContext, t, PriceAlertChecker.message(product.name, marketName, t))
            // Marca mesmo sem permissão, para não acumular avisos antigos quando ela for concedida
            alertDao.markNotified(t.alertId, t.price)
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission") // checado em canPostNotifications()
    private fun notify(context: Context, t: PriceAlertChecker.Trigger, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PRODUCT_ID, t.productId)
        }
        val pending = PendingIntent.getActivity(
            context, t.alertId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(PriceAlertChecker.title(t))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + t.alertId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem e o envio
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "price_alerts"
        const val UNIQUE_NOW = "price_alerts_now"
        const val CHANNEL_ID = "price_alerts"
        const val CHANNEL_NAME = "Alertas de preço"

        /** Extra com o id do produto, para quem quiser abrir o detalhe ao tocar na notificação. */
        const val EXTRA_PRODUCT_ID = "com.smartmarket.extra.PRODUCT_ID"

        private const val NOTIFICATION_BASE = 20_000
        private val LOOKBACK_MS = TimeUnit.DAYS.toMillis(7)

        /** Liga ou desliga a checagem periódica (a cada 12h). */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Checa agora (ex.: logo depois de salvar preços de uma nota ou encarte). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PriceAlertWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa quando um produto acompanhado fica mais barato"
            }
            manager.createNotificationChannel(channel)
        }

        fun canPostNotifications(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
