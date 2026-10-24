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
    private const val CAMPUS_HUXI = "虎溪校区"
    private const val CAMPUS_JINFENG = "金凤校区"

    // 智能提取楼栋并构造标准目的地搜索词
    fun buildWalkingDestination(position: String): String {
        val trimmed = position.trim()
        if (trimmed.isBlank() || trimmed == "待定" || trimmed.contains("未安排")) {
            return "$SCHOOL_NAME $CAMPUS_HUXI"
        }
        if (trimmed.contains(SCHOOL_NAME)) {
            return trimmed
        }

        // 仅在明确标明“金凤”时定向金凤校区，其余默认虎溪校区
        val campusName = if (trimmed.contains("金凤")) CAMPUS_JINFENG else CAMPUS_HUXI

        // 剥离校区前缀与房间号，提取纯建筑名称
        val withoutCampus = trimmed.replace(campusName, "").trim()
        val buildingPart = withoutCampus.split(Regex("\\s+")).firstOrNull().orEmpty()
        val cleanBuilding = buildingPart.replace(Regex("(?i)[a-z]?\\d{2,}.*$"), "").trim()
        val targetBuilding = cleanBuilding.ifBlank { buildingPart.ifBlank { withoutCampus } }

        return "$SCHOOL_NAME $campusName $targetBuilding".trim()
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
