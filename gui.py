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
from PIL import Image
from widgets import icon, AnimatedSwitch, PulsePower

from auth_adapter import AuthAdapter, AuthenticationError, ContextRequired, AlreadyOnline, ServiceRequired, ChallengeRequired, trusted_url, lan_ip

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
    "service": "",
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
            if password:
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
    def __init__(self, auth=None) -> None:
        super().__init__()
        self.local = LocalConfig()
        ctk.set_appearance_mode(str(self.local.settings.get("theme", "system")))
        ctk.set_default_color_theme("blue")
        self.title(APP_NAME)
        self.geometry("1040x660")
        self.minsize(760, 640)
        app_icon = Path(__file__).parent / "assets" / "wifi.ico"
        if app_icon.exists():
            self.after(250, lambda: self.iconbitmap(str(app_icon)) if self.winfo_exists() else None)
        self.configure(fg_color=BG)

        self.auth = auth if auth is not None else AuthAdapter()
        self.username_var = ctk.StringVar(value=self.local.last_username or self.auth.original_username)
        self.password_var = ctk.StringVar(value=self.local.get_password(self.username_var.get()))
        self.remember_var = ctk.BooleanVar(value=True)
        self.overlay = None
        self.reconnect_after_id = None
        self.keepalive_after_id = None
        self.keepalive_inflight = False
        self.last_lan_ip = lan_ip('172.28.228.2')
        self.closed = False
        self.generation = 0
        self.login_page_url = ''
        self.challenge_context = None
        self.captcha_value = ''
        self.metrics_after_id = None
        self.connected_since = None
        self.elapsed_seconds = 0
        self.toast = None
        self.toast_after_id = None
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
        if self.auth.config_path:
            self.log("已读取本机原 config.py；未复制或显示其中的密码。")
        self.auto_login_after_id = self.after(500, self._try_auto_login)
        self.metrics_after_id = self.after(1000, self._update_metrics)

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
        self._dismiss_toast()
        self.close_overlay()
        for child in self.root_frame.winfo_children():
            child.destroy()

    def _header(self, subtitle: str, right_widgets: list[tuple[str, Callable[[], None]]] | None = None) -> None:
        header = ctk.CTkFrame(self.root_frame, fg_color="transparent")
        header.pack(fill="x", pady=(0, 22))
        brand = ctk.CTkFrame(header, fg_color="transparent")
        brand.pack(side="left")
        icon = ctk.CTkFrame(brand, width=46, height=46, corner_radius=0, fg_color='transparent')
        icon.pack(side="left", padx=(0, 12))
        icon.pack_propagate(False)
        from widgets import icon as make_icon
        supplied = Image.open(Path(__file__).parent / 'assets' / 'wifi_reference.jpg')
        self.brand_image = ctk.CTkImage(supplied, size=(46, 45))
        ctk.CTkLabel(icon, text="", image=self.brand_image).pack(expand=True)
        labels = ctk.CTkFrame(brand, fg_color="transparent")
        labels.pack(side="left")
        ctk.CTkLabel(labels, text=APP_NAME, font=ctk.CTkFont(size=22, weight="bold"), text_color=TEXT).pack(anchor="w")
        ctk.CTkLabel(labels, text=subtitle, font=ctk.CTkFont(size=12), text_color=MUTED).pack(anchor="w", pady=(2, 0))
        if right_widgets:
            actions = ctk.CTkFrame(header, fg_color="transparent")
            actions.pack(side="right")
            for text, callback in right_widgets:
                ctk.CTkButton(actions, text=text, command=callback, width=72, height=34, corner_radius=11,
                              fg_color=PANEL_ALT, hover_color=BORDER, text_color=TEXT).pack(side="left", padx=(8, 0))
            self.theme_button = ctk.CTkButton(actions, text='', image=make_icon(
                'sun' if ctk.get_appearance_mode() == 'Dark' else 'moon', 21), width=36, height=34,
                corner_radius=17, fg_color=PANEL_ALT, hover_color=BORDER,
                command=self.toggle_theme)
            self.theme_button.pack(side='left', padx=(6, 0))

    def toggle_theme(self):
        self.set_theme('light' if ctk.get_appearance_mode() == 'Dark' else 'dark')

    def set_theme(self, mode):
        self.local.save_settings({'theme': mode})
        ctk.set_appearance_mode(mode)
        from widgets import icon as make_icon
        if hasattr(self, 'theme_button') and self.theme_button.winfo_exists():
            self.theme_button.configure(image=make_icon('sun' if ctk.get_appearance_mode() == 'Dark' else 'moon', 21))
        self.show_toast('主题：'+THEME_LABELS[mode])

    def _dismiss_toast(self):
        if self.toast_after_id:
            self.after_cancel(self.toast_after_id)
            self.toast_after_id = None
        if self.toast is not None:
            self.toast.destroy()
            self.toast = None

    def show_toast(self, message):
        self._dismiss_toast()
        self.toast = ctk.CTkLabel(self.root_frame, text=message, height=38, corner_radius=19,
                                  fg_color=('#173D36', '#D1FAE5'), text_color=('#FFFFFF', '#113B2B'))
        self.toast.lift()
        start = time.monotonic()
        def animate():
            if self.toast is None:
                return
            progress = min(1, (time.monotonic()-start)/.18)
            self.toast.place(relx=.5, rely=1, y=-18-int(14*progress), anchor='s')
            self.toast_after_id = self.after(16, animate) if progress < 1 else self.after(2000, self._dismiss_toast)
        animate()

    def _update_metrics(self):
        if self.connected and self.connected_since is not None:
            self.elapsed_seconds = max(0, int(time.monotonic()-self.connected_since))
        if hasattr(self, 'duration_label') and self.duration_label.winfo_exists():
            hours, rest = divmod(self.elapsed_seconds, 3600)
            minutes, seconds = divmod(rest, 60)
            self.duration_label.configure(text=f'{hours:02d}:{minutes:02d}:{seconds:02d}')
            self.last_lan_ip = getattr(self.session, 'lan_ip', '') or self.last_lan_ip
            self.ip_label.configure(text=self.last_lan_ip or '—')
        if not self.closed:
            self.metrics_after_id = self.after(1000, self._update_metrics)

    def _card(self, parent: Any, **kwargs: Any) -> ctk.CTkFrame:
        return ctk.CTkFrame(parent, fg_color=PANEL, corner_radius=22, border_width=1, border_color=BORDER, **kwargs)

    def _set_busy(self, value: bool, button: ctk.CTkButton | None = None, busy_text: str = "正在处理…", normal_text: str = "登录") -> None:
        self.busy = value
        if button is not None and button.winfo_exists():
            if isinstance(button, PulsePower):
                button.set_busy(value)
                return
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
        card = self._card(center, width=520)
        card.pack(anchor="center", pady=(10, 0), padx=10)
        card.pack_propagate(False)
        card.configure(height=500)

        title_row = ctk.CTkFrame(card, fg_color="transparent")
        title_row.pack(fill="x", padx=32, pady=(30, 4))
        ctk.CTkLabel(title_row, text="欢迎回来", font=ctk.CTkFont(size=27, weight="bold"), text_color=TEXT).pack(side="left")
        logo_path = Path(__file__).parent / "assets" / "school_logo.png"
        if logo_path.exists():
            self.school_logo = ctk.CTkImage(Image.open(logo_path), size=(180, 37))
            ctk.CTkLabel(title_row, text="", image=self.school_logo, fg_color="transparent",
                         corner_radius=5).pack(side="right")
        ctk.CTkLabel(card, text="使用校园网账号连接网络", font=ctk.CTkFont(size=13), text_color=MUTED).pack(anchor="w", padx=32, pady=(0, 24))

        ctk.CTkLabel(card, text="校园网账号", font=ctk.CTkFont(size=12, weight="bold"), text_color=MUTED).pack(anchor="w", padx=32, pady=(0, 7))
        username_row = ctk.CTkFrame(card, fg_color="transparent")
        username_row.pack(fill="x", padx=32, pady=(0, 16))
        self.username_box = ctk.CTkEntry(username_row, textvariable=self.username_var, height=44, corner_radius=12,
                                       border_color=BORDER, fg_color=FIELD, font=ctk.CTkFont(size=14))
        self.username_box.pack(side="left", fill="x", expand=True)
        self.account_picker = ctk.CTkButton(username_row, text="", image=icon("chevron"), width=44, height=44,
                    corner_radius=12, fg_color=PANEL_ALT, hover_color=BORDER, command=self.manage_accounts)
        self.account_picker.pack(side="right", padx=(8, 0))

        ctk.CTkLabel(card, text="登录密码", font=ctk.CTkFont(size=12, weight="bold"), text_color=MUTED).pack(anchor="w", padx=32, pady=(0, 7))
        password_row = ctk.CTkFrame(card, fg_color="transparent")
        password_row.pack(fill="x", padx=32, pady=(0, 12))
        self.password_entry = ctk.CTkEntry(password_row, textvariable=self.password_var, show="●", height=44, corner_radius=12,
                                            border_color=BORDER, fg_color=FIELD, placeholder_text="请输入校园网密码",
                                            font=ctk.CTkFont(size=14))
        self.password_entry.pack(side="left", fill="x", expand=True)
        self.password_visible = False
        self.password_eye = ctk.CTkButton(
            password_row,
            text="", image=icon("eye"),
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
        ctk.CTkCheckBox(opts, text="保存账号密码", variable=self.remember_var, checkbox_width=18, checkbox_height=18,
                        text_color=MUTED, fg_color=BLUE, hover_color=BLUE_HOVER, border_color=BORDER).pack(side="left")
        ctk.CTkButton(opts, text="管理账号", width=80, height=26, fg_color="transparent", hover_color=PANEL_ALT,
                      text_color=MUTED, command=self.manage_accounts).pack(side="right")

        self.accounts_panel = ctk.CTkScrollableFrame(card, fg_color=PANEL_ALT, corner_radius=12, height=90)
        self.accounts_panel_visible = False

        self.login_button = ctk.CTkButton(card, text="登录", command=self.login_from_form, height=48,
                                          corner_radius=14, fg_color=BLUE, hover_color=BLUE_HOVER,
                                          font=ctk.CTkFont(size=15, weight="bold"))
        self.login_button.pack(fill="x", padx=32, pady=(0, 14))
        self.form_hint = ctk.CTkLabel(card, text="只需学号和密码 · 自动获取当前网络认证参数",
                                    font=ctk.CTkFont(size=11), text_color=MUTED, wraplength=450)
        self.form_hint.pack(pady=(0, 12))
        self.password_entry.bind("<Return>", lambda _e: self.login_from_form())
        ctk.CTkButton(card, text='认证帮助 / 粘贴登录网址', width=190, height=26, fg_color='transparent',
                      text_color=MUTED, hover_color=PANEL_ALT, command=self.open_auth_help).pack(pady=(0,8))

    def toggle_password_visibility(self) -> None:
        self.password_visible = not self.password_visible
        self.password_entry.configure(show="" if self.password_visible else "●")
        self.password_eye.configure(image=icon("eye_off" if self.password_visible else "eye"))

    def _account_selected(self, _value: str) -> None:
        self.username_var.set(_value)
        self.password_var.set(self.local.get_password(_value))
        self._load_password_for_selected()
        self.manage_accounts()

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
            self.accounts_panel.place_forget()
            self.accounts_panel_visible = False
            return
        self._render_accounts_panel()
        # Account list overlays the lower card without changing the form geometry.
        self.accounts_panel.place(relx=.5, rely=.42, anchor='n', relwidth=.87)
        self.accounts_panel.lift()
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
        accounts = list(dict.fromkeys(self.local.accounts + ([self.auth.original_username] if self.auth.original_username else [])))
        if not accounts:
            ctk.CTkLabel(self.accounts_panel, text="暂无已保存账号", text_color=MUTED).pack(pady=(2, 12))
        for username in accounts:
            row = ctk.CTkFrame(self.accounts_panel, fg_color="transparent")
            row.pack(fill="x", padx=8, pady=(0, 7))
            ctk.CTkButton(row, text=username, text_color=TEXT, fg_color="transparent", hover_color=BORDER,
                          command=lambda u=username: self._account_selected(u)).pack(side="left", padx=8)
            if username in self.local.accounts:
                ctk.CTkButton(row, text="删除", width=62, height=28, corner_radius=9, fg_color="#4A2632",
                              hover_color="#713140", command=lambda u=username: self._delete_account(u)).pack(side="right")

    def _delete_account(self, username: str) -> None:
        self.local.forget_account(username)
        self.log(f"已删除本机保存的账号：{username}")
        if self.username_var.get().strip() == username:
            self.username_var.set(self.local.last_username)
            self.password_var.set(self.local.get_password(self.local.last_username))
        self._render_accounts_panel()

    def _try_auto_login(self) -> None:
        self.auto_login_after_id = None
        if not self.local.settings.get("auto_login"):
            return
        username = self.local.last_username or self.auth.original_username
        password = self.local.get_password(username) if username else ""
        if username and (password or self.auth.has_original_password(username)):
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
        if not username or not (password or self.auth.has_original_password(username)):
            messagebox.showwarning("信息不完整", "请输入校园网账号和密码。", parent=self)
            return
        self._start_authentication(username, password, bool(self.remember_var.get()), self.login_button, silent=False)

    def _start_authentication(self, username: str, password: str, remember: bool, button: ctk.CTkButton | None, silent: bool) -> None:
        if self.busy:
            return
        self._set_busy(True, button)
        self.current_username, self.current_password = username, password
        generation = self.generation
        settings = dict(self.local.settings)
        settings.update(login_page_url=self.login_page_url, _challenge_context=self.challenge_context,
                        captcha=self.captcha_value)
        self.log("正在向校园网服务器发起认证。")

        def work() -> None:
            try:
                result = self.auth.authenticate(username, password, settings, self.log)
                self._ui_queue.put((self._auth_result, (generation, username, password, remember, result, button, None, silent)))
            except Exception as exc:
                self._ui_queue.put((self._auth_result, (generation, username, password, remember, None, button, exc, silent)))

        threading.Thread(target=work, daemon=True).start()

    def _auth_result(self, generation, username, password, remember, result, button, error, silent):
        if self.closed or generation != self.generation:
            if result:
                result.client.close()
            return
        if error:
            self._authentication_failed(error, button, silent)
        else:
            self._authentication_succeeded(username, password, remember, result, button)

    def _authentication_succeeded(self, username: str, password: str, remember: bool, result: Any, button: ctk.CTkButton | None) -> None:
        login_view = button is self.login_button
        if login_view:
            self._set_busy(False, button, normal_text="登录")
        else:
            self._set_busy(False, button, normal_text="")
        try:
            self.local.remember_account(username, password, remember)
        except Exception:
            self.log("已连接，但本机凭据保存失败；本次连接不受影响。")
        self.current_username = username
        self.current_password = password
        self.session = result
        self.connected = True
        self.connected_since = time.monotonic()
        self.elapsed_seconds = 0
        self.challenge_context, self.captcha_value = None, ''
        self.login_page_url = ''
        if getattr(result, 'context', None):
            self.local.save_settings({'service': result.context.service})
        self.log("认证成功，服务器已确认在线。")
        if login_view:
            self.show_dashboard(message="校园网已连接")
        else:
            self._refresh_dashboard_status("校园网已重新连接")
        self._schedule_monitor()

    def _authentication_failed(self, error: Exception, button: ctk.CTkButton | None, silent: bool) -> None:
        if button is self.login_button:
            self._set_busy(False, button, normal_text="登录")
        elif button is not None:
            self._set_busy(False, button, normal_text="")
            self._refresh_dashboard_status("连接失败，点击中央按钮重试")
        else:
            self.busy = False
        if not isinstance(error, AuthenticationError):
            error = AuthenticationError("登录处理出现异常，请查看网络连接后重试。")
        self.log(f"认证失败：{error}")
        if isinstance(error, ChallengeRequired):
            self.challenge_context = error.context
        elif self.challenge_context is not None:
            self.challenge_context = None
            self.captcha_value = ''
        if not silent:
            if isinstance(error, (ContextRequired, ServiceRequired, ChallengeRequired)):
                self.open_auth_help(error)
            else:
                messagebox.showerror("连接失败", str(error), parent=self)
        elif isinstance(error, AlreadyOnline):
            self.show_toast('当前设备已在线；如需更换账号，请点击登录确认切换')
        elif getattr(error, 'retryable', False) and self.local.settings.get("keep_connected") and self.current_username:
            delay = int(self.local.settings.get("check_interval", 7)) * 1000
            self.reconnect_after_id = self.after(delay, self._retry_reconnect)

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
        card.pack(fill="both", expand=True, padx=12, pady=(0, 6))

        top = ctk.CTkFrame(card, fg_color="transparent")
        top.pack(fill="x", padx=30, pady=(24, 0))
        ctk.CTkLabel(top, text="连接控制", font=ctk.CTkFont(size=14, weight="bold"), text_color=TEXT).pack(side="left")
        self.status_pill = ctk.CTkLabel(top, text="● 已连接" if self.connected else "● 已断开", width=94, height=29,
                                        corner_radius=14, fg_color="#123C36" if self.connected else "#3D2630",
                                        text_color=GREEN if self.connected else RED, font=ctk.CTkFont(size=12, weight="bold"))
        self.status_pill.pack(side="right")

        center = ctk.CTkFrame(card, fg_color="transparent")
        center.pack(fill="both", expand=True, padx=3, pady=(6, 0))
        self.power_button = PulsePower(center, self.toggle_connection, size=244, connected=self.connected)
        self.power_button.pack(pady=(0, 2))
        self.status_title = ctk.CTkLabel(center, text="已连接校园网" if self.connected else "连接已断开",
                                         font=ctk.CTkFont(size=25, weight="bold"), text_color=TEXT)
        self.status_title.pack(pady=(0, 6))
        default_detail = f"当前账号：{self.current_username}" if self.connected else "点击中央按钮重新连接校园网"
        self.status_detail = ctk.CTkLabel(center, text=message or default_detail, font=ctk.CTkFont(size=13), text_color=MUTED)
        self.status_detail.pack()

        bottom = ctk.CTkFrame(card, fg_color=PANEL_ALT, corner_radius=15)
        bottom.pack(fill="x", padx=24, pady=(12, 18))
        bottom.grid_columnconfigure((0, 1, 2), weight=1, uniform='metrics')
        for column, title in enumerate(('当前账号', '校园网 / 局域网 IP', '本次已连接')):
            ctk.CTkLabel(bottom, text=title, font=ctk.CTkFont(size=11), text_color=MUTED).grid(row=0, column=column, sticky='w', padx=20, pady=(12,0))
        ctk.CTkLabel(bottom, text=self.current_username or '未登录', text_color=TEXT, font=ctk.CTkFont(size=14, weight='bold')).grid(row=1, column=0, sticky='w', padx=20, pady=(0,12))
        self.ip_label = ctk.CTkLabel(bottom, text=getattr(self.session, 'lan_ip', '') or self.last_lan_ip or '—', text_color=TEXT, font=ctk.CTkFont(size=14, weight='bold'))
        self.ip_label.grid(row=1, column=1, sticky='w', padx=20, pady=(0,12))
        self.duration_label = ctk.CTkLabel(bottom, text='00:00:00', text_color=TEXT, font=ctk.CTkFont(size=14, weight='bold'))
        self.duration_label.grid(row=1, column=2, sticky='w', padx=20, pady=(0,12))

        foot = ctk.CTkFrame(self.root_frame, fg_color="transparent")
        foot.pack(fill="x", pady=(14, 0))
        keep = "开启" if self.local.settings.get("keep_connected") else "关闭"
        auto = "开启" if self.local.settings.get("auto_login") else "关闭"
        self.footer_label = ctk.CTkLabel(foot, text=f"始终保持连接：{keep}    ·    启动自动登录：{auto}    ·    状态检查：每 {self.local.settings['check_interval']} 秒",
                      font=ctk.CTkFont(size=11), text_color=MUTED)
        self.footer_label.pack()

    def _refresh_dashboard_status(self, message: str | None = None) -> None:
        if not hasattr(self, "power_button") or not self.power_button.winfo_exists():
            return
        if self.connected:
            self.status_pill.configure(text="● 已连接", fg_color="#123C36", text_color=GREEN)
            self.power_button.set_status(True)
            self.status_title.configure(text="已连接校园网")
            self.status_detail.configure(text=message or f"当前账号：{self.current_username}")
        else:
            self.status_pill.configure(text="● 已断开", fg_color="#3D2630", text_color=RED)
            self.power_button.set_status(False)
            self.status_title.configure(text="连接已断开")
            self.status_detail.configure(text=message or "点击中央按钮重新连接校园网")

    def toggle_connection(self) -> None:
        if self.busy:
            return
        if self.connected:
            self._disconnect(manual=True)
        else:
            if not self.current_username or not (self.current_password or self.auth.has_original_password(self.current_username)):
                self.show_login_view()
                return
            self._start_authentication(self.current_username, self.current_password,
                                       bool(self.local.get_password(self.current_username)),
                                       self.power_button, silent=False)

    def _disconnect(self, manual: bool) -> None:
        self._cancel_monitor()
        self.generation += 1
        self._set_busy(True, getattr(self, "power_button", None), busy_text="…", normal_text="")
        session = self.session
        self.log("正在请求下线当前校园网会话。")

        def work() -> None:
            error: Exception | None = None
            try:
                self.auth.disconnect(session, self.local.settings, self.log)
            except Exception as exc:
                error = exc
            self._ui_queue.put((self._disconnect_finished, (error, manual)))

        threading.Thread(target=work, daemon=True).start()

    def _disconnect_finished(self, error: Exception | None, manual: bool) -> None:
        self._set_busy(False, getattr(self, "power_button", None), busy_text="…", normal_text="")
        if error:
            self.log("服务器未确认下线，正在继续检查当前会话。")
            self._schedule_monitor()
            self._refresh_dashboard_status("下线未确认，请重试")
            return
        self.connected = False
        self.session = None
        self._cancel_monitor()
        if error:
            self.log(f"下线请求返回错误：{error}")
        else:
            self.log("已结束当前会话。")
        self._refresh_dashboard_status("已手动断开连接" if manual else "连接已断开")

    def switch_account(self) -> None:
        if self.busy:
            return
        if self.connected:
            if not messagebox.askyesno("切换账号", "切换账号前会尝试下线当前会话，是否继续？", parent=self):
                return
            self.generation += 1
            self._cancel_monitor()
            self._disconnect_then_login_view()
        else:
            self.generation += 1
            self._cancel_monitor()
            self.show_login_view()

    def _disconnect_then_login_view(self) -> None:
        self.busy = True
        session = self.session
        self.log("切换账号：正在结束当前会话。")

        def work() -> None:
            try:
                self.auth.disconnect(session, self.local.settings, self.log)
            except Exception as exc:
                self._ui_queue.put((self._disconnect_finished, (exc, True)))
                return
            self._ui_queue.put((self._finish_switch_account, ()))

        threading.Thread(target=work, daemon=True).start()

    def _finish_switch_account(self) -> None:
        self.busy = False
        self.connected = False
        self.session = None
        self._cancel_monitor()
        self.show_login_view()

    # ---------- Monitoring and reconnect ----------
    def _cancel_monitor(self) -> None:
        if self.keepalive_after_id is not None:
            self.after_cancel(self.keepalive_after_id)
            self.keepalive_after_id = None
        if self.reconnect_after_id is not None:
            self.after_cancel(self.reconnect_after_id)
            self.reconnect_after_id = None
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
        self._schedule_keepalive()

    def _schedule_keepalive(self):
        if self.keepalive_after_id is not None:
            self.after_cancel(self.keepalive_after_id)
            self.keepalive_after_id = None
        if self.keepalive_inflight or not self.connected or not getattr(self.session, 'keepalive_interval', 0):
            return
        delay = max(50, int((self.session.next_keepalive-time.monotonic())*1000))
        self.keepalive_after_id = self.after(delay, self._keepalive_tick)

    def _keepalive_tick(self):
        self.keepalive_after_id = None
        if not self.connected or not self.session:
            return
        session, generation = self.session, self.generation
        self.keepalive_inflight = True
        def work():
            try:
                self.auth.keepalive(session)
            except AuthenticationError:
                self.log('保活未确认，将继续检查在线状态。')
                session.next_keepalive = time.monotonic()+max(1, session.keepalive_interval)
            finally:
                self._ui_queue.put((finished, ()))
        def finished():
            self.keepalive_inflight = False
            if generation == self.generation and session is self.session:
                self._schedule_keepalive()
        threading.Thread(target=work, daemon=True).start()

    def _check_status(self) -> None:
        self.monitor_after_id = None
        if not self.connected or self.session is None or self.busy:
            if self.connected:
                self._schedule_monitor()
            return
        session = self.session
        generation = self.generation

        def work() -> None:
            try:
                result = self.auth.check_status(session, self.local.settings, self.log)
                self._ui_queue.put((self._status_result, (generation, session, result)))
            except Exception as exc:
                self.log(f"状态检查暂时失败：{exc}")
                self._ui_queue.put((self._status_result, (generation, session, None)))

        threading.Thread(target=work, daemon=True).start()

    def _status_result(self, generation, session, result):
        if generation != self.generation or session is not self.session or self.closed:
            return
        if result is None:
            self._schedule_monitor()
        else:
            self._status_check_finished(result)

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
        self._cancel_monitor()
        self.connected = False
        if self.session:
            self.session.client.close()
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
        self.reconnect_after_id = None
        if self.connected or self.busy or not self.local.settings.get("keep_connected"):
            return
        if self.current_username and (self.current_password or self.auth.has_original_password(self.current_username)):
            self.log("后台重连重试。")
            self._start_authentication(
                self.current_username,
                self.current_password,
                bool(self.local.get_password(self.current_username)),
                getattr(self, "power_button", None),
                silent=True,
            )

    # ---------- Settings and logs ----------
    def _switch_discovered_session(self, online):
        if self.busy:
            return
        self.busy = True
        generation = self.generation
        self.online_switch_button.configure(state='disabled', text='正在下线…')
        def finish(error):
            if generation != self.generation:
                return
            self.busy = False
            if error:
                self.show_toast('未能确认下线，请稍后重新探测')
                if self.online_switch_button.winfo_exists():
                    self.online_switch_button.configure(state='normal', text='重试下线并继续登录')
                return
            self.login_page_url = ''
            self.captcha_value = ''
            self.close_overlay()
            if self.current_view == 'login':
                self.login_from_form()
            else:
                self._start_authentication(self.current_username, self.current_password,
                    bool(self.local.get_password(self.current_username)), self.power_button, silent=False)
        def work():
            error = None
            try:
                self.auth.disconnect_discovered(online)
                time.sleep(1)  # Give the gateway a moment to release its session.
            except Exception as exc:
                error = True  # Never expose session-bearing request exceptions.
            self._ui_queue.put((finish, (error,)))
        threading.Thread(target=work, daemon=True).start()

    def open_auth_help(self, error=None):
        panel = self._open_overlay('认证帮助')
        explanation = str(error) if error else '自动探测失败时，可粘贴浏览器当前校园认证登录页的完整网址。仅在内存中使用，不记录到日志或配置。'
        ctk.CTkLabel(panel, text=explanation, text_color=MUTED, wraplength=610, justify='left').pack(anchor='w', padx=26, pady=(0,18))
        if isinstance(error, AlreadyOnline):
            ctk.CTkLabel(panel, text='此操作会暂时中断当前设备的校园网连接。账号和密码输入会保留。',
                         text_color=MUTED, wraplength=610).pack(anchor='w', padx=26, pady=12)
            self.online_switch_button = ctk.CTkButton(panel, text='确认下线当前会话并继续登录',
                command=lambda: self._switch_discovered_session(error), height=42, fg_color=BLUE)
            self.online_switch_button.pack(anchor='w', padx=26, pady=12)
            return
        ctk.CTkLabel(panel, text='完整认证网址（包含 ? 后的参数）', text_color=TEXT).pack(anchor='w', padx=26)
        url_var = ctk.StringVar(value=self.login_page_url)
        ctk.CTkEntry(panel, textvariable=url_var, height=40, fg_color=FIELD, border_color=BORDER).pack(fill='x', padx=26, pady=(6,16))
        service_var = ctk.StringVar(value=self.local.settings.get('service', ''))
        if isinstance(error, ServiceRequired):
            ctk.CTkLabel(panel, text='请选择页面实际提供的服务', text_color=TEXT).pack(anchor='w', padx=26)
            self._choice_buttons(panel, error.services, service_var).pack(anchor='w', padx=26, pady=10)
        captcha_var = ctk.StringVar(value='')
        if isinstance(error, ChallengeRequired):
            image_label = ctk.CTkLabel(panel, text='正在加载验证码…', text_color=MUTED)
            image_label.pack(anchor='w', padx=26, pady=8)
            ctk.CTkEntry(panel, textvariable=captcha_var, placeholder_text='请输入验证码', height=38).pack(anchor='w', padx=26, pady=8)
            def load_image():
                try:
                    import io
                    url = error.context.captcha_url
                    if not trusted_url(url):
                        raise ValueError('untrusted')
                    response = error.context.client.get(url, timeout=(5,10), allow_redirects=False)
                    response.raise_for_status()
                    image = Image.open(io.BytesIO(response.content))
                    image.load()
                    self._ui_queue.put((show_image, (image,)))
                except Exception:
                    self._ui_queue.put((show_image, (None,)))
            def show_image(image):
                if not image_label.winfo_exists():
                    return
                if image is None:
                    image_label.configure(text='验证码加载失败，请返回后刷新认证网址。')
                else:
                    preview = ctk.CTkImage(image, size=(160,60))
                    image_label.configure(text='', image=preview)
            threading.Thread(target=load_image, daemon=True).start()
        def apply():
            candidate = url_var.get().strip()
            if candidate and not trusted_url(candidate):
                self.show_toast('仅接受学校认证域名或 IP 的网址')
                return
            self.login_page_url = candidate
            self.captcha_value = captcha_var.get().strip()
            if service_var.get():
                self.local.save_settings({'service': service_var.get()})
            if not isinstance(error, ChallengeRequired) and self.challenge_context:
                self.challenge_context.client.close()
                self.challenge_context = None
            self.close_overlay()
            self.show_toast('已更新认证信息，请点击登录重试')
        ctk.CTkButton(panel, text='使用这些信息', command=apply, height=40, fg_color=BLUE).pack(anchor='w', padx=26, pady=18)

    def _choice_buttons(self, parent, values, variable):
        row = ctk.CTkFrame(parent, fg_color='transparent')
        buttons = {}
        def choose(value):
            variable.set(value)
            for key, button in buttons.items():
                button.configure(fg_color=BLUE if key == value else PANEL_ALT,
                                 text_color='white' if key == value else TEXT)
        for value in values:
            button = ctk.CTkButton(row, text=value, width=62, height=34, corner_radius=17,
                fg_color=BLUE if variable.get() == value else PANEL_ALT,
                hover_color=BLUE_HOVER, text_color='white' if variable.get() == value else TEXT,
                command=lambda v=value: choose(v))
            button.pack(side='left', padx=4)
            buttons[value] = button
        return row

    def close_overlay(self):
        self.log_textbox = None
        if self.overlay is not None:
            self.overlay.destroy()
            self.overlay = None

    def _open_overlay(self, title):
        self.close_overlay()
        self.overlay = self._card(self.root_frame)
        self.overlay.place(relx=0, rely=0, relwidth=1, relheight=1)
        self.overlay.lift()
        top = ctk.CTkFrame(self.overlay, fg_color="transparent")
        top.pack(fill="x", padx=26, pady=(22, 12))
        ctk.CTkLabel(top, text=title, font=ctk.CTkFont(size=23, weight="bold"), text_color=TEXT).pack(side="left")
        ctk.CTkButton(top, text="返回", width=72, fg_color=PANEL_ALT, text_color=TEXT,
                      hover_color=BORDER, command=self.close_overlay).pack(side="right")
        return self.overlay

    def open_settings(self) -> None:
        win = self._open_overlay("连接设置")
        ctk.CTkLabel(win, text="配置自动连接与状态检测行为", font=ctk.CTkFont(size=12), text_color=MUTED).pack(anchor="w", padx=26, pady=(0, 18))
        body = self._card(win)
        body.pack(fill="x", padx=22, pady=(0, 18))

        keep_var = ctk.BooleanVar(value=bool(self.local.settings.get("keep_connected")))
        auto_var = ctk.BooleanVar(value=bool(self.local.settings.get("auto_login")))
        self._setting_row(body, "始终保持连接", "掉线后在后台自动尝试重新连接", keep_var)
        self._setting_row(body, "自动登录模式", "启动时使用最近一次保存的账号密码", auto_var)

        ctk.CTkButton(body, text='恢复跟随 Windows 主题', command=lambda: self.set_theme('system'),
                      fg_color=PANEL_ALT, text_color=TEXT, height=30).pack(anchor='w', padx=18, pady=(16,0))

        interval_frame = ctk.CTkFrame(body, fg_color="transparent")
        interval_frame.pack(fill="x", padx=18, pady=(10, 18))
        ctk.CTkLabel(interval_frame, text="状态检查间隔", font=ctk.CTkFont(size=13, weight="bold"), text_color=TEXT).pack(side="left")
        interval_var = ctk.StringVar(value=str(self.local.settings.get("check_interval", 7))+' 秒')
        self._choice_buttons(interval_frame, ['5 秒', '7 秒', '10 秒'], interval_var).pack(side='right')

        def save() -> None:
            interval = int(interval_var.get().split()[0])
            theme = self.local.settings['theme']
            self.local.save_settings({
                "keep_connected": bool(keep_var.get()),
                "auto_login": bool(auto_var.get()),
                "check_interval": interval,
                "theme": theme,
            })
            ctk.set_appearance_mode(theme)
            self.log(f"设置已保存：始终保持连接={'开启' if keep_var.get() else '关闭'}，自动登录={'开启' if auto_var.get() else '关闭'}，检查间隔={interval} 秒，主题={THEME_LABELS[theme]}。")
            self.show_toast('设置已保存')
            if self.connected:
                self._schedule_monitor()
            elif not keep_var.get():
                self._cancel_monitor()
            if hasattr(self, "footer_label") and self.footer_label.winfo_exists():
                self.footer_label.configure(text=f"始终保持连接：{'开启' if keep_var.get() else '关闭'}    ·    启动自动登录：{'开启' if auto_var.get() else '关闭'}    ·    状态检查：每 {interval} 秒")

        self.save_settings_button = ctk.CTkButton(win, text="保存设置", command=save, height=44, corner_radius=13, fg_color=BLUE,
                      hover_color=BLUE_HOVER, font=ctk.CTkFont(size=14, weight="bold"))
        self.save_settings_button.pack(fill="x", padx=22, pady=(0, 22))

    def _setting_row(self, parent: Any, title: str, description: str, variable: ctk.BooleanVar) -> None:
        row = ctk.CTkFrame(parent, fg_color="transparent")
        row.pack(fill="x", padx=18, pady=(16, 0))
        labels = ctk.CTkFrame(row, fg_color="transparent")
        labels.pack(side="left", fill="x", expand=True)
        ctk.CTkLabel(labels, text=title, font=ctk.CTkFont(size=13, weight="bold"), text_color=TEXT).pack(anchor="w")
        ctk.CTkLabel(labels, text=description, font=ctk.CTkFont(size=11), text_color=MUTED).pack(anchor="w", pady=(3, 0))
        AnimatedSwitch(row, variable=variable).pack(side="right", padx=(8, 0))

    def open_logs(self) -> None:
        card = self._open_overlay("运行日志")
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
        self.close_overlay()

    def on_close(self) -> None:
        self.closed = True
        self.generation += 1
        self._cancel_monitor()
        self._dismiss_toast()
        if self.challenge_context:
            self.challenge_context.client.close()
        for after_id in (self.ui_queue_after_id, self.auto_login_after_id, self.metrics_after_id):
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
    import sys
    if len(sys.argv) == 3 and sys.argv[1] == "--self-test":
        # Packaged smoke check: isolated preferences, no accounts or network.
        import tempfile
        global APP_DIR, ACCOUNTS_FILE, SETTINGS_FILE
        with tempfile.TemporaryDirectory() as folder:
            APP_DIR = Path(folder)
            ACCOUNTS_FILE, SETTINGS_FILE = APP_DIR / "accounts.json", APP_DIR / "settings.json"
            app = CQUSTWifiApp(auth=AuthAdapter(config_paths=[]))
            app.update()
            app.username_var.set("test-only")
            app.password_var.set("test-only")
            app.open_settings()
            app.save_settings_button.invoke()
            app.update_idletasks()
            report = {"form_preserved": app.password_var.get() == "test-only",
                      "settings_embedded": app.overlay is not None,
                      "logo_loaded": hasattr(app, "school_logo"),
                      "login_label": app.login_button.cget("text") == "登录",
                      "no_network": app.session is None}
            app.show_dashboard()
            app.update()
            report['circle_actual_square'] = app.power_button.winfo_width() == app.power_button.winfo_height()
            report['wide_window'] = app.winfo_width() > app.winfo_height()*1.4
            report['metrics_present'] = app.ip_label.winfo_exists() and app.duration_label.winfo_exists()
            app.set_theme('light')
            app.theme_button.invoke()
            report['single_theme_toggle'] = app.local.settings['theme'] == 'dark'
            app.geometry('760x660')
            app.update()
            center = app.power_button.master
            report['narrow_border_inset'] = center.winfo_x() >= 2 and center.winfo_x()+center.winfo_width() <= center.master.winfo_width()-2
            report['supplied_wifi_loaded'] = hasattr(app, 'brand_image')
            app.on_close()
            Path(sys.argv[2]).write_text(json.dumps(report), encoding="utf-8")
        return
    app = CQUSTWifiApp()
    app.mainloop()


if __name__ == "__main__":
    main()
