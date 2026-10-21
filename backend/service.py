"""Local JSON-lines pipe. Credentials never use command-line arguments or TCP ports."""
import base64
import json
import os
from pathlib import Path
import queue
import sys
import threading
import time
import keyring
from auth_adapter import AuthAdapter, AuthenticationError, AlreadyOnline, ContextRequired, ServiceRequired, ChallengeRequired, trusted_url, lan_ip

DEFAULTS = dict(keep_connected=False, auto_login=False, check_interval=10, theme='system', service='')

class Service:
    def __init__(self, folder=None):
        self.folder = Path(folder or Path(os.environ.get('APPDATA', str(Path.home()))) / 'CQUST Wifi')
        self.folder.mkdir(parents=True, exist_ok=True)
        self.settings = dict(DEFAULTS)
        self.settings.update({k:v for k,v in self.read('settings.json').items() if k in DEFAULTS})
        if self.settings['check_interval'] not in (5,10,120): self.settings['check_interval'] = 10
        if self.settings['theme'] not in ('system','light','dark'): self.settings['theme'] = 'system'
        accounts = self.read('accounts.json')
        self.accounts = [x for x in accounts.get('accounts',[]) if isinstance(x,str)]
        self.username = str(accounts.get('last_username',''))
        self.password = ''
        self.remember = True
        self.auth = AuthAdapter()
        self.session = None
        self.challenge = None
        self.online = None
        self.started = 0
        self.next_poll = float('inf')
        self.next_retry = float('inf')
        self.manual = False
        self.logs = []
        self.local_ip = lan_ip('172.28.228.2')
    def read(self,name):
        try:
            value = json.loads((self.folder/name).read_text(encoding='utf-8'))
            return value if isinstance(value,dict) else {}
        except (OSError,ValueError): return {}
    def write(self,name,data):
        target = self.folder/name
        temporary = target.with_suffix('.tmp')
        temporary.write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
        temporary.replace(target)
    def emit(self,kind,data):
        print(json.dumps(dict(event=kind,data=data),ensure_ascii=False),flush=True)
    def log(self,message):
        self.logs.append(time.strftime('%H:%M:%S')+'  '+message)
        self.logs = self.logs[-250:]
        self.emit('log',self.logs[-1])
    def snapshot(self):
        return dict(connected=self.session is not None, username=self.username,
            ip=self.session.lan_ip if self.session else self.local_ip, started=self.started,
            accounts=self.accounts, settings=self.settings, logs=self.logs)
    def saved_password(self,username):
        try: return keyring.get_password('CQUST Wifi',username) or ''
        except Exception: return ''
    def clear_challenge(self):
        if self.challenge: self.challenge.client.close()
        self.challenge = None
    def login(self,args,silent=False):
        if self.session: raise AuthenticationError('请先断开当前会话，再切换账号。')
        self.username = str(args.get('username',self.username)).strip()
        self.password = str(args.get('password','')) or self.saved_password(self.username)
        self.remember = bool(args.get('remember', self.remember))
        if not self.username or not self.password: raise AuthenticationError('请输入账号和密码；已保存账号可留空使用保存的密码。')
        self.manual = False
        self.next_retry = float('inf')
        options = dict(self.settings, login_page_url=args.get('url',''), captcha=args.get('captcha',''))
        if args.get('challenge') and self.challenge: options['_challenge_context'] = self.challenge
        else: self.clear_challenge()
        try:
            self.session = self.auth.authenticate(self.username,self.password,options,lambda _:None)
        except AuthenticationError as error:
            if isinstance(error,AlreadyOnline): self.online = error
            if isinstance(error,ChallengeRequired): self.challenge = error.context
            if silent and error.retryable: self.next_retry = time.monotonic()+self.settings['check_interval']
            raise
        self.challenge = None
        self.online = None
        self.started = time.time()
        self.next_poll = time.monotonic()+self.settings['check_interval']
        self.settings['service'] = self.session.context.service
        self.write('settings.json',self.settings)
        if self.username not in self.accounts: self.accounts.append(self.username)
        self.write('accounts.json',dict(accounts=self.accounts,last_username=self.username))
        if self.remember:
            try: keyring.set_password('CQUST Wifi',self.username,self.password)
            except Exception: self.log('已连接，但 Windows 凭据保存失败；下次需重新输入密码。')
        else:
            try: keyring.delete_password('CQUST Wifi',self.username)
            except keyring.errors.PasswordDeleteError: pass
        self.log('校园网已连接。')
        self.emit('state',self.snapshot())
        return self.snapshot()
    def error(self,exc):
        if not isinstance(exc,AuthenticationError): return dict(kind='error',message='操作未完成，请检查网络或本机权限后重试。')
        result = dict(kind='error',message=str(exc))
        if isinstance(exc,AlreadyOnline): result['kind']='online'
        elif isinstance(exc,ServiceRequired): result.update(kind='services',services=exc.services)
        elif isinstance(exc,ContextRequired): result['kind']='context'
        elif isinstance(exc,ChallengeRequired):
            result['kind']='captcha'
            try:
                url=exc.context.captcha_url
                if trusted_url(url):
                    response=exc.context.client.get(url,timeout=(3,5),allow_redirects=False)
                    if response.status_code==200 and len(response.content)<1000000:
                        mime=response.headers.get('Content-Type','').split(';')[0]
                        if mime in ('image/png','image/jpeg','image/gif'):
                            result['image']='data:'+mime+';base64,'+base64.b64encode(response.content).decode()
            except Exception: pass
        return result
    def command(self,method,args):
        if method=='snapshot': return self.snapshot()
        if method=='login': return self.login(args)
        if method=='disconnect':
            self.manual=True
            self.next_retry=float('inf')
            self.auth.disconnect(self.session,self.settings,lambda _:None)
            self.session=None
            self.started=0
            self.log('已手动断开；不会自动重连。')
            self.emit('state',self.snapshot())
            return self.snapshot()
        if method=='switchOnline':
            if not self.online: raise AuthenticationError('请重新探测当前会话。')
            self.auth.disconnect_discovered(self.online)
            self.online=None
            time.sleep(1)
            return self.login(args)
        if method=='settings':
            for key in ('keep_connected','auto_login'):
                if key in args and isinstance(args[key],bool): self.settings[key]=args[key]
            if args.get('check_interval') in (5,10,120): self.settings['check_interval']=args['check_interval']
            if args.get('theme') in ('system','light','dark'): self.settings['theme']=args['theme']
            if isinstance(args.get('service'),str): self.settings['service']=args['service'][:200]
            self.write('settings.json',self.settings)
            self.next_poll=time.monotonic()+self.settings['check_interval']
            if not self.settings['keep_connected']: self.next_retry=float('inf')
            self.emit('state',self.snapshot())
            return self.snapshot()
        if method=='forget':
            username=str(args.get('username',''))
            try: keyring.delete_password('CQUST Wifi',username)
            except keyring.errors.PasswordDeleteError: pass
            self.accounts=[x for x in self.accounts if x!=username]
            self.write('accounts.json',dict(accounts=self.accounts,last_username=self.username if self.username in self.accounts else ''))
            return self.snapshot()
        raise AuthenticationError('不支持的操作。')
    def tick(self):
        now=time.monotonic()
        if self.session:
            if self.session.keepalive_interval and now>=self.session.next_keepalive:
                try: self.auth.keepalive(self.session)
                except AuthenticationError:
                    self.session.next_keepalive=now+max(10,self.session.keepalive_interval)
                    self.log('保活暂未确认，继续检查在线状态。')
            if now>=self.next_poll:
                self.next_poll=now+self.settings['check_interval']
                try: result=self.auth.check_status(self.session,self.settings,lambda _:None)
                except AuthenticationError:
                    self.log('暂时无法确认在线状态，稍后重试。')
                    return
                if not result['online']:
                    self.session.client.close()
                    self.session=None
                    self.started=0
                    self.log('检测到校园网连接已断开。')
                    self.emit('state',self.snapshot())
                    if self.settings['keep_connected'] and not self.manual: self.next_retry=now
                    else: self.emit('notice','已在其他设备登录，已下线。' if result['reason']=='kicked' else '校园网连接已断开。')
                else: self.emit('state',self.snapshot())
        if not self.session and now>=self.next_retry and not self.manual:
            self.next_retry=float('inf')
            self.emit('busy',True)
            try: self.login(dict(username=self.username,password=self.password,remember=self.remember),silent=True)
            except AuthenticationError as exc: self.log('自动连接未完成：'+str(exc))
            finally: self.emit('busy',False)

def main():
    sys.stdin.reconfigure(encoding='utf-8')
    sys.stdout.reconfigure(encoding='utf-8')
    commands=queue.Queue()
    def reader():
        for line in sys.stdin:
            try:
                if len(line)<32768: commands.put(json.loads(line))
            except ValueError: pass
        commands.put(None)
    threading.Thread(target=reader,daemon=True).start()
    service=Service()
    service.emit('state',service.snapshot())
    if service.settings['auto_login'] and service.saved_password(service.username): service.next_retry=time.monotonic()+1
    while True:
        try: message=commands.get(timeout=.5)
        except queue.Empty:
            service.tick()
            continue
        if message is None: break
        method=message.get('method')
        if method in ('login','disconnect','switchOnline'): service.emit('busy',True)
        try: reply=dict(id=message.get('id'),ok=True,data=service.command(method,message.get('args',{})))
        except Exception as exc: reply=dict(id=message.get('id'),ok=False,error=service.error(exc))
        print(json.dumps(reply,ensure_ascii=False),flush=True)
        if method in ('login','disconnect','switchOnline'): service.emit('busy',False)
    service.clear_challenge()
    if service.session: service.session.client.close()

if __name__=='__main__': main()
