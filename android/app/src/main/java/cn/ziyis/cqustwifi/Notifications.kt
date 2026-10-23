package cn.ziyis.cqustwifi

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {
    fun build(context:Context,text:String,vpn:Boolean=false):Notification {
        val manager=context.getSystemService(NotificationManager::class.java)
        val channel=if(vpn)"vpn"else "monitor"
        manager.createNotificationChannel(NotificationChannel(channel,
            if(vpn)"电脑代理连接"else"校园网后台连接",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(context,if(vpn)2 else 1,
            Intent(context,if(vpn)CampusVpnService::class.java else MonitorService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("CQUST Wi-Fi").setContentText(text).setContentIntent(open)
            .setOngoing(true).setSilent(true).addAction(0,"停止",stop).build()
    }
}
