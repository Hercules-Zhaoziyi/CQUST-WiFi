from __future__ import annotations

import unittest
import gzip
import json
from unittest.mock import patch

from auth_adapter import AuthAdapter, CampusSession, AuthenticationError


class FakeResponse:
    def __init__(self, payload=None, *, url="http://172.28.228.2/eportal/index.jsp", text=""):
        self.payload = payload
        self.url = url
        self.text = text
        self.content = json.dumps(payload).encode() if payload is not None else text.encode()
        self.status_code = 200
        self.headers = {}

    def json(self):
        if self.payload is None:
            raise ValueError("not json")
        return self.payload

    def raise_for_status(self):
        return None


class FakeSession:
    def __init__(self, post_results):
        self.post_results = list(post_results)
        self.posts = []
        self.closed = False

    def get(self, *_args, **_kwargs):
        return FakeResponse(
            url=("http://172.28.228.2/eportal/index.jsp?"
                 "wlanuserip=10.1.2.3&wlanacname=ac-test&mac=00-00-00-00-00-00"),
            text='<input name="service" value="internet">',
        )

    def post(self, url, **kwargs):
        self.posts.append((url, kwargs))
        return FakeResponse(self.post_results.pop(0))

    def close(self):
        self.closed = True


class AuthAdapterTests(unittest.TestCase):
    def setUp(self):
        self.auth = AuthAdapter(config_paths=[])
        self.auth.profile = {"dataLogin": {"userId": "20260001", "password": "encrypted-secret",
                            "service": "互联网", "passwordEncrypt": "true",
                            "queryString": "wlanuserip=10.1.2.3&mac=001122",
                            "operatorPwd": "", "userIndex": "old"}}

    def test_authenticate_uses_runtime_credentials_and_discovered_query(self):
        self.auth.profile["dataLogin"]["passwordEncrypt"] = "false"
        fake = FakeSession([{"passwordEncrypt": "false"}, {"result": "success", "userIndex": "idx-1"},
                            {"result": "success", "userIndex": "idx-1", "userId": "20260001"}])
        with patch("auth_adapter.requests.Session", return_value=fake):
            session = self.auth.authenticate("20260001", "secret", {}, lambda _m: None)

        self.assertEqual(session.user_index, "idx-1")
        sent = fake.posts[1][1]["data"]
        self.assertEqual(sent["userId"], "20260001")
        self.assertEqual(sent["password"], "secret")
        self.assertIn("wlanuserip=10.1.2.3", sent["queryString"])
        self.assertEqual(sent["service"], "互联网")
        self.assertFalse(fake.trust_env)
        self.assertFalse(fake.posts[1][1]["allow_redirects"])

    def test_original_encrypted_config_is_preserved(self):
        fake = FakeSession([{"result": "success", "userIndex": "idx"},
                            {"result": "wait", "userIndex": "idx"}])
        with patch("auth_adapter.requests.Session", return_value=fake):
            self.auth.authenticate("20260001", "", {}, lambda _m: None)
        for key, value in self.auth.profile["dataLogin"].items():
            self.assertEqual(fake.posts[0][1]["data"][key], value)

    def test_gzip_without_encoding_header(self):
        response = FakeResponse({"result": "success"})
        response.content = gzip.compress(response.content)
        self.assertEqual(self.auth._response_data(response)["result"], "success")

    def test_manual_password_uses_original_rsa_mode(self):
        modulus = "c4" + "19"*127
        fake = FakeSession([{"passwordEncrypt": "false", "publicKeyExponent": "10001", "publicKeyModulus": modulus},
                            {"result": "success", "userIndex": "idx"},
                            {"result": "success"}])
        with patch("auth_adapter.requests.Session", return_value=fake):
            self.auth.authenticate("20260001", "new-password", {}, lambda _m: None)
        payload = fake.posts[1][1]["data"]
        self.assertEqual(payload["passwordEncrypt"], "true")
        self.assertNotEqual(payload["password"], "new-password")

    def test_errors_do_not_echo_secrets(self):
        fake = FakeSession([{"result": "fail", "message": "密码 encrypted-secret wrong"}])
        with patch("auth_adapter.requests.Session", return_value=fake):
            with self.assertRaises(AuthenticationError) as error:
                self.auth.authenticate("20260001", "", {}, lambda _m: None)
        self.assertNotIn("encrypted-secret", str(error.exception))
        self.assertTrue(fake.closed)

    def test_unknown_status_does_not_claim_kicked(self):
        fake = FakeSession([{"result": "error", "message": "server busy"}])
        with self.assertRaises(AuthenticationError):
            self.auth.check_status(CampusSession(fake, "idx", "query"), {}, lambda _m: None)

    def test_explicit_kick_is_classified(self):
        fake = FakeSession([{"result": "fail", "message": "账号已在其他设备登录"}])
        self.assertEqual(self.auth.check_status(CampusSession(fake, "idx", ""), {}, print)["reason"], "kicked")

    def test_failed_logout_preserves_session(self):
        fake = FakeSession([{"result": "fail"}])
        with self.assertRaises(AuthenticationError):
            self.auth.disconnect(CampusSession(fake, "idx", ""), {}, print)
        self.assertFalse(fake.closed)

    def test_rsa_matches_reversed_ascii_raw_rsa(self):
        modulus = "c4" + "19"*127
        raw = ("example>001122")[::-1].encode()
        expected = format(pow(int.from_bytes(raw, "little"), 65537, int(modulus, 16)), "0256x")
        self.assertEqual(self.auth.encrypt_password("example", "001122", "10001", modulus), expected)

    def test_online_status_accepts_legacy_wait_result(self):
        fake = FakeSession([{"result": "wait", "userIndex": "idx-2"}])
        session = CampusSession(fake, "", "query")
        result = self.auth.check_status(session, {}, lambda _m: None)
        self.assertEqual(result, {"online": True, "reason": "online"})
        self.assertEqual(session.user_index, "idx-2")

    def test_missing_server_session_is_not_falsely_classified_as_replaced(self):
        fake = FakeSession([{"result": "fail", "message": "用户不在线"}])
        session = CampusSession(fake, "idx", "query")
        result = self.auth.check_status(session, {}, lambda _m: None)
        self.assertEqual(result, {"online": False, "reason": "offline"})

    def test_disconnect_closes_http_session(self):
        fake = FakeSession([{"result": "success"}])
        session = CampusSession(fake, "idx", "query")
        self.auth.disconnect(session, {}, lambda _m: None)
        self.assertTrue(fake.closed)


if __name__ == "__main__":
    unittest.main()
