package com.neonbear.honeybeat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Runs the Cave download as a foreground service so Android keeps it alive in the background. */
class CaveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val CH = "cave"
        private const val ACTION_CANCEL = "cancel"

        fun start(ctx: Context, url: String, art: Boolean) {
            if (CaveState.running) return
            CaveState.running = true
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, CaveService::class.java).putExtra("url", url).putExtra("art", art)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val cancel = PendingIntent.getService(
            this, 0, Intent(this, CaveService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Cave")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", cancel)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            CaveJob.cancel()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra("url")
        if (url == null) {
            CaveState.running = false
            stopSelf()
            return START_NOT_STICKY
        }
        val art = intent.getBooleanExtra("art", true)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Cave downloads", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, 1, notification("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            try {
                CaveJob.run(applicationContext, url, art) { nm.notify(1, notification(it)) }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
