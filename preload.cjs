const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('cqust',{
  request:(method,args={})=>ipcRenderer.invoke('request',method,args),
  desktop:(command,value)=>ipcRenderer.invoke('desktop',command,value),
  subscribe:(callback)=>{const handler=(_event,payload)=>callback(payload);ipcRenderer.on('event',handler);return ()=>ipcRenderer.removeListener('event',handler);}
});
