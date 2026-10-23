'use strict';
(function(){
const styleLink=document.createElement('link');
styleLink.rel='stylesheet';
styleLink.href='schedule.css';
document.head.append(styleLink);

const scheduleNav=document.createElement('button');
scheduleNav.dataset.page='schedule';
scheduleNav.innerHTML='<span data-icon="calendar"></span>课表';
document.querySelector('nav [data-page="sites"]').after(scheduleNav);
icon(scheduleNav.querySelector('span'),'calendar');
scheduleNav.onclick=()=>{page('schedule');prefillStudent();};

const section=document.createElement('section');
section.id='schedule';
section.className='page';
section.innerHTML=`<div class="page-heading"><span class="eyebrow">COURSE TIMETABLE</span><h1>课程表</h1><p>同步教务课表，随时查看本周安排。</p></div>
<div id="schedule-setup" class="card schedule-setup" hidden><div class="schedule-setup-head"><div><h3>同步课表</h3><p>使用统一身份认证登录教务系统，密码仅用于本次同步，不会保存。理论课与实践教学课表会一起导入。</p></div><span class="schedule-badge" data-icon="shield"></span></div>
<label for="schedule-student">学号</label><input id="schedule-student" type="text" inputmode="numeric" autocomplete="username" maxlength="32" placeholder="请输入学号" spellcheck="false">
<label for="schedule-password">统一身份认证密码</label><div class="schedule-password"><input id="schedule-password" type="password" autocomplete="current-password" maxlength="256" placeholder="留空可复用当前校园网密码"><button id="schedule-eye" class="icon-button" type="button" aria-label="显示密码" title="显示密码" data-icon="eye"></button></div>
<p id="schedule-password-hint" class="schedule-hint"></p>
<button id="schedule-sync" class="primary" type="button">同步课表<span data-icon="arrow"></span></button>
<button id="schedule-setup-cancel" class="text-button" type="button" hidden>返回上次同步的课表</button>
<p id="schedule-setup-status" class="schedule-status" role="status"></p></div>
<div id="schedule-view" hidden><div class="schedule-profile card"><div class="schedule-person"><strong id="schedule-name"></strong><span id="schedule-meta"></span></div><div class="schedule-actions"><button id="schedule-change-account" class="text-button" type="button">更换</button><button id="schedule-sync-again" class="secondary" type="button">同步</button><button id="schedule-settings-open" class="icon-button" type="button" title="课表设置" aria-label="课表设置" data-icon="sliders"></button></div></div>
<div class="schedule-toolbar"><div class="schedule-week-nav"><button id="schedule-prev" class="icon-button" type="button" aria-label="上一周" title="上一周" data-icon="chevronLeft"></button><span id="schedule-week" class="schedule-week-label"></span><button id="schedule-next" class="icon-button" type="button" aria-label="下一周" title="下一周" data-icon="chevronRight"></button></div><button id="schedule-today" class="text-button" type="button">回到本周</button></div>
<p id="schedule-range" class="schedule-range"></p>
<div id="schedule-grid" class="schedule-grid" role="table" aria-label="周课表"></div>
<p id="schedule-empty-week" class="schedule-empty-week" hidden>本周暂无课程。</p>
<p id="schedule-sync-note" class="schedule-sync-note"></p></div>`;
document.querySelector('main').append(section);
section.querySelectorAll('[data-icon]').forEach(node=>icon(node,node.dataset.icon));

document.body.insertAdjacentHTML('beforeend',`<dialog id="schedule-detail-dialog"><div class="dialog-top"><h2 id="schedule-detail-title"></h2><button id="schedule-detail-close" class="icon-button" type="button" aria-label="关闭" data-icon="close"></button></div><p id="schedule-detail-sub" class="schedule-detail-sub"></p><dl class="schedule-detail-list"><div><dt>时间</dt><dd id="schedule-detail-time"></dd></div><div><dt>地点</dt><dd id="schedule-detail-place"></dd></div><div><dt>教师</dt><dd id="schedule-detail-teacher"></dd></div><div><dt>周次</dt><dd id="schedule-detail-weeks"></dd></div><div><dt>学分</dt><dd id="schedule-detail-credit"></dd></div><div><dt>类别</dt><dd id="schedule-detail-category"></dd></div><div><dt>课程代码</dt><dd id="schedule-detail-code"></dd></div></dl></dialog>
<dialog id="schedule-settings-dialog"><div class="dialog-top"><h2>课表设置</h2><button id="schedule-settings-close" class="icon-button" type="button" aria-label="关闭" data-icon="close"></button></div><p class="schedule-hint">设置本学期第一周的周一，用于计算当前周次。学期固定为 20 周。</p><label for="schedule-start-date">开学日期</label><input id="schedule-start-date" type="date"><p id="schedule-settings-status" class="schedule-hint" role="status"></p><div class="confirm-actions"><button id="schedule-start-reset" class="secondary" type="button">恢复默认</button><button id="schedule-start-save" class="primary" type="button">保存</button></div></dialog>`);
document.querySelectorAll('#schedule-detail-dialog [data-icon],#schedule-settings-dialog [data-icon]').forEach(node=>icon(node,node.dataset.icon));

const SECTIONS=[
 ['08:30','09:15'],['09:25','10:10'],['10:30','11:15'],['11:25','12:10'],['14:00','14:45'],['14:55','15:40'],
 ['16:00','16:45'],['16:55','17:40'],['19:00','19:45'],['19:55','20:40'],['20:50','21:35']
];
const DAY_NAMES=['一','二','三','四','五','六','日'];
const TOTAL_WEEKS=20;
let scheduleData=null,scheduleBusy=false,scheduleWeek=1,lastSemester='',slots=[];

function element(tag,className,text){const node=document.createElement(tag);if(className)node.className=className;if(text!==undefined)node.textContent=text;return node;}
function parseDate(value){
 if(!value)return null;
 const match=/^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value));
 if(!match)return null;
 const date=new Date(Number(match[1]),Number(match[2])-1,Number(match[3]));
 return isNaN(date.getTime())?null:date;
}
function startOfMonday(date){const copy=new Date(date.getFullYear(),date.getMonth(),date.getDate());copy.setDate(copy.getDate()-((copy.getDay()+6)%7));return copy;}
function weekMonday(week){const start=parseDate(scheduleData?.startDate||scheduleData?.defaultStartDate);if(!start)return null;const monday=startOfMonday(start);monday.setDate(monday.getDate()+(week-1)*7);return monday;}
function currentWeek(){
 const start=parseDate(scheduleData?.startDate||scheduleData?.defaultStartDate);
 if(!start)return 1;
 const days=Math.round((startOfMonday(new Date())-startOfMonday(start))/86400000);
 return Math.min(TOTAL_WEEKS,Math.max(1,Math.floor(days/7)+1));
}
function formatRange(week){
 const monday=weekMonday(week);
 if(!monday)return '第 '+week+' 周';
 const sunday=new Date(monday.getFullYear(),monday.getMonth(),monday.getDate()+6);
 return (monday.getMonth()+1)+'月'+monday.getDate()+'日 – '+(sunday.getMonth()+1)+'月'+sunday.getDate()+'日';
}
function formatSyncedAt(timestamp){
 if(!timestamp)return '尚未同步';
 const date=new Date(timestamp*1000);
 const pad=value=>String(value).padStart(2,'0');
 return (date.getMonth()+1)+'月'+date.getDate()+'日 '+pad(date.getHours())+':'+pad(date.getMinutes());
}
function formatWeeks(weeks){
 const list=[...new Set((weeks||[]).filter(week=>week>=1&&week<=TOTAL_WEEKS))].sort((left,right)=>left-right);
 if(!list.length)return '每周';
 const min=list[0],max=list[list.length-1],count=list.length;
 const consecutive=count===max-min+1;
 const odd=list.every(week=>week%2===1)&&count===Math.floor((max-min)/2)+1;
 const even=list.every(week=>week%2===0)&&count===Math.floor((max-min)/2)+1;
 if(consecutive)return min===max?'第'+min+'周':min+'-'+max+'周';
 if(odd)return min+'-'+max+'周(单)';
 if(even)return min+'-'+max+'周(双)';
 return list.join(',')+'周';
}
function sectionText(slot){return slot.start===slot.end?'第'+slot.start+'节':'第'+slot.start+'-'+slot.end+'节';}
function timeText(slot){
 const start=SECTIONS[slot.start-1]?.[0]||'';
 const end=SECTIONS[slot.end-1]?.[1]||'';
 return start&&end?start+' – '+end:'';
}
function activeInWeek(slot,week){return !Array.isArray(slot.weeks)||!slot.weeks.length||slot.weeks.includes(week);}

