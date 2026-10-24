package cn.ziyis.cqustwifi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.preference.ArrowPreference

enum class Community(val title:Int,val number:Int,val key:String){
    WIFI(R.string.unified_wifi_group,R.string.unified_wifi_group_number,"t88UwvHx3nH6bIdOXY2Odx5g8Qw-lB1k"),
    COURSE(R.string.unified_course_group,R.string.unified_course_group_number,"rIdKEU42PuQ9p4al09NyyXjRWlm62N_f")
}

fun joinCommunity(context:Context,community:Community){
    val uri=Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D${community.key}")
    val intent=Intent(Intent.ACTION_VIEW,uri)
    if(context !is android.app.Activity)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try{context.startActivity(intent)}catch(_:android.content.ActivityNotFoundException){copyCommunity(context,community)}
    catch(_:SecurityException){copyCommunity(context,community)}
}
private fun copyCommunity(context:Context,community:Community){
    val number=context.getString(community.number)
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(context.getString(community.title),number))
    Repository.message(context.getString(R.string.unified_group_fallback,number))
}
@Composable internal fun CommunityLinks(){
    val context=LocalContext.current
    Community.entries.forEach{community->ArrowPreference(title=stringResource(community.title),summary=stringResource(community.number),onClick={joinCommunity(context,community)})}
}
