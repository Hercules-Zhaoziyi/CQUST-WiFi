"""Local CQUST ePortal client. Fresh context, JS-compatible encoding, no secret logs."""
from __future__ import annotations
import ast
import gzip
import ipaddress
import json
import re
import socket
import time
import threading
from dataclasses import dataclass, field
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote, urljoin, urlsplit, parse_qs
import requests

PORTAL_ROOT = 'http://172.28.228.2'
PORTAL_HOSTS = {'172.28.228.2', 'aaa.cqust.edu.cn'}
LOGIN_URL = PORTAL_ROOT + '/eportal/InterFace.do?method=login'
STATUS_URL = PORTAL_ROOT + '/eportal/InterFace.do?method=getOnlineUserInfo'
LOGOUT_URL = PORTAL_ROOT + '/eportal/InterFace.do?method=logout'
DISCOVERY_URLS = ('http://aaa.cqust.edu.cn/', 'http://www.msftconnecttest.com/redirect',
                  PORTAL_ROOT+'/', 'http://123.123.123.123/')

class AuthenticationError(RuntimeError):
    retryable = False

class NetworkError(AuthenticationError):
    retryable = True

class ContextRequired(AuthenticationError):
    pass

class AlreadyOnline(ContextRequired):
    def __init__(self, page_url, index):
        super().__init__('学校入口检测到当前设备已有在线会话。确认下线后，程序会自动重新获取登录网址，无需手填参数。')
        self.page_url, self.index = page_url, index

class ServiceRequired(AuthenticationError):
    def __init__(self, services):
        super().__init__('认证页面提供多个服务，请选择后重试。')
        self.services = services

class ChallengeRequired(AuthenticationError):
    def __init__(self, context):
        super().__init__('服务器要求验证码，请输入图片中的验证码后重试。')
        self.context = context

def read_legacy_config(path):
    """Release scanner only; the authentication flow never replays this profile."""
    values = {}
    for node in ast.parse(Path(path).read_text(encoding='utf-8-sig')).body:
        if isinstance(node, ast.Assign):
            try:
                value = ast.literal_eval(node.value)
            except (ValueError, TypeError):
                continue
            for target in node.targets:
                if isinstance(target, ast.Name):
                    values[target.id] = value
    return values

def encode_component(value):
    return quote(str(value), safe="-_.!~*'()")

def encode_twice(value):
    return encode_component(encode_component(value))

def extract_auth_query(url):
    query = urlsplit(url).query
    match = re.search(r'(?:^|&)mac=([^&]+)', query, re.I)
    return query, match.group(1) if match else '111111111'

def trusted_url(url):
    p = urlsplit(url)
    try:
        return (p.scheme in ('http', 'https') and p.hostname in PORTAL_HOSTS
                and p.port in (None, 80, 443, 8080) and not p.username and not p.password)
    except ValueError:
        return False

def lan_ip(host):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect((host, 80))  # Routing lookup; no UDP payload is sent.
            return sock.getsockname()[0]
    except OSError:
        return ''

