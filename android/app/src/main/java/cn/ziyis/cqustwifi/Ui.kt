package cn.ziyis.cqustwifi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val Blue=Color(0xff4166f5)
private val Green=Color(0xff1eaf91)
private val CardShape=RoundedCornerShape(26.dp)
@Composable private fun GithubIcon(){val context=LocalContext.current;AsyncImage("file:///android_asset/github.svg",null,imageLoader=remember{ImageLoader.Builder(context).components{add(SvgDecoder.Factory())}.build()},modifier=Modifier.size(17.dp))}

@Composable fun CampusUi(onVpn:()->Unit,onNotifications:()->Unit) {
    val state by Repository.state.collectAsStateWithLifecycle()
    val issue by Repository.issues.collectAsStateWithLifecycle()
    var page by remember{mutableStateOf("连接")}
    var loginTarget by remember{mutableIntStateOf(0)}
    var portal by remember{mutableStateOf(false)}
    val systemDark=isSystemInDarkTheme()
    val dark=when(state.prefs.theme){"dark"->true;"light"->false;else->systemDark}
    val activity=LocalContext.current as? android.app.Activity
    SideEffect{activity?.let{
        androidx.core.view.WindowInsetsControllerCompat(it.window,it.window.decorView).apply{
            isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
        }
    }}
    val scheme=if(dark)darkColorScheme(primary=Color(0xff8ca4ff),background=Color(0xff101725),
        surface=Color(0xff192334),surfaceVariant=Color(0xff222f44),onSurface=Color(0xffe7edf9),
        onBackground=Color(0xffe7edf9),onSurfaceVariant=Color(0xff97a7bd),outline=Color(0xff52627b),
        secondaryContainer=Color(0xff26395f),onSecondaryContainer=Color(0xffa6b8ff))
        else lightColorScheme(primary=Blue,background=Color(0xfff4f6fb),surface=Color.White,
            surfaceVariant=Color(0xffedf1fa),onSurface=Color(0xff1b293e),onBackground=Color(0xff1b293e),
            onSurfaceVariant=Color(0xff7e8faa),outline=Color(0xffc4cfe0),outlineVariant=Color(0xffe1e7f1),
            secondaryContainer=Color(0xffe5ebff),onSecondaryContainer=Blue)
    val background by animateColorAsState(scheme.background,tween(if(state.prefs.animations)450 else 0),label="theme")
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(state.message){if(state.message.isNotEmpty()){snackbar.showSnackbar(state.message);Repository.dismissMessage()}}
    MaterialTheme(colorScheme=scheme) {
        Scaffold(containerColor=background,snackbarHost={SnackbarHost(snackbar)},topBar={
            if(page!="课程表")Column(Modifier.statusBarsPadding().background(background)){
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    Image(painterResource(R.drawable.app_logo),null,Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text("CQUST Wi-Fi",fontWeight=FontWeight.Bold,fontSize=22.sp)
                        Text("校园网络 · 轻松连接",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick={Repository.preferences(state.prefs.copy(theme=if(dark)"light"else"dark"))}){
                        Icon(if(dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,"切换主题")}
                    IconButton(onClick={page=if(page=="设置")"连接"else"设置"}){Icon(Icons.Outlined.Tune,"设置")}
                    IconButton(onClick={page=if(page=="关于")"连接"else"关于"}){Icon(Icons.Outlined.Info,"关于")}
                }
            }
        },bottomBar={
            NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
                listOf("连接" to Icons.Outlined.Wifi,"课程表" to Icons.Outlined.CalendarMonth,"常用网站" to Icons.Outlined.Language,
                    "代理" to Icons.Outlined.Shield).forEach{(name,icon)->
                    NavigationBarItem(selected=page==name,onClick={page=name},icon={Icon(icon,null)},label={Text(name,fontSize=12.sp)})
                }
            }
        }){padding->
            AnimatedContent(page,Modifier.padding(padding).fillMaxSize(),transitionSpec={
                fadeIn(tween(if(state.prefs.animations)220 else 0)) togetherWith fadeOut(tween(if(state.prefs.animations)100 else 0))
            },label="page"){current->
                when(current){
                    "连接"->Home(state,onPortal={portal=true},initialMode=loginTarget)
                    "常用网站"->Sites()
                    "课程表"->SchedulePage(onLogin={loginTarget=1;page="连接"})
                    "代理"->ProxyPage(state,onVpn)
                    "设置"->SettingsPage(state,onLogs={page="日志"},onNotifications)
                    "日志"->Logs(state)
                    else->About()
                }
            }
        }
        if(issue!=null)AuthDialog(issue!!,onPortal={Repository.dismissIssue();portal=true})
        if(portal)PortalDialog(onClose={portal=false},onResult={url->portal=false;Repository.retryAuth(url,"","")})
    }
}

