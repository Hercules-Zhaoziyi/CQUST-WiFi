package cn.ziyis.cqustwifi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Text

enum class ProbeStatus{IDLE,CHECKING,PORTAL,ONLINE,NO_WIFI,UNAVAILABLE}
data class AuthProbe(val status:ProbeStatus=ProbeStatus.IDLE,val host:String="")

@Composable internal fun AuthenticationGuide(onClose:()->Unit,onResult:(String)->Unit){
    val context=LocalContext.current
    val probe by Repository.authProbe.collectAsStateWithLifecycle()
    var address by remember{mutableStateOf("")}
    var invalid by remember{mutableStateOf(false)}
    val status=when(probe.status){
        ProbeStatus.IDLE->R.string.auth_probe_idle;ProbeStatus.CHECKING->R.string.auth_probe_working
        ProbeStatus.PORTAL->R.string.auth_probe_found;ProbeStatus.ONLINE->R.string.auth_probe_online
        ProbeStatus.NO_WIFI->R.string.auth_probe_no_wifi;ProbeStatus.UNAVAILABLE->R.string.auth_probe_failed
    }
    MiuixPrompt(onDismissRequest=onClose,title={Text(stringResource(R.string.auth_guide_title))},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Text(stringResource(R.string.auth_guide_body),fontSize=13.sp,lineHeight=22.sp)
            Text(if(probe.status==ProbeStatus.PORTAL)stringResource(status,probe.host)else stringResource(status),fontSize=14.sp,lineHeight=23.sp)
            ActionButton(onClick=Repository::probeAuthentication,enabled=probe.status!=ProbeStatus.CHECKING,modifier=Modifier.fillMaxWidth()){Text(stringResource(if(probe.status==ProbeStatus.CHECKING)R.string.auth_probe_working else R.string.auth_probe_start))}
            QuietButton(onClick={openLink(context,"http://aaa.cqust.edu.cn/")}){Text(stringResource(R.string.auth_probe_external))}
            MiuixInput(address,{address=it;invalid=false},label={Text(stringResource(R.string.auth_probe_manual))},maxLines=3)
            Text(stringResource(if(invalid)R.string.auth_probe_invalid else R.string.auth_probe_manual_help),fontSize=12.sp,lineHeight=21.sp)
            if(address.isNotBlank())SecondaryButton(onClick={if(Protocol.candidate(address.trim()))onResult(address.trim())else invalid=true},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.auth_probe_apply))}
        }
    },confirmButton={QuietButton(onClick=onClose){Text(stringResource(R.string.auth_guide_done))}})
}