class PageFields(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.fields, self.services, self.refresh = {}, [], ''
        self.service_select = False
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'input':
            self.fields[a.get('id', a.get('name', ''))] = a.get('value', '')
        if tag == 'select':
            self.service_select = a.get('id', a.get('name')) in ('net_access_type', 'service')
        if tag == 'option' and self.service_select and a.get('value'):
            self.services.append(a['value'])
        if tag == 'meta' and a.get('http-equiv', '').lower() == 'refresh':
            m = re.search(r'url\s*=\s*(.+)', a.get('content', ''), re.I)
            if m:
                self.refresh = m.group(1).strip().strip('\"\'')
    def handle_endtag(self, tag):
        if tag == 'select':
            self.service_select = False

@dataclass(repr=False)
class PortalContext:
    client: object
    page_url: str
    api_url: str
    query_string: str
    mac: str
    encrypt: bool
    exponent: str = ''
    modulus: str = ''
    service: str = '互联网'
    prefix: str = ''
    captcha_url: str = ''

@dataclass(repr=False)
class CampusSession:
    client: object
    user_index: str
    query_string: str
    username: str = ''
    context: PortalContext | None = None
    lan_ip: str = ''
    connected_at: float = field(default_factory=time.monotonic)
    keepalive_interval: float = 0
    next_keepalive: float = float('inf')
    request_lock: object = field(default_factory=threading.RLock, repr=False)

class AuthAdapter:
    timeout = (5, 15)
    def __init__(self, config_paths=None):
        # Signature retained for old callers/tests. No historical query/password/index.
        self.config_path, self.profile = None, {}
        self.original_username = ''
    def has_original_password(self, username):
        return False
    @staticmethod
    def _text(response):
        raw = response.content
        if raw.startswith(b'\x1f\x8b'):
            raw = gzip.decompress(raw)
        try:
            return raw.decode('utf-8-sig')
        except UnicodeDecodeError:
            return raw.decode('gb18030', errors='replace')
    @classmethod
    def _response_data(cls, response):
        try:
            value = json.loads(cls._text(response))
            if isinstance(value, (dict, list)):
                return value
        except (ValueError, OSError):
            pass
        raise AuthenticationError('认证服务器返回了非 JSON 内容，未确认认证结果。')
    @staticmethod
    def _headers(page_url):
        p = urlsplit(page_url)
        return {'Accept': '*/*', 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'Referer': page_url, 'Origin': f'{p.scheme}://{p.netloc}',
                'User-Agent': 'Mozilla/5.0', 'Accept-Encoding': 'gzip, deflate'}
    def _post(self, client, url, data, page_url=None):
        if not trusted_url(url):
            raise AuthenticationError('拒绝向非学校认证服务器发送请求。')
        try:
            r = client.post(url, data=data.encode('utf-8') if isinstance(data, str) else data,
                            headers=self._headers(page_url or PORTAL_ROOT+'/eportal/index.jsp'),
                            timeout=self.timeout, allow_redirects=False)
            if 300 <= r.status_code < 400:
                raise AuthenticationError('认证接口异常跳转，已停止发送凭据。')
            r.raise_for_status()
            return self._response_data(r)
        except requests.Timeout:
            raise NetworkError('认证服务器请求超时，请检查校园网连接后重试。') from None
        except requests.RequestException:
            raise NetworkError('校园认证服务器暂时不可达，请检查校园 Wi-Fi 或有线连接。') from None
    @staticmethod
    def _redirect(html, fields):
        if fields.refresh:
            return fields.refresh
        patterns = [r'(?:window\.|top\.|self\.|document\.)?location(?:\.href)?\s*=\s*[\"\']([^\"\']+)',
                    r'(?:window\.)?location\.(?:replace|assign)\(\s*[\"\']([^\"\']+)']
        for pattern in patterns:
            m = re.search(pattern, html, re.I)
            if m:
                return unescape(m.group(1))
        return ''
    def _follow(self, client, entry):
        url, visited = entry, set()
        for _ in range(7):
            if url in visited:
                break
            visited.add(url)
            if url not in DISCOVERY_URLS and not trusted_url(url):
                break
            r = client.get(url, timeout=(3, 5), allow_redirects=False,
                           headers={'User-Agent': 'Mozilla/5.0', 'Cache-Control': 'no-cache'})
            if 300 <= r.status_code < 400:
                target = r.headers.get('Location')
                if not target:
                    break
                url = urljoin(url, target)
                continue
            r.raise_for_status()
            html = self._text(r)
            parser = PageFields()
            parser.feed(html)
            query, _mac = extract_auth_query(url)
            if trusted_url(url) and urlsplit(url).path.lower().endswith('/success.jsp'):
                index = parse_qs(query).get('userIndex', [''])[0]
                if re.fullmatch('[A-Za-z0-9_.:-]{1,1024}', index):
                    raise AlreadyOnline(url, index)
            if trusted_url(url) and re.search(r'(?:^|&)wlanuserip=', query, re.I) and ('passwordEncrypt' in html or 'AuthInterFace' in html):
                return url, html, parser
            target = self._redirect(html, parser)
            if not target:
                break
            url = urljoin(url, target)
        return None
    def disconnect_discovered(self, online):
        """Only called after explicit UI confirmation; verify the current session first."""
        if not isinstance(online, AlreadyOnline) or not trusted_url(online.page_url):
            raise AuthenticationError('当前会话信息无效，请重新探测。')
        with requests.Session() as client:
            client.trust_env = False
            context = PortalContext(client, online.page_url,
                                    urljoin(online.page_url, './InterFace.do'), '', '', False)
            session = CampusSession(client, online.index, '', context=context)
            if self.check_status(session, {}, lambda _: None)['online']:
                self.disconnect(session, {}, lambda _: None)
    def discover_context(self, client, settings, log):
        manual = str(settings.get('login_page_url', '')).strip()
        if manual and not trusted_url(manual):
            raise ContextRequired('请输入 aaa.cqust.edu.cn 或 172.28.228.2 的完整校园认证网址。')
        found = None
        for entry in ([manual] if manual else DISCOVERY_URLS):
            try:
                found = self._follow(client, entry)
                if found:
                    break
            except (requests.RequestException, OSError, ValueError):
                continue
        if not found:
            raise ContextRequired('未获得本次网络的认证登录页。当前已在线或探测被拦截时，请在浏览器打开校园认证页，将包含 ? 后参数的完整登录网址粘贴到“认证帮助”；不会使用旧配置代替。')
        page_url, html, parser = found
        query, mac = extract_auth_query(page_url)
        init = re.search(r'AuthInterFace\.init\(\s*[\"\']([^\"\']*)[\"\']\s*\)', html)
        api = urljoin(page_url, (init.group(1) if init else './') + 'InterFace.do')
        if not trusted_url(api):
            raise ContextRequired('认证页面的接口指向非学校地址，已停止。')
        info = self._post(client, api+'?method=pageInfo', 'queryString='+encode_component(query), page_url)
        if not isinstance(info, dict):
            raise ContextRequired('认证页面配置格式无效。')
        values = dict(parser.fields)
        for key in ('passwordEncrypt', 'publicKeyExponent', 'publicKeyModulus'):
            if key in info:
                values[key] = str(info[key]).lower() if isinstance(info[key], bool) else str(info[key])
        encrypt = values.get('passwordEncrypt', '').lower()
        if encrypt not in ('true', 'false'):
            raise ContextRequired('当前页面未给出密码加密方式，已停止登录。')
        exponent, modulus = values.get('publicKeyExponent', ''), values.get('publicKeyModulus', '')
        if encrypt == 'true' and (not re.fullmatch('[0-9a-fA-F]+', exponent) or not re.fullmatch('[0-9a-fA-F]+', modulus)):
            raise ContextRequired('当前页面没有有效 RSA 公钥，请刷新认证网址；不使用历史公钥。')
        services = list(parser.services)
        if not services:
            data = self._post(client, api+'?method=getServices&queryString='+encode_component(query), '', page_url)
            items = data if isinstance(data, list) else data.get('services', data.get('serviceList', []))
            if isinstance(items, str):
                try:
                    items = json.loads(items)
                except ValueError:
                    items = []
            if isinstance(items, list):
                services = [str(x['serviceName']) for x in items if isinstance(x, dict) and x.get('serviceName')]
        if not services and values.get('net_access_type'):
            services = [values['net_access_type']]
        services = list(dict.fromkeys(s for s in services if s))
        if not services:
            raise ContextRequired('未能取得当前页面的服务列表，请刷新认证网址。')
        selected = settings.get('service', '')
        if selected not in services:
            if len(services) == 1:
                selected = services[0]
            else:
                raise ServiceRequired(services)
        context = PortalContext(client, page_url, api, query, mac, encrypt == 'true', exponent, modulus, selected)
        # Prefix is only used when the page explicitly marks its prefix control selected.
        prefix = re.search(r'prefixValue\s*=\s*[\"\']([^\"\']*)', html)
        chosen = re.search(r'<[^>]+id=[\"\']Tj_yes[\"\'][^>]+style=[\"\'][^\"\']*display\s*:\s*block', html, re.I)
        if prefix and chosen:
            context.prefix = prefix.group(1)
        if info.get('validCodeUrl'):
            context.captcha_url = urljoin(page_url, str(info['validCodeUrl']))
        if any(str(info.get(k, '')).lower() == 'true' for k in ('isOperatorPwd', 'isOperatorUserId')):
            raise ContextRequired('此服务要求独立运营商账号信息，请选择普通校园网服务或使用官方认证页。')
        log('已取得当前认证页面、服务及密码处理方式；未使用历史认证参数。')
        return context
    @staticmethod
    def encrypt_password(password, mac, exponent, modulus):
        # The supplied browser algorithm has nonstandard UTF-16 word arithmetic.
        # Only ASCII is accepted until non-ASCII parity is verified.
        try:
            data = (password+'>'+(mac or '111111111'))[::-1].encode('ascii')
        except UnicodeEncodeError:
            raise AuthenticationError('当前校园算法仅验证了 ASCII 密码；含中文等字符请使用官方页面登录。') from None
        n, e = int(modulus, 16), int(exponent, 16)
        chunk = 2*((n.bit_length()-1)//16)
        if chunk < 2 or chunk > 1024 or e <= 0:
            raise AuthenticationError('RSA 公钥参数无效。')
        data += b'\0' * (-len(data) % chunk)
        blocks = []
        for offset in range(0, len(data), chunk):
            encrypted = format(pow(int.from_bytes(data[offset:offset+chunk], 'little'), e, n), 'x')
            blocks.append(encrypted.zfill(((len(encrypted)+3)//4)*4))
        return ' '.join(blocks)
    def build_login_body(self, username, password, context, captcha=''):
        if not username.strip() or not password:
            raise AuthenticationError('请输入学号和密码。')
        value = self.encrypt_password(password, context.mac, context.exponent, context.modulus) if context.encrypt else password
        fields = [('userId', context.prefix+username.strip()), ('password', value), ('service', context.service),
                  ('queryString', context.query_string), ('operatorPwd', ''), ('operatorUserId', '')]
        body = '&'.join(k+'='+encode_twice(v) for k, v in fields)
        # Browser sends the normal alphanumeric captcha unencoded.
        if captcha and not re.fullmatch('[A-Za-z0-9]{1,16}', captcha):
            raise AuthenticationError('验证码应为图片中的字母或数字。')
        return body+'&validcode='+captcha+'&passwordEncrypt='+str(context.encrypt).lower()
    def authenticate(self, username, password, settings, log):
        context = settings.get('_challenge_context')
        client = context.client if isinstance(context, PortalContext) else requests.Session()
        client.trust_env = False
        retain = False
        try:
            context = context or self.discover_context(client, settings, log)
            captcha = str(settings.get('captcha', '')).strip()
            if context.captcha_url and not captcha:
                retain = True
                raise ChallengeRequired(context)
            body = self.build_login_body(username, password, context, captcha)
            result = self._post(client, context.api_url+'?method=login', body, context.page_url)
            if not isinstance(result, dict):
                raise AuthenticationError('登录响应格式异常。')
            if result.get('validCodeUrl') and result.get('result') != 'success':
                context.captcha_url = urljoin(context.page_url, str(result['validCodeUrl']))
                retain = True
                raise ChallengeRequired(context)
            if str(result.get('result', '')).lower() != 'success':
                msg = str(result.get('message', '')).lower()
                if any(x in msg for x in ('密码', 'password', '用户不存在', '账号不存在')):
                    raise AuthenticationError('服务器拒绝账号或密码，请核对输入。')
                if any(x in msg for x in ('在线', 'online')):
                    raise AuthenticationError('服务器提示账号或设备已在线，请先在原客户端下线再登录。')
                if any(x in msg for x in ('验证码', 'validcode')):
                    raise AuthenticationError('验证码错误或失效，请刷新官方认证页后重试。')
                raise AuthenticationError('学校服务器拒绝认证，可能是账号权限或服务限制；请在官方页面核对。未记录服务器返回的敏感内容。')
            index = str(result.get('userIndex') or '')
            if not re.fullmatch('[A-Za-z0-9_.:-]{1,1024}', index):
                raise AuthenticationError('服务器未返回有效会话，不能确认登录成功。')
            session = CampusSession(client, index, context.query_string, context.prefix+username.strip(), context,
                                    lan_ip(urlsplit(context.api_url).hostname))
            self._update_keepalive(session, result)
            if not self.check_status(session, settings, log)['online']:
                raise AuthenticationError('认证响应成功，但后续在线状态未确认。')
            return session
        except Exception:
            if not retain:
                client.close()
            raise
    @staticmethod
    def _update_keepalive(session, result):
        if 'keepaliveInterval' not in result:
            return
        try:
            value = float(result['keepaliveInterval'])
        except (ValueError, TypeError):
            return
        # Actual success.jsp schedules keepaliveInterval * 60 * 1000 (minutes).
        session.keepalive_interval = value*60 if 0 < value <= 1440 else 0
        session.next_keepalive = time.monotonic()+session.keepalive_interval if session.keepalive_interval else float('inf')
    def _session_post(self, session, method):
        api = session.context.api_url if session.context else PORTAL_ROOT+'/eportal/InterFace.do'
        page = session.context.page_url if session.context else PORTAL_ROOT+'/eportal/index.jsp'
        if not re.fullmatch('[A-Za-z0-9_.:-]{1,1024}', session.user_index):
            raise AuthenticationError('会话标识无效，请重新登录。')
        with session.request_lock:
            return self._post(session.client, api+'?method='+method, 'userIndex='+session.user_index, page)
    def check_status(self, session, settings, log):
        result = self._session_post(session, 'getOnlineUserInfo')
        if not isinstance(result, dict):
            raise AuthenticationError('在线状态响应无效。')
        state = str(result.get('result', '')).lower()
        if state in ('success', 'wait'):
            if session.username and result.get('userId') and result['userId'] != session.username:
                return {'online': False, 'reason': 'offline'}
            try:
                ip = str(result.get('userIp', ''))
                ipaddress.ip_address(ip)
                session.lan_ip = ip
            except ValueError:
                pass
            # Do not reset next_keepalive on each poll; doing so would starve it.
            if not session.keepalive_interval and result.get('keepaliveInterval'):
                self._update_keepalive(session, result)
            return {'online': True, 'reason': 'online'}
        if state not in ('fail', 'failure', 'offline'):
            raise NetworkError('在线状态暂时无法确认。')
        msg = str(result.get('message', '')).lower()
        reason = 'kicked' if any(w in msg for w in ('其他设备', '其它设备', '顶替', '挤下', 'another device', 'replaced')) else 'offline'
        return {'online': False, 'reason': reason}
    def keepalive(self, session):
        if not session.keepalive_interval or time.monotonic() < session.next_keepalive:
            return
        result = self._session_post(session, 'keepalive')
        session.next_keepalive = time.monotonic()+session.keepalive_interval
        if not isinstance(result, dict) or result.get('result') not in ('success', 'wait'):
            raise NetworkError('保活未确认，将继续查询在线状态。')
    def disconnect(self, session, settings, log):
        if session is None:
            return
        result = self._session_post(session, 'logout')
        if not isinstance(result, dict) or result.get('result') != 'success':
            raise AuthenticationError('服务器未确认下线，保留当前连接状态。')
        session.client.close()
