package top.msfxp.schedule.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import top.msfxp.schedule.R
import top.msfxp.schedule.util.AppActionHelper
import java.io.File

// 应用安装包下载完成自动调起安装广播接收器
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L || !AppActionHelper.isManagedApkDownload(context, downloadId)) {
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return

        cursor.use { c ->
            if (!c.moveToFirst()) return
            val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIdx < 0 || c.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) return

            val apkUri = resolveApkUri(context, downloadManager, downloadId, c) ?: return
            AppActionHelper.installApk(context, apkUri)
        }
    }

    // 解析安装包安全 Content URI
    private fun resolveApkUri(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        cursor: android.database.Cursor
    ): Uri? {
        val dmUri = downloadManager.getUriForDownloadedFile(downloadId)
        if (dmUri != null) return dmUri

        val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        if (localUriIdx >= 0) {
            val localUriStr = cursor.getString(localUriIdx)
            if (!localUriStr.isNullOrBlank()) {
                val file = File(Uri.parse(localUriStr).path.orEmpty())
                if (file.exists()) {
                    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            }
        }
        return null
    }
}
