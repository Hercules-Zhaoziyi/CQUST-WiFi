package cn.ziyis.cqustwifi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.TabRowWithContour
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
    var mainReturn by remember{mutableStateOf("连接")}
    var aboutReturn by remember{mutableStateOf("连接")}
    val mainPages=listOf("连接","课程表","常用网站","代理")
    fun settings(){if(page in mainPages)mainReturn=page;page="设置"}
    fun about(){aboutReturn=page;page="关于"}
    fun back(){page=when(page){"关于"->aboutReturn;"日志"->"设置";else->mainReturn}}
    BackHandler(enabled=page !in mainPages){back()}
    var loginTarget by remember{mutableIntStateOf(Repository.loginPurpose())}
    var portal by remember{mutableStateOf(false)}
    val pageState=rememberSaveableStateHolder()
    var manualAuth by remember{mutableStateOf(false)}
    val systemDark=isSystemInDarkTheme()
    val dark=when(state.prefs.theme){"dark"->true;"light"->false;else->systemDark}
    val mode=when(state.prefs.theme){"dark"->ColorSchemeMode.Dark;"light"->ColorSchemeMode.Light;else->ColorSchemeMode.System}
    val animatedTheme=rememberAnimatedTheme(dark,state.prefs.animations,mode)
    val controller=animatedTheme.controller
    val darkSystemBars=animatedTheme.darkFraction>=.5f
    val activity=LocalContext.current as? android.app.Activity
    SideEffect{activity?.let{
        androidx.core.view.WindowInsetsControllerCompat(it.window,it.window.decorView).apply{
            isAppearanceLightStatusBars=!darkSystemBars;isAppearanceLightNavigationBars=!darkSystemBars
        }
    }}
    val scheme=lightColorScheme()
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(state.message){if(state.message.isNotEmpty()){snackbar.showSnackbar(state.message);Repository.dismissMessage()}}
    LaunchedEffect(Unit){ScheduleStore.completed.collect{if(page=="连接"&&loginTarget!=0)page="课程表"}}
    LaunchedEffect(Unit){Repository.navigation.collect{if(it=="schedule"){page="课程表";Repository.navigation.value=null}}}
    LaunchedEffect(issue){if(issue==null)manualAuth=false}
    MiuixTheme(controller=controller){
    val palette=MiuixTheme.colorScheme
    val aligned=scheme.copy(background=palette.surface,surface=palette.surfaceContainer,onSurface=palette.onSurface,
        primary=palette.primary,onPrimary=palette.onPrimary,onBackground=palette.onSurface,
        onSurfaceVariant=palette.onSurfaceVariantSummary,surfaceVariant=palette.surfaceVariant,
        outline=palette.outline,outlineVariant=palette.dividerLine,secondary=palette.secondary,onSecondary=palette.onSecondary,
        secondaryContainer=palette.secondaryContainer,onSecondaryContainer=palette.onSecondaryContainer,
        primaryContainer=palette.primaryContainer,onPrimaryContainer=palette.onPrimaryContainer,
        tertiary=palette.onTertiaryContainer,onTertiary=palette.tertiaryContainer,
        tertiaryContainer=palette.tertiaryContainer,onTertiaryContainer=palette.onTertiaryContainer,
        surfaceTint=palette.primary,surfaceDim=palette.background,surfaceBright=palette.surface,
        surfaceContainerLowest=palette.background,surfaceContainerLow=palette.surface,
        surfaceContainer=palette.surfaceContainer,surfaceContainerHigh=palette.surfaceContainerHigh,
        surfaceContainerHighest=palette.surfaceContainerHighest,
        inverseSurface=lerp(Color(0xff303030),Color(0xffeeeeee),animatedTheme.darkFraction),
        inverseOnSurface=lerp(Color.White,Color(0xff202020),animatedTheme.darkFraction),
        inversePrimary=lerp(Color(0xffa6b8ff),Blue,animatedTheme.darkFraction),scrim=Color.Black,
        error=palette.error,onError=palette.onError,errorContainer=palette.errorContainer,onErrorContainer=palette.onErrorContainer)
    MaterialTheme(colorScheme=aligned) {
        Scaffold(containerColor=palette.surface,snackbarHost={SnackbarHost(snackbar)},topBar={
            if(page in listOf("关于","日志"))SmallTopAppBar(title=page,navigationIcon={IconButton(onClick={back()}){Icon(Icons.Outlined.ArrowBack,stringResource(R.string.unified_back))}})
            else if(page in mainPages&&page!="课程表")Column(Modifier.statusBarsPadding().background(palette.surface)){
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                    Image(painterResource(R.drawable.app_logo),null,Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text("CQUST Wi-Fi",fontWeight=FontWeight.Bold,fontSize=22.sp)
                        Text("校园网络 · 轻松连接",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick={Repository.preferences(state.prefs.copy(theme=if(dark)"light"else"dark"))}){
                        Icon(if(dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,"切换主题")}
                    IconButton(onClick={settings()}){Icon(Icons.Outlined.Tune,"设置")}
                    IconButton(onClick={about()}){Icon(Icons.Outlined.Info,"关于")}
                }
            }
        },bottomBar={
            if(page in mainPages)Box(Modifier.testTag("main-navigation")){NavigationBar(color=palette.surfaceContainer){
                listOf("连接" to Icons.Outlined.Wifi,"课程表" to Icons.Outlined.CalendarMonth,"常用网站" to Icons.Outlined.Language,
                    "代理" to Icons.Outlined.Shield).forEach{(name,icon)->
                    NavigationBarItem(selected=page==name,onClick={page=name},icon=icon,label=name)
                }
            }}
        }){padding->
            AnimatedContent(page,Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),transitionSpec={
                fadeIn(tween(if(state.prefs.animations)220 else 0)) togetherWith fadeOut(tween(if(state.prefs.animations)100 else 0))
            },label="page"){current->
                pageState.SaveableStateProvider(current){
                when(current){
                    "连接"->Home(state,onPortal={if(issue!=null)manualAuth=true else portal=true},initialMode=loginTarget,onMode={loginTarget=it})
                    "常用网站"->Sites()
                    "课程表"->SchedulePage(onLogin={loginTarget=1;page="连接"},onAbout={about()},onSettings={settings()},controller=controller)
                    "代理"->ProxyPage(state,onVpn)
                    "设置"->top.msfxp.schedule.App(settingsOnly=true,controller=controller,onExit={page=mainReturn},
                        onSharedLogin={loginTarget=1;page="连接"},onHostAbout={about()},
                        accountContent={logout->UnifiedAccounts(state,logout){mode->loginTarget=mode;page="连接"}},
                        preferencesContent={ConnectionPreferences(state)},
                        permissionsContent={BackgroundPreferences(onLogs={page="日志"},onNotifications)},
                        updatesContent={UpdatePreferences()})
                    "日志"->Logs(state)
                    else->About()
                }
                }
            }
        }
        if(issue!=null&&(loginTarget==0||manualAuth))AuthDialog(issue!!,onPortal={Repository.dismissIssue();portal=true})
        if(portal)AuthenticationGuide(onClose={portal=false},onResult={url->portal=false;Repository.retryAuth(url,"","")})
        UpdateDialog()
    }
    }
}

