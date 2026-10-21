from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

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
        self.assertEqual(self.app.current_view, "logs")
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


if __name__ == "__main__":
    unittest.main()
