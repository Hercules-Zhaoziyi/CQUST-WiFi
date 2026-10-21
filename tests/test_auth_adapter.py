import gzip
import json
import time
import unittest
from unittest.mock import patch
from urllib.parse import unquote
import requests
from auth_adapter import (AuthAdapter, AuthenticationError, ContextRequired, ChallengeRequired,
    ServiceRequired, NetworkError, AlreadyOnline, PortalContext, CampusSession, encode_twice, extract_auth_query)

MODULUS = 'c4'+'19'*127
QUERY = 'wlanuserip=10.1.2.3&MAC=aa%3Abb&url=http%3A%2F%2Fa.test%2Fa%2520b&x=1'
PAGE = 'http://aaa.cqust.edu.cn/eportal/index.jsp?'+QUERY
HTML = '<input id="passwordEncrypt" value="false"><script>AuthInterFace.init("./");</script>'

class Response:
    def __init__(self, payload=None, text='', status=200, headers=None):
        self.content = json.dumps(payload).encode() if payload is not None else text.encode()
        self.status_code, self.headers = status, headers or {}
    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError()

class Client:
    def __init__(self, gets=None, posts=None):
        self.gets = list(gets or [])
        self.posts = list(posts or [])
        self.sent, self.requested = [], []
        self.closed = False
    def get(self, url, **kwargs):
        self.requested.append(url)
        if not self.gets:
            raise requests.Timeout()
        return self.gets.pop(0)
    def post(self, url, **kwargs):
        self.sent.append((url, kwargs))
        return Response(self.posts.pop(0))
    def close(self):
        self.closed = True

def context(client, encrypted=True):
    return PortalContext(client, PAGE, 'http://aaa.cqust.edu.cn/eportal/InterFace.do', QUERY,
                         'aa%3Abb', encrypted, '10001', MODULUS, '互联网')