@Composable private fun PageColumn(content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
@Composable private fun Heading(title:String,subtitle:String){
    Column(Modifier.padding(top=14.dp,bottom=4.dp)){
        Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
        Text(subtitle,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp),lineHeight=22.sp)
    }
}
@Composable private fun Panel(content:@Composable ColumnScope.()->Unit){
    MiuixCard(modifier=Modifier.fillMaxWidth(),cornerRadius=26.dp,insideMargin=PaddingValues(20.dp),
        colors=CardDefaults.defaultColors(color=MiuixTheme.colorScheme.surfaceContainer)){
        Column(verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
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
    MiuixInput(value,onValueChange=onChange,label={Text(label)},placeholder={Text(placeholder)},
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
@Composable private fun Home(s:ViewState,onPortal:()->Unit,initialMode:Int=0,onMode:(Int)->Unit={}){
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
        Repository.loginPurpose(loginMode)
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
            if(connected&&s.status!=LinkState.OPEN)QuietButton(onClick={logoutConfirm=true},modifier=Modifier.align(Alignment.CenterHorizontally)){
                Icon(Icons.Outlined.Logout,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("退出登录")}
            if(s.status==LinkState.OPEN)QuietButton(onClick=onPortal,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("在官方页面管理账号与下线")}
        }
        run{
            Panel{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("统一登录",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("校园网与课表，按需使用",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Image(painterResource(R.drawable.school_logo),"重庆科技大学",Modifier.width(120.dp).height(32.dp),contentScale=ContentScale.Fit)
                }
                MiuixInput(user,{user=it},label={Text("校园网账号")},modifier=Modifier.fillMaxWidth().testTag("username"),
                    singleLine=true,shape=RoundedCornerShape(16.dp),trailingIcon={IconButton(onClick={accounts=true}){Icon(Icons.Outlined.ExpandMore,"选择账号")}})
                SecretField(password,{password=it},"统一认证密码",placeholder=if(Repository.hasPassword(user))"密码已安全保存，可留空"else"请输入密码")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("校园网","课表","两者").forEachIndexed{i,title->ChoiceChip(loginMode==i,{loginMode=i;onMode(i)},label={Text(title)})}
                }
                if(loginMode!=0){
                    Text("课表独立使用统一身份认证，无需开通校园网。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(scheduleMessage.isNotEmpty())Text(scheduleMessage,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(rememberPassword,{rememberPassword=it});Text("记住账号密码",fontSize=13.sp,modifier=Modifier.weight(1f))
                    QuietButton(onClick=onPortal){Text("认证帮助")}
                }
                ActionButton(onClick=submitLogin,enabled=!s.busy,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(17.dp)){Text("登录");Spacer(Modifier.width(10.dp));Icon(Icons.Outlined.ArrowForward,null)}
                Text("密码使用 Android Keystore 加密保存在本机。",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel{Setting("自动登录","仅选择校园网或两者时，检测到认证入口后自动连接。",s.prefs.autoLogin){Repository.preferences(s.prefs.copy(autoLogin=it))}}
        Text("每 ${if(s.prefs.interval==120)"2 分钟"else"${s.prefs.interval} 秒"}检查 · 可在设置中调整",fontSize=12.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
    if(accounts)MiuixPrompt(onDismissRequest={accounts=false},containerColor=MaterialTheme.colorScheme.surface,title={Text("校园网账号")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("选择已保存账号，或添加新的校园网账号。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            s.accounts.forEach{account->Row(verticalAlignment=Alignment.CenterVertically){
                Surface(onClick={user=account;Repository.chooseAccount(account);accounts=false},shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.weight(1f)){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AccountCircle,null);Spacer(Modifier.width(12.dp));Column{Text(account,fontWeight=FontWeight.SemiBold);Text(if(account==s.username)"当前账号"else"密码已保存",fontSize=11.sp)};Spacer(Modifier.weight(1f));if(account==s.username)Icon(Icons.Outlined.CheckCircle,null,tint=MaterialTheme.colorScheme.primary)}
                }
                IconButton(onClick={Repository.forgetAccount(account)}){Icon(Icons.Outlined.DeleteOutline,"忘记账号")}
            }}
            SecondaryButton(onClick={user="";password="";accounts=false},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("添加其他账号")}
        }
    },confirmButton={QuietButton(onClick={accounts=false}){Text("关闭")}})
    if(logoutConfirm)MiuixPrompt(onDismissRequest={logoutConfirm=false},title={Text("退出登录？")},text={Text("将向校园网关提交下线请求，已保存的账号密码仍保留。")},
        confirmButton={QuietButton(onClick={Repository.disconnect(true);logoutConfirm=false}){Text("退出登录")}},
        dismissButton={QuietButton(onClick={logoutConfirm=false}){Text("取消")}})
}

@Composable private fun Sites(){
    val context=LocalContext.current
    val sites=remember{Repository.sites()}
    val loader=remember{ImageLoader.Builder(context).components{add(SvgDecoder.Factory())}.build()}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Heading("常用网站","校园服务，触手可及。")}
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
    var shareQr by remember{mutableStateOf<String?>(null)}
    val scanner=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()){result->
        if(result.data?.getBooleanExtra("manual",false)==true)tab=0
        result.data?.getStringExtra("proxy_qr")?.let{raw->runCatching{ProxyQr.parse(raw)}.onSuccess{scanned=it}.onFailure{Repository.message(context.getString(R.string.proxy_scan_invalid))}}
    }
    LaunchedEffect(s.sharing){if(!s.sharing)shareQr=null}
    PageColumn{
        Heading("代理","连接自己的设备，分享已连接的网络。")
        MiuixCard(modifier=Modifier.fillMaxWidth().testTag("proxy-scan-entry"),cornerRadius=24.dp,
            insideMargin=PaddingValues(18.dp),showIndication=true,
            colors=CardDefaults.defaultColors(color=MiuixTheme.colorScheme.primary.copy(alpha=.10f)),
            onClick={scanner.launch(android.content.Intent(context,ProxyScannerActivity::class.java))}){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Surface(shape=RoundedCornerShape(15.dp),color=MiuixTheme.colorScheme.surfaceContainer){
                    Icon(Icons.Outlined.QrCodeScanner,null,Modifier.padding(12.dp).size(26.dp),tint=MiuixTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(horizontal=13.dp)){
                    Text(stringResource(R.string.proxy_scan_entry),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    Text(stringResource(R.string.proxy_scan_entry_detail),fontSize=12.sp,lineHeight=20.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary,modifier=Modifier.padding(top=5.dp))
                }
                Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp),tint=MiuixTheme.colorScheme.primary)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val width=(maxWidth-14.dp)/2
            TabRowWithContour(tabs=listOf("连接电脑","本机共享"),selectedTabIndex=tab,onTabSelected={tab=it},
                modifier=Modifier.fillMaxWidth(),minWidth=width,maxWidth=width,height=48.dp,cornerRadius=16.dp)
        }
        if(tab==0){
            Panel{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Laptop,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text("设备 SOCKS5",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        Text(s.vpnMessage,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                if(!s.vpn){
                    MiuixInput(host,{host=it.trim()},label={Text("服务器 IP")},placeholder={Text("例如 172.21.x.x")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    MiuixInput(port,{port=it.filter(Char::isDigit)},label={Text("端口")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    MiuixInput(user,{user=it},label={Text("代理用户名")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
                    Setting("使用校园网密码","仅当电脑端也选择校园网密码时使用；不会显示密码。",campus){campus=it;password=""}
                    if(!campus)SecretField(password,{password=it},"电脑代理密码",visibleByDefault=true)
                }else{
                    Text("socks5://$host:$port",fontSize=15.sp)
                    Text("代理用户名：$user",fontSize=13.sp)
                    Text(s.transfer,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionButton(onClick={
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
                        listOf("mixed" to "混合","http" to "HTTP","socks5" to "SOCKS5").forEach{(id,label)->ChoiceChip(mode==id,{mode=id},label={Text(label)})}
                    }
                    MiuixInput(sharePort,{sharePort=it.filter(Char::isDigit)},label={Text("监听端口")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
                    MiuixInput(shareUser,{shareUser=it},label={Text("代理用户名")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
                    Setting("使用校园网密码","由后台复用，不显示或复制。更推荐独立的代理密码。",shareCampus){shareCampus=it;sharePass=""}
                    if(!shareCampus){
                        SecretField(sharePass,{sharePass=it},"代理密码",visibleByDefault=true)
                        QuietButton(onClick={sharePass=Protocol.recommendedPassword()}){Text("推荐密码")}
                    }
                    Setting("允许局域网设备连接","只监听当前 Wi-Fi IP，不需要填写允许的设备 IP。",lan){lan=it}
                }else{
                    Text(s.sharingAddress,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                    Text("服务器：${s.sharingAddress.substringBeforeLast(':')}\n端口：${s.sharingAddress.substringAfterLast(':')}\n代理用户名：${s.sharingUser}",fontSize=14.sp,lineHeight=27.sp)
                    val uri=(if(mode=="http")"http"else"socks5")+"://"+s.sharingAddress
                    SecondaryButton(onClick={copy(context,uri)}){Icon(Icons.Outlined.ContentCopy,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("复制协议链接")}
                    SecondaryButton(onClick={runCatching{Repository.sharingQr()}.onSuccess{shareQr=it}.onFailure{Repository.message(it.message?:"当前配置不能生成连接二维码")}}){Icon(Icons.Outlined.QrCode,null);Spacer(Modifier.width(8.dp));Text("生成通用连接二维码")}
                    if(s.sharingSecret)Text("正在使用校园网密码，不提供显示或复制。",fontSize=12.sp)
                    else Repository.displayedSharePassword()?.let{secret->SecretField(secret,{},"本次代理密码",visibleByDefault=true)}
                }
            }
            Text("HTTP 支持 HTTPS CONNECT，不解密网页内容；SOCKS5 支持 TCP/UDP。认证本身不加密，请仅在可信网络与自己的设备之间使用。网络隔离可能阻止互访，应用不会改变校园网设备配额。",fontSize=12.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    scanned?.let{p->MiuixPrompt(onDismissRequest={scanned=null},title={Text(stringResource(R.string.proxy_scan_confirm))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(stringResource(R.string.proxy_scan_confirm_detail,p.host,p.port,p.username))
        if(s.sharing)Text(stringResource(R.string.proxy_scan_share_stop),color=MiuixTheme.colorScheme.primary)
        if(s.vpn)Text(stringResource(R.string.proxy_scan_vpn_stop),color=MiuixTheme.colorScheme.primary)
    }},confirmButton={QuietButton(enabled=!s.vpn,onClick={
        if(s.sharing)Repository.stopSharing()
        runCatching{Repository.saveVpn(p.host,p.port,p.username,p.password,false)}.onSuccess{host=p.host;port=p.port.toString();user=p.username;password=p.password;campus=false;scanned=null;tab=0;onVpn()}.onFailure{Repository.message("代理配置保存失败")}
    }){Text("确认并连接")}},dismissButton={QuietButton(onClick={scanned=null}){Text("取消")}})}
    shareQr?.let{payload->ProxyQrDialog(payload,onClose={shareQr=null})}
}
@Composable private fun ProxyQrDialog(payload:String,onClose:()->Unit){
    val bitmap=remember(payload){val matrix=com.google.zxing.qrcode.QRCodeWriter().encode(payload,com.google.zxing.BarcodeFormat.QR_CODE,720,720,mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
        android.graphics.Bitmap.createBitmap(720,720,android.graphics.Bitmap.Config.ARGB_8888).apply{
            val pixels=IntArray(720*720){i->if(matrix[i%720,i/720])android.graphics.Color.BLACK else android.graphics.Color.WHITE};setPixels(pixels,0,720,0,0,720,720)
        }}
    val profile=remember(payload){ProxyQr.parse(payload)}
    MiuixPrompt(onDismissRequest=onClose,
        properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        title={Text("连接到这台设备",fontSize=22.sp,fontWeight=FontWeight.SemiBold)},text={
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
            Text("在另一台设备的代理页，点击“扫码连接”",fontSize=13.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Surface(color=Color.White,shape=RoundedCornerShape(24.dp)){
                Image(bitmap.asImageBitmap(),"代理连接二维码",Modifier.padding(12.dp).fillMaxWidth().aspectRatio(1f))
            }
            Text("${profile.host}:${profile.port}",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text("二维码包含代理凭据，仅供本人可信设备使用，请勿公开分享。",fontSize=12.sp,lineHeight=20.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    },confirmButton={QuietButton(onClick=onClose){Text("完成")}})
}
private fun copy(context:Context,text:String){context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("代理地址",text));Repository.message("已复制")}

@Composable private fun BackgroundPreferences(onLogs:()->Unit,onNotifications:()->Unit){
    val context=LocalContext.current
    var notificationsReady by remember{mutableStateOf(false)}
    var batteryReady by remember{mutableStateOf(false)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    fun refreshPermissions(){
        val manager=context.getSystemService(android.app.NotificationManager::class.java)
        notificationsReady=androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()&&listOf("monitor","vpn").all{manager.getNotificationChannel(it)?.importance!=android.app.NotificationManager.IMPORTANCE_NONE}
        batteryReady=context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    DisposableEffect(lifecycle){refreshPermissions();val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)refreshPermissions()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
        Panel{
            Text("后台运行",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text(if(notificationsReady&&batteryReady)"通知与后台权限已就绪。"else"允许通知与后台运行有助于保持连接。权限生效后，对应按钮会隐藏。强行停止后仍需手动打开应用。",fontSize=13.sp,lineHeight=23.sp)
            if(!notificationsReady)SecondaryButton(onClick=onNotifications,modifier=Modifier.fillMaxWidth()){Text("允许通知栏连接入口")}
            if(!batteryReady)SecondaryButton(onClick={batterySettings(context)},modifier=Modifier.fillMaxWidth()){Text("打开电池与后台设置")}
            QuietButton(onClick=onLogs){Icon(Icons.Outlined.ReceiptLong,null);Spacer(Modifier.width(9.dp));Text("查看运行日志")}
        }
}
@Composable private fun UpdatePreferences(){
    val scope=rememberCoroutineScope()
    var update by remember{mutableStateOf<UpdateInfo?>(null)}
    var checking by remember{mutableStateOf(false)}
        Panel{
            Text("软件更新",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text("当前版本 ${BuildConfig.VERSION_NAME} · Android",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(onClick={checking=true;scope.launch{
                runCatching{Repository.checkUpdate()}.onSuccess{update=it}.onFailure{Repository.message("更新检测失败，请稍后重试。")}
                checking=false
            }},enabled=!checking){Text(if(checking)"正在检查…"else"检查 GitHub 新版本")}
            update?.let{u->
                Text(if(u.newer)"发现新版本 ${u.version}"else"暂未发现更新版本",fontWeight=FontWeight.SemiBold)
                Markdown(u.notes)
                if(u.newer&&u.download!=null)SecondaryButton(onClick={AppUpdates.offer(u)}){Text("应用内下载并更新")}
            }
        }
}

@Composable private fun ConnectionPreferences(s:ViewState){
    Panel{
        Text(stringResource(R.string.unified_connection),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        Setting(stringResource(R.string.unified_auto_login),stringResource(R.string.unified_auto_login_desc),s.prefs.autoLogin){Repository.preferences(s.prefs.copy(autoLogin=it))}
        HorizontalDivider()
        Setting(stringResource(R.string.unified_keep),stringResource(R.string.unified_keep_desc),s.prefs.keepConnected){Repository.preferences(s.prefs.copy(keepConnected=it))}
        HorizontalDivider()
        Text(stringResource(R.string.unified_interval),fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(5 to R.string.unified_five,10 to R.string.unified_ten,120 to R.string.unified_two_minutes).forEach{(n,label)->ChoiceChip(s.prefs.interval==n,{Repository.preferences(s.prefs.copy(interval=n))},label={Text(stringResource(label))})}
        }
        HorizontalDivider()
        Setting(stringResource(R.string.unified_animations),stringResource(R.string.unified_animations_desc),s.prefs.animations){Repository.preferences(s.prefs.copy(animations=it))}
        HorizontalDivider()
        Text(stringResource(R.string.unified_theme),fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("system" to R.string.unified_system,"light" to R.string.unified_light,"dark" to R.string.unified_dark).forEach{(id,label)->ChoiceChip(s.prefs.theme==id,{Repository.preferences(s.prefs.copy(theme=id))},label={Text(stringResource(label))})}
        }
    }
}

@Composable private fun UnifiedAccounts(s:ViewState,onCourseLogout:()->Unit,onLogin:(Int)->Unit){
    val repository=remember{org.koin.core.context.GlobalContext.get().get<top.msfxp.schedule.data.repository.SettingsRepository>()}
    val course by repository.appSettingsFlow.collectAsStateWithLifecycle()
    val rows=identityRows(s.username,course.studentId)
    Panel{
        Text(stringResource(R.string.unified_accounts),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        if(rows.isEmpty())ArrowPreference(title=stringResource(R.string.unified_login),summary=stringResource(R.string.unified_login_desc),onClick={onLogin(2)})
        rows.forEachIndexed{i,row->
            if(i>0)HorizontalDivider()
            val label=when(row.kind){IdentityKind.CAMPUS->R.string.unified_campus_account;IdentityKind.COURSE->R.string.unified_course_account;IdentityKind.SHARED->R.string.unified_shared_account}
            Row(Modifier.fillMaxWidth().clickable{onLogin(row.kind.loginMode)},verticalAlignment=Alignment.CenterVertically){
                Icon(if(row.kind==IdentityKind.CAMPUS)Icons.Outlined.Wifi else Icons.Outlined.AccountCircle,null,Modifier.size(32.dp),tint=MiuixTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start=12.dp)){
                    Text(row.username,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                    Text(stringResource(label),fontSize=12.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    if(row.kind!=IdentityKind.CAMPUS&&course.studentName.isNotBlank())Text(listOf(course.studentName,course.className).filter{it.isNotBlank()}.joinToString(" · "),fontSize=12.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Icon(Icons.Outlined.ChevronRight,stringResource(R.string.unified_change_account))
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            if(s.status==LinkState.CONNECTED)QuietButton(onClick={Repository.disconnect(logout=true)}){Text(stringResource(R.string.unified_disconnect_campus))}
            if(course.isLoggedIn)QuietButton(onClick=onCourseLogout){Text(stringResource(R.string.unified_logout_course))}
        }
    }
}
@Composable private fun UpdateDialog(){
    val update by AppUpdates.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val info=update.info
    if(update.visible&&info!=null)MiuixPrompt(onDismissRequest=AppUpdates::dismiss,title={Text(if(update.ready)"更新已下载"else"发现新版本 ${info.version}")},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Markdown(info.notes)
            Text("安装包将校验应用标识、版本、SHA-256 和签名。安装需要系统确认，不会静默安装或自动卸载旧版。",fontSize=12.sp,lineHeight=20.sp)
            if(update.downloading){LinearProgressIndicator(progress={update.percent/100f},modifier=Modifier.fillMaxWidth());Text("下载中 ${update.percent}%",fontSize=13.sp)}
            if(update.error.isNotEmpty())Text(update.error,fontSize=13.sp,color=MaterialTheme.colorScheme.error)
            CommunityLinks()
            QuietButton(onClick={openLink(context,info.url)}){Text("查看 GitHub 发布页")}
        }
    },confirmButton={QuietButton(onClick={if(update.ready)AppUpdates.install()else AppUpdates.download()},enabled=!update.downloading){Text(if(update.ready)"安装更新"else"下载并更新")}},dismissButton={QuietButton(onClick=AppUpdates::dismiss){Text("稍后")}})
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
        Heading("日志","只记录连接阶段，不记录密码或完整认证网址。")
        SecondaryButton(onClick=Repository::clearLogs){Text("清空本次日志")}
        Panel{if(s.logs.isEmpty())Text("暂无日志",fontSize=13.sp)else s.logs.reversed().forEach{Text(it,fontSize=12.sp,lineHeight=21.sp)}}
    }
}
@Composable private fun About(){
    val context=LocalContext.current
    var licenses by remember{mutableStateOf(false)}
    var advanced by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val courseSettings=remember{org.koin.core.context.GlobalContext.get().get<top.msfxp.schedule.data.repository.SettingsRepository>()}
    val coursePreferences by courseSettings.appSettingsFlow.collectAsStateWithLifecycle()
    PageColumn{
        Panel{
            Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(22.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(R.drawable.app_logo),null,Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)))
                Text(stringResource(R.string.unified_brand_cross),fontSize=28.sp,color=MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Image(painterResource(top.msfxp.schedule.R.drawable.ic_launcher_foreground),null,Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)).background(Color.White))
            }
            Text(stringResource(R.string.unified_brand),Modifier.align(Alignment.CenterHorizontally),fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("${BuildConfig.VERSION_NAME} · Android",Modifier.align(Alignment.CenterHorizontally),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.unified_tagline),Modifier.align(Alignment.CenterHorizontally),fontSize=14.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                QuietButton(onClick={openLink(context,"https://www.xiaohongshu.com/user/profile/60d40af7000000000101f612")}){
                    AsyncImage("file:///android_asset/xiaohongshu-logo.svg",null,imageLoader=remember{ImageLoader.Builder(context).components{add(SvgDecoder.Factory())}.build()},modifier=Modifier.width(26.dp).height(16.dp));Spacer(Modifier.width(4.dp));Text("十七号旅客",fontSize=11.sp)}
                QuietButton(onClick={openLink(context,"https://github.com/yuexps")}){GithubIcon();Spacer(Modifier.width(4.dp));Text("GitHub",fontSize=11.sp)}
            }
        }
        Panel{
            Text(stringResource(R.string.unified_intro_title),fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            Text(stringResource(R.string.unified_intro),fontSize=14.sp,lineHeight=24.sp)
            Text("本软件为非学校官方工具，请使用本人有权限的账号，并遵守学校网络管理规则。",fontSize=12.sp,lineHeight=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel{
            ArrowPreference(title=stringResource(R.string.unified_wifi_repo),summary=stringResource(R.string.unified_wifi_repo_path),startAction={GithubIcon()},onClick={openLink(context,"https://github.com/Hercules-Zhaoziyi/CQUST-WiFi")})
            ArrowPreference(title=stringResource(R.string.unified_course_repo),summary=stringResource(R.string.unified_course_repo_path),startAction={GithubIcon()},onClick={openLink(context,"https://github.com/yuexps/cqust-schedule-miuix")})
            CommunityLinks()
            ArrowPreference(title=stringResource(R.string.unified_website),summary=stringResource(R.string.unified_website_desc),onClick={openLink(context,"https://cqust-wifi.ziyis.cn/")})
        }
        Panel{
            Text("特别鸣谢",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Column{
                listOf("Gloridust/RuijieWIFI-AutoLogin" to "AutoLogin · 锐捷认证", "Zelm05/campus-autologin" to "campus-autologin · 网络探测", "heiher/hev-socks5-tunnel" to "HevSocks5Tunnel · 转发核心", "yuexps/cqust-schedule-miuix" to "cqust-schedule-miuix · 课程表", "compose-miuix-ui/miuix" to "Miuix · 界面组件").forEach{(repo,label)->
                    QuietButton(onClick={openLink(context,"https://github.com/$repo")},contentPadding=PaddingValues(horizontal=0.dp,vertical=2.dp)){GithubIcon();Spacer(Modifier.width(9.dp));Text(label,fontSize=12.sp)}
                }
            }
            Text("开源许可与依赖说明随源码及应用一并提供。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                QuietButton(onClick={licenses=true}){Text("查看开源许可")}
                QuietButton(onClick={advanced=true}){Text(stringResource(R.string.course_advanced_options))}
            }
        }
        Image(painterResource(R.drawable.school_logo),"重庆科技大学",Modifier.width(215.dp).height(38.dp).align(Alignment.CenterHorizontally),contentScale=ContentScale.Fit)
    }
    if(advanced)MiuixPrompt(onDismissRequest={advanced=false},title={Text(stringResource(R.string.course_advanced_options))},text={
        Setting(stringResource(top.msfxp.schedule.R.string.perm_show_test_features_title),
            stringResource(top.msfxp.schedule.R.string.perm_show_test_features_desc),coursePreferences.showTestFeatures){enabled->scope.launch{courseSettings.updateShowTestFeatures(enabled)}}
    },confirmButton={QuietButton(onClick={advanced=false}){Text(stringResource(R.string.course_advanced_done))}})
    if(licenses)MiuixPrompt(onDismissRequest={licenses=false},title={Text("开源许可")},text={
        val notices=remember{context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n"){name->
            name+"\n"+context.assets.open("licenses/$name").bufferedReader().use{it.readText()}}}
        Text(notices,Modifier.verticalScroll(rememberScrollState()),fontSize=11.sp,lineHeight=18.sp)
    },confirmButton={QuietButton(onClick={licenses=false}){Text("关闭")}})
}

@Composable private fun AuthDialog(issue:AuthIssue,onPortal:()->Unit){
    val context=LocalContext.current
    var url by remember(issue){mutableStateOf("")}
    var captcha by remember(issue){mutableStateOf("")}
    var service by remember(issue){mutableStateOf(issue.services.firstOrNull().orEmpty())}
    MiuixPrompt(onDismissRequest=Repository::dismissIssue,title={Text("认证帮助")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(issue.message.orEmpty(),fontSize=14.sp,lineHeight=22.sp)
            if(issue.kind=="captcha"){
                issue.image?.let{bytes->remember(bytes){BitmapFactory.decodeByteArray(bytes,0,bytes.size)}?.let{bitmap->
                    Image(bitmap.asImageBitmap(),"认证验证码",Modifier.fillMaxWidth().height(70.dp).clickable{Repository.refreshCaptcha()},contentScale=ContentScale.Fit)
                }}
                QuietButton(onClick=Repository::refreshCaptcha){Text("刷新验证码图片")}
                MiuixInput(captcha,{captcha=it},label={Text("验证码")},singleLine=true)
            }else{
                SecondaryButton(onClick=onPortal,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.auth_probe_start))}
                QuietButton(onClick={openLink(context,"http://aaa.cqust.edu.cn/")}){Text("在浏览器打开官方认证页")}
                Text("连接校园 Wi-Fi → 等待浏览器跳转 → 复制完整网址（包含 ? 后内容）→ 粘贴在下方。请勿公开分享认证网址。",fontSize=12.sp,lineHeight=21.sp)
                MiuixInput(url,{url=it},label={Text("完整认证网址")},modifier=Modifier.fillMaxWidth(),maxLines=3)
            }
            issue.services.forEach{name->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(service==name,{service=name});Text(name,fontSize=13.sp)}}
        }
    },confirmButton={QuietButton(onClick={Repository.retryAuth(url,service,captcha)},enabled=issue.kind!="captcha"||captcha.isNotBlank()){Text("继续登录")}},
        dismissButton={QuietButton(onClick=Repository::dismissIssue){Text("取消")}})
}
