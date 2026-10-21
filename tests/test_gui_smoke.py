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
        cls.app.withdraw()
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
        self.app.update_idletasks()
        self.assertEqual(self.app.power_button.cget("width"), 190)
        self.assertEqual(self.app.power_button.cget("height"), 190)
        self.assertEqual(self.app.power_button.cget("corner_radius"), 95)

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
        self.assertIsNone(self.app.overlay)

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


if __name__ == "__main__":
    unittest.main()
