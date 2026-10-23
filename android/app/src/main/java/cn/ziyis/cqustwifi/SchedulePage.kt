package cn.ziyis.cqustwifi

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import top.msfxp.schedule.data.api.*

object ScheduleStore {
    val data=MutableStateFlow<JSONObject?>(null)
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow("")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var loaded=false
    fun load(){if(!loaded){loaded=true;data.value=runCatching{Vault(Repository.app,"schedule.enc").read().takeIf{it.has("courses")}}.getOrNull()}}
    fun sync(user:String,password:String,remember:Boolean=false){
        if(busy.value)return
        scope.launch{
            busy.value=true;message.value="正在登录统一身份认证…"
            try{
                require(user.isNotBlank()&&password.isNotEmpty()){"请在共用登录入口填写统一身份认证账号密码。"}
                val session=CqustCasAuthClient.connect(user.trim(),password){message.value=it}.getOrThrow()
                if(remember)Repository.rememberIdentity(user.trim(),password)
                val result=CqustEamsParser.fetchAndParse(session,user.trim(),onProgress={message.value=it})
                require(result.success){"课表同步未完成，原缓存已保留。请检查统一认证凭据或网络。"}
                require(result.courses.isNotEmpty()){"本学期未解析到课程，已保留原缓存。"}
                val courses=JSONArray()
                fun add(name:String,day:Int,start:Int,end:Int,weeks:List<Int>,place:String,teacher:String){
                    courses.put(JSONObject().put("name",name).put("day",day).put("start",start).put("end",end).put("weeks",JSONArray(weeks)).put("place",place).put("teacher",teacher))
                }
                result.courses.forEach{add(it.name,it.day,it.startSection,it.endSection,it.weeks,it.position,it.teacher)}
                val practical=CqustPracticalParser.fetchAndParse(session,user.trim())
                if(practical.success)practical.items.forEach{add(it.name,it.day,it.startSection,it.endSection,it.weeks,it.location,it.teacher)}
                val sem=result.semesters.find{it.id==result.semesterId}?:result.semesters.firstOrNull()
                val start=sem?.startDate?.takeIf{runCatching{LocalDate.parse(it)}.isSuccess}.orEmpty()
                val next=JSONObject().put("user",user.trim()).put("name",result.profile.name).put("class",result.profile.className)
                    .put("semester",sem?.label.orEmpty()).put("startDate",start).put("courses",courses)
                Vault(Repository.app,"schedule.enc").write(next);data.value=next;loaded=true
                message.value=if(start.isEmpty())"同步成功，请按校历设置开学日期。"else"课表已同步"
            }catch(e:Exception){message.value=if(e is IllegalArgumentException)e.message.orEmpty()else"课表同步失败，已保留原缓存；请检查统一认证凭据或网络。"}
            finally{busy.value=false}
        }
    }
    fun startDate(date:String){scope.launch{runCatching{LocalDate.parse(date);val next=JSONObject(data.value.toString()).put("startDate",date);Vault(Repository.app,"schedule.enc").write(next);data.value=next}.onFailure{message.value="日期格式应为 YYYY-MM-DD"}}}
}

