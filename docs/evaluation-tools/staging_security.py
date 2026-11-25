"""Opt-in, read-only financial checks for one explicitly authorized FINORA staging origin."""

import argparse
import base64
import http.client
import json
import os
import re
import ssl
import time
from decimal import Decimal
from urllib.parse import urlsplit


ACCOUNT_FIELDS = {"id", "name", "type", "openingBalance", "archived"}
ERROR_FIELDS = {"timestamp", "status", "code", "message", "path"}
LOGIN_FIELDS = {"accessToken", "refreshToken", "expiresIn", "role", "language"}
ACCOUNTS = "/api/v1/accounts"
MAX_BODY = 256 * 1024


class CheckFailed(Exception):
    """Only fixed check labels reach user-facing output."""


def require(predicate, label):
    if not predicate:
        raise CheckFailed(label)


class Transport:
    def __init__(self, host, port=443, context=None):
        self.host = host
        self.port = port
        self.context = context if context is not None else ssl.create_default_context()
        require(self.context.check_hostname and self.context.verify_mode == ssl.CERT_REQUIRED,
                "TLS verification configuration")

    def http_policy(self, allow_redirect):
        connection = http.client.HTTPConnection(self.host, timeout=5)
        try:
            connection.request("GET", "/")
            response = connection.getresponse()
            if allow_redirect:
                require(response.status in (301, 302, 307, 308)
                        and response.getheader("Location") == f"https://{self.host}/",
                        "approved HTTP redirect destination")
            else:
                raise CheckFailed("HTTP port accepted a request")
        except ConnectionRefusedError:
            require(not allow_redirect, "approved redirect unavailable")
        finally:
            connection.close()

    def json(self, method, path, token=None, body=None):
        headers = {"Accept": "application/json"}
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        if body is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(body)
        connection = http.client.HTTPSConnection(self.host, self.port, timeout=10,
                                                 context=self.context)
        try:
            connection.request(method, path, body, headers)
            response = connection.getresponse()
            require(response.getheader("Content-Type", "").split(";")[0] == "application/json",
                    "JSON content type")
            payload = response.read(MAX_BODY + 1)
            require(len(payload) <= MAX_BODY, "bounded JSON response")
            return response.status, json.loads(payload, parse_float=Decimal)
        finally:
            connection.close()


def jwt_claims(token):
    # Only check the supplied fixture's shape and expiry. The server validates signatures.
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def login(transport, email, password):
    status, result = transport.json("POST", "/api/v1/auth/login",
                                    body={"email": email, "password": password})
    require(status == 200 and isinstance(result, dict) and set(result) == LOGIN_FIELDS
            and result["role"] == "USER" and isinstance(result["accessToken"], str)
            and bool(result["accessToken"]), "normal synthetic USER login")
    return result["accessToken"]


def account(value):
    require(isinstance(value, dict) and set(value) == ACCOUNT_FIELDS
            and type(value["id"]) is int and value["id"] > 0, "account response allowlist")
    return value["id"]


def denied(transport, path, token, expected_status, code):
    status, value = transport.json("GET", path, token)
    require(status == expected_status and isinstance(value, dict) and set(value) == ERROR_FIELDS
            and (value["status"], value["code"], value["path"]) == (expected_status, code, path),
            "account denial contract")


def verify(transport, owners, expired, allow_redirect=False):
    transport.http_policy(allow_redirect)
    status, health = transport.json("GET", "/actuator/health")
    require(status == 200 and isinstance(health, dict)
            and set(health) <= {"status", "groups"} and health.get("status") == "UP",
            "trusted HTTPS health")
    denied(transport, ACCOUNTS, None, 401, "UNAUTHORIZED")
    tokens = [login(transport, email, password) for email, password, _ in owners]
    now = time.time()
    old_claims, new_claims = map(jwt_claims, (expired, tokens[0]))
    require(old_claims.get("sub") is not None and old_claims["sub"] == new_claims.get("sub")
            and type(old_claims.get("exp")) is int and type(new_claims.get("exp")) is int
            and old_claims["exp"] < now < new_claims["exp"], "expired owner A fixture")
    denied(transport, ACCOUNTS, expired, 401, "UNAUTHORIZED")
    for index, token in enumerate(tokens):
        owner_id = owners[index][2]
        other_id = owners[1 - index][2]
        status, rows = transport.json("GET", ACCOUNTS, token)
        require(status == 200 and isinstance(rows, list), "authenticated account list")
        ids = {account(row) for row in rows}
        require(owner_id in ids and other_id not in ids, "owner-specific account list")
        status, value = transport.json("GET", f"{ACCOUNTS}/{owner_id}", token)
        require(status == 200 and account(value) == owner_id, "owned account detail")
        denied(transport, f"{ACCOUNTS}/{other_id}", token, 404, "NOT_FOUND")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-approved-https-redirect", action="store_true")
    args = parser.parse_args()
    try:
        origin = os.environ["FINORA_STAGE_APPROVED_ORIGIN"]
        host = urlsplit(origin).hostname
        require(host and origin == f"https://{host}"
                and re.fullmatch(r"[a-z0-9-]+(?:\.[a-z0-9-]+)+", host) is not None,
                "canonical approved HTTPS origin")
        require(os.environ.get("FINORA_STAGE_OWNER_APPROVED") == "yes"
                and os.environ.get("FINORA_STAGE_PROVIDERS_AND_FIREBASE_OFF_VERIFIED") == "yes",
                "owner approval and server configuration attestation")
        owners = [(os.environ[f"FINORA_STAGE_{owner}_EMAIL"],
                   os.environ[f"FINORA_STAGE_{owner}_PASSWORD"],
                   int(os.environ[f"FINORA_STAGE_{owner}_ACCOUNT_ID"])) for owner in ("A", "B")]
        expired = os.environ["FINORA_STAGE_A_EXPIRED_TOKEN"]
        require(all(email and password and account_id > 0 for email, password, account_id in owners)
                and owners[0][0] != owners[1][0] and owners[0][2] != owners[1][2]
                and bool(expired), "distinct synthetic fixture identities")
        verify(Transport(host), owners, expired, args.allow_approved_https_redirect)
    except CheckFailed as error:
        print(f"FAIL: {error}")
        return 1
    except (ValueError, TypeError, AttributeError, LookupError, OSError, http.client.HTTPException):
        print("FAIL: staging configuration, transport, TLS or response parsing")
        return 1
    print("PASS: HTTPS, HTTP policy, synthetic authentication, account isolation and response allowlists")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
