import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'))
import tempfile
import unittest
from unittest.mock import patch,MagicMock
import time
from service import Service
from auth_adapter import CampusSession,PortalContext,AuthenticationError,AlreadyOnline

class Tests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        with patch('service.lan_ip',return_value='10.0.0.2'):
            self.s=Service(self.temp.name)
        self.s.emit=MagicMock()
    def connected(self):
        context=PortalContext(MagicMock(),'http://aaa.cqust.edu.cn/','http://aaa.cqust.edu.cn/eportal/InterFace.do','','',False)
        return CampusSession(context.client,'test-index','','demo',context,'10.0.0.2')
    def test_intervals_only_new_values(self):
        for seconds in (5,10,120):
            self.s.command('settings',{'check_interval':seconds})
            self.assertEqual(self.s.settings['check_interval'],seconds)
        self.s.command('settings',{'check_interval':7,'password':'must-not-save'})
        self.assertEqual(self.s.settings['check_interval'],120)
        self.assertNotIn('must-not-save',(Path(self.temp.name)/'settings.json').read_text())
    def test_snapshot_never_exposes_password(self):
        self.s.password='synthetic-secret'
        self.assertNotIn('synthetic-secret',str(self.s.snapshot()))
    def test_reconnect_reuses_memory_even_without_remember(self):
        self.s.username='demo';self.s.password='synthetic-secret';self.s.remember=False
        with patch.object(self.s.auth,'authenticate',return_value=self.connected()) as auth,patch('service.keyring.delete_password'),patch('service.keyring.set_password') as save:
            result=self.s.command('reconnect',{'username':'different','password':'wrong'})
            self.assertEqual(auth.call_args.args[:2],('demo','synthetic-secret'))
            self.assertTrue(result['connected'])
            save.assert_not_called()
    def test_reconnect_can_use_keyring_if_memory_missing(self):
        self.s.username='demo'
        with patch.object(self.s,'saved_password',return_value='saved-example'),patch.object(self.s.auth,'authenticate',return_value=self.connected()) as auth,patch('service.keyring.set_password'):
            self.s.command('reconnect',{})
            self.assertEqual(auth.call_args.args[1],'saved-example')
    def test_animation_preference_persists(self):
        self.assertTrue(self.s.settings['animations'])
        self.s.command('settings',{'animations':False})
        self.assertFalse(Service(self.temp.name).settings['animations'])
    def test_confirmed_online_reconnect_keeps_unsaved_memory_password(self):
        self.s.username='demo';self.s.password='memory-only';self.s.remember=False
        self.s.online=AlreadyOnline('http://aaa.cqust.edu.cn/eportal/success.jsp','test-index')
        with patch.object(self.s.auth,'disconnect_discovered'),patch('service.time.sleep'),patch.object(self.s.auth,'authenticate',return_value=self.connected()) as auth,patch('service.keyring.delete_password'):
            self.s.command('switchOnline',{'reconnect':True,'password':''})
            self.assertEqual(auth.call_args.args[:2],('demo','memory-only'))
    def test_original_interval_migrated_without_changing_file_on_read(self):
        path=Path(self.temp.name)/'settings.json'
        path.write_text('{"check_interval":7}')
        self.assertEqual(Service(self.temp.name).settings['check_interval'],10)
        self.assertIn('7',path.read_text())
    def test_login_uses_saved_password_without_sending_it_back(self):
        with patch.object(self.s,'saved_password',return_value='synthetic-secret'),patch.object(self.s.auth,'authenticate',return_value=self.connected()) as auth,patch('service.keyring.set_password'):
            result=self.s.command('login',dict(username='demo',password=''))
            self.assertEqual(auth.call_args.args[1],'synthetic-secret')
            self.assertTrue(result['connected'])
            self.assertNotIn('synthetic-secret',str(result))
    def test_manual_disconnect_blocks_auto_reconnect(self):
        self.s.session=self.connected()
        self.s.settings['keep_connected']=True
        with patch.object(self.s.auth,'disconnect'):
            self.s.command('disconnect',{})
        self.assertTrue(self.s.manual)
        self.assertEqual(self.s.next_retry,float('inf'))
    def test_kicked_notice_without_keepalive(self):
        self.s.session=self.connected();self.s.next_poll=0
        with patch.object(self.s.auth,'check_status',return_value={'online':False,'reason':'kicked'}): self.s.tick()
        self.s.emit.assert_any_call('notice','已在其他设备登录，已下线。')
    def test_keep_connected_is_silent(self):
        self.s.session=self.connected();self.s.next_poll=0;self.s.settings['keep_connected']=True
        with patch.object(self.s.auth,'check_status',return_value={'online':False,'reason':'kicked'}),patch.object(self.s,'login') as login:
            self.s.tick()
            login.assert_called_once()
        self.assertFalse(any(call.args[0]=='notice' for call in self.s.emit.call_args_list))
    def test_reconnect_preserves_no_save_choice(self):
        self.s.remember=False;self.s.next_retry=0
        with patch.object(self.s,'login') as login:
            self.s.tick()
            self.assertFalse(login.call_args.args[0]['remember'])
    def test_unknown_errors_are_sanitized(self):
        self.assertNotIn('secret',str(self.s.error(ValueError('secret'))))
    def test_switch_requires_detected_session(self):
        with patch.object(self.s.auth,'disconnect_discovered') as disconnect:
            with self.assertRaises(AuthenticationError):self.s.command('switchOnline',{})
            disconnect.assert_not_called()
    def test_online_error_notifies_without_logout(self):
        with patch.object(self.s.auth,'authenticate',side_effect=AlreadyOnline('http://aaa.cqust.edu.cn/eportal/success.jsp','fake')),patch.object(self.s.auth,'disconnect_discovered') as disconnect:
            with self.assertRaises(AlreadyOnline):self.s.login(dict(username='demo',password='synthetic'))
            disconnect.assert_not_called()
        self.assertEqual(self.s.error(self.s.online)['kind'],'online')

if __name__=='__main__':unittest.main()