function setStatus(message,isError){
 const setup=$('schedule-setup-status'),note=$('schedule-sync-note');
 if(setup){setup.textContent=message||'';setup.classList.toggle('error',isError===true);}
 if(note&&scheduleData&&scheduleData.syncedAt>0)note.textContent=message||baseNote();
}
function baseNote(){
 if(!scheduleData)return '';
 const count=new Set(slots.map(slot=>slot.name)).size;
 return '上次同步 '+formatSyncedAt(scheduleData.syncedAt)+' · 共 '+count+' 门课程';
}
function updateHint(){
 const hint=$('schedule-password-hint');
 if(!hint)return;
 const student=$('schedule-student').value.trim();
 const same=student&&state&&state.connected&&state.username===student;
 hint.textContent=same?'已登录校园网，可留空复用当前账号密码；统一认证密码不同时请手动填写。':'请填写统一身份认证密码；密码仅用于本次同步，不会保存。';
}
function prefillStudent(){
 if($('schedule-student').value)return updateHint();
 const student=scheduleData?.profile?.studentId||state?.username||'';
 if(student)$('schedule-student').value=student;
 updateHint();
}
function showSetup(message,isError){
 $('schedule-setup').hidden=false;
 $('schedule-view').hidden=true;
 prefillStudent();
 $('schedule-setup-cancel').hidden=!(scheduleData&&scheduleData.syncedAt>0);
 setStatus(message||'',isError);
}
function showView(){
 $('schedule-setup').hidden=true;
 $('schedule-view').hidden=false;
 $('schedule-password').value='';
}
function setBusy(value){
 scheduleBusy=value;
 const sync=$('schedule-sync'),again=$('schedule-sync-again');
 if(sync){sync.disabled=value;sync.firstChild.textContent=value?'正在同步…':'同步课表';}
 if(again)again.disabled=value;
 if(value)setStatus('正在连接教务系统…',false);
}

