package cn.ziyis.cqustwifi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.*
import kotlinx.coroutines.*
import top.msfxp.schedule.data.api.CqustCasAuthClient
import java.util.concurrent.atomic.AtomicBoolean

object PortalStore {
    const val URL="https://web.cqust.edu.cn/"
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val busy=AtomicBoolean(false)
    val connecting=kotlinx.coroutines.flow.MutableStateFlow(false)
    @Volatile private var cookies:List<String> = emptyList()
    @Volatile private var portalUser=""
    fun cookiesForWindow()=cookies.toList()
    fun open(context:android.content.Context){
        context.startActivity(Intent(context,PortalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun openAuthenticated(context:android.content.Context){
        val user=portalUser.ifBlank{Repository.state.value.username}
        val account=Repository.sharedAccount(user)
        if(account!=null)signIn(user,account.password,account.remember)else{
            cookies=emptyList();open(context)
        }
    }
    fun forget(user:String){if(portalUser==user){cookies=emptyList();portalUser=""}}
    fun signIn(user:String,password:String,remember:Boolean){
        if(!busy.compareAndSet(false,true))return
        connecting.value=true
        scope.launch {
            try {
                require(user.trim().matches(Regex("[A-Za-z0-9_-]{1,32}"))&&password.isNotEmpty()&&password.length<=256)
                val result=CqustCasAuthClient.login(user.trim(),password)
                val session=result.session
                if(!result.success||session==null){Repository.message(result.errorMessage?:Repository.app.getString(R.string.portal_failed));return@launch}
                try {
                    check(session.hasVpnTicket())
                    cookies=session.portalCookies()
                    check(cookies.isNotEmpty())
                    portalUser=user.trim()
                    Repository.rememberIdentity(user.trim(),password,remember)
                    withContext(Dispatchers.Main){open(Repository.app)}
                }finally{session.close()}
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){Repository.message(Repository.app.getString(R.string.portal_failed))}
            finally{busy.set(false);connecting.value=false}
        }
    }
}

class PortalActivity:Activity(){
    private lateinit var web:WebView
    private var loadGeneration=0
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;fitsSystemWindows=true}
        val toolbar=LinearLayout(this)
        toolbar.addView(Button(this).apply{text=getString(R.string.portal_back);setOnClickListener{if(web.canGoBack())web.goBack()else finish()}},LinearLayout.LayoutParams(0,-2,1f))
        toolbar.addView(Button(this).apply{text=getString(R.string.portal_external);setOnClickListener{openLink(this@PortalActivity,PortalStore.URL)}},LinearLayout.LayoutParams(0,-2,2f))
        web=WebView(this).apply{
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                    val u=request.url
                    if(u.scheme=="https"&&u.userInfo==null&&(u.host=="cqust.edu.cn"||u.host?.endsWith(".cqust.edu.cn")==true))return false
                    return true
                }
                override fun onReceivedSslError(view:WebView,handler:android.webkit.SslErrorHandler,error:android.net.http.SslError){handler.cancel();Toast.makeText(this@PortalActivity,R.string.portal_failed,Toast.LENGTH_LONG).show()}
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
        column.addView(toolbar);column.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(column)
        loadPortal()
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);loadPortal()}
    private fun loadPortal(){
        val generation=++loadGeneration
        val manager=CookieManager.getInstance()
        manager.removeAllCookies {
            if(generation!=loadGeneration||isFinishing||isDestroyed)return@removeAllCookies
            val cookies=PortalStore.cookiesForWindow()
            fun load(){if(generation==loadGeneration&&!isFinishing&&!isDestroyed){manager.flush();web.loadUrl(PortalStore.URL)}}
            if(cookies.isEmpty())load()else{
                var remaining=cookies.size
                cookies.forEach{cookie->manager.setCookie(PortalStore.URL,cookie){if(--remaining==0)load()}}
            }
        }
    }
    override fun onDestroy(){loadGeneration++;web.stopLoading();web.destroy();CookieManager.getInstance().removeAllCookies(null);super.onDestroy()}
}
