use std::{sync::{Mutex,atomic::{AtomicBool,Ordering}},time::{Duration,Instant}};
use tauri::{Manager,PhysicalPosition};

pub struct Snapping{pub enabled:AtomicBool,pending:Mutex<Option<Instant>>}
impl Snapping{
 pub fn new(enabled:bool)->Self{Self{enabled:AtomicBool::new(enabled),pending:Mutex::new(None)}}
 pub fn moved(&self){if self.enabled.load(Ordering::Relaxed){if let Ok(mut p)=self.pending.lock(){*p=Some(Instant::now());}}}
 pub fn tick(&self,app:&tauri::AppHandle){
  if !self.enabled.load(Ordering::Relaxed){if let Ok(mut p)=self.pending.lock(){*p=None;}return}
  // Wait until the drag really finishes. Never fight the pointer during a move.
  if unsafe{windows_sys::Win32::UI::Input::KeyboardAndMouse::GetAsyncKeyState(1)}<0{return}
  let ready=if let Ok(mut p)=self.pending.lock(){if p.map(|t|t.elapsed()>=Duration::from_millis(160)).unwrap_or(false){*p=None;true}else{false}}else{false};if !ready{return}
  let Some(w)=app.get_webview_window("main")else{return};
  if w.is_minimized().unwrap_or(true)||!w.is_visible().unwrap_or(false){return}
  let (Ok(pos),Ok(size),Ok(Some(monitor)),Ok(scale))=(w.outer_position(),w.outer_size(),w.current_monitor(),w.scale_factor())else{return};
  let area=monitor.work_area();let target=snap((pos.x,pos.y),(size.width as i32,size.height as i32),(area.position.x,area.position.y,area.size.width as i32,area.size.height as i32),(14.*scale).round() as i32);
  if target!=(pos.x,pos.y){let _=w.set_position(PhysicalPosition::new(target.0,target.1));}
 }
}
fn edge(value:i32,low:i32,high:i32,threshold:i32)->i32{if (value-low).abs()<=threshold{low}else if (value-high).abs()<=threshold{high}else{value}}
fn snap(pos:(i32,i32),size:(i32,i32),area:(i32,i32,i32,i32),threshold:i32)->(i32,i32){(edge(pos.0,area.0,area.0+area.2-size.0,threshold),edge(pos.1,area.1,area.1+(area.3-size.1).max(0),threshold))}
#[cfg(test)]mod tests{use super::*;#[test]fn magnets_preserve_size_and_negative_monitor_coordinates(){assert_eq!(snap((9,7),(440,820),(0,0,1920,1040),14),(0,0));assert_eq!(snap((1474,215),(440,820),(0,0,1920,1040),14),(1480,220));assert_eq!(snap((-1914,150),(440,820),(-1920,0,1920,1040),14),(-1920,150));assert_eq!(snap((120,140),(440,820),(0,0,1920,1040),14),(120,140));}}
