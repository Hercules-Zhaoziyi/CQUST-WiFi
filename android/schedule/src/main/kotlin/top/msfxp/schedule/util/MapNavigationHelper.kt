package top.msfxp.schedule.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import top.msfxp.schedule.R

// 地图应用类型
enum class MapAppType(
    val packageName: String,
    val appNameRes: Int
) {
    AMAP("com.autonavi.minimap", R.string.map_app_amap),
    BAIDU("com.baidu.BaiduMap", R.string.map_app_baidu),
    TENCENT("com.tencent.map", R.string.map_app_tencent)
}

// 地图步行导航与跳转工具类
object MapNavigationHelper {

    private const val SCHOOL_NAME = "重庆科技大学"
    private const val CAMPUS_HUXI = CourseLocationFormatter.CAMPUS_HUXI

    // 判断地点是否可导航
    private fun canNavigate(position: String): Boolean {
        val trimmed = position.trim()
        return trimmed.isNotBlank() && trimmed != "待定" && !trimmed.contains("未安排")
    }

    // 构造步行导航目的地：仅虎溪校区解析楼栋，其余保持原始文本
    fun buildWalkingDestination(position: String): String? {
        if (!canNavigate(position)) return null

        val trimmed = position.trim()
        if (trimmed.contains(SCHOOL_NAME)) {
            return trimmed
        }

        val parsed = CourseLocationFormatter.parse(trimmed)
        if (parsed.campus != CAMPUS_HUXI) {
            return "$SCHOOL_NAME $trimmed"
        }

        val target = parsed.building.ifBlank { parsed.room.ifBlank { parsed.detail } }
        return listOf(SCHOOL_NAME, parsed.campus, target)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    // 检查是否安装指定包名的应用
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    // 获取当前设备已安装的目标地图列表
    fun getAvailableMapApps(context: Context): List<MapAppType> {
        val list = mutableListOf<MapAppType>()
        if (isPackageInstalled(context, MapAppType.AMAP.packageName)) list.add(MapAppType.AMAP)
        if (isPackageInstalled(context, MapAppType.BAIDU.packageName)) list.add(MapAppType.BAIDU)
        if (isPackageInstalled(context, MapAppType.TENCENT.packageName)) list.add(MapAppType.TENCENT)
        return list
    }

    // 调起目标地图软件的步行导航
    fun openMapWalkingNavigation(context: Context, type: MapAppType, destination: String) {
        val encodedDest = Uri.encode(destination)
        try {
            when (type) {
                MapAppType.AMAP -> {
                    val uri = Uri.parse("androidamap://route/plan/?sourceApplication=cqust_schedule&dname=$encodedDest&dev=0&t=2")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        `package` = MapAppType.AMAP.packageName
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                MapAppType.BAIDU -> {
                    val uri = Uri.parse("baidumap://map/direction?destination=$encodedDest&mode=walking&src=andr.cqust.schedule")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        `package` = MapAppType.BAIDU.packageName
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                MapAppType.TENCENT -> {
                    val uri = Uri.parse("qqmap://map/routeplan?type=walk&from=我的位置&to=$encodedDest&referer=cqust_schedule")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        `package` = MapAppType.TENCENT.packageName
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, R.string.map_open_error, Toast.LENGTH_SHORT).show()
        }
    }
}
