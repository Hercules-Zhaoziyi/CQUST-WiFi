package top.msfxp.schedule.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

// 外部应用跳转与交互辅助工具
object AppActionHelper {

    // 发起添加 QQ 群流程
    fun joinQQGroup(context: Context, key: String): Boolean {
        val uri = Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // 复制文本至系统剪贴板
    fun copyToClipboard(context: Context, text: String, label: String = "Text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    // 打开系统外部浏览器访问链接
    fun openBrowser(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private const val PREFS_DOWNLOAD = "app_download_prefs"
    private const val KEY_LAST_APK_DOWNLOAD_ID = "last_apk_download_id"
    private const val KEY_PENDING_INSTALL_URI = "pending_install_apk_uri"

    // 记录本 App 发起的 APK 下载任务 ID
    fun markApkDownloadId(context: Context, downloadId: Long) {
        context.getSharedPreferences(PREFS_DOWNLOAD, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_APK_DOWNLOAD_ID, downloadId)
            .apply()
    }

    // 检查是否属于本 App 跟踪的 APK 下载任务
    fun isManagedApkDownload(context: Context, downloadId: Long): Boolean {
        val savedId = context.getSharedPreferences(PREFS_DOWNLOAD, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_APK_DOWNLOAD_ID, -1L)
        return savedId != -1L && savedId == downloadId
    }

    // 记录等待安装的 APK URI (用于用户授权返回后自动继续安装)
    fun savePendingInstallApkUri(context: Context, uriString: String) {
        context.getSharedPreferences(PREFS_DOWNLOAD, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_INSTALL_URI, uriString)
            .apply()
    }

    // 获取并清除待安装的 APK URI
    fun consumePendingInstallApkUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_DOWNLOAD, Context.MODE_PRIVATE)
        val uri = prefs.getString(KEY_PENDING_INSTALL_URI, null)
        if (!uri.isNullOrBlank()) {
            prefs.edit().remove(KEY_PENDING_INSTALL_URI).apply()
        }
        return uri
    }

    // 调起应用安装器 (若未授权则跳转设置并在返回后自动继续)
    fun installApk(context: Context, apkUri: Uri) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            savePendingInstallApkUri(context, apkUri.toString())
            android.widget.Toast.makeText(
                context,
                context.getString(top.msfxp.schedule.R.string.about_install_permission_tip),
                android.widget.Toast.LENGTH_LONG
            ).show()
            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(settingsIntent)
            } catch (_: Exception) {
                launchInstallIntent(context, apkUri)
            }
            return
        }

        launchInstallIntent(context, apkUri)
    }

    // 执行安装意图
    fun launchInstallIntent(context: Context, apkUri: Uri) {
        try {
            android.widget.Toast.makeText(
                context,
                context.getString(top.msfxp.schedule.R.string.about_install_started_toast),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                context,
                context.getString(top.msfxp.schedule.R.string.about_install_failed_toast),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // 检查并恢复待安装任务 (用户授权返回时调用)
    fun resumePendingInstallIfNeeded(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && context.packageManager.canRequestPackageInstalls()) {
            val pendingUri = consumePendingInstallApkUri(context)
            if (!pendingUri.isNullOrBlank()) {
                launchInstallIntent(context, Uri.parse(pendingUri))
            }
        }
    }

    // 调用系统下载管理器直接下载文件 (默认存放在 /Download/重科课表/ 目录下)
    fun downloadWithSystemManager(
        context: Context,
        url: String,
        fileName: String,
        title: String,
        description: String,
        subDir: String = "重科课表"
    ): Boolean {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                ?: return false
            val subPath = if (subDir.isBlank()) fileName else "$subDir/$fileName"
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setMimeType("application/vnd.android.package-archive")
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, subPath)
                setTitle(title)
                setDescription(description)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val downloadId = downloadManager.enqueue(request)
            markApkDownloadId(context, downloadId)
            true
        } catch (_: Exception) {
            false
        }
    }

    // 检查本地是否已存在完整可用的安装包并返回安全 Content URI
    fun getExistingApkUri(
        context: Context,
        fileName: String,
        subDir: String = "重科课表"
    ): Uri? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val targetFile = if (subDir.isBlank()) {
            File(downloadDir, fileName)
        } else {
            File(File(downloadDir, subDir), fileName)
        }

        if (!targetFile.exists() || !targetFile.isFile || targetFile.length() <= 0L) {
            return null
        }

        // 校验 APK 结构完整性，避免半成品或损坏包
        val packageInfo = runCatching {
            context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
        }.getOrNull() ?: return null

        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        }.getOrNull()
    }

    // 统一处理 Release 安装包下载或安装 (本地已存在则秒装，未存在则启动系统后台下载)
    fun processReleaseDownload(
        context: Context,
        release: top.msfxp.schedule.data.api.AppRelease,
        onNotify: (String) -> Unit
    ): Boolean {
        val fileName = "cqust-schedule-${release.tagName}-arm64-v8a.apk"
        val existingUri = getExistingApkUri(context, fileName)
        if (existingUri != null) {
            onNotify(context.getString(top.msfxp.schedule.R.string.about_apk_already_downloaded_toast))
            installApk(context, existingUri)
            return true
        }

        val targetUrl = top.msfxp.schedule.data.api.UpdateCheckService.resolveApkDownloadUrl(release)
        val isApk = targetUrl.endsWith(".apk", ignoreCase = true) ||
                release.assets.any { it.browserDownloadUrl == targetUrl }

        if (!isApk) {
            val opened = openBrowser(context, targetUrl)
            if (!opened) {
                onNotify(context.getString(top.msfxp.schedule.R.string.about_browser_open_failed))
            }
            return opened
        }

        val title = context.getString(top.msfxp.schedule.R.string.about_download_notification_title, release.tagName)
        val desc = context.getString(top.msfxp.schedule.R.string.about_download_notification_desc)
        val started = downloadWithSystemManager(
            context = context,
            url = targetUrl,
            fileName = fileName,
            title = title,
            description = desc
        )
        if (started) {
            onNotify(context.getString(top.msfxp.schedule.R.string.about_download_started_toast))
            return true
        } else {
            val opened = openBrowser(context, targetUrl)
            if (!opened) {
                onNotify(context.getString(top.msfxp.schedule.R.string.about_browser_open_failed))
            }
            return false
        }
    }
}
