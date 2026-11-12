package cn.ziyis.cqustwifi

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.*

object PortalBrowser {
    private const val CHROME="com.android.chrome"
    fun launch(context:Context,address:String,destination:String){
        val app=context.applicationContext
        val service=Intent("android.support.customtabs.action.CustomTabsService").setPackage(CHROME)
        if(app.packageManager.resolveService(service,0)==null){openLink(app,address);return}
        val handler=Handler(Looper.getMainLooper())
        var bound=false
        var session:CustomTabsSession?=null
        var navigated=false
        fun open(url:String){
            val intent=CustomTabsIntent.Builder(session).setShowTitle(true)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xff4166f5.toInt()).build()).build()
            intent.intent.setPackage(CHROME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try{intent.launchUrl(app,Uri.parse(url))}catch(_:android.content.ActivityNotFoundException){openLink(app,url)}
        }
        val connection=object:CustomTabsServiceConnection(){
            override fun onCustomTabsServiceConnected(name:ComponentName,client:CustomTabsClient){
                session=client.newSession(object:CustomTabsCallback(){
                    override fun onNavigationEvent(event:Int,extras:android.os.Bundle?){
                        if(event==CustomTabsCallback.NAVIGATION_FINISHED&&!navigated){navigated=true;if(destination!=PortalStore.URL&&destination!=address)handler.post{open(destination)}}
                    }
                })
                open(address)
            }
            override fun onServiceDisconnected(name:ComponentName){}
        }
        bound=CustomTabsClient.bindCustomTabsService(app,CHROME,connection)
        if(!bound){openLink(app,address);return}
        handler.postDelayed({if(bound){app.unbindService(connection);bound=false}},30000)
    }
}
