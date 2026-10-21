from __future__ import annotations

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import time

import customtkinter as ctk

import gui


class GuiSmokeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory()
        app_dir = Path(cls.temp_dir.name)
        gui.APP_DIR = app_dir
        gui.ACCOUNTS_FILE = app_dir / "accounts.json"
        gui.SETTINGS_FILE = app_dir / "settings.json"
        with patch("gui.AuthAdapter", return_value=gui.AuthAdapter(config_paths=[])), patch.object(gui.LocalConfig, "get_password", return_value=""):
            cls.app = gui.CQUSTWifiApp()
        cls.app.deiconify()
        cls.app.update()
        cls.app.update_idletasks()

    @classmethod
    def tearDownClass(cls):
        if cls.app.winfo_exists():
            cls.app.on_close()
        cls.temp_dir.cleanup()

    def setUp(self):
        self.app.connected = False
        self.app.session = None
        self.app.busy = False
        self.app.local.settings.update(gui.DEFAULT_SETTINGS)
        self.app.show_login_view()
        self.app.update()
        self.app.update_idletasks()

    def test_default_theme_and_password_eye(self):
        self.assertEqual(self.app.local.settings["theme"], "system")
        self.app.password_var.set("example")
        self.app.toggle_password_visibility()
        self.assertEqual(self.app.password_entry.cget("show"), "")
        self.app.toggle_password_visibility()
        self.assertEqual(self.app.password_entry.cget("show"), "●")

    def test_account_management_and_logs_stay_inside_main_window(self):
        self.app.manage_accounts()
        self.assertTrue(self.app.accounts_panel_visible)
        self.assertEqual(len([w for w in self.app.winfo_children() if isinstance(w, ctk.CTkToplevel)]), 0)
        self.app.open_logs()
        self.assertIsNotNone(self.app.overlay)
        self.assertIsNotNone(self.app.log_textbox)
        self.assertEqual(len([w for w in self.app.winfo_children() if isinstance(w, ctk.CTkToplevel)]), 0)

    def test_dashboard_has_circular_primary_control(self):
        self.app.connected = True
        self.app.current_username = "test-user"
        self.app.show_dashboard()
        self.app.update()
        button = self.app.power_button
        self.assertIsInstance(button, gui.PulsePower)
        self.assertEqual(button.winfo_width(), button.winfo_height())
        x1,y1,x2,y2 = button.circle_bounds
        self.assertAlmostEqual(x2-x1, y2-y1)

    def test_settings_save_preserves_form_and_creates_no_window(self):
        self.app.username_var.set("example-user")
        self.app.password_var.set("unsaved-input")
        original_entry = self.app.password_entry
        self.app.open_settings()
        self.assertEqual(len([w for w in self.app.winfo_children() if isinstance(w, ctk.CTkToplevel)]), 0)
        self.app.save_settings_button.invoke()
        self.assertIs(self.app.password_entry, original_entry)
        self.assertEqual(self.app.password_var.get(), "unsaved-input")
        self.assertEqual(self.app.username_var.get(), "example-user")
        self.assertIsNotNone(self.app.overlay)
        self.assertEqual(self.app.toast.cget('text'), '设置已保存')

    def test_log_return_preserves_input(self):
        self.app.password_var.set("draft")
        self.app.open_logs()
        self.app._close_logs_view()
        self.assertEqual(self.app.password_var.get(), "draft")

    def test_no_native_combobox_and_plain_login_label(self):
        self.assertIsInstance(self.app.username_box, ctk.CTkEntry)
        self.assertEqual(self.app.login_button.cget("text"), "登录")
        self.assertEqual(self.app.password_eye.cget("text"), "")

    def test_switch_animation_finishes_and_reverses(self):
        variable = ctk.BooleanVar(value=False)
        switch = gui.AnimatedSwitch(self.app, variable)
        switch.toggle()
        self.assertTrue(variable.get())
        self.assertLess(switch.position, 1)
        deadline = time.monotonic()+.25
        while time.monotonic() < deadline:
            self.app.update()
            time.sleep(.01)
        self.assertEqual(switch.position, 1)
        switch.toggle()
        self.assertFalse(variable.get())
        switch.destroy()

    def test_kick_prompts_without_keep_connected(self):
        self.app.connected = True
        self.app.show_dashboard()
        with patch("gui.messagebox.showwarning") as prompt:
            self.app._status_check_finished({"online": False, "reason": "kicked"})
        self.assertFalse(self.app.connected)
        prompt.assert_called_once()

    def test_keep_connected_reconnects_without_prompt(self):
        self.app.local.settings["keep_connected"] = True
        self.app.current_username, self.app.current_password = "demo", "demo"
        self.app.show_dashboard()
        with patch("gui.messagebox.showwarning") as prompt, patch.object(self.app, "_start_authentication") as reconnect:
            self.app._status_check_finished({"online": False, "reason": "kicked"})
        prompt.assert_not_called()
        self.assertTrue(reconnect.call_args.kwargs["silent"])

    def test_cancel_account_switch_does_not_stop_monitor(self):
        self.app.connected = True
        self.app._schedule_monitor()
        timer = self.app.monitor_after_id
        with patch("gui.messagebox.askyesno", return_value=False):
            self.app.switch_account()
        self.assertEqual(self.app.monitor_after_id, timer)

    def test_stale_status_result_is_ignored(self):
        self.app.connected = True
        with patch.object(self.app, "_status_check_finished") as finish:
            self.app._status_result(self.app.generation-1, None, {"online": False})
        finish.assert_not_called()

    def test_account_panel_does_not_hide_login_button(self):
        self.app.manage_accounts()
        self.app.update_idletasks()
        button = self.app.login_button
        card = button.master
        self.assertLessEqual(button.winfo_y()+button.winfo_height(), card.winfo_height())

    def test_original_config_login_does_not_erase_saved_password(self):
        with patch("gui.keyring.set_password") as save, patch("gui.keyring.delete_password") as delete:
            self.app.local.remember_account("test-original", "", True)
        save.assert_not_called()
        delete.assert_not_called()

    def test_theme_icons_are_on_main_page(self):
        self.app.set_theme('light')
        self.app.theme_button.invoke()
        self.assertEqual(self.app.local.settings['theme'], 'dark')
        self.app.theme_button.invoke()
        self.assertEqual(self.app.local.settings['theme'], 'light')
        self.app.set_theme('system')
        self.assertEqual(self.app.local.settings['theme'], 'system')

    def test_narrow_dashboard_preserves_border_and_header(self):
        self.app.geometry('660x660')
        self.app.show_dashboard()
        self.app.update()
        center = self.app.power_button.master
        card = center.master
        self.assertGreaterEqual(center.winfo_x(), 2)
        self.assertLessEqual(center.winfo_x()+center.winfo_width(), card.winfo_width()-2)
        self.assertTrue(self.app.theme_button.winfo_ismapped())
        self.assertGreaterEqual(self.app.theme_button.winfo_rootx(), self.app.winfo_rootx())
        self.assertLessEqual(self.app.theme_button.winfo_rootx()+self.app.theme_button.winfo_width(), self.app.winfo_rootx()+self.app.winfo_width())
        self.app.geometry('700x820')

    def test_about_links_and_embedded_page_preserve_input(self):
        self.app.password_var.set('example-only')
        self.app.open_about()
        self.app.update()
        self.assertIsNotNone(self.app.overlay)
        with patch('webbrowser.open') as opened:
            self.app.website_link.invoke()
            opened.assert_called_once_with('https://ziyis.cn')
        self.assertEqual(self.app.password_var.get(), 'example-only')
        self.app.close_overlay()

    def test_vector_brand_is_generated_with_transparent_corners(self):
        image = gui.wifi_brand(184)
        self.assertEqual(image.size, (184,184))
        self.assertEqual(image.getpixel((0,0))[3], 0)

    def test_online_help_requires_explicit_action_and_keeps_inputs(self):
        from auth_adapter import AlreadyOnline
        self.app.username_var.set('demo')
        self.app.password_var.set('example')
        with patch.object(self.app.auth, 'disconnect_discovered') as disconnect:
            self.app.open_auth_help(AlreadyOnline('http://aaa.cqust.edu.cn/eportal/success.jsp', 'test-index'))
            self.app.update()
            disconnect.assert_not_called()
            self.assertTrue(self.app.online_switch_button.winfo_ismapped())
        self.assertEqual(self.app.password_var.get(), 'example')

    def test_portrait_window_and_metrics(self):
        self.app.geometry('700x820')
        self.app.connected = True
        self.app.connected_since = time.monotonic()-3661
        self.app.show_dashboard()
        self.app.update_idletasks()
        self.assertGreater(self.app.winfo_height(), self.app.winfo_width())
        self.assertTrue(self.app.ip_label.winfo_exists())
        if self.app.metrics_after_id:
            self.app.after_cancel(self.app.metrics_after_id)
        self.app._update_metrics()
        self.assertEqual(self.app.duration_label.cget('text'), '01:01:01')

    def test_fullscreen_disabled_but_resize_allowed(self):
        self.app.state('zoomed')
        self.app.attributes('-fullscreen', True)
        self.app.update()
        self.assertNotEqual(self.app.state(), 'zoomed')
        self.assertFalse(self.app.attributes('-fullscreen'))
        self.assertTrue(all(self.app.resizable()))

    def test_pulse_animation_advances(self):
        self.app.show_dashboard()
        button = self.app.power_button
        old = button.phase
        time.sleep(.05)
        self.app.update()
        self.assertNotEqual(button.phase,old)


if __name__ == "__main__":
    unittest.main()