class AuthTests(unittest.TestCase):
    def setUp(self):
        self.auth = AuthAdapter(config_paths=[])
    def test_raw_query_and_mac_not_decoded(self):
        self.assertEqual(extract_auth_query(PAGE+'#fragment'), (QUERY, 'aa%3Abb'))
        self.assertEqual(extract_auth_query('http://aaa.cqust.edu.cn/?wlanuserip=x')[1], '111111111')
    def test_login_double_encoding_exactly_two_layers(self):
        c = context(Client())
        body = self.auth.build_login_body(' demo ', ' pass! ', c)
        fields = dict(part.split('=',1) for part in body.split('&'))
        self.assertEqual(fields['service'], '%25E4%25BA%2592%25E8%2581%2594%25E7%25BD%2591')
        self.assertEqual(unquote(unquote(fields['queryString'])), QUERY)
        self.assertEqual(fields['userId'], 'demo')
        self.assertNotIn('userIndex', fields)
        self.assertEqual(fields['password'], encode_twice(self.auth.encrypt_password(' pass! ', c.mac, c.exponent, c.modulus)))
    def test_current_false_flag_sends_plain_password_with_encoding(self):
        body = self.auth.build_login_body('demo', ' a+b% ', context(Client(), False))
        self.assertIn('password='+encode_twice(' a+b% '), body)
        self.assertTrue(body.endswith('passwordEncrypt=false'))
    def test_http_and_html_redirects_discover_fresh_context(self):
        client = Client([Response(status=302, headers={'Location':'http://aaa.cqust.edu.cn/start'}),
                         Response(text='<script>location.href="'+PAGE.replace('&','&amp;')+'"</script>'),
                         Response(text=HTML)],
                        [{'passwordEncrypt':'true','publicKeyExponent':'10001','publicKeyModulus':MODULUS},
                         {'services':[{'serviceName':'互联网'}]}])
        found = self.auth.discover_context(client, {}, lambda x: None)
        self.assertEqual(found.query_string, QUERY)
        self.assertEqual(found.mac, 'aa%3Abb')
        self.assertTrue(found.encrypt)
        self.assertEqual(found.api_url, 'http://aaa.cqust.edu.cn/eportal/InterFace.do')
        self.assertEqual(client.sent[0][1]['data'], ('queryString='+encode_twice(QUERY).replace('%25','%')).encode())
    def test_manual_url_is_current_and_preserves_percent_encoding(self):
        client = Client([Response(text=HTML)], [{'passwordEncrypt':'false'}, {'services':[{'serviceName':'互联网'}]}])
        c = self.auth.discover_context(client, {'login_page_url':PAGE}, lambda x: None)
        self.assertEqual(c.page_url, PAGE)
        self.assertEqual(client.requested, [PAGE])
    def test_no_stale_config_fallback(self):
        self.auth.profile = {'dataLogin': {'queryString': QUERY, 'password': 'historical'}}
        with self.assertRaises(ContextRequired):
            self.auth.discover_context(Client(), {}, lambda x: None)
    def test_school_entry_first_and_existing_session_not_a_manual_url_error(self):
        client = Client([Response(status=302, headers={'Location':'/eportal/success.jsp?userIndex=current-demo'}), Response(text=HTML)])
        with self.assertRaises(AlreadyOnline) as error:
            self.auth.discover_context(client, {}, lambda _: None)
        self.assertEqual(client.requested[0], 'http://aaa.cqust.edu.cn/')
        self.assertEqual(error.exception.index, 'current-demo')
        self.assertNotIn('current-demo', str(error.exception))
        self.assertFalse(client.sent)

    def test_confirmed_switch_checks_then_logs_out_current_index(self):
        client = Client(posts=[{'result':'success'}, {'result':'success'}])
        from unittest.mock import MagicMock
        manager = MagicMock()
        manager.__enter__.return_value = client
        with patch('auth_adapter.requests.Session', return_value=manager):
            self.auth.disconnect_discovered(AlreadyOnline('http://aaa.cqust.edu.cn/eportal/success.jsp', 'current-demo'))
        self.assertIn('getOnlineUserInfo', client.sent[0][0])
        self.assertIn('logout', client.sent[1][0])
        self.assertEqual(client.sent[1][1]['data'], b'userIndex=current-demo')
    def test_untrusted_url_rejected_before_any_request(self):
        client = Client()
        with self.assertRaises(ContextRequired):
            self.auth.discover_context(client, {'login_page_url':'http://evil.test/?'+QUERY}, print)
        self.assertFalse(client.requested)
    def test_multiple_services_require_selection(self):
        client = Client([Response(text=HTML)], [{'passwordEncrypt':'false'}, {'services':[{'serviceName':'互联网'},{'serviceName':'其他'}]}])
        with self.assertRaises(ServiceRequired) as error:
            self.auth.discover_context(client, {'login_page_url':PAGE}, print)
        self.assertEqual(error.exception.services, ['互联网','其他'])
    def test_authentication_sends_raw_bytes_and_checks_online(self):
        client = Client(posts=[{'result':'success','userIndex':'fresh-index'}, {'result':'wait','userId':'demo','userIp':'10.1.2.3'}])
        c = context(client)
        with patch('auth_adapter.requests.Session', return_value=client), patch.object(self.auth,'discover_context',return_value=c):
            session = self.auth.authenticate('demo','example',{},lambda x: None)
        self.assertEqual(session.user_index, 'fresh-index')
        self.assertEqual(session.lan_ip,'10.1.2.3')
        self.assertIsInstance(client.sent[0][1]['data'], bytes)
        self.assertFalse(client.trust_env)
        self.assertFalse(client.sent[0][1]['allow_redirects'])
        self.assertEqual(client.sent[0][1]['headers']['Referer'], PAGE)
        self.assertEqual(client.sent[1][1]['data'], b'userIndex=fresh-index')
    def test_secret_server_error_is_not_echoed(self):
        client = Client(posts=[{'result':'fail','message':'password example and index secret'}])
        with patch('auth_adapter.requests.Session',return_value=client), patch.object(self.auth,'discover_context',return_value=context(client)):
            with self.assertRaises(AuthenticationError) as error:
                self.auth.authenticate('demo','example',{},lambda x: None)
        self.assertNotIn('example',str(error.exception))
        self.assertNotIn('secret',str(error.exception))
        self.assertTrue(client.closed)
    def test_captcha_preserves_same_session_for_user_input(self):
        client = Client(posts=[{'result':'fail','validCodeUrl':'/eportal/captcha.jsp'}])
        c = context(client)
        with patch('auth_adapter.requests.Session',return_value=client), patch.object(self.auth,'discover_context',return_value=c):
            with self.assertRaises(ChallengeRequired) as error:
                self.auth.authenticate('demo','example',{},lambda x: None)
        self.assertIs(error.exception.context.client,client)
        self.assertFalse(client.closed)
    def test_gzip_response_without_header(self):
        response = Response({'result':'success'})
        response.content = gzip.compress(response.content)
        self.assertEqual(self.auth._response_data(response)['result'],'success')
    def test_non_ascii_password_has_explicit_safe_error(self):
        with self.assertRaises(AuthenticationError):
            self.auth.encrypt_password('含中文','mac','10001',MODULUS)
    def test_keepalive_zero_sends_nothing(self):
        c = Client()
        self.auth.keepalive(CampusSession(c,'idx',''))
        self.assertFalse(c.sent)
    def test_server_keepalive_interval_is_minutes(self):
        s = CampusSession(Client(),'idx','')
        self.auth._update_keepalive(s, {'keepaliveInterval':2})
        self.assertEqual(s.keepalive_interval,120)
        self.assertGreater(s.next_keepalive, time.monotonic()+119)
        self.auth._update_keepalive(s, {'keepaliveInterval':0})
        self.assertEqual(s.keepalive_interval,0)
    def test_keepalive_positive_and_logout_use_unencoded_index_body(self):
        c = Client(posts=[{'result':'success'},{'result':'success'}])
        s = CampusSession(c,'idx-123','', keepalive_interval=20, next_keepalive=0)
        self.auth.keepalive(s)
        self.assertGreater(s.next_keepalive,time.monotonic())
        self.auth.disconnect(s,{},print)
        self.assertEqual([x[1]['data'] for x in c.sent], [b'userIndex=idx-123',b'userIndex=idx-123'])
        self.assertTrue(c.closed)
    def test_unconfirmed_logout_does_not_close(self):
        c = Client(posts=[{'result':'fail'}])
        with self.assertRaises(AuthenticationError):
            self.auth.disconnect(CampusSession(c,'idx',''),{},print)
        self.assertFalse(c.closed)
    def test_unknown_status_is_not_offline(self):
        c = Client(posts=[{'result':'error'}])
        with self.assertRaises(NetworkError):
            self.auth.check_status(CampusSession(c,'idx',''),{},print)
    def test_explicit_kick_only(self):
        c = Client(posts=[{'result':'fail','message':'其他设备登录'}, {'result':'fail','message':'用户不在线'}])
        s = CampusSession(c,'idx','')
        self.assertEqual(self.auth.check_status(s,{},print)['reason'],'kicked')
        self.assertEqual(self.auth.check_status(s,{},print)['reason'],'offline')
