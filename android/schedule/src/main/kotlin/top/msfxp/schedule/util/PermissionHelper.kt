package top.msfxp.schedule.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// 权限检测与系统设置跳转工具类
object PermissionHelper {
    private const val TAG = "PermissionHelper"

    // 检测通知权限
    fun isNotificationGranted(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // 检测精确闹钟权限
    fun isExactAlarmGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    // 检测日历读写权限
    fun isCalendarGranted(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    // 检测勿扰策略访问权限
    fun isDndAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted ?: false
    }

    // 检测忽略电池优化权限
    fun isBatteryOptimizationsIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    // 打开通知设置
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    // 打开精确闹钟设置
    fun openExactAlarmSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppDetailsSettings(context)
            }
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    // 打开勿扰策略设置
    fun openDndSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    // 打开系统自启动设置
    fun openAutoStartSettings(context: Context) {
        val brand = Build.MANUFACTURER.lowercase()
        val intents = when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> listOf(
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            )
            brand.contains("huawei") -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            brand.contains("honor") -> listOf(
                Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            )
            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
            )
            brand.contains("vivo") || brand.contains("iqoo") -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            )
            brand.contains("meizu") -> listOf(
                Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")),
                Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.PermissionMainActivity"))
            )
            else -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Start vendor autostart intent failed: ${e.message}")
            }
        }
        openAppDetailsSettings(context)
    }

    // 打开后台运行与电池优化设置
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(listIntent)
            } catch (_: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    // 打开应用详情设置
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Open app details settings failed: ${e.message}")
        }
    }
}
