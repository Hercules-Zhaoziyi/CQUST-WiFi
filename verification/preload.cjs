const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('__TAURI__',{
 core:{invoke:(command,args)=>ipcRenderer.invoke(command,args)},
 event:{listen:async(_name,callback)=>{const listener=(_event,data)=>callback({payload:data});ipcRenderer.on('event',listener);return ()=>ipcRenderer.removeListener('event',listener);}}
});