@Composable fun SchedulePage(onLogin:()->Unit){
    LaunchedEffect(Unit){ScheduleStore.load()}
    val data by ScheduleStore.data.collectAsStateWithLifecycle()
    val busy by ScheduleStore.busy.collectAsStateWithLifecycle()
    val message by ScheduleStore.message.collectAsStateWithLifecycle()
    var week by remember{mutableIntStateOf(1)}
    var dateDialog by remember{mutableStateOf(false)}
    var date by remember(data){mutableStateOf(data?.optString("startDate").orEmpty())}
    var detail by remember{mutableStateOf<JSONObject?>(null)}
    val start=runCatching{LocalDate.parse(data?.optString("startDate"))}.getOrNull()
    LaunchedEffect(start){if(start!=null)week=(ChronoUnit.DAYS.between(start,LocalDate.now())/7+1).toInt().coerceIn(1,30)}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(data?.optString("name")?.ifBlank{"我的课表"}?:"我的课表",fontSize=20.sp)
                Text(data?.optString("semester")?.ifBlank{"统一身份认证登录 · 无需开通校园网"}?:"统一身份认证登录 · 无需开通校园网",fontSize=10.sp)}
            TextButton(onClick=onLogin){Text(if(data==null)"登录"else"同步 / 更换")}
            TextButton(onClick={dateDialog=true},enabled=data!=null){Text("校历")}
        }
        if(message.isNotEmpty())Text(message,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(data==null){
            Spacer(Modifier.height(40.dp));Text("请使用连接页的共用登录入口，选择“课表”或“两者”进行同步。校园网认证失败不会阻止课表登录。",fontSize=15.sp)
        }else{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={week=(week-1).coerceAtLeast(1)}){Icon(Icons.Outlined.ChevronLeft,"上一周")}
                Text("第 $week 周",Modifier.weight(1f),fontSize=20.sp)
                TextButton(onClick={if(start!=null)week=(ChronoUnit.DAYS.between(start,LocalDate.now())/7+1).toInt().coerceIn(1,30)}){Text("本周")}
                IconButton(onClick={week=(week+1).coerceAtMost(30)}){Icon(Icons.Outlined.ChevronRight,"下一周")}
            }
            val courses=data!!.getJSONArray("courses");val active=(0 until courses.length()).map{courses.getJSONObject(it)}.filter{c->val w=c.getJSONArray("weeks");(0 until w.length()).any{w.optInt(it)==week}}
            Row(Modifier.fillMaxWidth()){
                Spacer(Modifier.width(30.dp));listOf("一","二","三","四","五","六","日").forEachIndexed{i,day->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text("周$day",fontSize=11.sp);Text(start?.plusDays(((week-1)*7+i).toLong())?.let{"${it.monthValue}/${it.dayOfMonth}"}?:"",fontSize=9.sp)}}
            }
            Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())){
                Column(Modifier.width(30.dp)){for(section in 1..11)Box(Modifier.height(68.dp),contentAlignment=Alignment.TopCenter){Text(section.toString(),fontSize=12.sp)}}
                for(day in 1..7)Box(Modifier.weight(1f).height((11*68).dp)){
                    for(section in 0..10)HorizontalDivider(Modifier.offset(y=(section*68).dp),color=MaterialTheme.colorScheme.outlineVariant)
                    active.filter{it.optInt("day")==day}.forEach{c->
                        val colors=listOf(0xff4cba87,0xff499fe1,0xffed9650,0xffe87279,0xff49bbb1,0xff9382ce)
                        val color=Color(colors[(c.getString("name").hashCode()and Int.MAX_VALUE)%colors.size])
                        Column(Modifier.offset(y=((c.optInt("start")-1)*68).dp).padding(1.dp).fillMaxWidth().height(((c.optInt("end")-c.optInt("start")+1)*68-2).dp).background(color,RoundedCornerShape(8.dp)).clickable{detail=c}.padding(4.dp)){
                            Text(c.getString("name"),color=Color.White,fontSize=10.sp,lineHeight=13.sp,maxLines=4)
                            Spacer(Modifier.weight(1f));Text(c.optString("place"),color=Color.White,fontSize=8.sp,lineHeight=10.sp,maxLines=2)
                        }
                    }
                }
            }
        }
    }
    if(dateDialog)AlertDialog(onDismissRequest={dateDialog=false},title={Text("校历开学日期")},text={OutlinedTextField(date,{date=it},label={Text("第一周周一：YYYY-MM-DD")})},confirmButton={TextButton(onClick={ScheduleStore.startDate(date);dateDialog=false}){Text("保存")}})
    detail?.let{c->AlertDialog(onDismissRequest={detail=null},title={Text(c.optString("name"))},text={Text("${c.optString("place")}\n${c.optString("teacher")}\n第 ${c.optInt("start")}–${c.optInt("end")} 节")},confirmButton={TextButton(onClick={detail=null}){Text("关闭")}})}
}
