use std::sync::atomic::AtomicBool;
use tauri::WebviewWindow;

pub struct Snapping {
    pub enabled: AtomicBool,
    pub animations: AtomicBool,
    pub hold: AtomicBool,
}

impl Snapping {
    pub fn new(_enabled: bool) -> Self {
        Self {
            enabled: AtomicBool::new(false),
            animations: AtomicBool::new(false),
            hold: AtomicBool::new(false),
        }
    }

    pub fn release(&self, _window: &WebviewWindow) {}
}
