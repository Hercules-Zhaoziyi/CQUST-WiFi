"""CQUST campus-portal authentication used by the Windows GUI.

The request fields and result handling intentionally mirror the original
``login.py``/``config.py`` implementation. Usernames and passwords are passed
in by the GUI at runtime and are never imported from or written to config.py.
"""

from __future__ import annotations

from dataclasses import dataclass
from html import unescape
import json
import re
from typing import Any, Callable
from urllib.parse import parse_qs, unquote, urlparse

import requests


PORTAL_ROOT = "http://172.28.228.2"
LOGIN_URL = f"{PORTAL_ROOT}/eportal/InterFace.do?method=login"
STATUS_URL = f"{PORTAL_ROOT}/eportal/InterFace.do?method=getOnlineUserInfo"
LOGOUT_URL = f"{PORTAL_ROOT}/eportal/InterFace.do?method=logout"
DISCOVERY_URLS = (
    "http://123.123.123.123/",
    "http://www.msftconnecttest.com/redirect",
    f"{PORTAL_ROOT}/eportal/index.jsp",
)


class AuthenticationError(RuntimeError):
    """The portal responded, but authentication was not successful."""


@dataclass
class CampusSession:
    client: requests.Session
    user_index: str
    query_string: str


class AuthAdapter:
    timeout = 8

    def _headers(self) -> dict[str, str]:
        return {
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Origin": PORTAL_ROOT,
            "Referer": f"{PORTAL_ROOT}/eportal/index.jsp",
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"
            ),
            "X-Requested-With": "XMLHttpRequest",
        }

    @staticmethod
    def _response_data(response: requests.Response) -> dict[str, Any]:
        try:
            value = response.json()
            if isinstance(value, dict):
                return value
        except (ValueError, json.JSONDecodeError):
            pass

        text = response.text.strip()
        try:
            value = json.loads(text)
            if isinstance(value, dict):
                return value
        except (ValueError, json.JSONDecodeError):
            pass

        result = re.search(r'["\']result["\']\s*:\s*["\']([^"\']+)', text, re.I)
        message = re.search(r'["\'](?:message|msg)["\']\s*:\s*["\']([^"\']*)', text, re.I)
        user_index = re.search(r'["\']userIndex["\']\s*:\s*["\']([^"\']*)', text, re.I)
        return {
            "result": result.group(1) if result else "",
            "message": unescape(message.group(1)) if message else text[:240],
            "userIndex": user_index.group(1) if user_index else "",
        }

    @staticmethod
    def _query_from_url(url: str) -> str:
        parsed = urlparse(url)
        values = parse_qs(parsed.query, keep_blank_values=True)
        if values.get("queryString"):
            return unquote(values["queryString"][0])
        if "wlanuserip=" in parsed.query.lower() or "wlanacname=" in parsed.query.lower():
            return unquote(parsed.query)
        return ""

    def _discover_portal(self, client: requests.Session, log: Callable[[str], None]) -> tuple[str, str]:
        for url in DISCOVERY_URLS:
            try:
                response = client.get(
                    url,
                    headers={"User-Agent": self._headers()["User-Agent"]},
                    timeout=self.timeout,
                    allow_redirects=True,
                )
            except requests.RequestException:
                continue

            query_string = self._query_from_url(response.url)
            if not query_string:
                match = re.search(
                    r'["\']queryString["\']\s*[:=]\s*["\']([^"\']+)',
                    response.text,
                    re.I,
                )
                if match:
                    query_string = unquote(unescape(match.group(1)))
            if not query_string:
                match = re.search(r'(wlanuserip=[^"\'<>\s]+)', response.text, re.I)
                if match:
                    query_string = unquote(unescape(match.group(1)))

            service = ""
            service_match = re.search(
                r'name=["\']service["\'][^>]*value=["\']([^"\']*)',
                response.text,
                re.I,
            )
            if service_match:
                service = unescape(service_match.group(1))
            if query_string:
                log("已自动获取本机校园网认证参数。")
                return query_string, service

        raise AuthenticationError("未检测到 CQUST 校园网认证页面，请先连接学校 Wi-Fi 后重试。")

    def authenticate(
        self,
        username: str,
        password: str,
        settings: dict[str, Any],
        log: Callable[[str], None],
    ) -> CampusSession:
        del settings
        client = requests.Session()
        query_string, service = self._discover_portal(client, log)
        data = {
            "userId": username,
            "password": password,
            "service": service,
            "queryString": query_string,
            "operatorPwd": "",
            "operatorUserId": "",
            "validcode": "",
            "passwordEncrypt": "false",
            "userIndex": "",
        }
        try:
            response = client.post(LOGIN_URL, headers=self._headers(), data=data, timeout=self.timeout)
            response.raise_for_status()
        except requests.RequestException as exc:
            client.close()
            raise AuthenticationError(f"无法连接校园网认证服务器：{exc}") from exc

        result = self._response_data(response)
        state = str(result.get("result", "")).lower()
        if state not in {"success", "wait"}:
            client.close()
            message = str(result.get("message") or "账号、密码或网络环境不正确")
            raise AuthenticationError(message)
        return CampusSession(client, str(result.get("userIndex", "")), query_string)

    def check_status(
        self,
        session: CampusSession,
        settings: dict[str, Any],
        log: Callable[[str], None],
    ) -> dict[str, Any]:
        del settings, log
        try:
            response = session.client.post(
                STATUS_URL,
                headers=self._headers(),
                data={"userIndex": session.user_index},
                timeout=self.timeout,
            )
            response.raise_for_status()
        except requests.RequestException as exc:
            raise RuntimeError(f"在线状态检查失败：{exc}") from exc

        result = self._response_data(response)
        state = str(result.get("result", "")).lower()
        if state in {"success", "wait"}:
            if not session.user_index:
                session.user_index = str(result.get("userIndex", ""))
            return {"online": True, "reason": "online"}

        message = str(result.get("message") or result.get("msg") or "").lower()
        offline_words = ("网络断开", "network unavailable", "not reachable")
        # A valid portal response saying that a previously authenticated session
        # is gone normally means that the account was replaced by another device.
        reason = "offline" if any(word in message for word in offline_words) else "kicked"
        return {"online": False, "reason": reason}

    def disconnect(
        self,
        session: CampusSession | None,
        settings: dict[str, Any],
        log: Callable[[str], None],
    ) -> None:
        del settings
        if session is None:
            return
        try:
            response = session.client.post(
                LOGOUT_URL,
                headers=self._headers(),
                data={"userIndex": session.user_index},
                timeout=self.timeout,
            )
            response.raise_for_status()
        except requests.RequestException as exc:
            log(f"校园网下线请求未完成：{exc}")
        finally:
            session.client.close()
