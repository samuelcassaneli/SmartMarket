package com.smartmarket

import android.content.Context
import com.smartmarket.work.AutoBackupWorker
import com.smartmarket.work.FlyerSyncWorker
import com.smartmarket.work.PriceAlertWorker
import com.smartmarket.work.ProductPhotoWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Agenda as tarefas periódicas (encartes, alertas, backup) e reage às mudanças nas configurações:
 * ligar ou desligar um recurso em Configurações agenda ou cancela o worker correspondente.
 */
object BackgroundJobs {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun scheduleAll(context: Context) {
        val app = context.applicationContext as SmartMarketApplication
        scope.launch {
            app.container.settings.settings
                .map { Triple(it.checkIntervalDays, it.priceAlertsEnabled, it.autoBackupEnabled && it.autoBackupTreeUri.isNotEmpty()) }
                .distinctUntilChanged()
                .collect { (intervalDays, alerts, backup) ->
                    FlyerSyncWorker.schedule(app, intervalDays)
                    PriceAlertWorker.schedule(app, alerts)
                    AutoBackupWorker.schedule(app, backup)
                }
        }
        scope.launch {
            app.container.settings.settings.map { it.photosEnabled }.distinctUntilChanged()
                .collect { ProductPhotoWorker.schedule(app, it) }
        }
    }
}
