package com.smartmarket.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartmarket.SmartMarketApplication
import com.smartmarket.domain.backup.BackupManager
import java.util.concurrent.TimeUnit

/**
 * Backup automático semanal: grava `smartmarket-backup-AAAA-MM-DD.json`
 * na pasta escolhida em Configurações e mantém só os 4 mais recentes.
 */
class AutoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SmartMarketApplication).container
        val settings = container.settings.current()
        if (!settings.autoBackupEnabled || settings.autoBackupTreeUri.isBlank()) return Result.success()

        val treeUri = Uri.parse(settings.autoBackupTreeUri)
        return try {
            BackupManager(applicationContext, container.db, container.settings).exportToTree(treeUri, keep = KEEP)
            Result.success()
        } catch (e: SecurityException) {
            // Permissão da pasta perdida: não adianta tentar de novo
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "auto_backup"
        const val UNIQUE_NOW = "auto_backup_now"
        const val KEEP = 4

        /** Liga ou desliga o backup semanal. A pasta vem de `AppSettings.autoBackupTreeUri`. */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Faz um backup na pasta agora. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            )
        }
    }
}
