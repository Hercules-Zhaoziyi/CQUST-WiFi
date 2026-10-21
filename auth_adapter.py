"""Original CQUST ePortal protocol; no wire payloads in logs."""
from __future__ import annotations
import ast
import gzip
import json
import re
import sys
from dataclasses import dataclass
from html import unescape
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit
import requests

PORTAL_ROOT = "http://172.28.228.2"
LOGIN_URL = PORTAL_ROOT + "/eportal/InterFace.do?method=login"
STATUS_URL = PORTAL_ROOT + "/eportal/InterFace.do?method=getOnlineUserInfo"
LOGOUT_URL = PORTAL_ROOT + "/eportal/InterFace.do?method=logout"
DISCOVERY_URLS = ("http://123.123.123.123/", "http://www.msftconnecttest.com/redirect")


class AuthenticationError(RuntimeError):
    pass


def read_legacy_config(path: Path) -> dict:
    """Read literal assignments without executing config.py."""
    values = {}
    for node in ast.parse(path.read_text(encoding="utf-8-sig")).body:
        if isinstance(node, ast.Assign):
            try:
                value = ast.literal_eval(node.value)
            except (ValueError, TypeError):
                continue
            for target in node.targets:
                if isinstance(target, ast.Name):
                    values[target.id] = value
    if not isinstance(values.get("dataLogin"), dict):
        raise ValueError("Missing dataLogin")
    for name, default in (("loginUrl", LOGIN_URL), ("checkStatusUrl", STATUS_URL)):
        url = urlsplit(values.get(name, default))
        if url.scheme != "http" or url.hostname != "172.28.228.2" or url.port not in (None, 80, 8080):
            raise ValueError("Unsupported portal")
    return values


@dataclass(repr=False)
class CampusSession:
    client: object
    user_index: str
    query_string: str
    username: str = ""


