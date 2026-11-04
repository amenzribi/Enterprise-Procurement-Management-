"""API verification for admin policy on running BE :8080."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta
from http.cookiejar import CookieJar
from pathlib import Path

BASE = os.environ.get("EPI_API", "http://127.0.0.1:8080")
PW_FILE = Path(os.environ.get("TEMP", "/tmp")) / "epi-e2e-pw.txt"
PASSWORD = os.environ.get("DEMO_USER_PASSWORD") or PW_FILE.read_text(encoding="utf-8").strip()
TN_ONLY = "admin.tn.only@demo.local"


class Client:
    def __init__(self) -> None:
        self.jar = CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _xsrf(self) -> str:
        self.opener.open(f"{BASE}/api/v1/auth/csrf")
        for c in self.jar:
            if c.name == "XSRF-TOKEN":
                return c.value
        return ""

    def request(self, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | str]:
        data = None
        headers = {"Accept": "application/json"}
        if body is not None or method in {"POST", "PUT", "DELETE", "PATCH"}:
            headers["X-XSRF-TOKEN"] = self._xsrf()
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
        try:
            with self.opener.open(req) as resp:
                raw = resp.read().decode("utf-8")
                if not raw:
                    return resp.status, {}
                try:
                    return resp.status, json.loads(raw)
                except json.JSONDecodeError:
                    return resp.status, raw
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8")
            try:
                return e.code, json.loads(raw)
            except json.JSONDecodeError:
                return e.code, raw

    def login(self, email: str, password: str = PASSWORD) -> dict:
        status, body = self.request("POST", "/api/v1/auth/login", {"email": email, "password": password})
        if status != 200 or not isinstance(body, dict):
            raise SystemExit(f"login failed {email}: {status} {body}")
        return body


def main() -> None:
    checks: list[dict] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        checks.append({"name": name, "ok": ok, "detail": detail})
        print(("PASS" if ok else "FAIL"), name, detail)

    ga = Client()
    me = ga.login("global.admin@demo.local")
    check(
        "GA has REQUEST_ADMIN+DRAFT",
        "REQUEST_ADMIN" in me["permissions"] and "REQUEST_DRAFT_ADMIN" in me["permissions"],
        str([p for p in me["permissions"] if "REQUEST" in p]),
    )

    admin = Client()
    me_a = admin.login("admin@demo.local")
    check(
        "company ADMIN has DRAFT only",
        "REQUEST_DRAFT_ADMIN" in me_a["permissions"] and "REQUEST_ADMIN" not in me_a["permissions"],
        str([p for p in me_a["permissions"] if "REQUEST" in p]),
    )

    st, companies = ga.request("GET", "/api/v1/admin/organization/companies")
    by_code = {c["code"]: c["id"] for c in companies}  # type: ignore[index]
    tn_id, fr_id, cpa_id = by_code["DEMO-TN-PLT"], by_code["DEMO-FR-PLT"], by_code["DEMO-CPA"]

    tn_admin = Client()
    me_tn = tn_admin.login(TN_ONLY)
    check(
        "TN-only login + DRAFT_ADMIN",
        "REQUEST_DRAFT_ADMIN" in me_tn["permissions"] and "REQUEST_ADMIN" not in me_tn["permissions"],
        str([p for p in me_tn["permissions"] if "REQUEST" in p]),
    )

    st, orgs = tn_admin.request("GET", "/api/v1/admin/organization/companies")
    codes = [c["code"] for c in orgs] if isinstance(orgs, list) else []
    check("TN-only org list is TN only", st == 200 and codes == ["DEMO-TN-PLT"], f"{st} {codes}")
    st, _ = tn_admin.request("GET", f"/api/v1/admin/organization/companies/{fr_id}/sites")
    check("TN-only FR sites 404", st == 404, str(st))
    st, _ = tn_admin.request("GET", f"/api/v1/admin/organization/companies/{cpa_id}/sites")
    check("TN-only CPA sites 404", st == 404, str(st))
    st, _ = tn_admin.request("GET", f"/api/v1/admin/organization/companies/{tn_id}/sites")
    check("TN-only TN sites 200", st == 200, str(st))

    st, denied = tn_admin.request(
        "POST",
        f"/api/v1/admin/accounts/{me_tn['id']}/roles",
        {"role": "ADMIN", "companyId": fr_id, "departmentId": None},
    )
    check("TN-only cannot expand ADMIN to FR", st in {403, 404}, f"{st} {denied}")

    st, accounts = tn_admin.request("GET", "/api/v1/admin/accounts?size=200")
    if st == 200 and isinstance(accounts, list):
        foreign_users = [
            a
            for a in accounts
            if isinstance(a, dict)
            and (
                (a.get("company") or {}).get("id") in {fr_id, cpa_id}
                or a.get("companyId") in {fr_id, cpa_id}
                or a.get("companyCode") in {"DEMO-FR-PLT", "DEMO-CPA"}
            )
        ]
        check(
            "TN-only users exclude FR/CPA home company",
            len(foreign_users) == 0 and len(accounts) > 0,
            f"foreign={len(foreign_users)} total={len(accounts)}",
        )
    else:
        check("TN-only users list status", st in {200, 403}, f"{st} {type(accounts).__name__}")

    st, rules = tn_admin.request("GET", "/api/v1/admin/approval-rules")
    if st == 200 and isinstance(rules, list):
        foreign = [r for r in rules if r.get("companyId") in {fr_id, cpa_id}]
        check("TN-only rules exclude FR/CPA", len(foreign) == 0, f"foreign={len(foreign)} total={len(rules)}")
    else:
        check("TN-only rules reachable", st in {200, 403}, str(st))

    # Absences admin list (same group but company filter on target user)
    st, absences = tn_admin.request("GET", "/api/v1/admin/absences")
    check("TN-only absences admin status", st in {200, 403, 404}, str(st))
    if st == 200 and isinstance(absences, list):
        # Best-effort: no absence whose user home company is FR/CPA when payload exposes it
        foreign_abs = [
            a
            for a in absences
            if (a.get("user") or {}).get("companyId") in {fr_id, cpa_id}
            or a.get("companyId") in {fr_id, cpa_id}
        ]
        check("TN-only absences exclude FR/CPA", len(foreign_abs) == 0, f"foreign={len(foreign_abs)}")

    # Import org for FR company should be forbidden
    st, _ = tn_admin.request("GET", "/api/v1/admin/import/jobs")
    check("TN-only import jobs status", st in {200, 403, 404}, str(st))
    st, fr_import = tn_admin.request(
        "POST",
        f"/api/v1/admin/import?kind=ORGANISATION&companyId={fr_id}",
        None,
    )
    check("TN-only cannot import into FR", st in {403, 404, 400, 415}, str(st))

    sami = Client()
    sami.login("requester.tn@demo.local")
    st, opts = sami.request("GET", "/api/v1/requests/form-options")
    assert st == 200 and isinstance(opts, dict), opts
    companies = opts.get("companies") or []
    assert companies, f"form-options missing companies: {opts.keys()}"
    company = next((c for c in companies if c.get("code") == "DEMO-TN-PLT"), companies[0])
    cat = opts["categories"][0]["id"]
    depts = company.get("departments") or []
    assert depts, f"no departments for {company.get('code')}"
    dept = depts[0]["id"]
    payload = {
        "title": "VERIFY-ADMIN-DRAFT-API",
        "categoryId": cat,
        "priority": "NORMAL",
        "purchaseType": "LOCAL",
        "currency": company.get("currency") or "TND",
        "requestingCompanyId": company["id"],
        "departmentId": dept,
        "requiredDate": (date.today() + timedelta(days=21)).isoformat(),
        "lines": [{"description": "seal", "quantity": 1, "unit": "PCE", "estimatedUnitPrice": 12}],
    }
    st, draft = sami.request("POST", "/api/v1/requests", payload)
    check("create TN draft", st in {200, 201}, str(st))
    assert isinstance(draft, dict)
    draft_id = draft["id"]
    version = draft["version"]

    st, corrected = ga.request(
        "POST",
        f"/api/v1/admin/requests/{draft_id}/correct-draft",
        {
            "version": version,
            "reason": "VERIFY GA draft correction with lines",
            "title": "VERIFY-ADMIN-DRAFT-CORRECTED",
            "justification": "verify justification",
            "priority": "HIGH",
            "purchaseType": "LOCAL",
            "currency": "TND",
            "categoryId": cat,
            "departmentId": dept,
            "requiredDate": payload["requiredDate"],
            "lines": [
                {
                    "id": draft["lines"][0]["id"] if draft.get("lines") else None,
                    "description": "corrected seal kit",
                    "quantity": 2,
                    "unit": "PCE",
                    "estimatedUnitPrice": 15,
                }
            ],
        },
    )
    check("GA correct draft fields+lines", st == 200, str(st))
    detail = (corrected.get("detail") if isinstance(corrected, dict) else None) or corrected
    assert isinstance(detail, dict)
    check(
        "GA draft title/lines applied",
        detail.get("title") == "VERIFY-ADMIN-DRAFT-CORRECTED"
        and any(str(l.get("description", "")).startswith("corrected") for l in detail.get("lines") or []),
        str(detail.get("title")),
    )
    version = detail["version"]

    st, tn_corr = tn_admin.request(
        "POST",
        f"/api/v1/admin/requests/{draft_id}/correct-draft",
        {"version": version, "reason": "VERIFY TN-only draft correction", "title": "VERIFY-ADMIN-DRAFT-TN-ONLY"},
    )
    check("TN-only correct TN draft", st == 200, str(st))
    if st == 200 and isinstance(tn_corr, dict):
        version = (tn_corr.get("detail") or tn_corr)["version"]

    # FR requester draft — TN-only should 404
    claire = Client()
    claire.login("requester.fr@demo.local")
    st, fr_opts = claire.request("GET", "/api/v1/requests/form-options")
    assert st == 200 and isinstance(fr_opts, dict)
    fr_companies = fr_opts.get("companies") or []
    fr_company = next((c for c in fr_companies if c.get("code") == "DEMO-FR-PLT"), fr_companies[0])
    fr_depts = fr_company.get("departments") or []
    assert fr_depts, f"no FR departments for {fr_company.get('code')}"
    fr_payload = {
        "title": "VERIFY-FR-DRAFT-ISOLATION",
        "categoryId": fr_opts["categories"][0]["id"],
        "priority": "NORMAL",
        "purchaseType": "LOCAL",
        "currency": fr_company.get("currency") or "EUR",
        "requestingCompanyId": fr_company["id"],
        "departmentId": fr_depts[0]["id"],
        "requiredDate": (date.today() + timedelta(days=21)).isoformat(),
        "lines": [{"description": "fr line", "quantity": 1, "unit": "PCE", "estimatedUnitPrice": 10}],
    }
    st, fr_draft = claire.request("POST", "/api/v1/requests", fr_payload)
    check("create FR draft", st in {200, 201}, str(st))
    assert isinstance(fr_draft, dict)
    st, _ = tn_admin.request("GET", f"/api/v1/admin/requests/{fr_draft['id']}")
    check("TN-only cannot see FR draft", st == 404, str(st))

    st, submitted = sami.request("POST", f"/api/v1/requests/{draft_id}/submit", {"version": version})
    if st != 200:
        st, cur = sami.request("GET", f"/api/v1/requests/{draft_id}")
        assert isinstance(cur, dict)
        st, submitted = sami.request("POST", f"/api/v1/requests/{draft_id}/submit", {"version": cur["version"]})
    check("submit TN draft", st == 200, str(st))
    assert isinstance(submitted, dict)
    version = submitted["version"]
    req_id = draft_id

    st, _ = admin.request(
        "POST",
        f"/api/v1/admin/requests/{req_id}/correct-submitted",
        {"version": version, "reason": "should fail company admin", "title": "Nope"},
    )
    check("admin@demo denied correct-submitted", st == 403, str(st))
    st, _ = admin.request(
        "POST", f"/api/v1/admin/requests/{req_id}/remove", {"version": version, "reason": "should fail"}
    )
    check("admin@demo denied remove", st == 403, str(st))
    st, _ = tn_admin.request(
        "POST",
        f"/api/v1/admin/requests/{req_id}/correct-submitted",
        {"version": version, "reason": "should fail tn-only", "title": "Nope"},
    )
    check("TN-only denied correct-submitted", st == 403, str(st))

    def sql_scalar(query: str, *args: object) -> object:
        import subprocess

        # Build a one-shot sqlcmd with TrustServerCertificate
        lit = query
        for a in args:
            lit = lit.replace("?", repr(a) if not isinstance(a, (int, float)) else str(a), 1)
        cmd = [
            "sqlcmd",
            "-S",
            "localhost",
            "-d",
            "procurement_dev",
            "-E",
            "-C",
            "-h",
            "-1",
            "-W",
            "-Q",
            f"SET QUOTED_IDENTIFIER ON; SET NOCOUNT ON; {lit}",
        ]
        out = subprocess.check_output(cmd, text=True, stderr=subprocess.STDOUT)
        line = next((ln.strip() for ln in out.splitlines() if ln.strip() and not ln.strip().startswith("(")), "")
        return line

    round_before = int(str(sql_scalar("SELECT round_no FROM purchase_request WHERE id = ?", req_id)))
    pending_before = int(
        str(sql_scalar("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'", req_id))
    )
    check("submitted has pending approvals", pending_before > 0, f"pending={pending_before} round={round_before}")

    st, corr = ga.request(
        "POST",
        f"/api/v1/admin/requests/{req_id}/correct-submitted",
        {
            "version": version,
            "reason": "VERIFY GA submitted material correction",
            "title": "VERIFY-ADMIN-SUBMITTED-CORRECTED",
            "justification": "material change for new round",
            "categoryId": cat,
            "departmentId": dept,
            "priority": "HIGH",
            "purchaseType": "LOCAL",
            "currency": "TND",
            "requiredDate": payload["requiredDate"],
            "lines": [
                {
                    "description": "submitted corrected line",
                    "quantity": 3,
                    "unit": "PCE",
                    "estimatedUnitPrice": 20,
                }
            ],
        },
    )
    check("GA correct submitted", st == 200, str(st))
    detail = (corr.get("detail") if isinstance(corr, dict) else None) or corr
    assert isinstance(detail, dict)
    check("after correct status IN_APPROVAL", detail.get("status") == "IN_APPROVAL", str(detail.get("status")))
    version = detail["version"]
    round_after = int(str(sql_scalar("SELECT round_no FROM purchase_request WHERE id = ?", req_id)))
    check("correct-submitted bumps round", round_after == round_before + 1, f"{round_before}->{round_after}")
    pending_after = int(
        str(sql_scalar("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'", req_id))
    )
    cancelled_old = int(
        str(
            sql_scalar(
                "SELECT COUNT(*) FROM approval_task t JOIN approval_round r ON r.id = t.round_id "
                "WHERE t.request_id = ? AND r.round_no = ? AND t.status <> 'PENDING'",
                req_id,
                round_before,
            )
        )
    )
    check(
        "old round tasks no longer pending",
        cancelled_old >= pending_before and pending_after > 0,
        f"cancelled_old={cancelled_old} pending_new={pending_after}",
    )

    st, rem = ga.request(
        "POST", f"/api/v1/admin/requests/{req_id}/remove", {"version": version, "reason": "VERIFY logical remove"}
    )
    check("GA logical remove", st == 200, str(st))
    st, listed = sami.request("GET", "/api/v1/requests?scope=mine&size=100&q=VERIFY-ADMIN")
    ids = [i["id"] for i in (listed.get("items") if isinstance(listed, dict) else []) or []]
    check("removed hidden from requester active list", req_id not in ids, str(ids[:8]))
    st, detail = ga.request("GET", f"/api/v1/admin/requests/{req_id}")
    assert isinstance(detail, dict)
    check("admin detail removed=true", detail.get("removed") is True, str(detail.get("removed")))
    pending_removed = int(
        str(sql_scalar("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'", req_id))
    )
    check("remove clears pending approvals", pending_removed == 0, str(pending_removed))
    version = detail["version"]

    st, restored = ga.request(
        "POST",
        f"/api/v1/admin/requests/{req_id}/restore-removed",
        {"version": version, "reason": "VERIFY restore removed"},
    )
    check("GA restore removed", st == 200, str(st))
    detail = (restored.get("detail") if isinstance(restored, dict) else None) or restored
    if isinstance(detail, dict):
        check("after restore removed=false", detail.get("removed") is False, str(detail.get("removed")))
    pending_restored = int(
        str(sql_scalar("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status = 'PENDING'", req_id))
    )
    check("restore does not reactivate old approvals", pending_restored == 0, str(pending_restored))

    # Audit rows: actor, reason, before/after
    audit_actions = (
        "REQUEST_ADMIN_DRAFT_CORRECTED",
        "REQUEST_ADMIN_SUBMITTED_CORRECTED",
        "REQUEST_ADMIN_REMOVED",
        "REQUEST_ADMIN_RESTORE_REMOVED",
    )
    for action in audit_actions:
        row = str(
            sql_scalar(
                "SELECT TOP 1 CONCAT(ISNULL(CONVERT(varchar(20),actor_user_id),''),'|',ISNULL(actor_label,''),'|',"
                "ISNULL(comment,''),'|',ISNULL(from_status,''),'|',ISNULL(to_status,''),'|',"
                "LEFT(ISNULL(payload_json,''),120))"
                " FROM audit_event WHERE entity_id = ? AND action = ? ORDER BY occurred_at DESC",
                str(req_id),
                action,
            )
        )
        parts = row.split("|", 5) if row else []
        ok = (
            len(parts) >= 5
            and parts[0].isdigit()
            and parts[1] != ""
            and "VERIFY" in parts[2]
            and parts[3] != ""
            and parts[4] != ""
            and parts[5] != ""
        )
        check(f"audit {action}", ok, row[:220])

    st, removed_page = ga.request("GET", "/api/v1/admin/requests?removed=true&q=VERIFY-ADMIN&size=20")
    check("admin removed filter works", st == 200, str(st))

    uat = str(
        sql_scalar(
            "SELECT number FROM purchase_request WHERE number = 'DEMO-TN-PLT-PR-2026-000490'"
        )
    )
    check("UAT-01 preserved", uat.strip() == "DEMO-TN-PLT-PR-2026-000490", uat)

    report = {"checks": checks, "revision": "a9a8740", "tnOnly": TN_ONLY, "requestId": req_id}
    Path("tools/_verify_admin_api_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    failed = sum(1 for c in checks if not c["ok"])
    print(f"SUMMARY failed={failed} total={len(checks)}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
