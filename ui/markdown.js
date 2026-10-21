'use strict';
// Markdown is untrusted release content: parse locally, sanitize to a small DOM
// allowlist, never load embedded images or allow script/style/IPC URLs.
window.cqustMarkdown=Object.freeze({render(target,source,base='https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/'){
 const text=String(source||'暂无更新说明。').slice(0,16000);
 if(!window.marked||!window.DOMPurify){target.textContent=text;return;}
 const fragment=window.DOMPurify.sanitize(window.marked.parse(text,{gfm:true,breaks:false,async:false}),{
  ALLOWED_TAGS:['h1','h2','h3','h4','h5','h6','p','br','hr','strong','em','del','s','blockquote','ul','ol','li','pre','code','a','table','thead','tbody','tr','th','td'],
  ALLOWED_ATTR:['href','title','start'],RETURN_DOM_FRAGMENT:true,ALLOW_DATA_ATTR:false,ALLOW_ARIA_ATTR:false
 });
 fragment.querySelectorAll('a').forEach(link=>{try{const raw=link.getAttribute('href');if(!raw)throw new Error();const u=new URL(raw,base);if(!['https:','http:'].includes(u.protocol)||u.username||u.password||!u.hostname||u.href.length>2048)throw new Error();link.href=u.href;link.setAttribute('rel','noopener noreferrer');}catch{link.removeAttribute('href');link.setAttribute('aria-disabled','true');}});
 target.replaceChildren(fragment);
 target.onclick=event=>{const link=event.target.closest('a[href]');if(link&&target.contains(link)){event.preventDefault();window.cqust.desktop('markdownLink',link.href).catch(()=>{document.getElementById('update-status').textContent='无法打开此链接，请稍后重试。';});}};
}});
