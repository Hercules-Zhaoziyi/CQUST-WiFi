'use strict';
const tauri=window.__TAURI__;
window.cqust=Object.freeze({
 request:(method,args={})=>tauri.core.invoke('request',{method,args}),
 desktop:(command,value=null)=>tauri.core.invoke('desktop',{command,value}),
 subscribe:callback=>{let unlisten;let disposed=false;tauri.event.listen('cqust-event',event=>callback(event.payload)).then(fn=>{if(disposed)fn();else unlisten=fn;});return ()=>{disposed=true;unlisten?.();};}
});
