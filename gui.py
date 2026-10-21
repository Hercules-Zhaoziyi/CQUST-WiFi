from __future__ import annotations

import json
import os
import queue
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Callable
import tkinter.messagebox as messagebox

import customtkinter as ctk
import keyring

from auth_adapter import AuthAdapter

APP_NAME = "CQUST Wi-Fi"
APP_DIR = Path(os.environ.get("APPDATA", str(Path.home() / ".config"))) / "CQUST Wifi"
ACCOUNTS_FILE = APP_DIR / "accounts.json"
SETTINGS_FILE = APP_DIR / "settings.json"
KEYRING_SERVICE = "CQUST Wifi"

BG = ("#F2F5FA", "#0B1220")
PANEL = ("#FFFFFF", "#111C30")
PANEL_ALT = ("#EAF0F8", "#17243A")
BORDER = ("#D3DCE9", "#263650")
FIELD = ("#F7F9FC", "#0D1728")
TEXT = ("#152238", "#F3F6FC")
MUTED = ("#607089", "#95A4BC")
BLUE = "#3978F6"
BLUE_HOVER = "#2E67D8"
GREEN = "#23C58E"
RED = "#F05C68"
AMBER = "#F5B84B"
THEME_LABELS = {"system": "跟随 Windows", "light": "亮色", "dark": "暗色"}
THEME_VALUES = {label: value for value, label in THEME_LABELS.items()}

DEFAULT_SETTINGS = {
    "keep_connected": False,
    "auto_login": False,
    "check_interval": 7,
    "theme": "system",
}


