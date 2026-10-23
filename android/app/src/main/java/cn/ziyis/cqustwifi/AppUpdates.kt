package cn.ziyis.cqustwifi

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class UpdateState(val info:UpdateInfo?=null,val visible:Boolean=false,val downloading:Boolean=false,val percent:Int=0,val ready:Boolean=false,val error:String="")

object AppUpdates {
    val state=MutableStateFlow(UpdateState())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var lastAttempt=0L
    private var checking=false
    private var watching=false
    private fun prefs()=Repository.app.getSharedPreferences("updater",Context.MODE_PRIVATE)
    private fun target(version:String):File {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?")))
        return File(checkNotNull(Repository.app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)){"更新存储空间不可用"},"updates/update-$version.apk")
    }
    fun onResume(){
        resumeDownload()
        if(prefs().getBoolean("install_after_permission",false)&&Repository.app.packageManager.canRequestPackageInstalls()&&state.value.ready){
            prefs().edit().putBoolean("install_after_permission",false).apply();install()
        }
        val now=System.currentTimeMillis()
        if(checking||now-lastAttempt<120000||now-prefs().getLong("last_success",0)<6*60*60*1000)return
        lastAttempt=now;checking=true
        scope.launch{
            runCatching{Repository.checkUpdate()}.onSuccess{info->
                prefs().edit().putLong("last_success",now).apply()
                if(info.newer&&(prefs().getString("dismissed_version","")!=info.version||now-prefs().getLong("dismissed_at",0)>24*60*60*1000))offer(info)
            };checking=false
        }
    }
    fun offer(info:UpdateInfo){if(!state.value.downloading)state.value=UpdateState(info=info,visible=true)}
    fun dismiss(){state.value.info?.let{prefs().edit().putString("dismissed_version",it.version).putLong("dismissed_at",System.currentTimeMillis()).apply()};state.update{it.copy(visible=false)}}
    fun download(){
        val info=state.value.info?:return
        val url=info.download?:return
        if(state.value.downloading)return
        try{
            require(info.newer&&url.startsWith("https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/download/"))
            require(info.digest.matches(Regex("sha256:[0-9a-fA-F]{64}"))){"发布包尚缺校验信息，请在发布页查看。"}
            require(info.size in 1..200_000_000){"安装包大小异常"}
            val file=target(info.version);file.parentFile!!.mkdirs()
            if(file.exists())check(file.delete())
            val request=DownloadManager.Request(Uri.parse(url)).setTitle("CQUST Wi-Fi ${info.version}")
                .setDescription("下载完成后校验安装包，再由系统确认安装")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file))
            val id=Repository.app.getSystemService(DownloadManager::class.java).enqueue(request)
            prefs().edit().putLong("download_id",id).putString("download_info",JSONObject().put("version",info.version).put("digest",info.digest).put("size",info.size).put("url",url).put("release",info.url).put("notes",info.notes).toString()).apply()
            state.update{it.copy(downloading=true,ready=false,error="",percent=0)};resumeDownload()
        }catch(e:Exception){state.update{it.copy(error=if(e is IllegalArgumentException)e.message.orEmpty()else"下载未启动，请检查网络或存储空间。")}}
    }
    private fun resumeDownload(){
        if(watching)return
        val id=prefs().getLong("download_id",0);if(id==0L)return
        val info=runCatching{JSONObject(prefs().getString("download_info","").orEmpty()).let{o->UpdateInfo(o.getString("version"),o.getString("notes"),o.getString("url"),o.getString("release"),true,o.getString("digest"),o.getLong("size"))}}.getOrNull()?:return
        watching=true
        scope.launch{
            try{
                val manager=Repository.app.getSystemService(DownloadManager::class.java)
                var done=false
                while(!done){
                    manager.query(DownloadManager.Query().setFilterById(id)).use{cursor->
                        check(cursor.moveToFirst()){"下载任务已失效，请重试。"}
                        val status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if(status==DownloadManager.STATUS_FAILED)error("下载失败，请稍后重试。")
                        if(status==DownloadManager.STATUS_SUCCESSFUL){
                            verify(target(info.version),info)
                            state.value=UpdateState(info=info,visible=true,ready=true,percent=100);done=true
                        }else{
                            val total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val loaded=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            if(loaded>200_000_000){manager.remove(id);error("安装包超过大小限制")}
                            state.update{it.copy(info=info,downloading=true,percent=if(total>0)((loaded*100)/total).toInt().coerceIn(0,99)else 0)}
                        }
                    }
                    if(!done)delay(700)
                }
            }catch(e:Exception){
                prefs().edit().remove("download_id").apply()
                state.value=UpdateState(info=info,visible=true,error=e.message?.takeIf{it.length<180}?:"安装包校验未通过，请重新下载。")
            }finally{watching=false}
        }
    }
    @Suppress("DEPRECATION") private fun verify(file:File,info:UpdateInfo){
        check(file.isFile&&file.length()==info.size){"安装包长度不符，请重新下载。"}
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use{input->val buffer=ByteArray(65536);while(true){val size=input.read(buffer);if(size<0)break;digest.update(buffer,0,size)}}
        val actual=digest.digest().joinToString(""){"%02x".format(it.toInt()and 255)}
        check(actual.equals(info.digest.removePrefix("sha256:"),true)){"安装包校验值不符，已阻止安装。"}
        val pm=Repository.app.packageManager
        val archive=pm.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)?:error("无法读取安装包。")
        val current=pm.getPackageInfo(Repository.app.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        check(archive.packageName==Repository.app.packageName&&archive.longVersionCode>current.longVersionCode&&archive.versionName==info.version){"安装包版本或应用标识不符，已阻止安装。"}
        fun signers(p:android.content.pm.PackageInfo)=p.signingInfo?.apkContentsSigners?.map{MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList()}?.toSet().orEmpty()
        check(signers(current).isNotEmpty()&&signers(current)==signers(archive)){"签名不一致，不能直接覆盖更新。请在发布页查看重装说明；不会自动卸载或清除数据。"}
    }
    fun install(){
        val info=state.value.info?:return
        if(!state.value.ready)return
        val context=Repository.app
        try{
            if(!context.packageManager.canRequestPackageInstalls()){
                prefs().edit().putBoolean("install_after_permission",true).apply()
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return
            }
            verify(target(info.version),info)
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",target(info.version))
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }catch(e:Exception){state.update{it.copy(error=e.message?.takeIf{it.length<180}?:"无法打开系统安装界面。")}}
    }
}
