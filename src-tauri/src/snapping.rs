use std::{collections::VecDeque,sync::{Mutex,atomic::{AtomicBool,Ordering}},time::{Duration,Instant}};
use tauri::{Manager,PhysicalPosition,WebviewWindow};
type Point=(i32,i32);
type Area=(i32,i32,i32,i32);
#[derive(Clone,Copy,Debug,PartialEq)]enum Edge{Left,Right,Top,Bottom}
#[derive(Clone,Copy)]struct Dock{edge:Edge,shown:Point,hidden:Point,size:Point,area:Area,open:bool,outside:Option<Instant>}
struct Motion{from:Point,to:Point,start:Instant}
#[derive(Default)]struct State{pending:Option<Instant>,dock:Option<Dock>,motion:Option<Motion>,recent:VecDeque<(Point,Instant)>,skip_snap:bool}
#[derive(Clone,Copy)]struct Input{cursor:Point,down:bool}
pub struct Snapping{pub enabled:AtomicBool,pub animations:AtomicBool,pub hold:AtomicBool,state:Mutex<State>,#[cfg(feature="verify-ui")]input:Mutex<Option<Input>>}
impl Snapping{
 pub fn new(enabled:bool)->Self{Self{enabled:AtomicBool::new(enabled),animations:AtomicBool::new(true),hold:AtomicBool::new(false),state:Mutex::new(State::default()),#[cfg(feature="verify-ui")]input:Mutex::new(None)}}
 fn input(&self)->Option<Input>{
  #[cfg(feature="verify-ui")]if let Some(input)=*self.input.lock().ok()?{return Some(input)}
  let mut p=windows_sys::Win32::Foundation::POINT{x:0,y:0};
  if unsafe{windows_sys::Win32::UI::WindowsAndMessaging::GetCursorPos(&mut p)}==0{return None}
  Some(Input{cursor:(p.x,p.y),down:unsafe{windows_sys::Win32::UI::Input::KeyboardAndMouse::GetAsyncKeyState(1)}<0})
 }
 pub fn moved(&self,pos:PhysicalPosition<i32>)->bool{
  let now=Instant::now();if let Ok(mut s)=self.state.lock(){s.recent.retain(|(_,t)|now.duration_since(*t)<Duration::from_secs(1));let point=(pos.x,pos.y);if s.recent.iter().any(|(p,_)|*p==point){return false}
   // Real dragging cancels docking AND motion. Never re-capture the same drag.
   let docked=s.dock.take().is_some();let moving=s.motion.take().is_some();if docked||moving{s.skip_snap=true;}s.pending=Some(now);return docked||moving;
  }false
 }
 pub fn unclip(&self,w:&WebviewWindow){clip(w,(0,0),None)}
 fn place(&self,w:&WebviewWindow,pos:Point,area:Option<Area>){
  if let Ok(mut s)=self.state.lock(){s.recent.push_back((pos,Instant::now()));while s.recent.len()>80{s.recent.pop_front();}}
  let _=w.set_position(PhysicalPosition::new(pos.0,pos.1));clip(w,pos,area);
 }
 pub fn release(&self,w:&WebviewWindow){
  let anchor=if let Ok(mut s)=self.state.lock(){let p=s.dock.map(|d|d.shown);s.dock=None;s.motion=None;s.pending=None;s.skip_snap=true;p}else{None};
  if let Some(p)=anchor{self.place(w,p,None)}else{clip(w,(0,0),None)}
 }
 pub fn reveal(&self,w:&WebviewWindow){
  let Ok(p)=w.outer_position()else{return};let mut action=None;
  if let Ok(mut s)=self.state.lock(){if let Some(mut d)=s.dock{d.open=true;d.outside=Some(Instant::now()+Duration::from_secs(1));s.dock=Some(d);s.pending=None;s.motion=Some(Motion{from:(p.x,p.y),to:d.shown,start:Instant::now()});if !self.animations.load(Ordering::Relaxed){s.motion=None;action=Some(d.shown);}}}
  if let Some(p)=action{self.place(w,p,None)}
 }
 pub fn tick(&self,app:&tauri::AppHandle){
  let Some(input)=self.input()else{return};let Some(w)=app.get_webview_window("main")else{return};
  if !self.enabled.load(Ordering::Relaxed){let active=self.state.lock().map(|s|s.dock.is_some()||s.motion.is_some()).unwrap_or(false);if active{self.release(&w)}return}
  let active=self.state.lock().map(|s|s.pending.is_some()||s.dock.is_some()||s.motion.is_some()).unwrap_or(false);if !active{return}
  if w.is_minimized().unwrap_or(true)||!w.is_visible().unwrap_or(false){return}
  let now=Instant::now();let mut action=None;let mut clear=false;let Ok(mut s)=self.state.lock()else{return};
  if input.down{if s.motion.take().is_some(){if let Some(d)=s.dock.take(){action=Some((d.shown,None));s.skip_snap=true;}}drop(s);if let Some((p,a))=action{self.place(&w,p,a)}return}
  if s.pending.map(|t|now.duration_since(t)>=Duration::from_millis(180)).unwrap_or(false){
   s.pending=None;if s.skip_snap{s.skip_snap=false;clear=true}else{
    drop(s);let geometry=(w.outer_position(),w.outer_size(),w.current_monitor(),w.scale_factor());let Ok(mut again)=self.state.lock()else{return};
    if let (Ok(p),Ok(size),Ok(Some(m)),Ok(scale))=geometry{let a=m.work_area();if let Some(d)=dock((p.x,p.y),(size.width as i32,size.height as i32),(a.position.x,a.position.y,a.size.width as i32,a.size.height as i32),scale){again.motion=Some(Motion{from:(p.x,p.y),to:d.shown,start:now});again.dock=Some(d);}}s=again;
   }
  }
  if let Some(mut d)=s.dock{
   let inside=contains(input.cursor,if d.open{d.shown}else{current(&s)},d.size)&&contains(input.cursor,(d.area.0,d.area.1),(d.area.2,d.area.3));
   if inside||(d.open&&self.hold.load(Ordering::Relaxed)){d.outside=None;if !d.open{let p=current(&s);d.open=true;s.motion=Some(Motion{from:p,to:d.shown,start:now});}}
   else if d.open{match d.outside{None=>d.outside=Some(now),Some(t)if now>=t&&now.duration_since(t)>=Duration::from_millis(850)=>{let p=current(&s);d.open=false;s.motion=Some(Motion{from:p,to:d.hidden,start:now});},_=>()}}
   s.dock=Some(d);
  }
  if let Some(m)=&s.motion{
   let progress=if self.animations.load(Ordering::Relaxed){now.duration_since(m.start).as_secs_f64()/0.24}else{1.};
   let p=tween(m.from,m.to,progress);action=Some((p,s.dock.map(|d|d.area)));if progress>=1.{s.motion=None;if s.dock.map(|d|d.open).unwrap_or(true){action=Some((p,None));}}
  }
  drop(s);if clear{clip(&w,(0,0),None)}if let Some((p,a))=action{self.place(&w,p,a)}
 }
}
fn current(s:&State)->Point{s.recent.back().map(|(p,_)|*p).or_else(||s.dock.map(|d|d.shown)).unwrap_or((0,0))}
fn contains(p:Point,origin:Point,size:Point)->bool{p.0>=origin.0&&p.0<origin.0+size.0&&p.1>=origin.1&&p.1<origin.1+size.1}
fn dock(pos:Point,size:Point,area:Area,scale:f64)->Option<Dock>{
 let threshold=(14.*scale).round() as i32;let strip=(6.*scale).round().max(4.) as i32;
 let options=[(Edge::Left,(pos.0-area.0).abs()),(Edge::Right,(pos.0-(area.0+area.2-size.0)).abs()),(Edge::Top,(pos.1-area.1).abs()),(Edge::Bottom,(pos.1-(area.1+area.3-size.1)).abs())];
 let edge=options.into_iter().filter(|(_,v)|*v<=threshold).min_by_key(|(_,v)|*v)?.0;
 let mut shown=(pos.0.clamp(area.0,(area.0+area.2-size.0).max(area.0)),pos.1.clamp(area.1,(area.1+area.3-size.1).max(area.1)));
 let hidden=match edge{Edge::Left=>{shown.0=area.0;(area.0-size.0+strip,shown.1)},Edge::Right=>{shown.0=area.0+area.2-size.0;(area.0+area.2-strip,shown.1)},Edge::Top=>{shown.1=area.1;(shown.0,area.1-size.1+strip)},Edge::Bottom=>{shown.1=area.1+area.3-size.1;(shown.0,area.1+area.3-strip)}};
 Some(Dock{edge,shown,hidden,size,area,open:true,outside:None})
}
fn tween(from:Point,to:Point,t:f64)->Point{let k=1.-(1.-t.clamp(0.,1.)).powi(3);((from.0 as f64+(to.0-from.0) as f64*k).round() as i32,(from.1 as f64+(to.1-from.1) as f64*k).round() as i32)}
fn clip(w:&WebviewWindow,pos:Point,area:Option<Area>){
 // Do not change HRGN during animations: it invalidates the non-client frame
 // and can leave an accent border after undocking. Let DWM clip naturally.
 let _=(w,pos,area); // No HRGN, style, or non-client-frame mutation at all.
}
#[cfg(test)]mod tests{use super::*;
 #[test]fn hides_to_recoverable_strip_on_each_edge(){let area=(-1920,40,1920,1040);let size=(440,820);for (p,e)in [((-1912,100),Edge::Left),((-448,100),Edge::Right),((-1200,45),Edge::Top),((-1200,253),Edge::Bottom)]{let d=dock(p,size,area,1.).unwrap();assert_eq!(d.edge,e);assert!(d.open);match e{Edge::Left=>assert_eq!(d.hidden.0+size.0,area.0+6),Edge::Right=>assert_eq!(d.hidden.0,area.0+area.2-6),Edge::Top=>assert_eq!(d.hidden.1+size.1,area.1+6),Edge::Bottom=>assert_eq!(d.hidden.1,area.1+area.3-6)}}assert!(dock((-1200,120),size,area,1.).is_none());}
 #[test]fn easing_is_smooth_and_bounded(){assert_eq!(tween((0,0),(100,200),0.),(0,0));assert_eq!(tween((0,0),(100,200),1.),(100,200));let p=tween((0,0),(100,200),0.5);assert!(p.0>50&&p.0<100&&p.1<200);}
 #[test]fn application_moves_do_not_redock_but_drag_does(){let snap=Snapping::new(true);{let mut s=snap.state.lock().unwrap();s.dock=dock((5,80),(440,820),(0,0,1920,1080),1.);s.motion=Some(Motion{from:(5,80),to:(0,80),start:Instant::now()});s.recent.push_back(((0,80),Instant::now()));}snap.moved(PhysicalPosition::new(0,80));assert!(snap.state.lock().unwrap().dock.is_some());snap.moved(PhysicalPosition::new(40,80));let s=snap.state.lock().unwrap();assert!(s.dock.is_none()&&s.motion.is_none()&&s.skip_snap&&s.pending.is_some());}
}
#[cfg(feature="verify-ui")]pub fn verify(app:&tauri::AppHandle,w:&WebviewWindow)->serde_json::Value{
 use serde_json::json;
 fn frame(w:&WebviewWindow)->Option<(isize,i32)>{w.hwnd().ok().map(|h|unsafe{use windows_sys::Win32::{Graphics::Gdi::{CreateRectRgn,GetWindowRgn,DeleteObject},UI::WindowsAndMessaging::{GetWindowLongPtrW,GWL_STYLE,WS_CAPTION,WS_THICKFRAME}};let region=CreateRectRgn(0,0,0,0);let kind=GetWindowRgn(h.0 as _,region);DeleteObject(region as _);(GetWindowLongPtrW(h.0 as _,GWL_STYLE)&(WS_CAPTION|WS_THICKFRAME)as isize,kind)})}
 let initial_frame=frame(w);
  let snap=app.state::<Snapping>();let Ok(original)=w.outer_position()else{return json!({})};let Ok(Some(m))=w.current_monitor()else{return json!({})};let a=m.work_area();let scale=w.scale_factor().unwrap_or(1.);let y=a.position.y+(40.*scale) as i32;let near=(a.position.x+(12.*scale) as i32,y);let height=w.outer_size().map(|s|s.height as i32).unwrap_or(a.size.height as i32);let resting_y=y.clamp(a.position.y,(a.position.y+a.size.height as i32-height).max(a.position.y));let anchor=(a.position.x,resting_y);let wait=|ms|std::thread::sleep(Duration::from_millis(ms));
 *snap.input.lock().unwrap()=Some(Input{cursor:(-100000,-100000),down:false});snap.hold.store(false,Ordering::Relaxed);snap.enabled.store(true,Ordering::Relaxed);snap.animations.store(true,Ordering::Relaxed);
 let _=w.set_position(PhysicalPosition::new(near.0,near.1));wait(270);let half=w.outer_position().ok();wait(320);let attached=w.outer_position().map(|p|(p.x,p.y)==anchor).unwrap_or(false);let animated=half.map(|p|p.x>anchor.0&&p.x<near.0).unwrap_or(false);wait(1050);let hidden=w.outer_position().map(|p|p.x<a.position.x-100).unwrap_or(false);
 *snap.input.lock().unwrap()=Some(Input{cursor:(a.position.x+2,y+30),down:false});wait(80);let half=w.outer_position().ok();wait(300);let hover=w.outer_position().map(|p|(p.x,p.y)==anchor).unwrap_or(false);let reveal_animated=half.map(|p|p.x<anchor.0&&p.x>anchor.0-(400.*scale) as i32).unwrap_or(false);
 *snap.input.lock().unwrap()=Some(Input{cursor:(-100000,-100000),down:false});wait(1300);snap.reveal(w);wait(350);let tray=w.outer_position().map(|p|(p.x,p.y)==anchor).unwrap_or(false);
 *snap.input.lock().unwrap()=Some(Input{cursor:(a.position.x+100,y+30),down:true});let free=PhysicalPosition::new(a.position.x+(50.*scale) as i32,y);let _=w.set_position(free);wait(80);*snap.input.lock().unwrap()=Some(Input{cursor:(-100000,-100000),down:false});wait(1100);let drag=w.outer_position().map(|p|p==free).unwrap_or(false)&&snap.state.lock().unwrap().dock.is_none();
 let _=w.set_position(PhysicalPosition::new(near.0,near.1));wait(1600);snap.enabled.store(false,Ordering::Relaxed);wait(350);let restored=w.outer_position().map(|p|p.x==anchor.0).unwrap_or(false);let _=w.set_position(PhysicalPosition::new(near.0,near.1));wait(500);let disabled=w.outer_position().map(|p|(p.x,p.y)==near).unwrap_or(false);
 snap.release(w);let _=w.set_position(original);wait(200);*snap.input.lock().unwrap()=None;snap.enabled.store(true,Ordering::Relaxed);
 let frame_clean=initial_frame.is_some()&&frame(w)==initial_frame;
 json!({"attached":attached,"animated":animated,"hidden":hidden,"hover":hover,"revealAnimated":reveal_animated,"tray":tray,"drag":drag,"restored":restored,"disabled":disabled,"frameClean":frame_clean})
}
