import base64
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import ssl
import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import Mock, patch

import staging_security as stage


def token(subject, expires):
    claims = base64.urlsafe_b64encode(json.dumps({"sub": subject, "exp": expires}).encode())
    return "fixture." + claims.decode().rstrip("=") + ".fixture"


class ContractTest(unittest.TestCase):
    def setUp(self):
        now = int(time.time())
        self.expired = token("A", now - 60)
        self.tokens = (token("A", now + 60), token("B", now + 60))
        self.owners = [("a@example.invalid", "synthetic-A", 101),
                       ("b@example.invalid", "synthetic-B", 202)]
        self.transport = Mock()

    def fixture(self, leak=False):
        def result(method, path, bearer=None, body=None):
            def error(status, code):
                return status, dict(timestamp="fixture", status=status, code=code,
                                    message="fixture", path=path)

            def account(identity):
                return dict(id=identity, name="synthetic", type="CASH",
                            openingBalance=0, archived=False)

            if path == "/actuator/health":
                self.assertIsNone(bearer)
                return 200, {"status": "UP"}
            if method == "POST":
                self.assertEqual(path, "/api/v1/auth/login")
                self.assertIsNone(bearer)
                index = 0 if body["email"] == self.owners[0][0] else 1
                self.assertEqual(body["password"], self.owners[index][1])
                return 200, dict(accessToken=self.tokens[index], refreshToken="synthetic-refresh",
                                 expiresIn=60, role="USER", language="EN")
            if bearer is None or bearer == self.expired:
                return error(401, "UNAUTHORIZED")
            owned = self.owners[self.tokens.index(bearer)][2]
            if path == stage.ACCOUNTS:
                row = account(owned)
                if leak:
                    row["userId"] = 999
                return 200, [row]
            if path == f"{stage.ACCOUNTS}/{owned}":
                return 200, account(owned)
            return error(404, "NOT_FOUND")

        self.transport.json.side_effect = result

    def test_two_owner_contract(self):
        self.fixture()
        stage.verify(self.transport, self.owners, self.expired)
        self.transport.http_policy.assert_called_once_with(False)
        self.assertEqual(self.transport.json.call_count, 11)

    def test_unexpected_financial_field_is_rejected_without_values_in_error(self):
        self.fixture(leak=True)
        with self.assertRaisesRegex(stage.CheckFailed, "^account response allowlist$"):
            stage.verify(self.transport, self.owners, self.expired)

    def test_expired_fixture_must_match_owner(self):
        self.fixture()
        with self.assertRaisesRegex(stage.CheckFailed, "^expired owner A fixture$"):
            stage.verify(self.transport, self.owners, token("other", int(time.time()) - 60))

    def test_cli_without_authorization_does_not_create_transport(self):
        with patch.dict(os.environ, {"FINORA_STAGE_APPROVED_ORIGIN": "https://stage.example.invalid"},
                        clear=True), patch("sys.argv", ["staging_security.py"]), \
                patch.object(stage, "Transport") as transport, patch("builtins.print"):
            self.assertEqual(stage.main(), 1)
            transport.assert_not_called()

    def test_http_policy_refusal_and_explicit_redirect(self):
        with patch.object(stage.http.client, "HTTPConnection") as http:
            connection = http.return_value
            connection.request.side_effect = ConnectionRefusedError
            stage.Transport("stage.example.invalid").http_policy(False)
            connection.request.assert_called_once_with("GET", "/")
            connection.request.side_effect = None
            response = connection.getresponse.return_value
            response.status = 308
            response.getheader.return_value = "https://stage.example.invalid/"
            stage.Transport("stage.example.invalid").http_policy(True)
            response.getheader.return_value = "https://other.example.invalid/"
            with self.assertRaisesRegex(stage.CheckFailed, "redirect destination"):
                stage.Transport("stage.example.invalid").http_policy(True)


@unittest.skipUnless(os.environ.get("FINORA_TEST_OPENSSL"), "local fixture OpenSSL not configured")
class LocalTlsTest(unittest.TestCase):
    def test_trusted_certificate_required_and_hostname_checked(self):
        class Health(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"status":"UP"}')

            def log_message(self, *_args):
                pass

        with tempfile.TemporaryDirectory() as directory:
            cert, key = (os.path.join(directory, name) for name in ("cert.pem", "key.pem"))
            subprocess.run([os.environ["FINORA_TEST_OPENSSL"], "req", "-x509", "-newkey", "rsa:2048",
                            "-nodes", "-days", "1", "-subj", "/CN=localhost",
                            "-addext", "subjectAltName=DNS:localhost", "-keyout", key, "-out", cert],
                           check=True, capture_output=True)
            server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            server_context.load_cert_chain(cert, key)
            trusted = ssl.create_default_context(cafile=cert)
            with ThreadingHTTPServer(("127.0.0.1", 0), Health) as server:
                server.socket = server_context.wrap_socket(server.socket, server_side=True)
                thread = threading.Thread(target=server.serve_forever, daemon=True)
                thread.start()
                try:
                    port = server.server_port
                    self.assertEqual(stage.Transport("localhost", port, trusted).json("GET", "/"),
                                     (200, {"status": "UP"}))
                    with self.assertRaises(ssl.SSLCertVerificationError):
                        stage.Transport("127.0.0.1", port, trusted).json("GET", "/")
                    with self.assertRaises(ssl.SSLCertVerificationError):
                        stage.Transport("localhost", port).json("GET", "/")
                finally:
                    server.shutdown()
                    thread.join()


if __name__ == "__main__":
    unittest.main()