function applySchedule(view,resetWeek){
 scheduleData=view;
 slots=Array.isArray(view.slots)?view.slots:[];
 $('schedule-student').value=view.profile?.studentId||state?.username||'';
 if(!view.syncedAt){showSetup('');return;}
 if(resetWeek||view.semesterId!==lastSemester||!scheduleWeek){scheduleWeek=currentWeek();lastSemester=view.semesterId||'';}
 $('schedule-name').textContent=view.profile?.name||'校园网用户';
 $('schedule-meta').textContent=[view.profile?.className,view.semesterLabel].filter(Boolean).join(' · ')||'重庆科技大学';
 showView();
 renderWeek();
 $('schedule-sync-note').textContent=baseNote();
}

function renderWeek(){
 scheduleWeek=Math.min(TOTAL_WEEKS,Math.max(1,scheduleWeek));
 $('schedule-week').textContent='第 '+scheduleWeek+' 周';
 $('schedule-range').textContent=formatRange(scheduleWeek);
 $('schedule-prev').disabled=scheduleWeek<=1;
 $('schedule-next').disabled=scheduleWeek>=TOTAL_WEEKS;
 const current=currentWeek();
 const visible=slots.filter(slot=>activeInWeek(slot,scheduleWeek)&&slot.day>=1&&slot.day<=7&&slot.start>=1&&slot.end<=11);
 $('schedule-empty-week').hidden=visible.length>0;
 renderGrid(visible,current);
}