@Composable private fun PageColumn(content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
@Composable private fun Heading(kicker:String,title:String,subtitle:String){
    Column(Modifier.padding(top=14.dp,bottom=4.dp)){
        Text(kicker,fontSize=11.sp,letterSpacing=2.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
        Text(subtitle,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp),lineHeight=22.sp)
    }
}
@Composable private fun Panel(content:@Composable ColumnScope.()->Unit){
    Surface(shape=CardShape,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}
@Composable private fun Setting(title:String,description:String,value:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f).padding(end=14.dp)){Text(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            Text(description,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))}
        Switch(value,onCheckedChange=onChange)
    }
}
@Composable private fun SecretField(value:String,onChange:(String)->Unit,label:String="密码",visibleByDefault:Boolean=false,placeholder:String=""){
    var visible by remember{mutableStateOf(visibleByDefault)}
    OutlinedTextField(value,onValueChange=onChange,label={Text(label)},placeholder={Text(placeholder)},
        modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),
        visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
        trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"隐藏密码"else"显示密码")}})
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier){
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally){
        Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank{"—"},fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=7.dp))
    }
}
@Composable private fun Home(s:ViewState,onPortal:()->Unit,initialMode:Int=0){
    var loginMode by remember{mutableIntStateOf(initialMode)}
    val scheduleMessage by ScheduleStore.message.collectAsStateWithLifecycle()
    var user by remember{mutableStateOf(s.username)}
    var password by remember{mutableStateOf("")}
    var rememberPassword by remember{mutableStateOf(true)}
    var accounts by remember{mutableStateOf(false)}
    var logoutConfirm by remember{mutableStateOf(false)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(s.username){user=s.username;password=""}
    LaunchedEffect(Unit){while(true){delay(1000);now=System.currentTimeMillis()}}
    val connected=s.status in listOf(LinkState.CONNECTED,LinkState.OPEN)
    val submitLogin:()->Unit={
        val secret=password.ifEmpty{Repository.sharedAccount(user)?.password.orEmpty()}
        if(loginMode!=1)Repository.login(user,password,rememberPassword)
        if(loginMode!=0)ScheduleStore.sync(user,secret,rememberPassword)
    }
    val title=when(s.status){LinkState.CONNECTED,LinkState.OPEN->"已连接";LinkState.CONNECTING->"正在连接";LinkState.OFFLINE->"未连接 Wi-Fi";LinkState.NEED_AUTH->"等待连接";LinkState.UNKNOWN->"状态待确认"}
    val hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting=when(hour){in 5..10->"早安，让今天的灵感从在线开始。";in 11..13->"午间小憩，连接不必费心。";in 14..18->"下午好，让专注与灵感保持在线。";in 19..22->"夜色渐深，愿你的努力都有回响。";else->"夜深了，网络在线，也记得早点休息。"}
    PageColumn{
        Panel{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.VerifiedUser,null,tint=MaterialTheme.colorScheme.primary)
                Text(s.name.ifBlank{s.username.ifBlank{"校园网用户"}},Modifier.weight(1f).padding(start=9.dp),fontSize=14.sp)
                SuggestionChip(onClick={Repository.refresh()},label={Text(if(connected)"已连接"else"刷新状态")})
            }
            Box(Modifier.fillMaxWidth().height(230.dp),contentAlignment=Alignment.Center){
                val transition=rememberInfiniteTransition(label="connection")
                val pulse by transition.animateFloat(.94f,1.06f,infiniteRepeatable(tween(2200),RepeatMode.Reverse),label="pulse")
                val onlineColor=if(connected)Green else Blue
                Box(Modifier.size(if(s.prefs.animations)(210*pulse).dp else 210.dp).border(1.dp,onlineColor.copy(alpha=.10f),CircleShape))
                Box(Modifier.size(178.dp).border(1.dp,onlineColor.copy(alpha=.16f),CircleShape))
                Surface(onClick={
                    if(s.busy)return@Surface
                    if(connected&&s.status!=LinkState.OPEN)Repository.disconnect()
                    else if(s.status==LinkState.OPEN)Repository.refresh()
                    else submitLogin()
                },shape=CircleShape,color=onlineColor,shadowElevation=14.dp,modifier=Modifier.size(138.dp).testTag("power")){
                    Box(contentAlignment=Alignment.Center){if(s.busy)CircularProgressIndicator(color=Color.White)
                    else Icon(Icons.Outlined.PowerSettingsNew,title,Modifier.size(64.dp),tint=Color.White)}
                }
            }
            Text(title,Modifier.fillMaxWidth(),fontSize=29.sp,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,letterSpacing=1.sp)
            Text(if(connected)greeting else "连接校园，让灵感不掉线。",Modifier.fillMaxWidth(),
                fontSize=13.sp,lineHeight=21.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(s.started>0){val seconds=((now-s.started)/1000).coerceAtLeast(0)
                Text("本次已连接  %02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60),
                    Modifier.fillMaxWidth(),fontSize=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.background){
                Row(Modifier.fillMaxWidth().padding(vertical=18.dp)){
                    Metric("当前账号",s.username,Modifier.weight(1f));VerticalDivider(Modifier.height(42.dp))
                    Metric("局域网 IP",s.ip,Modifier.weight(1f))
                }
            }
            if(connected&&s.status!=LinkState.OPEN)TextButton(onClick={logoutConfirm=true},modifier=Modifier.align(Alignment.CenterHorizontally)){
                Icon(Icons.Outlined.Logout,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("退出登录")}
            if(s.status==LinkState.OPEN)TextButton(onClick=onPortal,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("在官方页面管理账号与下线")}
        }
        run{
            Panel{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("你好，欢迎回来",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("使用本人的校园网账号",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Image(painterResource(R.drawable.school_logo),"重庆科技大学",Modifier.width(120.dp).height(32.dp),contentScale=ContentScale.Fit)
                }
                OutlinedTextField(user,{user=it},label={Text("校园网账号")},modifier=Modifier.fillMaxWidth().testTag("username"),
                    singleLine=true,shape=RoundedCornerShape(16.dp),trailingIcon={IconButton(onClick={accounts=true}){Icon(Icons.Outlined.ExpandMore,"选择账号")}})
                SecretField(password,{password=it},"登录密码",placeholder=if(Repository.hasPassword(user))"密码已安全保存，可留空"else"请输入密码")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("校园网","课表","两者").forEachIndexed{i,title->FilterChip(loginMode==i,{loginMode=i},label={Text(title)})}
                }
                if(loginMode!=0){
                    Text("课表独立使用统一身份认证，无需开通校园网。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(scheduleMessage.isNotEmpty())Text(scheduleMessage,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(rememberPassword,{rememberPassword=it});Text("记住账号密码",fontSize=13.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick=onPortal){Text("认证帮助")}
                }
                Button(onClick=submitLogin,enabled=!s.busy,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(17.dp)){Text("登录");Spacer(Modifier.width(10.dp));Icon(Icons.Outlined.ArrowForward,null)}
                Text("密码使用 Android Keystore 加密保存在本机。",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel{Setting("自动登录","检测到校园认证入口时，使用选定账号连接。",s.prefs.autoLogin){Repository.preferences(s.prefs.copy(autoLogin=it))}}
        Text("每 ${if(s.prefs.interval==120)"2 分钟"else"${s.prefs.interval} 秒"}检查 · 可在设置中调整",fontSize=12.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
    if(accounts)AlertDialog(onDismissRequest={accounts=false},containerColor=MaterialTheme.colorScheme.surface,title={Text("校园网账号")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("选择已保存账号，或添加新的校园网账号。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            s.accounts.forEach{account->Row(verticalAlignment=Alignment.CenterVertically){
                Surface(onClick={user=account;Repository.chooseAccount(account);accounts=false},shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.weight(1f)){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AccountCircle,null);Spacer(Modifier.width(12.dp));Column{Text(account,fontWeight=FontWeight.SemiBold);Text(if(account==s.username)"当前账号"else"密码已保存",fontSize=11.sp)};Spacer(Modifier.weight(1f));if(account==s.username)Icon(Icons.Outlined.CheckCircle,null,tint=MaterialTheme.colorScheme.primary)}
                }
                IconButton(onClick={Repository.forgetAccount(account)}){Icon(Icons.Outlined.DeleteOutline,"忘记账号")}
            }}
            OutlinedButton(onClick={user="";password="";accounts=false},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("添加其他账号")}
        }
    },confirmButton={TextButton(onClick={accounts=false}){Text("关闭")}})
    if(logoutConfirm)AlertDialog(onDismissRequest={logoutConfirm=false},title={Text("退出登录？")},text={Text("将向校园网关提交下线请求，已保存的账号密码仍保留。")},
        confirmButton={TextButton(onClick={Repository.disconnect(true);logoutConfirm=false}){Text("退出登录")}},
        dismissButton={TextButton(onClick={logoutConfirm=false}){Text("取消")}})
}

@Composable private fun Sites(){
    val context=LocalContext.current
    val sites=remember{Repository.sites()}
    val loader=remember{ImageLoader.Builder(context).components{add(SvgDecoder.Factory())}.build()}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Heading("CAMPUS SHORTCUTS","常用网站","校园服务，触手可及。")}
        items(sites.chunked(2)){row->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                row.forEach{site->
                    Surface(onClick={openLink(context,site.url)},shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.weight(1f)){
                        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            AsyncImage("file:///android_asset/${site.icon}",site.name,imageLoader=loader,modifier=Modifier.size(42.dp))
                            Text(site.name,fontSize=15.sp,fontWeight=FontWeight.SemiBold,lineHeight=22.sp)
                            Text(if(site.viaPortal)"经信息门户"else"直达网站",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(row.size==1)Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun ProxyPage(s:ViewState,onVpn:()->Unit){
    val profile=remember{Repository.vpnProfile()}
    var tab by remember{mutableIntStateOf(0)}
    var host by remember{mutableStateOf(profile.host)}
    var port by remember{mutableStateOf(profile.port.toString())}
    var user by remember{mutableStateOf(profile.username.ifBlank{s.username})}
    var campus by remember{mutableStateOf(profile.campusPassword)}
    var password by remember{mutableStateOf(if(profile.campusPassword)""else profile.password)}
    var sharePort by remember{mutableStateOf("7890")}
    var shareUser by remember{mutableStateOf(s.username)}
    var sharePass by remember{mutableStateOf("")}
    var shareCampus by remember{mutableStateOf(true)}
    var lan by remember{mutableStateOf(true)}
    var mode by remember{mutableStateOf("mixed")}
    val context=LocalContext.current
    var scanned by remember{mutableStateOf<VpnProfile?>(null)}
    val scanner=androidx.activity.compose.rememberLauncherForActivityResult(com.journeyapps.barcodescanner.ScanContract()){result->
        result.contents?.let{raw->runCatching{ProxyQr.parse(raw)}.onSuccess{scanned=it}.onFailure{Repository.message("不是有效的局域网代理二维码，请核对桌面端配置。")}}
    }
    PageColumn{
        Heading("YOUR DEVICES, CONNECTED","代理","连接电脑共享的网络，或在自己的设备之间转发。")
        if(tab==0&&!s.vpn)OutlinedButton(onClick={scanner.launch(com.journeyapps.barcodescanner.ScanOptions().setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE).setPrompt("扫描桌面端代理配置二维码").setOrientationLocked(false))},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text("扫码导入并连接")
        }
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant){
            Row(Modifier.fillMaxWidth().padding(5.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("连接电脑","本机共享").forEachIndexed{i,title->
                    Surface(onClick={tab=i},shape=RoundedCornerShape(16.dp),
                        color=if(tab==i)MaterialTheme.colorScheme.surface else Color.Transparent,modifier=Modifier.weight(1f)){
                        Row(Modifier.padding(vertical=14.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                            Icon(if(i==0)Icons.Outlined.Computer else Icons.Outlined.Share,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(title,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if(tab==0){
            Panel{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Laptop,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text("电脑 SOCKS5",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        Text(s.vpnMessage,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                if(!s.vpn){
                    OutlinedTextField(host,{host=it.trim()},label={Text("电脑服务器 IP")},placeholder={Text("例如 172.21.x.x")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("端口")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    OutlinedTextField(user,{user=it},label={Text("代理用户名")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
                    Setting("使用校园网密码","仅当电脑端也选择校园网密码时使用；不会显示密码。",campus){campus=it;password=""}
                    if(!campus)SecretField(password,{password=it},"电脑代理密码",visibleByDefault=true)
                }else{
                    Text("socks5://$host:$port",fontSize=15.sp)
                    Text("代理用户名：$user",fontSize=13.sp)
                    Text(s.transfer,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick={
                    if(s.vpn)onVpn()
                    else runCatching{Repository.saveVpn(host,port.toIntOrNull()?:0,user,password,campus)}.onSuccess{onVpn()}.onFailure{Repository.message(it.message?:"代理信息无效。")}
                },modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(17.dp)){Icon(if(s.vpn)Icons.Outlined.StopCircle else Icons.Outlined.VpnKey,null);Spacer(Modifier.width(9.dp));Text(if(s.vpn)"断开电脑代理"else"连接并授权 VPN")}
                Setting("自动连接","仅在保存配置时的 Wi-Fi 网关匹配、且已获系统授权时尝试连接。",s.prefs.vpnAuto){Repository.preferences(s.prefs.copy(vpnAuto=it))}
            }
            Panel{
                Text("连接前确认",fontWeight=FontWeight.SemiBold)
                Text("1. 电脑开启 HTTP + SOCKS5 或 SOCKS5 代理。\n2. 手机连接可访问电脑的校园 Wi-Fi，Wi-Fi 系统代理设为“无”。\n3. 填写电脑显示的 IP、端口、账号密码，并确认 Android VPN 授权。",fontSize=13.sp,lineHeight=24.sp)
                Text("VPN/TUN 转发其他 App 的公网 IPv4/IPv6、TCP/UDP 和 DNS；校园内网与本应用认证流量直连 Wi-Fi。SOCKS5 上游本身不加密，仅在可信网络使用。系统限制或不兼容流量仍可能影响个别 App。",fontSize=12.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }else{
            Panel{
                Setting("启用代理",if(s.sharing)"运行中 · 必须账号密码认证"else"已关闭 · 不监听端口",s.sharing){
                    if(!it)Repository.stopSharing()else Repository.startSharing(ProxyOptions(sharePort.toIntOrNull()?:0,mode,lan,shareUser,sharePass,shareCampus))
                }
                if(!s.sharing){
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        listOf("mixed" to "混合","http" to "HTTP","socks5" to "SOCKS5").forEach{(id,label)->FilterChip(mode==id,{mode=id},label={Text(label)})}
                    }
                    OutlinedTextField(sharePort,{sharePort=it.filter(Char::isDigit)},label={Text("监听端口")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
                    OutlinedTextField(shareUser,{shareUser=it},label={Text("代理用户名")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
                    Setting("使用校园网密码","由后台复用，不显示或复制。更推荐独立的代理密码。",shareCampus){shareCampus=it;sharePass=""}
                    if(!shareCampus){
                        SecretField(sharePass,{sharePass=it},"代理密码",visibleByDefault=true)
                        TextButton(onClick={sharePass=Protocol.recommendedPassword()}){Text("推荐密码")}
                    }
                    Setting("允许局域网设备连接","只监听当前 Wi-Fi IP，不需要填写允许的设备 IP。",lan){lan=it}
                }else{
                    Text(s.sharingAddress,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                    Text("服务器：${s.sharingAddress.substringBeforeLast(':')}\n端口：${s.sharingAddress.substringAfterLast(':')}\n代理用户名：${s.sharingUser}",fontSize=14.sp,lineHeight=27.sp)
                    val uri=(if(mode=="http")"http"else"socks5")+"://"+s.sharingAddress
                    OutlinedButton(onClick={copy(context,uri)}){Icon(Icons.Outlined.ContentCopy,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("复制协议链接")}
                    if(s.sharingSecret)Text("正在使用校园网密码，不提供显示或复制。",fontSize=12.sp)
                    else Repository.displayedSharePassword()?.let{secret->SecretField(secret,{},"本次代理密码",visibleByDefault=true)}
                }
            }
            Text("HTTP 支持 HTTPS CONNECT，不解密网页内容；SOCKS5 支持 TCP/UDP。认证本身不加密，请仅在可信网络与自己的设备之间使用。网络隔离可能阻止互访，应用不会改变校园网设备配额。",fontSize=12.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    scanned?.let{p->AlertDialog(onDismissRequest={scanned=null},title={Text("确认连接电脑代理")},text={Text("服务器：${p.host}\n端口：${p.port}\n用户名：${p.username}\n\n二维码包含代理凭据，请只扫描自己电脑生成的配置。")},confirmButton={TextButton(onClick={
        runCatching{Repository.saveVpn(p.host,p.port,p.username,p.password,false)}.onSuccess{host=p.host;port=p.port.toString();user=p.username;password=p.password;campus=false;scanned=null;onVpn()}.onFailure{Repository.message("代理配置保存失败")}
    }){Text("确认并连接")}},dismissButton={TextButton(onClick={scanned=null}){Text("取消")}})}
}
private fun copy(context:Context,text:String){context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("代理地址",text));Repository.message("已复制")}

@Composable private fun SettingsPage(s:ViewState,onLogs:()->Unit,onNotifications:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var update by remember{mutableStateOf<UpdateInfo?>(null)}
    var checking by remember{mutableStateOf(false)}
    PageColumn{
        Heading("MAKE IT YOURS","连接与偏好","操作即时生效，让连接按你的习惯运行。")
        Panel{
            Setting("自动登录","检测到认证入口时使用选定账号；不会绕过验证码。",s.prefs.autoLogin){Repository.preferences(s.prefs.copy(autoLogin=it))}
            HorizontalDivider()
            Setting("始终保持连接","会话失效后静默尝试重连；手动断开时暂停。",s.prefs.keepConnected){Repository.preferences(s.prefs.copy(keepConnected=it))}
            HorizontalDivider()
            Text("状态检查间隔",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(5 to "5 秒",10 to "10 秒",120 to "2 分钟").forEach{(n,label)->
                    FilterChip(s.prefs.interval==n,{Repository.preferences(s.prefs.copy(interval=n))},label={Text(label)})
                }
            }
            HorizontalDivider()
            Setting("界面动效","页面淡入、主题渐变与连接按钮呼吸。",s.prefs.animations){Repository.preferences(s.prefs.copy(animations=it))}
            HorizontalDivider()
            Text("界面主题",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("system" to "跟随系统","light" to "白天","dark" to "夜间").forEach{(id,label)->
                    FilterChip(s.prefs.theme==id,{Repository.preferences(s.prefs.copy(theme=id))},label={Text(label)})
                }
            }
        }
        Panel{
            Text("后台运行",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text("Android 可能限制后台服务。请按手机品牌允许自启动、后台运行和通知。强行停止应用后，需要手动重新打开；不能保证所有系统下永久保活。",fontSize=13.sp,lineHeight=23.sp)
            OutlinedButton(onClick=onNotifications,modifier=Modifier.fillMaxWidth()){Text("允许通知栏连接入口")}
            OutlinedButton(onClick={batterySettings(context)},modifier=Modifier.fillMaxWidth()){Text("打开电池与后台设置")}
            TextButton(onClick=onLogs){Icon(Icons.Outlined.ReceiptLong,null);Spacer(Modifier.width(9.dp));Text("查看运行日志")}
        }
        Panel{
            Text("软件更新",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text("当前版本 ${BuildConfig.VERSION_NAME} · Android",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={checking=true;scope.launch{
                runCatching{Repository.checkUpdate()}.onSuccess{update=it}.onFailure{Repository.message("更新检测失败，请稍后重试。")}
                checking=false
            }},enabled=!checking){Text(if(checking)"正在检查…"else"检查 GitHub 新版本")}
            update?.let{u->
                Text(if(u.newer)"发现新版本 ${u.version}"else"暂未发现更新版本",fontWeight=FontWeight.SemiBold)
                Markdown(u.notes)
                if(u.newer&&u.download!=null)OutlinedButton(onClick={openLink(context,u.download)}){Text("下载 Android 安装包")}
            }
        }
    }
}
@Composable private fun Markdown(text:String){
    val color=MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(factory={context->TextView(context).apply{textSize=14f;setTextColor(color);setTextIsSelectable(true)}},
        update={view->view.setTextColor(color);Markwon.create(view.context).setMarkdown(view,
            text.replace(Regex("""(?i)(javascript|intent|file|content|data):[^\s)]+"""),"#"))},
        modifier=Modifier.fillMaxWidth())
}
@Composable private fun Logs(s:ViewState){
    PageColumn{
        Heading("CONNECTION JOURNAL","日志","只记录脱敏的连接阶段，不记录密码或完整认证网址。")
        OutlinedButton(onClick=Repository::clearLogs){Text("清空本次日志")}
        Panel{if(s.logs.isEmpty())Text("暂无日志",fontSize=13.sp)else s.logs.reversed().forEach{Text(it,fontSize=12.sp,lineHeight=21.sp)}}
    }
}
@Composable private fun About(){
    val context=LocalContext.current
    var licenses by remember{mutableStateOf(false)}
    PageColumn{
        Panel{
            Image(painterResource(R.drawable.app_logo),null,Modifier.size(74.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(22.dp)))
            Text("CQUST Wi-Fi",Modifier.align(Alignment.CenterHorizontally),fontSize=29.sp,fontWeight=FontWeight.Bold)
            Text("${BuildConfig.VERSION_NAME} · Android",Modifier.align(Alignment.CenterHorizontally),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("让校园网络连接，简单一点。",Modifier.align(Alignment.CenterHorizontally),fontSize=14.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                TextButton(onClick={openLink(context,"https://www.xiaohongshu.com/user/profile/60d40af7000000000101f612")}){
                    AsyncImage("file:///android_asset/xiaohongshu-logo.svg",null,imageLoader=remember{ImageLoader.Builder(context).components{add(SvgDecoder.Factory())}.build()},modifier=Modifier.width(26.dp).height(16.dp));Spacer(Modifier.width(4.dp));Text("十七号旅客",fontSize=11.sp)}
                TextButton(onClick={openLink(context,"https://github.com/yuexps")}){GithubIcon();Spacer(Modifier.width(4.dp));Text("GitHub",fontSize=11.sp)}
                TextButton(onClick={openLink(context,"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi")}){Icon(Icons.Outlined.Code,null,Modifier.size(15.dp));Text("开源项目",fontSize=11.sp)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedButton(onClick={openLink(context,"https://qm.qq.com/q/Rn7B5NBbiK")},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Groups,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("加入内测群",fontSize=12.sp)}
                OutlinedButton(onClick={openLink(context,"https://cqust-wifi.ziyis.cn/")},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Language,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("软件介绍",fontSize=12.sp)}
            }
        }
        Panel{
            Text("Android 版，轻松连接",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            Text("Wi-Fi 定向认证、多账号与安全凭据、验证码、常用网站、通知栏监测，以及电脑 SOCKS5 的 VPN/TUN 转发。",fontSize=14.sp,lineHeight=24.sp)
            Text("本软件为非学校官方工具，请使用本人有权限的账号，并遵守学校网络管理规则。",fontSize=12.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel{
            Text("特别鸣谢",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Column{
                listOf("Gloridust/RuijieWIFI-AutoLogin" to "AutoLogin · 锐捷认证", "Zelm05/campus-autologin" to "campus-autologin · 网络探测", "heiher/hev-socks5-tunnel" to "HevSocks5Tunnel · 转发核心", "yuexps/cqust-schedule-miuix" to "cqust-schedule-miuix · 课程表").forEach{(repo,label)->
                    TextButton(onClick={openLink(context,"https://github.com/$repo")},contentPadding=PaddingValues(horizontal=0.dp,vertical=2.dp)){GithubIcon();Spacer(Modifier.width(9.dp));Text(label,fontSize=12.sp)}
                }
            }
            Text("开源许可与依赖说明随源码及应用一并提供。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={licenses=true}){Text("查看开源许可")}
        }
        Image(painterResource(R.drawable.school_logo),"重庆科技大学",Modifier.width(215.dp).height(38.dp).align(Alignment.CenterHorizontally),contentScale=ContentScale.Fit)
    }
    if(licenses)AlertDialog(onDismissRequest={licenses=false},title={Text("开源许可")},text={
        val notices=remember{context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n"){name->
            name+"\n"+context.assets.open("licenses/$name").bufferedReader().use{it.readText()}}}
        Text(notices,Modifier.verticalScroll(rememberScrollState()),fontSize=11.sp,lineHeight=18.sp)
    },confirmButton={TextButton(onClick={licenses=false}){Text("关闭")}})
}

@Composable private fun AuthDialog(issue:AuthIssue,onPortal:()->Unit){
    val context=LocalContext.current
    var url by remember(issue){mutableStateOf("")}
    var captcha by remember(issue){mutableStateOf("")}
    var service by remember(issue){mutableStateOf(issue.services.firstOrNull().orEmpty())}
    AlertDialog(onDismissRequest=Repository::dismissIssue,title={Text("认证帮助")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(issue.message.orEmpty(),fontSize=14.sp,lineHeight=22.sp)
            if(issue.kind=="captcha"){
                issue.image?.let{bytes->remember(bytes){BitmapFactory.decodeByteArray(bytes,0,bytes.size)}?.let{bitmap->
                    Image(bitmap.asImageBitmap(),"认证验证码",Modifier.fillMaxWidth().height(70.dp).clickable{Repository.refreshCaptcha()},contentScale=ContentScale.Fit)
                }}
                TextButton(onClick=Repository::refreshCaptcha){Text("刷新验证码图片")}
                OutlinedTextField(captcha,{captcha=it},label={Text("验证码")},singleLine=true)
            }else{
                OutlinedButton(onClick=onPortal,modifier=Modifier.fillMaxWidth()){Text("自动读取校园网动态跳转")}
                TextButton(onClick={openLink(context,"http://aaa.cqust.edu.cn/")}){Text("在浏览器打开官方认证页")}
                Text("连接校园 Wi-Fi → 等待浏览器跳转 → 复制完整网址（包含 ? 后内容）→ 粘贴在下方。请勿公开分享认证网址。",fontSize=12.sp,lineHeight=21.sp)
                OutlinedTextField(url,{url=it},label={Text("完整认证网址")},modifier=Modifier.fillMaxWidth(),maxLines=3)
            }
            issue.services.forEach{name->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(service==name,{service=name});Text(name,fontSize=13.sp)}}
        }
    },confirmButton={TextButton(onClick={Repository.retryAuth(url,service,captcha)},enabled=issue.kind!="captcha"||captcha.isNotBlank()){Text("继续登录")}},
        dismissButton={TextButton(onClick=Repository::dismissIssue){Text("取消")}})
}
@Composable private fun PortalDialog(onClose:()->Unit,onResult:(String)->Unit){
    var finished by remember{mutableStateOf(false)}
    val wifi=remember{Repository.engine.wifi()}
    DisposableEffect(wifi){
        val cm=Repository.engine.connectivity
        val previous=cm.boundNetworkForProcess
        val bound=wifi!=null&&cm.bindProcessToNetwork(wifi)
        if(!bound)Repository.message("未找到校园 Wi-Fi，请连接后重新打开认证页。")
        onDispose{if(bound&&cm.boundNetworkForProcess==wifi)cm.bindProcessToNetwork(previous)}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("读取官方认证跳转")},text={
        Column{
            Text("仅打开学校认证页面，不向页面注入账号密码。捕获完整网址后会自动返回。",fontSize=12.sp,lineHeight=20.sp)
            AndroidView(factory={context->WebView(context).apply{
                layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,500)
                settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
                settings.domStorageEnabled=false
                webViewClient=object:WebViewClient(){
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):android.webkit.WebResourceResponse? {
                        return if(Protocol.trusted(request.url.toString()))null else android.webkit.WebResourceResponse(
                            "text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        val url=request.url.toString()
                        if(Protocol.candidate(url)){if(!finished){finished=true;view.stopLoading();onResult(url)};return true}
                        return !Protocol.trusted(url)
                    }
                    override fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?){
                        if(Protocol.candidate(url)&&!finished){finished=true;view.stopLoading();onResult(url)}
                    }
                }
                if(wifi!=null)loadUrl("http://aaa.cqust.edu.cn/")
            }},modifier=Modifier.fillMaxWidth().height(250.dp),
                onRelease={it.stopLoading();it.clearHistory();it.destroy()})
        }
    },confirmButton={TextButton(onClick=onClose){Text("关闭")}})
}