class AuthAdapter:
    timeout = 8

    def __init__(self, config_paths: list[Path] | None = None):
        base = Path(sys.executable).parent if getattr(sys, "frozen", False) else Path(__file__).parent
        if config_paths is None:
            desktop = Path.home() / "Desktop" / "CQUST Wifi"
            config_paths = [base / "config.py", desktop / "config.py",
                            desktop / "CQUST_Wifi_Python" / "config.py"]
        self.profile = {}
        self.config_path = None
        for path in config_paths:
            try:
                self.profile = read_legacy_config(path)
                self.config_path = path
                break
            except (OSError, ValueError, SyntaxError, TypeError):
                continue

    @property
    def original_username(self):
        return str(self.profile.get("dataLogin", {}).get("userId", ""))

    def has_original_password(self, username):
        return bool(username and username == self.original_username
                    and self.profile.get("dataLogin", {}).get("password"))

    def _headers(self):
        headers = {"Accept": "application/json, */*", "Accept-Encoding": "gzip, deflate",
                   "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                   "Origin": PORTAL_ROOT, "Referer": PORTAL_ROOT + "/eportal/index.jsp",
                   "User-Agent": "Mozilla/5.0"}
        for k, v in self.profile.get("header", {}).items():
            if k.lower() in {"user-agent", "accept", "accept-language", "content-type"}:
                headers[k] = v
        return headers

    @staticmethod
    def _text(response):
        raw = response.content
        if raw.startswith(b"\x1f\x8b"):
            raw = gzip.decompress(raw)
        try:
            return raw.decode("utf-8-sig")
        except UnicodeDecodeError:
            return raw.decode("gb18030", errors="replace")

    @classmethod
    def _response_data(cls, response):
        try:
            value = json.loads(cls._text(response))
            if isinstance(value, dict):
                return value
        except (ValueError, OSError):
            pass
        raise AuthenticationError("认证服务器返回了无法识别的内容，请稍后重试。")

    def _post(self, client, url, data):
        try:
            response = client.post(url, headers=self._headers(), data=data,
                                   timeout=self.timeout, allow_redirects=False)
            if 300 <= response.status_code < 400:
                raise AuthenticationError("认证接口发生异常跳转，已停止发送凭据。")
            response.raise_for_status()
            return self._response_data(response)
        except requests.RequestException:
            raise AuthenticationError("无法连接校园网认证服务器，请检查校园 Wi-Fi 或有线网络。") from None

    @staticmethod
    def _query_from_url(url):
        query = urlsplit(url).query
        values = parse_qs(query, keep_blank_values=True)
        if values.get("queryString"):
            return unquote(values["queryString"][0])
        return query if "wlanuserip=" in query.lower() else ""

    def _discover_portal(self, client, log):
        for url in DISCOVERY_URLS:
            try:
                response = client.get(url, timeout=3, allow_redirects=False)
                targets = [response.headers.get("Location", "")]
                targets += re.findall(r'https?://[^"\'<>\s]+', self._text(response))
                for target in targets:
                    if urlsplit(target).hostname == "172.28.228.2":
                        query = self._query_from_url(unescape(target))
                        if query:
                            log("已获取本机校园网认证参数。")
                            return query
            except (requests.RequestException, OSError):
                continue
        return ""

    @staticmethod
    def encrypt_password(password, mac, exponent, modulus):
        """School login_bch.js/RSAUtils: reverse UTF-16 units and raw RSA."""
        n, e = int(modulus, 16), int(exponent, 16)
        chunk = 2 * ((n.bit_length() - 1) // 16)
        if chunk < 2 or e < 3:
            raise AuthenticationError("服务器的密码加密参数无效。")
        raw = (password + ">" + (mac or "111111111")).encode("utf-16-le")
        units = [int.from_bytes(raw[i:i+2], "little") for i in range(0, len(raw), 2)][::-1]
        units += [0] * ((-len(units)) % chunk)
        blocks = []
        for start in range(0, len(units), chunk):
            part = units[start:start+chunk]
            value = sum((part[i] + (part[i+1] << 8)) << (8*i) for i in range(0, chunk, 2))
            encrypted = format(pow(value, e, n), "x")
            blocks.append(encrypted.zfill(((len(encrypted)+3)//4)*4))
        return " ".join(blocks)

    def authenticate(self, username, password, settings, log):
        client = requests.Session()
        client.trust_env = False
        original = dict(self.profile.get("dataLogin", {}))
        use_original = not password and self.has_original_password(username)
        try:
            query = str(original.get("queryString", "")) or self._discover_portal(client, log)
            if not query:
                raise AuthenticationError("未取得认证参数。请将原 config.py 放在程序旁，或连接校园网络后重试。")
            data = {k: original.get(k, "") for k in (
                "userId", "password", "service", "queryString", "operatorPwd",
                "operatorUserId", "validcode", "passwordEncrypt", "userIndex")}
            data.update(userId=username, queryString=query)
            if use_original:
                log("已对接原配置：保留服务、加密密码与认证参数。")
            else:
                if not password:
                    raise AuthenticationError("请输入密码，或使用原配置对应的账号。")
                info = self._post(client, PORTAL_ROOT + "/eportal/InterFace.do?method=pageInfo",
                                  {"queryString": query})
                encrypt = str(info.get("passwordEncrypt", "")).lower()
                # Preserve the original config's RSA mode instead of downgrading
                # because the browser page's default encryption flag is false.
                if str(original.get("passwordEncrypt", "")).lower() == "true":
                    encrypt = "true"
                if encrypt not in {"true", "false"}:
                    raise AuthenticationError("未取得密码加密方式，已停止登录以保护密码。")
                data.update(passwordEncrypt=encrypt, password=password, userIndex="")
                if encrypt == "true":
                    mac = parse_qs(query).get("mac", ["111111111"])[0]
                    try:
                        data["password"] = self.encrypt_password(password, mac,
                            info["publicKeyExponent"], info["publicKeyModulus"])
                    except (KeyError, ValueError, TypeError):
                        raise AuthenticationError("无法取得服务器密码公钥，请重试。") from None
            result = self._post(client, LOGIN_URL, data)
            if str(result.get("result", "")).lower() != "success":
                msg = str(result.get("message", ""))
                if any(w in msg for w in ("密码", "password", "账号")):
                    raise AuthenticationError("认证服务器拒绝了账号或密码，请核对后重试。")
                raise AuthenticationError("服务器未接受登录。请核对账号、服务和原配置认证参数是否仍有效。")
            index = str(result.get("userIndex") or "")
            if not index:
                raise AuthenticationError("服务器未返回有效会话，不能确认登录成功。")
            session = CampusSession(client, index, query, username)
            if not self.check_status(session, settings, log)["online"]:
                raise AuthenticationError("登录请求已提交，但在线状态未确认，请重试。")
            return session
        except Exception:
            client.close()
            raise

    def check_status(self, session, settings, log):
        result = self._post(session.client, STATUS_URL, {"userIndex": session.user_index})
        state = str(result.get("result", "")).lower()
        if state in {"success", "wait"}:
            if session.username and result.get("userId") and result["userId"] != session.username:
                return {"online": False, "reason": "offline"}
            session.user_index = str(result.get("userIndex") or session.user_index)
            return {"online": True, "reason": "online"}
        if state not in {"fail", "failure", "offline"}:
            raise AuthenticationError("在线状态暂时无法确认。")
        msg = str(result.get("message", "")).lower()
        kicked = any(w in msg for w in ("其他设备", "其它设备", "另一", "顶替", "挤下", "replaced", "another device"))
        return {"online": False, "reason": "kicked" if kicked else "offline"}

    def disconnect(self, session, settings, log):
        if session is None:
            return
        result = self._post(session.client, LOGOUT_URL, {"userIndex": session.user_index})
        if str(result.get("result", "")).lower() != "success":
            raise AuthenticationError("服务器未确认下线，保留当前连接状态。")
        session.client.close()
