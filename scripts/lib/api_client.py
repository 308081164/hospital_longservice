"""Shared API client: docker exec curl or direct HTTP."""

from __future__ import annotations

import json
import mimetypes
import os
import subprocess
import uuid
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class ApiError(RuntimeError):
    def __init__(self, message: str, *, code: int | None = None, payload: Any = None):
        super().__init__(message)
        self.code = code
        self.payload = payload


def parse_api_json(raw: str | bytes, *, context: str = "API") -> dict[str, Any]:
    text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ApiError(f"{context} 非 JSON: {text[:200]}") from exc
    if not isinstance(data, dict):
        raise ApiError(f"{context} 响应格式异常")
    return data


def assert_api_ok(data: dict[str, Any], *, context: str = "API") -> dict[str, Any]:
    if data.get("code") != 200:
        raise ApiError(f"{context}: {data.get('msg') or data}", code=data.get("code"), payload=data)
    return data


class ApiClient:
    def __init__(
        self,
        *,
        api_base: str | None = None,
        mode: str = "docker",
        backend_container: str | None = None,
        username: str | None = None,
        password: str | None = None,
    ):
        self.api_base = (
            api_base
            or os.environ.get("API_BASE")
            or os.environ.get("API_INTERNAL")
            or "http://127.0.0.1:8000"
        ).rstrip("/")
        self.mode = mode if mode in {"docker", "direct"} else "docker"
        self.backend_container = backend_container or os.environ.get(
            "BACKEND_CONTAINER", "hospital-backend"
        )
        self.username = username or os.environ.get("SMOKE_USER") or os.environ.get(
            "ADMIN_USERNAME", "admin"
        )
        self.password = (
            password
            or os.environ.get("SMOKE_PASS")
            or os.environ.get("ADMIN_PASSWORD")
            or os.environ.get("APP_ADMIN_PASSWORD")
            or "admin123"
        )
        self._token: str | None = None

    @classmethod
    def from_env(cls, mode: str | None = None) -> ApiClient:
        resolved = mode or os.environ.get("API_MODE", "docker")
        return cls(mode=resolved)

    def configure(
        self,
        *,
        api_base: str | None = None,
        mode: str | None = None,
        backend_container: str | None = None,
        username: str | None = None,
        password: str | None = None,
    ) -> None:
        if api_base is not None:
            self.api_base = api_base.rstrip("/")
        if mode is not None:
            self.mode = mode
        if backend_container is not None:
            self.backend_container = backend_container
        if username is not None:
            self.username = username
        if password is not None:
            self.password = password
        self._token = None

    def curl_raw(self, curl_args: list[str]) -> str:
        if self.mode != "docker":
            raise ApiError("curl_raw 仅 docker 模式可用；direct 请用 request_json/export_v2")
        cmd = ["docker", "exec", self.backend_container, "curl", "-sS", *curl_args]
        return subprocess.check_output(cmd, text=True)

    def _direct_request(
        self,
        method: str,
        path: str,
        *,
        headers: dict[str, str] | None = None,
        body: bytes | None = None,
        json_body: dict[str, Any] | None = None,
        timeout: int = 120,
    ) -> tuple[int, bytes, dict[str, str]]:
        url = f"{self.api_base}{path}"
        hdrs = dict(headers or {})
        data = body
        if json_body is not None:
            data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
            hdrs.setdefault("Content-Type", "application/json")
        req = Request(url, data=data, headers=hdrs, method=method.upper())
        try:
            with urlopen(req, timeout=timeout) as resp:
                return resp.status, resp.read(), dict(resp.headers.items())
        except HTTPError as exc:
            return exc.code, exc.read(), dict(exc.headers.items())
        except URLError as exc:
            raise ApiError(f"请求失败 {url}: {exc}") from exc

    def request_json(
        self,
        method: str,
        path: str,
        *,
        token: str | None = None,
        json_body: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        if self.mode == "docker":
            args = ["-X", method.upper(), f"{self.api_base}{path}"]
            if json_body is not None:
                args.extend(
                    ["-H", "Content-Type: application/json", "-d", json.dumps(json_body, ensure_ascii=False)]
                )
            if token:
                args.extend(["-H", f"Authorization: Bearer {token}"])
            raw = self.curl_raw(args)
            return parse_api_json(raw, context=f"{method} {path}")

        hdrs: dict[str, str] = {}
        if token:
            hdrs["Authorization"] = f"Bearer {token}"
        _, body, _ = self._direct_request(method, path, headers=hdrs, json_body=json_body)
        return parse_api_json(body, context=f"{method} {path}")

    def health(self) -> dict[str, Any]:
        return self.request_json("GET", "/api/v1/base/health")

    def version(self) -> dict[str, Any]:
        return self.request_json("GET", "/api/v1/base/version")

    def login(self, *, force: bool = False) -> str:
        if self._token and not force:
            return self._token
        data = self.request_json(
            "POST",
            "/api/v1/base/access_token",
            json_body={"username": self.username, "password": self.password},
        )
        assert_api_ok(data, context="login")
        token = data["data"]["access_token"]
        if not token:
            raise ApiError("login 成功但 access_token 为空")
        self._token = token
        return token

    def userinfo(self, *, token: str | None = None) -> dict[str, Any]:
        tok = token or self._token or self.login()
        data = self.request_json("GET", "/api/v1/base/userinfo", token=tok)
        return assert_api_ok(data, context="userinfo")

    def get(self, path: str, *, token: str | None = None) -> dict[str, Any]:
        tok = token or self._token or self.login()
        return self.request_json("GET", path, token=tok)

    def post_json(self, path: str, body: dict[str, Any], *, token: str | None = None) -> dict[str, Any]:
        tok = token or self._token or self.login()
        return self.request_json("POST", path, token=tok, json_body=body)

    def post_multipart(
        self,
        path: str,
        fields: dict[str, str],
        file_field: str,
        file_path: Path,
        *,
        token: str | None = None,
    ) -> dict[str, Any]:
        tok = token or self._token or self.login()
        if self.mode == "docker":
            container_path = f"/tmp/cli_upload_{file_path.name}"
            subprocess.check_call(
                ["docker", "cp", str(file_path), f"{self.backend_container}:{container_path}"]
            )
            args = [
                "-X",
                "POST",
                f"{self.api_base}{path}",
                "-H",
                f"Authorization: Bearer {tok}",
                "-F",
                f"{file_field}=@{container_path}",
            ]
            for key, value in fields.items():
                args.extend(["-F", f"{key}={value}"])
            raw = self.curl_raw(args)
            data = parse_api_json(raw, context=f"POST {path}")
            assert_api_ok(data, context=f"POST {path}")
            return data

        boundary = f"----HospitalCli{uuid.uuid4().hex}"
        parts: list[bytes] = []
        for key, value in fields.items():
            parts.append(
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="{key}"\r\n\r\n'
                f"{value}\r\n".encode("utf-8")
            )
        mime = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
        file_bytes = file_path.read_bytes()
        parts.append(
            (
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="{file_field}"; filename="{file_path.name}"\r\n'
                f"Content-Type: {mime}\r\n\r\n"
            ).encode("utf-8")
            + file_bytes
            + b"\r\n"
        )
        parts.append(f"--{boundary}--\r\n".encode("utf-8"))
        body = b"".join(parts)
        _, resp_body, _ = self._direct_request(
            "POST",
            path,
            headers={
                "Authorization": f"Bearer {tok}",
                "Content-Type": f"multipart/form-data; boundary={boundary}",
            },
            body=body,
            timeout=300,
        )
        data = parse_api_json(resp_body, context=f"POST {path}")
        assert_api_ok(data, context=f"POST {path}")
        return data

    def export_v2(
        self,
        job_id: int,
        dest: Path,
        export_type: str = "bill",
        *,
        token: str | None = None,
    ) -> None:
        tok = token or self._token or self.login()
        payload = {"exportType": export_type, "useStrategyEngine": True}
        path = f"/api/hospital-reconciliations/{job_id}/export-v2"
        if self.mode == "docker":
            container_tmp = f"/tmp/s8_job_{job_id}_{export_type}.xlsx"
            self.curl_raw(
                [
                    "-X",
                    "POST",
                    f"{self.api_base}{path}",
                    "-H",
                    f"Authorization: Bearer {tok}",
                    "-H",
                    "Content-Type: application/json",
                    "-d",
                    json.dumps(payload, ensure_ascii=False),
                    "-o",
                    container_tmp,
                ]
            )
            subprocess.check_call(
                ["docker", "cp", f"{self.backend_container}:{container_tmp}", str(dest)]
            )
        else:
            _, body, _ = self._direct_request(
                "POST",
                path,
                headers={
                    "Authorization": f"Bearer {tok}",
                    "Content-Type": "application/json",
                },
                json_body=payload,
                timeout=300,
            )
            dest.write_bytes(body)
        if dest.read_bytes()[:2] != b"PK":
            snippet = dest.read_text(encoding="utf-8", errors="replace")[:200]
            raise ApiError(f"export-v2 Job #{job_id} 非 xlsx: {snippet}")

    def list_reconciliations(
        self, *, hospital_name: str | None = None, token: str | None = None
    ) -> list[dict[str, Any]]:
        tok = token or self._token or self.login()
        if hospital_name:
            path = f"/api/hospital-reconciliations?{urlencode({'hospital_name': hospital_name})}"
        else:
            path = "/api/hospital-reconciliations"
        data = self.request_json("GET", path, token=tok)
        assert_api_ok(data, context="list reconciliations")
        payload = data.get("data")
        if isinstance(payload, list):
            return payload
        if isinstance(payload, dict):
            for key in ("items", "rows", "content", "list"):
                rows = payload.get(key)
                if isinstance(rows, list):
                    return rows
        return []

    def customers(self, *, token: str | None = None) -> list[dict[str, Any]]:
        tok = token or self._token or self.login()
        data = self.request_json("GET", "/api/v1/customers", token=tok)
        assert_api_ok(data, context="customers")
        rows = data.get("data")
        return rows if isinstance(rows, list) else []

    def customer_by_code(self, code: str, *, token: str | None = None) -> dict[str, Any] | None:
        target = code.strip().upper()
        for row in self.customers(token=token):
            row_code = str(row.get("code") or "").strip().upper()
            if row_code == target:
                return row
        return None

    def product_rules(self, customer_id: int, *, token: str | None = None) -> list[dict[str, Any]]:
        tok = token or self._token or self.login()
        data = self.request_json("GET", f"/api/v1/customers/{customer_id}/product-rules", token=tok)
        assert_api_ok(data, context="product-rules")
        rows = data.get("data")
        return rows if isinstance(rows, list) else []

    def simulate_billing(
        self,
        *,
        customer_id: int,
        hospital_name: str,
        sample_row: dict[str, Any],
        rule_id: int | None = None,
        token: str | None = None,
    ) -> dict[str, Any]:
        tok = token or self._token or self.login()
        body: dict[str, Any] = {
            "customerId": customer_id,
            "hospitalName": hospital_name,
            "sampleRow": sample_row,
        }
        if rule_id is not None:
            body["ruleId"] = rule_id
        data = self.request_json("POST", "/api/v1/billing-rules/simulate", token=tok, json_body=body)
        assert_api_ok(data, context="simulate")
        payload = data.get("data")
        return payload if isinstance(payload, dict) else {}

    def reconciliation_rows(self, job_id: int, *, token: str | None = None) -> list[dict[str, Any]]:
        tok = token or self._token or self.login()
        rows: list[dict[str, Any]] = []
        page = 1
        while True:
            path = f"/api/hospital-reconciliations/{job_id}/rows?page={page}&size=500"
            data = self.request_json("GET", path, token=tok)
            assert_api_ok(data, context=f"rows job {job_id}")
            payload = data.get("data") or {}
            batch = payload.get("rows") or payload.get("items") or []
            rows.extend(batch)
            total = payload.get("total") or payload.get("totalElements") or len(batch)
            if page * 500 >= total or not batch:
                break
            page += 1
        return rows

    def update_customer(
        self, customer_id: int, body: dict[str, Any], *, token: str | None = None
    ) -> dict[str, Any]:
        tok = token or self._token or self.login()
        data = self.request_json("PUT", f"/api/v1/customers/{customer_id}", token=tok, json_body=body)
        assert_api_ok(data, context="update customer")
        payload = data.get("data")
        return payload if isinstance(payload, dict) else {}

    def create_product_rule(
        self, customer_id: int, body: dict[str, Any], *, token: str | None = None
    ) -> dict[str, Any]:
        tok = token or self._token or self.login()
        data = self.request_json(
            "POST", f"/api/v1/customers/{customer_id}/product-rules", token=tok, json_body=body
        )
        assert_api_ok(data, context="create product rule")
        payload = data.get("data")
        return payload if isinstance(payload, dict) else {}


_default_client: ApiClient | None = None


def get_client() -> ApiClient:
    global _default_client
    if _default_client is None:
        _default_client = ApiClient.from_env()
    return _default_client


def configure_client(**kwargs: Any) -> ApiClient:
    global _default_client
    client = get_client()
    client.configure(**kwargs)
    _default_client = client
    return client