class LocalConfig:
    """Stores app preferences separately from the campus-auth config.

    Passwords are stored in the OS keyring; this JSON file only stores usernames
    and UI preferences.
    """

    def __init__(self) -> None:
        APP_DIR.mkdir(parents=True, exist_ok=True)
        self.accounts: list[str] = []
        self.last_username = ""
        self.settings = dict(DEFAULT_SETTINGS)
        self._load_accounts()
        self._load_settings()

    def _load_accounts(self) -> None:
        try:
            data = json.loads(ACCOUNTS_FILE.read_text(encoding="utf-8"))
            self.accounts = list(dict.fromkeys(str(x) for x in data.get("accounts", []) if str(x)))
            self.last_username = str(data.get("last_username", ""))
        except (OSError, ValueError, TypeError):
            self.accounts, self.last_username = [], ""

    def _save_accounts(self) -> None:
        ACCOUNTS_FILE.write_text(
            json.dumps({"accounts": self.accounts, "last_username": self.last_username}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    def _load_settings(self) -> None:
        try:
            data = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
            self.settings.update({k: data[k] for k in DEFAULT_SETTINGS if k in data})
        except (OSError, ValueError, TypeError):
            pass
        try:
            interval = int(self.settings.get("check_interval", 7))
        except (TypeError, ValueError):
            interval = 7
        self.settings["check_interval"] = interval if interval in (5, 7, 10) else 7
        if self.settings.get("theme") not in {"system", "light", "dark"}:
            self.settings["theme"] = "system"

    def save_settings(self, settings: dict[str, Any]) -> None:
        self.settings.update(settings)
        SETTINGS_FILE.write_text(json.dumps(self.settings, ensure_ascii=False, indent=2), encoding="utf-8")

    def remember_account(self, username: str, password: str, remember_password: bool) -> None:
        username = username.strip()
        if not username:
            return
        if username not in self.accounts:
            self.accounts.append(username)
        self.last_username = username
        if remember_password:
            keyring.set_password(KEYRING_SERVICE, username, password)
        else:
            try:
                keyring.delete_password(KEYRING_SERVICE, username)
            except keyring.errors.PasswordDeleteError:
                pass
        self._save_accounts()

    def get_password(self, username: str) -> str:
        try:
            return keyring.get_password(KEYRING_SERVICE, username) or ""
        except Exception:
            return ""

    def forget_account(self, username: str) -> None:
        self.accounts = [item for item in self.accounts if item != username]
        if self.last_username == username:
            self.last_username = self.accounts[-1] if self.accounts else ""
        try:
            keyring.delete_password(KEYRING_SERVICE, username)
        except Exception:
            pass
        self._save_accounts()


class CQUSTWifiApp(ctk.CTk):
    def __init__(self) -> None:
        super().__init__()
        self.local = LocalConfig()
        ctk.set_appearance_mode(str(self.local.settings.get("theme", "system")))
        ctk.set_default_color_theme("blue")
        self.title(APP_NAME)
        self.geometry("920x700")
        self.minsize(760, 620)
        self.configure(fg_color=BG)

        self.auth = AuthAdapter()
        self.session: Any = None
        self.current_username = ""
        self.current_password = ""
        self.connected = False
        self.busy = False
        self.monitor_after_id: str | None = None
        self.ui_queue_after_id: str | None = None
        self.auto_login_after_id: str | None = None
        self.logs: list[str] = []
        self.log_textbox: ctk.CTkTextbox | None = None
        self.current_view = "login"
        self._ui_queue: queue.Queue[tuple[Callable[..., None], tuple[Any, ...]]] = queue.Queue()
        self.protocol("WM_DELETE_WINDOW", self.on_close)

        self.root_frame = ctk.CTkFrame(self, fg_color="transparent")
        self.root_frame.pack(fill="both", expand=True, padx=28, pady=24)
        self.show_login_view()
        self.ui_queue_after_id = self.after(80, self._drain_ui_queue)
        self.log("应用已启动。")
        self.auto_login_after_id = self.after(500, self._try_auto_login)

    # ---------- UI utilities ----------
    def log(self, message: str) -> None:
        line = f"[{datetime.now().strftime('%H:%M:%S')}] {message}"
        self.logs.append(line)
        self.logs = self.logs[-1500:]
        self._ui_queue.put((self._append_log_if_open, (line,)))

    def _drain_ui_queue(self) -> None:
        try:
            while True:
                callback, args = self._ui_queue.get_nowait()
                callback(*args)
        except queue.Empty:
            pass
        if self.winfo_exists():
            self.ui_queue_after_id = self.after(80, self._drain_ui_queue)

    def _append_log_if_open(self, line: str) -> None:
        if self.log_textbox is None or not self.log_textbox.winfo_exists():
            return
        self.log_textbox.configure(state="normal")
        self.log_textbox.insert("end", line + "\n")
        self.log_textbox.see("end")
        self.log_textbox.configure(state="disabled")

    def _clear_view(self) -> None:
        for child in self.root_frame.winfo_children():
            child.destroy()

    def _header(self, subtitle: str, right_widgets: list[tuple[str, Callable[[], None]]] | None = None) -> None:
        header = ctk.CTkFrame(self.root_frame, fg_color="transparent")
        header.pack(fill="x", pady=(0, 22))
        brand = ctk.CTkFrame(header, fg_color="transparent")
        brand.pack(side="left")
        icon = ctk.CTkFrame(brand, width=46, height=46, corner_radius=15, fg_color=BLUE)
        icon.pack(side="left", padx=(0, 12))
        icon.pack_propagate(False)
        ctk.CTkLabel(icon, text="📶", font=ctk.CTkFont(size=23, weight="bold"), text_color="white").pack(expand=True)
        labels = ctk.CTkFrame(brand, fg_color="transparent")
        labels.pack(side="left")
        ctk.CTkLabel(labels, text=APP_NAME, font=ctk.CTkFont(size=22, weight="bold"), text_color=TEXT).pack(anchor="w")
        ctk.CTkLabel(labels, text=subtitle, font=ctk.CTkFont(size=12), text_color=MUTED).pack(anchor="w", pady=(2, 0))
        if right_widgets:
            actions = ctk.CTkFrame(header, fg_color="transparent")
            actions.pack(side="right")
            for text, callback in right_widgets:
                ctk.CTkButton(actions, text=text, command=callback, width=88, height=34, corner_radius=11,
                              fg_color=PANEL_ALT, hover_color=BORDER, text_color=TEXT).pack(side="left", padx=(8, 0))

    def _card(self, parent: Any, **kwargs: Any) -> ctk.CTkFrame:
        return ctk.CTkFrame(parent, fg_color=PANEL, corner_radius=22, border_width=1, border_color=BORDER, **kwargs)

    def _set_busy(self, value: bool, button: ctk.CTkButton | None = None, busy_text: str = "正在处理…", normal_text: str = "登录并连接") -> None:
        self.busy = value
        if button is not None:
            button.configure(state="disabled" if value else "normal", text=busy_text if value else normal_text)

    # ---------- Login view ----------
    def show_login_view(self) -> None:
        self._cancel_monitor()
        self.current_view = "login"
        self.log_textbox = None
        self._clear_view()
        self._header("校园网智能连接助手", [("运行日志", self.open_logs), ("设置", self.open_settings)])

        center = ctk.CTkFrame(self.root_frame, fg_color="transparent")
        center.pack(fill="both", expand=True)
        card = self._card(center, width=430)
        card.pack(anchor="center", pady=(10, 0), padx=10)
        card.pack_propagate(False)
        card.configure(height=560)

        ctk.CTkLabel(card, text="欢迎回来", font=ctk.CTkFont(size=27, weight="bold"), text_color=TEXT).pack(anchor="w", padx=32, pady=(30, 4))
        ctk.CTkLabel(card, text="使用校园网账号连接网络", font=ctk.CTkFont(size=13), text_color=MUTED).pack(anchor="w", padx=32, pady=(0, 24))

        ctk.CTkLabel(card, text="校园网账号", font=ctk.CTkFont(size=12, weight="bold"), text_color=MUTED).pack(anchor="w", padx=32, pady=(0, 7))
        values = self.local.accounts or [""]
        self.username_var = ctk.StringVar(value=self.local.last_username)
        self.username_box = ctk.CTkComboBox(card, values=values, variable=self.username_var, height=44, corner_radius=12,
                                             border_color=BORDER, fg_color=FIELD, button_color=PANEL_ALT,
                                             command=self._account_selected, font=ctk.CTkFont(size=14))
        self.username_box.pack(fill="x", padx=32, pady=(0, 16))
        self.username_box.bind("<FocusOut>", lambda _e: self._load_password_for_selected())

        ctk.CTkLabel(card, text="登录密码", font=ctk.CTkFont(size=12, weight="bold"), text_color=MUTED).pack(anchor="w", padx=32, pady=(0, 7))
        self.password_var = ctk.StringVar(value=self.local.get_password(self.local.last_username) if self.local.last_username else "")
        password_row = ctk.CTkFrame(card, fg_color="transparent")
        password_row.pack(fill="x", padx=32, pady=(0, 12))
        self.password_entry = ctk.CTkEntry(password_row, textvariable=self.password_var, show="●", height=44, corner_radius=12,
                                            border_color=BORDER, fg_color=FIELD, placeholder_text="请输入校园网密码",
                                            font=ctk.CTkFont(size=14))
        self.password_entry.pack(side="left", fill="x", expand=True)
        self.password_visible = False
        self.password_eye = ctk.CTkButton(
            password_row,
            text="👁",
            command=self.toggle_password_visibility,
            width=44,
            height=44,
            corner_radius=12,
            fg_color=PANEL_ALT,
            hover_color=BORDER,
            text_color=TEXT,
            font=ctk.CTkFont(size=17),
        )
        self.password_eye.pack(side="right", padx=(8, 0))

        opts = ctk.CTkFrame(card, fg_color="transparent")
        opts.pack(fill="x", padx=30, pady=(0, 16))
        self.remember_var = ctk.BooleanVar(value=bool(self.local.get_password(self.local.last_username)) if self.local.last_username else True)
        ctk.CTkCheckBox(opts, text="保存账号密码", variable=self.remember_var, checkbox_width=18, checkbox_height=18,
                        text_color=MUTED, fg_color=BLUE, hover_color=BLUE_HOVER, border_color=BORDER).pack(side="left")
        ctk.CTkButton(opts, text="管理账号", width=80, height=26, fg_color="transparent", hover_color=PANEL_ALT,
                      text_color=MUTED, command=self.manage_accounts).pack(side="right")

        self.accounts_panel = ctk.CTkFrame(card, fg_color=PANEL_ALT, corner_radius=12)
        self.accounts_panel_visible = False

        self.login_button = ctk.CTkButton(card, text="登录并连接  →", command=self.login_from_form, height=48,
                                          corner_radius=14, fg_color=BLUE, hover_color=BLUE_HOVER,
                                          font=ctk.CTkFont(size=15, weight="bold"))
        self.login_button.pack(fill="x", padx=32, pady=(0, 14))
        ctk.CTkLabel(card, text="密码安全保存在 Windows 凭据管理器", font=ctk.CTkFont(size=11), text_color=MUTED).pack(pady=(0, 24))
        self.password_entry.bind("<Return>", lambda _e: self.login_from_form())

    def toggle_password_visibility(self) -> None:
        self.password_visible = not self.password_visible
        self.password_entry.configure(show="" if self.password_visible else "●")
        self.password_eye.configure(text="🙈" if self.password_visible else "👁")

    def _account_selected(self, _value: str) -> None:
        self._load_password_for_selected()

    def _load_password_for_selected(self) -> None:
        username = self.username_var.get().strip()
        password = self.local.get_password(username)
        if password:
            self.password_var.set(password)
            self.remember_var.set(True)
        elif username != self.local.last_username:
            self.password_var.set("")
            self.remember_var.set(True)

    def manage_accounts(self) -> None:
        if self.accounts_panel_visible:
            self.accounts_panel.pack_forget()
            self.accounts_panel_visible = False
            return
        self._render_accounts_panel()
        self.accounts_panel.pack(fill="x", padx=32, pady=(0, 12), before=self.login_button)
        self.accounts_panel_visible = True

    def _render_accounts_panel(self) -> None:
        for child in self.accounts_panel.winfo_children():
            child.destroy()
        ctk.CTkLabel(
            self.accounts_panel,
            text="已保存账号",
            font=ctk.CTkFont(size=12, weight="bold"),
            text_color=TEXT,
        ).pack(anchor="w", padx=12, pady=(10, 5))
        if not self.local.accounts:
            ctk.CTkLabel(self.accounts_panel, text="暂无已保存账号", text_color=MUTED).pack(pady=(2, 12))
        for username in list(self.local.accounts):
            row = ctk.CTkFrame(self.accounts_panel, fg_color="transparent")
            row.pack(fill="x", padx=8, pady=(0, 7))
            ctk.CTkLabel(row, text=username, text_color=TEXT).pack(side="left", padx=8)
            ctk.CTkButton(row, text="删除", width=62, height=28, corner_radius=9, fg_color="#4A2632",
                          hover_color="#713140", command=lambda u=username: self._delete_account(u)).pack(side="right")

    def _delete_account(self, username: str) -> None:
        self.local.forget_account(username)
        self.log(f"已删除本机保存的账号：{username}")
        self.username_box.configure(values=self.local.accounts or [""])
        if self.username_var.get().strip() == username:
            self.username_var.set(self.local.last_username)
            self.password_var.set(self.local.get_password(self.local.last_username))
        self._render_accounts_panel()

    def _try_auto_login(self) -> None:
        self.auto_login_after_id = None
        if not self.local.settings.get("auto_login"):
            return
        username = self.local.last_username
        password = self.local.get_password(username) if username else ""
        if username and password:
            self.username_var.set(username)
            self.password_var.set(password)
            self.current_username = username
            self.current_password = password
            self.log("检测到已启用启动自动登录，正在尝试连接。")
            self._start_authentication(username, password, remember=True, button=self.login_button, silent=True)
        else:
            self.log("已启用启动自动登录，但没有可用的已保存账号密码。")

    def login_from_form(self) -> None:
        if self.busy:
            return
        username = self.username_var.get().strip()
        password = self.password_var.get()
        if not username or not password:
            messagebox.showwarning("信息不完整", "请输入校园网账号和密码。", parent=self)
            return
        self._start_authentication(username, password, bool(self.remember_var.get()), self.login_button, silent=False)

    def _start_authentication(self, username: str, password: str, remember: bool, button: ctk.CTkButton | None, silent: bool) -> None:
        if self.busy:
            return
        self._set_busy(True, button)
        self.log(f"开始为账号 {username} 发起校园网认证。")

        def work() -> None:
            try:
                result = self.auth.authenticate(username, password, self.local.settings, self.log)
                self.after(0, lambda: self._authentication_succeeded(username, password, remember, result, button))
            except Exception as exc:
                self.after(0, lambda err=exc: self._authentication_failed(err, button, silent))

        threading.Thread(target=work, daemon=True).start()

    def _authentication_succeeded(self, username: str, password: str, remember: bool, result: Any, button: ctk.CTkButton | None) -> None:
        login_view = button is self.login_button
        if login_view:
            self._set_busy(False, button, normal_text="登录并连接  →")
        else:
            self._set_busy(False, button, normal_text="⚡")
        self.local.remember_account(username, password, remember)
        self.current_username = username
        self.current_password = password
        self.session = result
        self.connected = True
        self.log(f"账号 {username} 认证成功。")
        if login_view:
            self.show_dashboard(message="校园网已连接")
        else:
            self._refresh_dashboard_status("校园网已重新连接")
        self._schedule_monitor()

    def _authentication_failed(self, error: Exception, button: ctk.CTkButton | None, silent: bool) -> None:
        if button is self.login_button:
            self._set_busy(False, button, normal_text="登录并连接  →")
        elif button is not None:
            self._set_busy(False, button, normal_text="⚡")
            self._refresh_dashboard_status("连接失败，点击闪电按钮重试")
        self.log(f"认证失败：{error}")
        if not silent:
            messagebox.showerror("连接失败", str(error), parent=self)
        elif self.local.settings.get("keep_connected") and self.current_username and self.current_password:
            delay = int(self.local.settings.get("check_interval", 7)) * 1000
            self.after(delay, self._retry_reconnect)

    # ---------- Dashboard ----------
    def show_dashboard(self, message: str | None = None) -> None:
        self.current_view = "dashboard"
        self.log_textbox = None
        self._clear_view()
        self._header("校园网连接状态", [
            ("运行日志", self.open_logs),
            ("设置", self.open_settings),
            ("切换账号", self.switch_account),
        ])
        outer = ctk.CTkFrame(self.root_frame, fg_color="transparent")
        outer.pack(fill="both", expand=True)
        card = self._card(outer)
        card.pack(fill="both", expand=True, padx=50, pady=(0, 6))

        top = ctk.CTkFrame(card, fg_color="transparent")
        top.pack(fill="x", padx=30, pady=(24, 0))
        ctk.CTkLabel(top, text="连接控制", font=ctk.CTkFont(size=14, weight="bold"), text_color=TEXT).pack(side="left")
        self.status_pill = ctk.CTkLabel(top, text="● 已连接" if self.connected else "● 已断开", width=94, height=29,
                                        corner_radius=14, fg_color="#123C36" if self.connected else "#3D2630",
                                        text_color=GREEN if self.connected else RED, font=ctk.CTkFont(size=12, weight="bold"))
        self.status_pill.pack(side="right")

        center = ctk.CTkFrame(card, fg_color="transparent")
        center.pack(fill="both", expand=True, pady=(6, 0))
        self.power_button = ctk.CTkButton(center, text="⚡", command=self.toggle_connection, width=190, height=190,
                                          corner_radius=100, fg_color=GREEN if self.connected else "#28364D",
                                          hover_color="#1BAA79" if self.connected else "#354760",
                                          text_color="#071B18" if self.connected else "#D8E2F2",
                                          font=ctk.CTkFont(size=60, weight="bold"), border_width=8,
                                          border_color="#1B8C6A" if self.connected else "#354760")
        self.power_button.pack(pady=(18, 18))
        self.status_title = ctk.CTkLabel(center, text="已连接校园网" if self.connected else "连接已断开",
                                         font=ctk.CTkFont(size=25, weight="bold"), text_color=TEXT)
        self.status_title.pack(pady=(0, 6))
        default_detail = f"当前账号：{self.current_username}" if self.connected else "点击中央按钮重新连接校园网"
        self.status_detail = ctk.CTkLabel(center, text=message or default_detail, font=ctk.CTkFont(size=13), text_color=MUTED)
        self.status_detail.pack()

        bottom = ctk.CTkFrame(card, fg_color=PANEL_ALT, corner_radius=15)
        bottom.pack(fill="x", padx=24, pady=(0, 24))
        ctk.CTkLabel(bottom, text="当前账号", font=ctk.CTkFont(size=11), text_color=MUTED).pack(anchor="w", padx=16, pady=(12, 2))
        ctk.CTkLabel(bottom, text=self.current_username or "未登录", font=ctk.CTkFont(size=14, weight="bold"), text_color=TEXT).pack(anchor="w", padx=16, pady=(0, 12))

        foot = ctk.CTkFrame(self.root_frame, fg_color="transparent")
        foot.pack(fill="x", pady=(14, 0))
        keep = "开启" if self.local.settings.get("keep_connected") else "关闭"
        auto = "开启" if self.local.settings.get("auto_login") else "关闭"
        ctk.CTkLabel(foot, text=f"始终保持连接：{keep}    ·    启动自动登录：{auto}    ·    状态检查：每 {self.local.settings['check_interval']} 秒",
                      font=ctk.CTkFont(size=11), text_color=MUTED).pack()

    def _refresh_dashboard_status(self, message: str | None = None) -> None:
        if not hasattr(self, "power_button") or not self.power_button.winfo_exists():
            return
        if self.connected:
            self.status_pill.configure(text="● 已连接", fg_color="#123C36", text_color=GREEN)
            self.power_button.configure(fg_color=GREEN, hover_color="#1BAA79", text_color="#071B18", border_color="#1B8C6A")
            self.status_title.configure(text="已连接校园网")
            self.status_detail.configure(text=message or f"当前账号：{self.current_username}")
        else:
            self.status_pill.configure(text="● 已断开", fg_color="#3D2630", text_color=RED)
            self.power_button.configure(fg_color="#28364D", hover_color="#354760", text_color="#D8E2F2", border_color="#354760")
            self.status_title.configure(text="连接已断开")
            self.status_detail.configure(text=message or "点击中央按钮重新连接校园网")

    def toggle_connection(self) -> None:
        if self.busy:
            return
        if self.connected:
            self._disconnect(manual=True)
        else:
            if not self.current_username or not self.current_password:
                self.show_login_view()
                return
            self._start_authentication(self.current_username, self.current_password,
                                       bool(self.local.get_password(self.current_username)),
                                       self.power_button, silent=False)

    def _disconnect(self, manual: bool) -> None:
        self._set_busy(True, getattr(self, "power_button", None), busy_text="…", normal_text="⚡")
        session = self.session
        self.log("正在请求下线当前校园网会话。")

        def work() -> None:
            error: Exception | None = None
            try:
                self.auth.disconnect(session, self.local.settings, self.log)
            except Exception as exc:
                error = exc
            self.after(0, lambda: self._disconnect_finished(error, manual))

        threading.Thread(target=work, daemon=True).start()

    def _disconnect_finished(self, error: Exception | None, manual: bool) -> None:
        self._set_busy(False, getattr(self, "power_button", None), busy_text="…", normal_text="⚡")
        self.connected = False
        self.session = None
        self._cancel_monitor()
        if error:
            self.log(f"下线请求返回错误：{error}")
        else:
            self.log("已结束当前会话。")
        self._refresh_dashboard_status("已手动断开连接" if manual else "连接已断开")

    def switch_account(self) -> None:
        if self.connected:
            if not messagebox.askyesno("切换账号", "切换账号前会尝试下线当前会话，是否继续？", parent=self):
                return
            self._disconnect_then_login_view()
        else:
            self.show_login_view()

    def _disconnect_then_login_view(self) -> None:
        session = self.session
        self.log("切换账号：正在结束当前会话。")

        def work() -> None:
            try:
                self.auth.disconnect(session, self.local.settings, self.log)
            except Exception as exc:
                self.log(f"切换账号时下线失败：{exc}")
            self.after(0, self._finish_switch_account)

        threading.Thread(target=work, daemon=True).start()

    def _finish_switch_account(self) -> None:
        self.connected = False
        self.session = None
        self._cancel_monitor()
        self.show_login_view()

    # ---------- Monitoring and reconnect ----------
    def _cancel_monitor(self) -> None:
        if self.monitor_after_id is not None:
            try:
                self.after_cancel(self.monitor_after_id)
            except Exception:
                pass
            self.monitor_after_id = None

    def _schedule_monitor(self) -> None:
        self._cancel_monitor()
        seconds = int(self.local.settings.get("check_interval", 7))
        self.monitor_after_id = self.after(seconds * 1000, self._check_status)

    def _check_status(self) -> None:
        self.monitor_after_id = None
        if not self.connected or self.session is None or self.busy:
            if self.connected:
                self._schedule_monitor()
            return
        session = self.session

        def work() -> None:
            try:
                result = self.auth.check_status(session, self.local.settings, self.log)
                self.after(0, lambda: self._status_check_finished(result))
            except Exception as exc:
                self.log(f"状态检查暂时失败：{exc}")
                self.after(0, self._schedule_monitor)

        threading.Thread(target=work, daemon=True).start()

    def _status_check_finished(self, result: Any) -> None:
        if not isinstance(result, dict):
            self.log("状态检查结果格式不正确，稍后重试。")
            self._schedule_monitor()
            return
        if result.get("online"):
            self.log("状态检查：校园网在线。")
            self._schedule_monitor()
            return

        reason = str(result.get("reason", "offline")).lower()
        self.connected = False
        self.session = None
        self._refresh_dashboard_status("已在其他设备登录，已下线" if reason in ("kicked", "other_device", "replaced") else "检测到连接已断开")
        self.log("检测到当前会话已离线。")
        if reason in ("kicked", "other_device", "replaced"):
            self.log("离线原因：账号可能已在其他设备登录，当前设备被下线。")
        if self.local.settings.get("keep_connected"):
            self.log("已启用始终保持连接，正在后台尝试重新认证。")
            self._start_authentication(self.current_username, self.current_password,
                                       bool(self.local.get_password(self.current_username)),
                                       getattr(self, "power_button", None), silent=True)
        else:
            if reason in ("kicked", "other_device", "replaced"):
                messagebox.showwarning(
                    "连接已断开",
                    "账号已在其他设备登录，当前设备已下线。",
                    parent=self,
                )
            self._schedule_monitor_when_disconnected()

    def _schedule_monitor_when_disconnected(self) -> None:
        # Without keep-connected, do not repeatedly authenticate. The user can
        # reconnect manually with the central button.
        pass

    def _retry_reconnect(self) -> None:
        if self.connected or self.busy or not self.local.settings.get("keep_connected"):
            return
        if self.current_username and self.current_password:
            self.log("后台重连重试。")
            self._start_authentication(
                self.current_username,
                self.current_password,
                bool(self.local.get_password(self.current_username)),
                getattr(self, "power_button", None),
                silent=True,
            )

    # ---------- Settings and logs ----------
    def open_settings(self) -> None:
        return_view = self.current_view
        win = ctk.CTkToplevel(self)
        win.title("设置")
        win.geometry("500x510")
        win.configure(fg_color=BG)
        win.transient(self)
        win.grab_set()
        ctk.CTkLabel(win, text="连接设置", font=ctk.CTkFont(size=23, weight="bold"), text_color=TEXT).pack(anchor="w", padx=26, pady=(24, 4))
        ctk.CTkLabel(win, text="配置自动连接与状态检测行为", font=ctk.CTkFont(size=12), text_color=MUTED).pack(anchor="w", padx=26, pady=(0, 18))
        body = self._card(win)
        body.pack(fill="x", padx=22, pady=(0, 18))

        keep_var = ctk.BooleanVar(value=bool(self.local.settings.get("keep_connected")))
        auto_var = ctk.BooleanVar(value=bool(self.local.settings.get("auto_login")))
        self._setting_row(body, "始终保持连接", "掉线后在后台自动尝试重新连接", keep_var)
        self._setting_row(body, "自动登录模式", "启动时使用最近一次保存的账号密码", auto_var)

        theme_frame = ctk.CTkFrame(body, fg_color="transparent")
        theme_frame.pack(fill="x", padx=18, pady=(16, 0))
        theme_labels = ctk.CTkFrame(theme_frame, fg_color="transparent")
        theme_labels.pack(side="left", fill="x", expand=True)
        ctk.CTkLabel(theme_labels, text="界面主题", font=ctk.CTkFont(size=13, weight="bold"), text_color=TEXT).pack(anchor="w")
        ctk.CTkLabel(theme_labels, text="默认跟随 Windows，也可手动选择", font=ctk.CTkFont(size=11), text_color=MUTED).pack(anchor="w", pady=(3, 0))
        theme_var = ctk.StringVar(value=THEME_LABELS[str(self.local.settings.get("theme", "system"))])
        ctk.CTkComboBox(
            theme_frame,
            values=list(THEME_VALUES),
            variable=theme_var,
            width=140,
            height=34,
            corner_radius=10,
            fg_color=FIELD,
            border_color=BORDER,
        ).pack(side="right")

        interval_frame = ctk.CTkFrame(body, fg_color="transparent")
        interval_frame.pack(fill="x", padx=18, pady=(10, 18))
        ctk.CTkLabel(interval_frame, text="状态检查间隔", font=ctk.CTkFont(size=13, weight="bold"), text_color=TEXT).pack(side="left")
        interval_var = ctk.StringVar(value=str(self.local.settings.get("check_interval", 7)))
        ctk.CTkComboBox(interval_frame, values=["5", "7", "10"], variable=interval_var, width=100, height=34,
                        corner_radius=10, fg_color=FIELD, border_color=BORDER).pack(side="right")

        def save() -> None:
            interval = int(interval_var.get())
            theme = THEME_VALUES.get(theme_var.get(), "system")
            self.local.save_settings({
                "keep_connected": bool(keep_var.get()),
                "auto_login": bool(auto_var.get()),
                "check_interval": interval,
                "theme": theme,
            })
            ctk.set_appearance_mode(theme)
            self.log(f"设置已保存：始终保持连接={'开启' if keep_var.get() else '关闭'}，自动登录={'开启' if auto_var.get() else '关闭'}，检查间隔={interval} 秒，主题={THEME_LABELS[theme]}。")
            win.destroy()
            if self.connected:
                self._schedule_monitor()
                self.show_dashboard("设置已保存")
            elif return_view == "dashboard":
                self.show_dashboard("设置已保存；当前未连接")
            else:
                self.show_login_view()

        ctk.CTkButton(win, text="保存设置", command=save, height=44, corner_radius=13, fg_color=BLUE,
                      hover_color=BLUE_HOVER, font=ctk.CTkFont(size=14, weight="bold")).pack(fill="x", padx=22, pady=(0, 22))

    def _setting_row(self, parent: Any, title: str, description: str, variable: ctk.BooleanVar) -> None:
        row = ctk.CTkFrame(parent, fg_color="transparent")
        row.pack(fill="x", padx=18, pady=(16, 0))
        labels = ctk.CTkFrame(row, fg_color="transparent")
        labels.pack(side="left", fill="x", expand=True)
        ctk.CTkLabel(labels, text=title, font=ctk.CTkFont(size=13, weight="bold"), text_color=TEXT).pack(anchor="w")
        ctk.CTkLabel(labels, text=description, font=ctk.CTkFont(size=11), text_color=MUTED).pack(anchor="w", pady=(3, 0))
        ctk.CTkSwitch(row, text="", variable=variable, width=45, progress_color=BLUE).pack(side="right", padx=(8, 0))

    def open_logs(self) -> None:
        self.logs_return_view = "dashboard" if self.current_view == "dashboard" else "login"
        self.current_view = "logs"
        self._clear_view()
        self._header("界面内实时运行日志", [("返回", self._close_logs_view)])
        card = self._card(self.root_frame)
        card.pack(fill="both", expand=True, padx=30, pady=(0, 8))
        top = ctk.CTkFrame(card, fg_color="transparent")
        top.pack(fill="x", padx=20, pady=(18, 10))
        ctk.CTkLabel(top, text="运行日志", font=ctk.CTkFont(size=20, weight="bold"), text_color=TEXT).pack(side="left")

        def clear_logs() -> None:
            self.logs.clear()
            if self.log_textbox is not None and self.log_textbox.winfo_exists():
                self.log_textbox.configure(state="normal")
                self.log_textbox.delete("1.0", "end")
                self.log_textbox.configure(state="disabled")

        ctk.CTkButton(top, text="清空", width=70, height=30, corner_radius=9, fg_color=PANEL_ALT,
                      hover_color=BORDER, command=clear_logs).pack(side="right")
        self.log_textbox = ctk.CTkTextbox(card, corner_radius=14, fg_color=("#F7F9FC", "#080F1C"), text_color=TEXT,
                                          font=ctk.CTkFont(family="Consolas", size=12), wrap="word")
        self.log_textbox.pack(fill="both", expand=True, padx=20, pady=(0, 20))
        self.log_textbox.insert("end", "\n".join(self.logs) + ("\n" if self.logs else ""))
        self.log_textbox.see("end")
        self.log_textbox.configure(state="disabled")

    def _close_logs_view(self) -> None:
        self.log_textbox = None
        if getattr(self, "logs_return_view", "login") == "dashboard":
            self.show_dashboard()
        else:
            self.show_login_view()

    def on_close(self) -> None:
        self._cancel_monitor()
        for after_id in (self.ui_queue_after_id, self.auto_login_after_id):
            if after_id is not None:
                try:
                    self.after_cancel(after_id)
                except Exception:
                    pass
        self.ui_queue_after_id = None
        self.auto_login_after_id = None
        self.current_password = ""
        self.destroy()


def main() -> None:
    app = CQUSTWifiApp()
    app.mainloop()


if __name__ == "__main__":
    main()