function renderGrid(visible,current){
 const grid=$('schedule-grid');
 grid.replaceChildren();
 grid.append(element('span','schedule-corner','节次'));
 const monday=weekMonday(scheduleWeek);
 const today=new Date();
 const isCurrentWeek=scheduleWeek===current;
 for(let day=1;day<=7;day++){
  const head=element('span','schedule-day-head day-'+day);
  const label=element('b','','周'+DAY_NAMES[day-1]);
  let detail='';
  if(monday){const date=new Date(monday.getFullYear(),monday.getMonth(),monday.getDate()+day-1);detail=(date.getMonth()+1)+'/'+date.getDate();}
  const small=element('small','',detail);
  head.append(label,small);
  if(isCurrentWeek&&((today.getDay()+6)%7)+1===day)head.classList.add('today');
  grid.append(head);
 }
 for(let day=1;day<=7;day++){
  for(let section=1;section<=11;section++){
   const cell=element('span','schedule-cell day-'+day+' row-'+section);
   if(isCurrentWeek&&((today.getDay()+6)%7)+1===day)cell.classList.add('today');
   grid.append(cell);
  }
 }
 for(let section=1;section<=11;section++){
  const cell=element('span','schedule-time row-'+section);
  cell.append(element('b','',String(section)),element('small','',SECTIONS[section-1][0]));
  grid.append(cell);
 }
 const groups=new Map();
 for(const slot of visible){
  const key=slot.day+'|'+slot.start+'|'+slot.end;
  if(!groups.has(key))groups.set(key,[]);
  groups.get(key).push(slot);
 }
 for(const group of groups.values()){
  const first=group[0];
  const stack=element('div','schedule-slot day-'+first.day+' s'+first.start+'-'+first.end);
  for(const slot of group){
   const block=element('button','schedule-block color-'+((Number(slot.color)||0)%16));
   block.type='button';
   block.dataset.span=String(slot.end-slot.start+1);
   block.title=slot.name+' · '+sectionText(slot);
   block.setAttribute('aria-label',slot.name+'，'+sectionText(slot)+'，'+(slot.location||'地点待定'));
   block.append(element('strong','',slot.name),element('small','',slot.location||slot.project||''));
   block.onclick=()=>openDetail(slot);
   stack.append(block);
  }
  grid.append(stack);
 }
}

function openDetail(slot){
 $('schedule-detail-title').textContent=slot.name;
 $('schedule-detail-sub').textContent=slot.courseType==='practical'?(slot.project?'实践教学 · '+slot.project:'实践教学'):'理论课程';
 setDetail('schedule-detail-time',sectionText(slot)+(timeText(slot)?' · '+timeText(slot):''));
 setDetail('schedule-detail-place',slot.location||'待定');
 setDetail('schedule-detail-teacher',slot.teacher||'');
 setDetail('schedule-detail-weeks',formatWeeks(slot.weeks));
 setDetail('schedule-detail-credit',slot.credit||'');
 setDetail('schedule-detail-category',slot.category||'');
 setDetail('schedule-detail-code',slot.code||'');
 $('schedule-detail-dialog').showModal();
}
function setDetail(id,value){
 const node=$(id);
 node.textContent=value||'—';
 node.parentElement.hidden=!value;
}

