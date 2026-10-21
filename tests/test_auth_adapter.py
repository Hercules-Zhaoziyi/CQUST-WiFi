from __future__ import annotations

import unittest
from unittest.mock import patch

from auth_adapter import AuthAdapter, CampusSession


class FakeResponse:
    def __init__(self, payload=None, *, url="http://172.28.228.2/eportal/index.jsp", text=""):
        self.payload = payload
        self.url = url
        self.text = text

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
    def test_authenticate_uses_runtime_credentials_and_discovered_query(self):
        fake = FakeSession([{"result": "success", "userIndex": "idx-1"}])
        with patch("auth_adapter.requests.Session", return_value=fake):
            session = AuthAdapter().authenticate("20260001", "secret", {}, lambda _m: None)

        self.assertEqual(session.user_index, "idx-1")
        sent = fake.posts[0][1]["data"]
        self.assertEqual(sent["userId"], "20260001")
        self.assertEqual(sent["password"], "secret")
        self.assertIn("wlanuserip=10.1.2.3", sent["queryString"])
        self.assertEqual(sent["service"], "internet")

    def test_online_status_accepts_legacy_wait_result(self):
        fake = FakeSession([{"result": "wait", "userIndex": "idx-2"}])
        session = CampusSession(fake, "", "query")
        result = AuthAdapter().check_status(session, {}, lambda _m: None)
        self.assertEqual(result, {"online": True, "reason": "online"})
        self.assertEqual(session.user_index, "idx-2")

    def test_missing_server_session_is_classified_as_replaced(self):
        fake = FakeSession([{"result": "fail", "message": "用户不在线"}])
        session = CampusSession(fake, "idx", "query")
        result = AuthAdapter().check_status(session, {}, lambda _m: None)
        self.assertEqual(result, {"online": False, "reason": "kicked"})

    def test_disconnect_closes_http_session(self):
        fake = FakeSession([{"result": "success"}])
        session = CampusSession(fake, "idx", "query")
        AuthAdapter().disconnect(session, {}, lambda _m: None)
        self.assertTrue(fake.closed)


if __name__ == "__main__":
    unittest.main()
