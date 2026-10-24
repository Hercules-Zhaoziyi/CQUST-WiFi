package top.msfxp.schedule.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.AutoControlMode
import top.msfxp.schedule.widget.updateAllAppWidgets

// 课程自动化音频模式切换短期前台服务（用于 Android 14+ / 17 后台音频强化提权）
class AudioModeControlService : Service() {

    companion object {
        private const val TAG = "AudioModeControlService"
        private const val CHANNEL_ID = "cqust_audio_control_service_channel"
        private const val NOTIFICATION_ID = 20001

        const val EXTRA_IS_ENABLED = "extra_is_enabled"
        const val EXTRA_MODE_TYPE = "extra_mode_type"

        // 启动短期前台服务以在后台合法切换音频模式
        fun start(context: Context, isEnabled: Boolean, mode: AutoControlMode) {
            val intent = Intent(context, AudioModeControlService::class.java).apply {
                putExtra(EXTRA_IS_ENABLED, isEnabled)
                putExtra(EXTRA_MODE_TYPE, mode.value)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start AudioModeControlService failed, fallback to direct toggle", e)
                CoroutineScope(Dispatchers.IO).launch {
                    CourseAlarmReceiver.toggleMode(context, isEnabled, mode)
                    updateAllAppWidgets(context)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_audio_control_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }

        val isEnabled = intent?.getBooleanExtra(EXTRA_IS_ENABLED, false) ?: false
        val modeStr = intent?.getStringExtra(EXTRA_MODE_TYPE)
        val mode = AutoControlMode.fromString(modeStr)

        val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceScope.launch {
            try {
                CourseAlarmReceiver.toggleMode(this@AudioModeControlService, isEnabled, mode)
                updateAllAppWidgets(this@AudioModeControlService)
            } catch (e: Exception) {
                Log.e(TAG, "Execute toggleMode in foreground service failed", e)
            } finally {
                try {
                    ServiceCompat.stopForeground(this@AudioModeControlService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    Log.w(TAG, "stopForeground in finally failed: ${e.message}")
                }
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground on timeout failed: ${e.message}")
        }
        stopSelf(startId)
    }

    // 确保服务通知渠道已初始化
    private fun ensureNotificationChannel() {
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_audio_control_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }
}