async function syncSchedule(){
 if(scheduleBusy)return;
 const student=($('schedule-student').value||'').trim()||scheduleData?.profile?.studentId||state?.username||'';
 if(!student){showSetup('请输入学号。',true);$('schedule-student').focus();return;}
 const password=$('schedule-password').value;
 setBusy(true);
 const result=await call('scheduleSync',{student_id:student,password,use_campus_password:true});
 setBusy(false);
 if(result.ok){
  $('schedule-password').value='';
  applySchedule(result.data,true);
  toast('课表已同步');
  return;
 }
 const message=result.error?.message||'课表同步失败，请稍后重试。';
 if(result.error?.kind==='credentials'){
  showSetup(message,true);
  $('schedule-password').focus();
 }else{
  if(!$('schedule-setup').hidden)setStatus(message,true);
  else{$('schedule-sync-note').textContent=message;}
  toast(message);
 }
}

async function loadSchedule(){
 const result=await call('schedule');
 if(result.ok)applySchedule(result.data,false);
 else toast(result.error?.message||'课表缓存读取失败。');
}

function saveStartDate(value){
 $('schedule-settings-status').textContent='正在保存…';
 call('scheduleStartDate',{startDate:value}).then(result=>{
  if(!result.ok){$('schedule-settings-status').textContent=result.error?.message||'保存失败。';return;}
  $('schedule-settings-dialog').close();
  applySchedule(result.data,false);
  toast(value?'开学日期已保存':'已恢复默认开学日期');
 });
}

$('schedule-sync').onclick=syncSchedule;
$('schedule-change-account').onclick=()=>showSetup('',false);
$('schedule-sync-again').onclick=()=>{const student=scheduleData?.profile?.studentId||state?.username||'';if(student)$('schedule-student').value=student;syncSchedule();};
$('schedule-setup-cancel').onclick=()=>{if(scheduleData&&scheduleData.syncedAt>0){showView();renderWeek();}};
$('schedule-eye').onclick=()=>{
 const input=$('schedule-password');
 const reveal=input.type==='password';
 input.type=reveal?'text':'password';
 icon($('schedule-eye'),reveal?'eyeOff':'eye');
 $('schedule-eye').setAttribute('aria-label',reveal?'隐藏密码':'显示密码');
 $('schedule-eye').title=reveal?'隐藏密码':'显示密码';
};
$('schedule-prev').onclick=()=>{if(scheduleWeek>1){scheduleWeek--;renderWeek();}};
$('schedule-next').onclick=()=>{if(scheduleWeek<TOTAL_WEEKS){scheduleWeek++;renderWeek();}};
$('schedule-today').onclick=()=>{scheduleWeek=currentWeek();renderWeek();};
$('schedule-detail-close').onclick=()=>$('schedule-detail-dialog').close();
$('schedule-settings-open').onclick=()=>{
 if(!scheduleData)return;
 $('schedule-start-date').value=scheduleData.startDate||scheduleData.defaultStartDate||'';
 $('schedule-settings-status').textContent='默认开学日期：'+(scheduleData.defaultStartDate||'—')+'；学期共 20 周。';
 $('schedule-settings-dialog').showModal();
};
$('schedule-settings-close').onclick=()=>$('schedule-settings-dialog').close();
$('schedule-start-save').onclick=()=>saveStartDate($('schedule-start-date').value);
$('schedule-start-reset').onclick=()=>saveStartDate('');
$('schedule-student').oninput=updateHint;

window.cqust.subscribe(({event,data})=>{
 if(event==='schedule-progress'&&scheduleBusy)setStatus(String(data||''),false);
 if(event==='state'){
  if(!$('schedule-student').value&&data?.username)$('schedule-student').value=data.username;
  updateHint();
 }
});

loadSchedule();
setTimeout(prefillStudent,800);
window.cqustSchedule={async signIn(credentials){
 if(scheduleBusy)return {ok:false,error:{message:'课表正在同步，请稍候。'}};
 setBusy(true);
 try{
  const result=await call('scheduleSignIn',{student_id:credentials.username,password:credentials.password,remember:credentials.remember,use_campus_password:true,network_requested:credentials.purpose==='both'});
  if(result.ok){$('schedule-password').value='';applySchedule(result.data,true);}
  return result;
 }finally{setBusy(false);}
}};
})();
