package cn.ziyis.cqustwifi

import android.app.Application

class CampusApp:Application() {
    override fun onCreate(){super.onCreate();Repository.initialize(this)}
}
