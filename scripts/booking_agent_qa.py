#!/usr/bin/env python3
"""Run Booking Agent checks against a clone on the dedicated local QA PostgreSQL cluster."""
import argparse
from base64 import b64encode
from contextlib import contextmanager, nullcontext
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
import os
from pathlib import Path
from queue import Empty
import re
import secrets
import signal
import subprocess
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
from uuid import UUID, uuid4

from booking_agent_activation import ActivationInbox

QA_HOST = "127.0.0.1"
QA_PORT = 15439
TEMPLATE = re.compile(r"casms_(?:qa|ba_final)_[a-z0-9_]{1,40}")
FEATURES = ("list", "create", "lookup", "detail", "contract", "confirm", "cancel", "cabin", "sale")
INVENTORY = """
SELECT json_build_object('database',current_database(),
 'version',(SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$'),
 'published',(SELECT count(*) FROM "Cruise" WHERE status='PUBLISHED'),
 'agents',(SELECT count(*) FROM "UserRole" r JOIN "User" u USING(user_id)
           WHERE role='BOOKING_AGENT' AND u.status='ACTIVE'),
 'assignments',(SELECT count(*) FROM "CruiseStaff" WHERE role_in_cruise='BOOKING_AGENT' AND status='ACTIVE'),
 'bookings',(SELECT count(*) FROM "Booking"),
 'confirmedPayments',(SELECT count(*) FROM "Payment" WHERE status='Confirmed'),
 'preparedDrafts',coalesce((SELECT json_agg(cruise_id) FROM
    (SELECT c.cruise_id FROM "Cruise" c JOIN "CruisePlanReview" r USING(cruise_id)
     WHERE c.status='DRAFT' AND c.departure_datetime>now() AND r.status='APPROVED' AND r.ready_at IS NOT NULL
     ORDER BY c.departure_datetime, c.cruise_id LIMIT 10) r),'[]'::json),
 'candidates',coalesce((SELECT json_agg(cruise_id) FROM
    (SELECT cruise_id FROM "Cruise" WHERE status='PUBLISHED' ORDER BY cruise_id LIMIT 10) q),'[]'::json));"""


class Blocked(Exception):
    pass


class Failed(Exception):
    pass


class TransportLoss(Failed):
    pass


def require(condition, message):
    if not condition:
        raise Failed(message)


def qa_config(path, template):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        key, separator, value = line.partition("=")
        if separator and key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
            values[key] = value.strip().strip('"').strip("'")
    url = values.get("DB_URL", "")
    match = re.fullmatch(r"jdbc:postgresql://(?:127\.0\.0\.1|localhost):15439/([a-z0-9_]+)"
                         r"(?:\?stringtype=unspecified)?", url)
    source = template or (match[1] if match else "")
    if not match or not TEMPLATE.fullmatch(match[1]) or not TEMPLATE.fullmatch(source):
        raise Blocked("Use a CASMS QA template on loopback PostgreSQL port 15439")
    if not values.get("DB_USERNAME") or "DB_PASSWORD" not in values:
        raise Blocked("QA PostgreSQL credentials missing")
    return source, values


class Pg:
    def __init__(self, executable, values):
        self.values = values
        self.base = [executable, "-X", "-w", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-h", QA_HOST,
                     "-p", str(QA_PORT), "-U", values["DB_USERNAME"]]

    def query(self, database, sql, *, readonly=True):
        env = {key: val for key, val in os.environ.items() if not key.startswith("PG")}
        env.update(PGPASSWORD=self.values["DB_PASSWORD"], PGCONNECT_TIMEOUT="5",
                   PGOPTIONS="-c statement_timeout=30000" + (" -c default_transaction_read_only=on" if readonly else ""))
        proc = subprocess.run(self.base + ["-d", database, "-c", sql], capture_output=True,
                              text=True, env=env, timeout=45, check=False)
        require(proc.returncode == 0, "PostgreSQL QA command failed; check cluster, credentials and template")
        return proc.stdout.strip()

    def inventory(self, database):
        result = json.loads(self.query(database, INVENTORY))
        require(result["database"] == database, "Unexpected QA database identity")
        return result

    def history(self, database, through):
        require(type(through) is int, "No original migration version")
        return self.query(database, """SELECT md5(string_agg(to_jsonb(h)::text, '|' ORDER BY installed_rank))
            FROM flyway_schema_history h
            WHERE success AND version ~ '^[0-9]+$' AND version::int <= """ + str(through))

    def committed_inventory(self, database):
        return self.query(database, """SELECT json_build_array((SELECT count(*) FROM "Booking"),
            (SELECT count(*) FROM "CabinAssignment"),(SELECT count(*) FROM "BookingPassenger"));""")


@contextmanager
def backend(args, pg, database, activation=None):
    allowed = {"PATH", "SYSTEMROOT", "WINDIR", "SYSTEMDRIVE", "TEMP", "TMP", "USERPROFILE",
               "HOME", "JAVA_HOME", "LANG", "LC_ALL"}
    env = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    env.update(SPRING_DATASOURCE_USERNAME=pg.values["DB_USERNAME"],
               SPRING_DATASOURCE_PASSWORD=pg.values["DB_PASSWORD"], JWT_SECRET=secrets.token_hex(48),
               SEPAY_ENABLED="false", CASMS_BOOKING_EMAIL_ENABLED="false")
    command = [args.java, "-jar", str(args.jar.resolve()), "--spring.config.location=classpath:/",
               "--spring.profiles.active=booking-agent-qa",
               f"--spring.datasource.url=jdbc:postgresql://{QA_HOST}:{QA_PORT}/{database}?stringtype=unspecified",
               "--casms.flyway.legacy-upgrade-jdbc-url=", "--spring.flyway.clean-disabled=true",
               "--spring.flyway.validate-on-migrate=true", "--spring.jpa.hibernate.ddl-auto=validate",
               "--spring.jpa.show-sql=false", "--spring.mail.host=127.0.0.1",
               "--casms.staff-assignment-email.enabled=false",
               "--casms.booking.cancellation-workflow.enabled=true", "--server.address=127.0.0.1", "--server.port=0",
               "--logging.level.com.casms=INFO", "--logging.level.com.casms.booking.service.BookingService=WARN",
               "--logging.level.com.casms.payment.service=WARN", "--logging.level.com.casms.identity.service.AuthService=WARN"]
    if activation:
        token_key = b64encode(secrets.token_bytes(32)).decode("ascii")
        env["SPRING_APPLICATION_JSON"] = json.dumps({"casms": {"employee-activation-email": {"token-key": token_key}}})
        command.extend([f"--spring.mail.port={activation.server_address[1]}",
                        "--spring.mail.properties.mail.smtp.auth=false",
                        "--spring.mail.properties.mail.smtp.starttls.enable=false",
                        "--spring.mail.properties.mail.smtp.starttls.required=false",
                        "--casms.employee-activation-email.enabled=true",
                        "--casms.employee-activation-email.portal-url=https://booking-agent.example.test/activate",
                        "--casms.employee-activation-email.from=activation@example.test",
                        "--casms.employee-activation-email.poll-ms=1000"])
    else:
        command.extend(["--spring.mail.port=1", "--casms.employee-activation-email.enabled=false"])
    log = args.evidence.with_suffix(".backend.log")
    flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    with log.open("wb") as stream:
        process = subprocess.Popen(command, env=env, stdout=stream, stderr=subprocess.STDOUT, creationflags=flags)
        try:
            deadline = time.monotonic() + 100
            while time.monotonic() < deadline and process.poll() is None:
                content = log.read_text(encoding="utf-8", errors="replace")
                match = re.search(r"Tomcat started on port (\d+)", content)
                if match and "Role permissions initialized successfully" in content:
                    yield f"http://{QA_HOST}:{match[1]}/api"
                    return
                time.sleep(0.5)
            raise Failed("QA Backend startup failed or timed out; inspect its local Backend log")
        finally:
            if process.poll() is None:
                try:
                    if os.name == "nt":
                        process.send_signal(signal.CTRL_BREAK_EVENT)
                    else:
                        process.terminate()
                    process.wait(10)
                except (OSError, subprocess.TimeoutExpired):
                    process.kill()
                    process.wait()


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Api:
    def __init__(self, base, log):
        self.base, self.log = base, log
        self.client = build_opener(ProxyHandler({}), NoRedirect())

    def call(self, label, method, path, token=None, *, payload=None, key=None, status=(200,), code=None):
        row = {"check": label, "method": method, "path": path.split("?")[0]}
        self.log.append(row)
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        if payload is not None:
            headers["Content-Type"] = "application/json"
        if key:
            headers["Idempotency-Key"] = key
        request = Request(self.base + path, headers=headers, method=method,
                          data=json.dumps(payload).encode() if payload is not None else None)
        try:
            try:
                response = self.client.open(request, timeout=15)
            except HTTPError as error:
                response = error
            with response:
                row["http"] = response.status
                body = response.read()
            envelope = json.loads(body) if body else {}
        except (OSError, ValueError) as error:
            row["transport"] = type(error).__name__
            raise TransportLoss(f"{label}: unavailable response; POST was not retried") from None
        business_code = envelope.get("code", envelope.get("error"))
        if isinstance(business_code, str) and re.fullmatch(r"[A-Z0-9_]{1,80}", business_code):
            row["code"] = business_code
        codes = (code,) if isinstance(code, str) else code
        require(row["http"] in status and (codes is None or business_code in codes),
                f"{label}: unexpected HTTP {row['http']} {row.get('code', '')}")
        if 200 <= row["http"] < 300 and row["http"] != 204:
            require(envelope.get("success") is True, label + ": invalid success envelope")
        return envelope.get("data")

    def login(self, actor, *, role=None, permissions=(), credentials=None):
        prefix = "CASMS_BA_QA_" + actor
        username, password = credentials or (os.getenv(prefix + "_USERNAME"), os.getenv(prefix + "_PASSWORD"))
        if not username or not password:
            raise Blocked(actor + " QA credentials are required")
        token = self.call(actor + " login", "POST", "/auth/login",
                          payload={"username": username, "password": password})["accessToken"]
        me = self.call(actor + " permissions", "GET", "/auth/me", token)
        if (role and role not in me["roles"]) or not set(permissions).issubset(me["effectivePermissions"]):
            raise Blocked(actor + " lacks required role or permissions")
        if role == "BOOKING_AGENT" and set(me["roles"]) != {"BOOKING_AGENT"}:
            raise Blocked(actor + " must be a scoped Booking Agent without an additional role")
        return token, str(UUID(me["userId"]))


def url(path, **params):
    return path + "?" + urlencode(params)


def activate_qa_employee(api, inbox, employee_code, role):
    hr, _ = api.login("HR", permissions=("USER_CREATE",))
    roles = api.call("QA employee assignable roles", "GET", "/employees/assignable-roles", hr)
    if role not in roles:
        raise Blocked(f"QA employee administrator cannot provision {role}")
    recipient = employee_code + "@example.test"
    employee = api.call(f"create QA employee {role}", "POST", "/employees", hr, status=(201,),
                        payload={"employeeCode": employee_code, "fullName": "Booking Agent QA",
                                 "email": recipient, "role": role})
    require(employee["status"] == "PENDING_ACTIVATION", "Expected pending employee activation")
    try:
        token = inbox.tokens[recipient].get(timeout=30)
    except Empty as error:
        raise Blocked("No employee activation email arrived at the isolated QA mailbox") from error
    password = secrets.token_urlsafe(32)
    api.call(f"activate QA employee {role}", "POST", "/auth/employee-account/activate",
             payload={"token": token, "password": password})
    return recipient, password


def plans_from(path):
    plans = json.loads(path.read_text(encoding="utf-8-sig")).get("cruises", []) if path else []
    if len(plans) not in (0, 2):
        raise Blocked("Provisioning requires exactly two candidate cruises")
    for plan in plans:
        plan["cruiseId"] = str(UUID(plan["cruiseId"]))
        scope = plan["scope"]
        if scope not in ("CABIN_ONLY", "SELECTED_PACKAGES", "WHOLE_CRUISE"):
            raise Blocked("Invalid sale scope")
        for key in ("legacyPackageIds", "fareOfferIds"):
            plan[key] = [str(UUID(item)) for item in plan.get(key, [])]
            if len(set(plan[key])) != len(plan[key]):
                raise Blocked("Duplicate selected package")
        if scope == "SELECTED_PACKAGES" and not plan["legacyPackageIds"]:
            raise Blocked("SELECTED_PACKAGES needs legacy package IDs")
        if plan.get("tourPackageId"):
            plan["tourPackageId"] = str(UUID(plan["tourPackageId"]))
            if scope == "CABIN_ONLY" or (scope == "SELECTED_PACKAGES"
                                        and plan["tourPackageId"] not in plan["legacyPackageIds"]):
                raise Blocked("Booking legacy package is outside the sale selection")
        if plan.get("fare"):
            fare = plan["fare"]
            fare["offerId"] = str(UUID(fare["offerId"]))
            for optional in ("priceRevisionId", "cabinId"):
                if fare.get(optional):
                    fare[optional] = str(UUID(fare[optional]))
            if scope != "WHOLE_CRUISE" or plan["fare"]["offerId"] not in plan["fareOfferIds"] or plan.get("tourPackageId"):
                raise Blocked("Fare booking requires a selected WHOLE_CRUISE offer")
        if plan.get("expiredQuoteId"):
            plan["expiredQuoteId"] = str(UUID(plan["expiredQuoteId"]))
        if plan.get("paymentEvidence") and plan["paymentEvidence"].get("order") not in ("contract-first", "payment-first"):
            raise Blocked("Specify paymentEvidence.order as contract-first or payment-first")
    if len(plans) == 2 and plans[0]["cruiseId"] == plans[1]["cruiseId"]:
        raise Blocked("Candidate cruises must differ")
    return plans


def readiness(api, plan, actor):
    query = [("saleScope", plan["scope"])]
    query += [("selectedPackageIds", item) for item in plan.get("legacyPackageIds", [])]
    query += [("selectedCruisePackageOfferIds", item) for item in plan.get("fareOfferIds", [])]
    return api.call("sale readiness", "GET", "/cruise/" + plan["cruiseId"] + "/sale-readiness?" + urlencode(query), actor)


def advance_prepared_plan(api, plan, crm, ops, result):
    route = "/cruise-plans/" + plan["cruiseId"]
    view = api.call("review prepared QA plan", "GET", route, ops)
    if ("REQUEST_REVISION" in view["allowedActions"]
            and "PLAN_APPROVAL_REQUIRED" in view["readyBlockers"]):
        api.call("return stale QA approval for re-review", "POST", route + "/request-revision", ops,
                 payload={"expectedRevision": view["revision"], "fingerprint": view["fingerprint"],
                          "reason": "QA plan snapshot requires a fresh approval"})
        view = api.call("plan readback after request-revision", "GET", route, ops)
    for action, token, blockers in (("submit", crm, "planBlockers"),
                                    ("approve", ops, "planBlockers"),
                                    ("ready", ops, "readyBlockers")):
        allowed = {item.lower() for item in view["allowedActions"]}
        if ("set_ready" if action == "ready" else action) not in allowed:
            continue
        if view[blockers]:
            result[blockers] = view[blockers]
            raise Blocked("Prepared QA plan still needs the recorded evidence or approvals")
        payload = {"expectedRevision": view["revision"], "fingerprint": view["fingerprint"],
                   "reason": "Booking Agent isolated QA signoff"}
        if action == "submit":
            preparation = api.call("read preparation revisions", "GET",
                                   "/cruise/" + plan["cruiseId"] + "/preparation", crm)
            owned = [row for row in preparation["sections"] if row["ownerRole"] == "CRUISE_MANAGER"]
            payload["preparationRevisions"] = {row["sectionCode"]: row["revision"] for row in owned}
            payload["preparationEvidence"] = plan.get("preparationEvidence")
            result["preparationSections"] = {row["sectionCode"]: row["status"] for row in owned}
        api.call("advance prepared QA plan: " + action, "POST", route + "/" + action, token,
                 payload=payload, status=(200, 422))
        if api.log[-1]["http"] == 422:
            code = api.log[-1].get("code", "UNPROCESSABLE_ENTITY")
            raise Blocked(f"Prepared plan {action} rejected: {code}")
        view = api.call("plan readback after " + action, "GET", route, ops)
    result["planStatus"] = view["planStatus"]
    result["readyStatus"] = view["readyStatus"]


def provision(api, plan, sales, staff, owner, other, event, result):
    cruise = "/cruise/" + plan["cruiseId"]
    crm = None
    if plan.get("attachLegacyPackages") or plan.get("advancePreparedPlan"):
        crm, _ = api.login("CRM", role="CRUISE_MANAGER", permissions=("CRUISE_UPDATE_DRAFT",))
    if plan.get("attachLegacyPackages"):
        packages = api.call("legacy package assignments", "GET", cruise + "/packages", crm)
        assigned_ids = {row["tourPackageId"] for row in packages}
        for package_id in plan["legacyPackageIds"]:
            if package_id not in assigned_ids:
                api.call("attach existing legacy package", "POST", cruise + "/packages", crm,
                         payload={"tourPackageId": package_id}, status=(200, 201))
    if plan.get("advancePreparedPlan"):
        sales, _ = api.login("SALES", role="OPERATIONS_MANAGER",
                             permissions=("ITINERARY_APPROVE", "CRUISE_VALIDATE", "CRUISE_OPEN_SALE"))
        advance_prepared_plan(api, plan, crm, sales, result)
    ready = readiness(api, plan, sales)
    result["blockers"] = ready.get("blockers", [])
    if not ready.get("ready"):
        raise Blocked("Resolve the recorded sale-readiness blockers through the owning services")
    if not api.call("sellability before open", "GET", cruise + "/sellability", sales)["sellable"]:
        api.call("open sale using revision and fingerprint", "POST", cruise + "/open-sale", sales,
                 payload={"expectedRevision": ready["cruiseRevision"], "fingerprint": ready["fingerprint"],
                          "saleScope": plan["scope"], "packageIds": plan["legacyPackageIds"],
                          "farePackageOfferIds": plan["fareOfferIds"], "eventIdentity": event})
    open_sale = api.call("sellability after open", "GET", cruise + "/sellability", sales)
    require(open_sale["sellable"], "Open sale readback is not sellable")
    result["sellability"] = {key: open_sale.get(key) for key in ("saleState", "sellable", "transactionReady")}
    if plan["fareOfferIds"]:
        catalog = api.call("published fare catalogue after open", "GET", cruise + "/fare-package-offers/available")
        offered = {row["selection"]["offerId"] for row in catalog["offers"]}
        require(set(plan["fareOfferIds"]).issubset(offered), "Opened fare offers are missing from the public catalogue")
    def assigned(user, rows):
        return any(row["userId"] == user and row["status"] == "ACTIVE"
                   and row["roleInCruise"] == "BOOKING_AGENT" for row in rows)
    rows = api.call("staff before assignment", "GET", cruise + "/staff", sales)
    if assigned(other, rows):
        raise Blocked("The other QA agent is already assigned to this cruise")
    if not assigned(owner, rows):
        if not crm:
            crm, _ = api.login("CRM", role="CRUISE_MANAGER", permissions=("CRUISE_PREPARATION_SUBMIT",))
        staffing_route = "/cruises/" + plan["cruiseId"] + "/staffing-requests"
        context = api.call("read staffing prerequisites", "GET", staffing_route, crm)
        request = context.get("request")
        result["staffing"] = {"hasOperatingBudget": context["hasOperatingBudget"],
                              "status": request["status"] if request else None}
        if not request and not context["hasOperatingBudget"]:
            api.call("staffing requires proposed operating budget", "POST", staffing_route, crm,
                     payload={"scheduleRevision": context["scheduleRevision"], "requirements": []},
                     status=(422,), code="PREPARATION_BUDGET_REQUIRED")
            raise Blocked("CRM must save the authorized projected operating budget before requesting staffing")
        if not request and plan.get("submitStaffingRequest"):
            request = api.call("submit QA staffing request", "POST", staffing_route, crm,
                               payload={"scheduleRevision": context["scheduleRevision"], "requirements": []})
            result["staffing"]["status"] = request["status"]
        if not request or request["status"] != "SENT_TO_HR":
            raise Blocked("Staffing request must pass its owning workflow and reach SENT_TO_HR")
        api.call("assign Booking Agent", "POST", cruise + "/staff", staff,
                 payload={"userId": owner, "roleInCruise": "BOOKING_AGENT"})
    require(assigned(owner, api.call("staff readback", "GET", cruise + "/staff", staff)), "Assignment not persisted")


def book(api, plan, token, key, pg, database):
    cid = plan["cruiseId"]
    fare = plan.get("fare")
    cabins = [row for row in api.call("available cabins", "GET", url("/booking/available-cabins", cruiseId=cid), token)
              if row["remainingCapacity"] > 0]
    eligible_ids = None
    if fare:
        catalogue = api.call("published fare catalogue", "GET", f"/cruise/{cid}/fare-package-offers/available")
        available = next((row["selection"] for row in catalogue["offers"]
                          if row["selection"]["offerId"] == fare["offerId"]), None)
        if not available:
            raise Blocked("QA fare offer is not in the available catalogue")
        if fare.get("priceRevisionId") and fare["priceRevisionId"] != available["priceRevisionId"]:
            raise Blocked("QA fare fixture specifies an outdated price revision")
        eligible_ids = {fare["cabinId"]} if fare.get("cabinId") else set(available["physicalCabinIds"])
    cabin = next((row for row in cabins if eligible_ids is None or row["cabinId"] in eligible_ids), None)
    if not cabin:
        raise Blocked("Fixture needs a suitable available cabin")
    sale = api.call("assigned sellability", "GET", f"/cruise/{cid}/sellability", token)
    if not sale["sellable"] or not sale.get("currency"):
        raise Blocked("Fixture needs sellability and a sale currency")
    selection = {"cabinId": cabin["cabinId"]}
    if plan.get("tourPackageId"):
        selection["tourPackageId"] = plan["tourPackageId"]
    if fare:
        selection.update(cabinSelectionMode="PHYSICAL_CABIN", farePackageOfferId=fare["offerId"],
                         farePackagePriceRevisionId=available["priceRevisionId"])
    quote_body = {"cruiseId": cid, "currency": sale["currency"], "paymentPlan": "DEPOSIT", "passengers": [selection]}
    if not fare and cabin["remainingCapacity"] < 50:
        api.call("quote exceeds cabin capacity", "POST", "/booking/quotes", token,
                 payload=dict(quote_body, passengers=[selection] * (cabin["remainingCapacity"] + 1)),
                 status=(400, 409, 422), code=("CABIN_OVERCAPACITY", "EXCEEDS_SHIP_CAPACITY"))
    if fare:
        stale = dict(selection, farePackagePriceRevisionId=str(uuid4()))
        api.call("stale fare revision", "POST", "/booking/quotes", token,
                 payload=dict(quote_body, passengers=[stale]), status=(400, 409, 422), code="FARE_QUOTED_PRICE_STALE")
    quote = api.call("create quote", "POST", "/booking/quotes", token, payload=quote_body, status=(200, 201))
    quoted = api.call("quote readback", "GET", "/booking/quotes/" + quote["quoteId"], token)
    require(quote.get("fingerprint") and quote["fingerprint"] == quoted["fingerprint"], "Quote fingerprint differs")
    email = key + "@example.test"
    passenger = dict(selection, fullName="Booking QA Passenger", email=email)
    body = dict(quote_body, quoteId=quote["quoteId"], passengers=[passenger])
    before = pg.committed_inventory(database)
    if plan.get("expiredQuoteId"):
        expired_id = plan["expiredQuoteId"]
        expired = api.call("read supplied expired QA quote", "GET", "/booking/quotes/" + expired_id, token)
        expires_at = datetime.fromisoformat(expired["expiresAt"].replace("Z", "+00:00"))
        require(expired["cruiseId"] == cid and expires_at <= datetime.now(timezone.utc), "Supplied quote is not expired on the fixture cruise")
        api.call("reject expired quote", "POST", "/booking", token, key=key + "-expired",
                 payload=dict(body, quoteId=expired_id), status=(400, 409, 422), code="QUOTE_EXPIRED")
    api.call("reject quote selection mismatch", "POST", "/booking", token, key=key + "-mismatch",
             payload=dict(body, passengers=[dict(passenger, cabinId=str(uuid4()))]), status=(400, 404, 409, 422))
    require(before == pg.committed_inventory(database), "Rejected booking changed committed business row counts")
    try:
        api.call("create booking; discard response to simulate client response loss", "POST", "/booking", token,
                 key=key, payload=body, status=(200, 201))
    except TransportLoss:
        pass
    saved = None
    for _ in range(10):
        saved = api.call("recover booking by GET", "GET", url("/booking/recovery", idempotencyKey=key), token,
                         status=(200, 404, 409))
        if isinstance(saved, dict) and saved.get("bookingId"):
            break
        time.sleep(1)
    if not isinstance(saved, dict) or not saved.get("bookingId"):
        raise Blocked("No confirmed recovery result after response loss; POST was not retried")
    repeated = api.call("explicit idempotency check", "POST", "/booking", token, key=key, payload=body, status=(200, 201))
    require(saved["bookingId"] == repeated["bookingId"] and saved["agreedFareAmount"] == quoted["totalAmount"]
            and saved["currency"] == quoted["currency"], "Idempotency or quote readback differs")
    return saved, email, cabins


def verify_booking(api, plan, token, other, saved, email, cabins, actor, key, verdict, finance=None):
    cid, bid = plan["cruiseId"], "/booking/" + saved["bookingId"]
    api.call("cross-cruise sale-readiness", "GET", f"/cruise/{cid}/sale-readiness", other, status=(403,))
    api.call("cross-cruise booking detail", "GET", bid, other, status=(403,))
    detail = api.call("booking detail", "GET", bid, token)
    require(detail["bookingId"] == saved["bookingId"], "Booking detail differs")
    verdict["detail"] = "PASS"
    path = url("/booking", cruiseId=cid, size=100)
    page = api.call("owner scoped booking list", "GET", path, token)
    require(any(row["bookingId"] == saved["bookingId"] for row in page["content"]), "Assigned booking missing")
    require(not api.call("other agent scoped list", "GET", path, other)["content"], "Cross-cruise booking leaked")
    verdict["list"] = "PASS"
    found = api.call("lookup by contact", "GET", url("/booking/lookup", reference=saved["bookingReference"], email=email))
    require(found["bookingId"] == saved["bookingId"], "Lookup returned a different booking")
    verdict["lookup"] = "PASS"
    api.call("confirm unsigned booking", "POST", bid + "/confirm", token,
             status=(400, 409, 422), code="BOOKING_CONTRACT_NOT_ACCEPTED")
    api.call("deny direct cancel under workflow", "POST", bid + "/cancel", token,
             payload={"reason": "QA cancellation"}, status=(409,), code="CANCELLATION_CASE_REQUIRED")
    passenger = detail["passengers"][0]
    replacement = next((row["cabinId"] for row in cabins if row["cabinId"] != passenger.get("cabinId")), None)
    if replacement:
        change = {"bookingPassengerId": passenger["bookingPassengerId"], "newCabinId": replacement,
                  "changeReason": "QA second cabin"}
        if plan.get("fare"):
            api.call("fare cabin change unsupported", "POST", "/booking/change-cabin", token,
                     payload=change, status=(409,), code="FARE_CABIN_CHANGE_NOT_SUPPORTED")
            verdict["cabin"] = "PASS"
        else:
            api.call("legacy cabin change", "POST", "/booking/change-cabin", token, payload=change)
            after = api.call("cabin change readback", "GET", bid, token)
            require(after["passengers"][0]["cabinId"] == replacement, "Cabin change not persisted")
            verdict["cabin"] = "PASS"
    reference = os.getenv("CASMS_BA_QA_SIGNED_CONTRACT_REFERENCE_" + actor)
    def accept_contract():
        api.call("accept signed QA contract", "POST", bid + "/contract/accept", token, payload={"documentReference": reference})
        current = api.call("contract readback", "GET", bid, token)
        require(current["contractStatus"] == "Accepted", "Contract not accepted")
        verdict["contract"] = "PASS"
        return current

    evidence = plan.get("paymentEvidence", {})
    receipt = os.getenv("CASMS_BA_QA_COLLECTION_RECEIPT_" + actor)
    if finance and not receipt:
        amount = money(detail["requiredDepositAmount"])
        pending = open_deposit_payment(api, finance, detail, amount, key + "-invalid-evidence")
        reject_blank_receipt(api, finance, pending, amount)
        persisted = api.call("payment still pending after invalid receipt", "GET", "/payment/" + pending["paymentId"], finance)
        require(persisted["status"] == "Pending", "Invalid evidence changed payment status")
    if reference and evidence and receipt:
        if not finance:
            finance, _ = api.login("FINANCE", role="FINANCE_OFFICER", permissions=("PAYMENT_PROCESS",))
        required_deposit = money(detail["requiredDepositAmount"])
        amount = money(evidence.get("collectedAmount"))
        partial_receipt = os.getenv("CASMS_BA_QA_PARTIAL_RECEIPT_" + actor)
        partial_amount = money(evidence["partialAmount"]) if evidence.get("partialAmount") is not None else Decimal(0)
        if (bool(partial_amount) != bool(partial_receipt) or partial_amount >= required_deposit
                or partial_amount + amount != required_deposit
                or (partial_amount and evidence["order"] != "contract-first")):
            raise Blocked("Supply evidenced collections totaling the required deposit; partial collection is contract-first")
        if evidence.get("order") == "contract-first":
            accept_contract()
            if partial_amount:
                collect_deposit(api, finance, detail, partial_amount, partial_receipt, key + "-partial")
                partial = api.call("booking after partial collection", "GET", bid, token)
                require(partial["bookingStatus"] == "Draft" and money(partial["totalPaidAmount"]) == partial_amount,
                        "Partial collection changed confirmation or the verified amount")
            api.call("confirm with insufficient verified deposit", "POST", bid + "/confirm", token,
                     status=(400, 409, 422), code="BOOKING_DEPOSIT_NOT_PAID")
        payment = collect_deposit(api, finance, detail, amount, receipt, key)
        current = api.call("booking after collection", "GET", bid, token)
        if evidence.get("order") == "payment-first":
            require(current["bookingStatus"] == "Draft" and current["contractStatus"] == "Draft",
                    "Payment confirmed the booking before contract acceptance")
            current = accept_contract()
        require(current["bookingStatus"] == "Confirmed", "Expected auto-confirm after signed contract and verified deposit")
        for _ in range(2):
            repeated = api.call("idempotent manual confirmation", "POST", bid + "/confirm", token)
            require(repeated["bookingStatus"] == "Confirmed" and repeated["bookingId"] == saved["bookingId"],
                    "Manual confirm did not preserve the confirmed booking")
        verdict["confirm"] = "PASS"
        refund_reference = os.getenv("CASMS_BA_QA_REFUND_RECEIPT_" + actor)
        if evidence.get("refundAmount") is not None and refund_reference:
            verify_refund(api, finance, payment, detail, current, bid, token, evidence, refund_reference, key)
    elif reference:
        accept_contract()
        api.call("confirm with insufficient verified deposit", "POST", bid + "/confirm", token,
                 status=(400, 409, 422), code="BOOKING_DEPOSIT_NOT_PAID")
    cancellation = {"scenario": "CUSTOMER", "reason": "QA cancellation"}
    cancel_route = bid + "/cancellations"
    case = api.call("request cancellation", "POST", cancel_route, token, key=key + "-cancel",
                    payload=cancellation, status=(200, 201))
    repeated = api.call("replay cancellation request", "POST", cancel_route, token, key=key + "-cancel",
                        payload=cancellation, status=(200, 201))
    history = api.call("cancellation readback", "GET", bid + "/cancellations", token)
    require(case["status"] == "PENDING_FINANCE" and repeated["caseId"] == case["caseId"]
            and sum(row["caseId"] == case["caseId"] for row in history) == 1,
            "Pending cancellation case missing from history")
    api.call("cross-cruise cancellation history", "GET", cancel_route, other, status=(403,))
    case_route = "/cancellations/" + case["caseId"]
    decision = {"revision": case["revision"], "reason": "QA cancellation decision"}
    api.call("deny Booking Agent cancellation approval", "POST", case_route + "/approve", token,
             payload=decision, status=(403,))
    withdrawn = api.call("withdraw own cancellation", "POST", case_route + "/withdraw", token, payload=decision)
    require(withdrawn["status"] == "WITHDRAWN", "Cancellation was not withdrawn")
    api.call("reject stale cancellation decision", "POST", case_route + "/withdraw", token,
             payload=decision, status=(409,), code="CANCELLATION_STALE_REVISION")
    if plan.get("verifyFinanceCancellation") or finance:
        if not finance:
            finance, _ = api.login("FINANCE", role="FINANCE_OFFICER", permissions=("PAYMENT_REFUND",))
        pending = api.call("request cancellation for Finance review", "POST", cancel_route, token,
                           key=key + "-finance-cancel", payload=cancellation, status=(200, 201))
        completed = api.call("Finance approves cancellation", "POST",
                             "/cancellations/" + pending["caseId"] + "/approve", finance,
                             payload=dict(decision, revision=pending["revision"]))
        cancelled = api.call("booking after approved cancellation", "GET", bid, token)
        require(completed["status"] == "COMPLETED" and cancelled["bookingStatus"] == "Cancelled",
                "Finance approval did not complete the case and cancel the booking")
    verdict["cancel"] = "PASS"


def money(value):
    try:
        amount = Decimal(str(value))
    except InvalidOperation as error:
        raise Blocked("QA evidence amount is invalid") from error
    if not amount.is_finite() or amount <= 0:
        raise Blocked("QA evidence amount must be positive")
    return amount


def open_deposit_payment(api, finance, booking, amount, key):
    return api.call("create pending cash payment", "POST", "/payment/process", finance, status=(200, 201),
                    payload={"bookingId": booking["bookingId"], "paymentType": "DEPOSIT", "paymentMethod": "Cash",
                             "currency": booking["currency"], "requestedAmount": str(amount),
                             "idempotencyKey": key + "-collect"})


def reject_blank_receipt(api, finance, payment, amount):
    api.call("reject blank settlement evidence", "POST", "/payment/" + payment["paymentId"] + "/confirm", finance,
             payload={"confirmedAmount": str(amount), "processorReference": ""}, status=(400, 422))


def collect_deposit(api, finance, booking, amount, receipt, key):
    payment = open_deposit_payment(api, finance, booking, amount, key)
    reject_blank_receipt(api, finance, payment, amount)
    confirmed = api.call("record supplied QA cash receipt", "POST", "/payment/" + payment["paymentId"] + "/confirm", finance,
                         payload={"confirmedAmount": str(amount), "processorReference": receipt})
    require(confirmed["status"] == "Confirmed", "Payment was not confirmed")
    return confirmed


def verify_refund(api, finance, payment, booking, before, bid, token, evidence, receipt, key):
    amount = money(evidence["refundAmount"])
    refund = api.call("create QA refund request", "POST", "/refunds", finance, status=(201,),
                      payload={"originalPaymentId": payment["paymentId"], "requestedAmount": str(amount),
                               "currency": booking["currency"], "idempotencyKey": key + "-refund",
                               "refundReason": "QA evidenced refund"})
    route = "/refunds/" + refund["refundId"]
    api.call("approve QA refund", "POST", route + "/approve", finance, payload={"approvedAmount": str(amount)})
    api.call("complete QA refund with supplied receipt", "POST", route + "/complete", finance,
             payload={"processorOrReceiptReference": receipt})
    after = api.call("verified net paid after refund", "GET", bid, token)
    expected = max(Decimal(0), Decimal(str(before["totalPaidAmount"])) - amount)
    require(Decimal(str(after["totalPaidAmount"])) == expected, "Verified refund did not reduce net collection")


def smoke(api, pg, database, candidates, agents, report):
    for actor, (token, uid) in agents.items():
        for path in ("/booking?page=0&size=20", "/cruise?status=PUBLISHED&page=0&size=20"):
            page = api.call(actor + " scoped list", "GET", path, token)
            require(isinstance(page.get("content"), list), "Scoped list did not return a page")
        for raw in candidates:
            cid = str(UUID(raw))
            assigned = pg.query(database, f"""SELECT EXISTS (SELECT FROM "CruiseStaff"
                WHERE cruise_id='{cid}' AND user_id='{uid}' AND role_in_cruise='BOOKING_AGENT' AND status='ACTIVE');""")
            if assigned == "f":
                for path in (f"/cruise/{cid}/sale-readiness", f"/cruise/{cid}/sellability",
                             url("/booking/available-cabins", cruiseId=cid)):
                    api.call(actor + " unassigned cruise", "GET", path, token, status=(403,))
                cabin = pg.query(database, f"""SELECT cabin_id FROM "Cabin" JOIN "Cruise" USING(ship_id)
                    WHERE cruise_id='{cid}' ORDER BY cabin_id LIMIT 1;""")
                if cabin:
                    api.call(actor + " quote rejected outside assignment", "POST", "/booking/quotes", token,
                             payload={"cruiseId": cid, "currency": "USD", "paymentPlan": "DEPOSIT",
                                      "passengers": [{"cabinId": str(UUID(cabin))}]}, status=(403,))
                break
        missing = str(uuid4())
        api.call("unknown booking", "GET", "/booking/" + missing, token, status=(404,))
        api.call("unknown contract", "POST", f"/booking/{missing}/contract/accept", token,
                 payload={"documentReference": "NEGATIVE-QA-CASE"}, status=(404,))
        api.call("unknown booking confirmation", "POST", f"/booking/{missing}/confirm", token, status=(404,))
        api.call("unknown cancellation history", "GET", f"/booking/{missing}/cancellations", token, status=(404,))
        api.call("workflow blocks direct cancel", "POST", f"/booking/{missing}/cancel", token,
                 payload={"reason": "QA out-of-workflow probe"}, status=(409,), code="CANCELLATION_CASE_REQUIRED")
        api.call("unknown passenger cabin change", "POST", "/booking/change-cabin", token,
                 payload={"bookingPassengerId": missing, "newCabinId": str(uuid4())}, status=(404,))
        api.call("agent cannot record a payment", "POST", "/payment/process", token,
                 payload={"bookingId": missing, "requestedAmount": 100, "currency": "VND",
                          "paymentMethod": "Cash", "idempotencyKey": missing}, status=(403,))
        api.call("unknown recovery", "GET", url("/booking/recovery", idempotencyKey=missing), token,
                 status=(404,), code="RECOVERY_NOT_FOUND")
    api.call("unknown lookup", "GET", url("/booking/lookup", reference="BA-QA-" + uuid4().hex[:10],
                                          email="lookup@example.test"), status=(404,))
    sales = None
    if os.getenv("CASMS_BA_QA_SALES_USERNAME"):
        sales, _ = api.login("SALES", permissions=("CRUISE_READ", "CRUISE_OPEN_SALE"))
    for raw in candidates:
        cid = str(UUID(raw))
        public = api.call("public sellability", "GET", f"/cruise/{cid}/sellability")
        observed = {key: public.get(key) for key in ("saleState", "sellable", "transactionReady", "remainingCapacity")}
        observed["cruiseId"] = cid
        if sales:
            ready = readiness(api, {"cruiseId": cid, "scope": "WHOLE_CRUISE"}, sales)
            observed["readinessBlockers"] = ready["blockers"]
            plan = api.call("observe plan prerequisites", "GET", f"/cruise-plans/{cid}", sales)
            observed["plan"] = {key: plan.get(key) for key in ("planStatus", "planBlockers", "readyStatus", "readyBlockers")}
            if "READY_CONFIRMATION_REQUIRED" in ready["blockers"]:
                api.call("open sale refused without readiness confirmation", "POST", f"/cruise/{cid}/open-sale", sales,
                         payload={"expectedRevision": ready["cruiseRevision"], "fingerprint": ready["fingerprint"],
                                  "saleScope": "WHOLE_CRUISE", "eventIdentity": "baqa-denied-" + uuid4().hex[:16]},
                         status=(422,), code="READY_CONFIRMATION_REQUIRED")
        report["observed"].append(observed)
    return sales


def inspect_prepared_drafts(api, sales, drafts, report):
    if not sales or not drafts:
        return
    report["draftPlans"] = []
    for raw in drafts:
        cid = str(UUID(raw))
        ready = readiness(api, {"cruiseId": cid, "scope": "WHOLE_CRUISE"}, sales)
        report["draftPlans"].append({"cruiseId": cid, "saleReady": ready["ready"], "blockers": ready["blockers"]})


def run(args, report):
    source, values = qa_config(args.qa_config, args.template)
    pg = Pg(args.psql, values)
    report["source"] = pg.inventory(source)
    if args.inventory_only:
        return
    plans = plans_from(args.plan)
    if not args.jar or not args.jar.is_file():
        raise Blocked("A built Backend JAR is required")
    database = "casms_ba_qa_" + uuid4().hex[:14]
    pg.query("postgres", f"CREATE DATABASE {database} TEMPLATE {source}", readonly=False)
    report["clone"] = database
    try:
        through = report["source"]["version"]
        history = pg.history(database, through)
        run_id = "baqa-" + database.removeprefix("casms_ba_qa_")
        recipients = []
        if args.create_agent_b:
            recipients.append(run_id + "-ba@example.test")
        if args.create_staff_hr:
            recipients.append(run_id + "-hr@example.test")
        if args.create_finance:
            recipients.append(run_id + "-finance@example.test")
        inbox = ActivationInbox(recipients) if recipients else nullcontext()
        with inbox as activation, backend(args, pg, database, activation) as base:
            report["database"] = pg.inventory(database)
            require(report["database"]["version"] >= 138, "The QA clone did not reach V138")
            require(history == pg.history(database, through), "Applied migration history changed")
            report["migration"] = "PASS"
            api = Api(base, report["http"])
            agents = {}
            for actor in ("AGENT_A", "AGENT_B"):
                try:
                    credentials = (activate_qa_employee(api, activation, run_id + "-ba", "BOOKING_AGENT")
                                   if actor == "AGENT_B" and args.create_agent_b else None)
                    agents[actor] = api.login(actor, role="BOOKING_AGENT",
                                               permissions=("BOOKING_READ", "BOOKING_CREATE", "BOOKING_CONFIRM",
                                                            "BOOKING_CONTRACT_MANAGE", "BOOKING_CANCEL"),
                                               credentials=credentials)
                except Blocked as blocker:
                    report["blocked"].append(str(blocker))
            sales = smoke(api, pg, database, report["database"]["candidates"], agents, report)
            inspect_prepared_drafts(api, sales, report["database"]["preparedDrafts"], report)
            if not plans or len(agents) != 2:
                raise Blocked("Positive transaction tests require two candidate cruises and two active QA agents")
            require(agents["AGENT_A"][1] != agents["AGENT_B"][1], "The QA agents must be different users")
            if not sales:
                raise Blocked("SALES QA credentials are required")
            staff_credentials = (activate_qa_employee(api, activation, run_id + "-hr", "HR_MANAGER")
                                 if args.create_staff_hr else None)
            staff, _ = api.login("STAFF", role="HR_MANAGER", permissions=("CRUISE_STAFF_READ", "CRUISE_STAFF_ASSIGN"),
                                 credentials=staff_credentials)
            finance = None
            if args.create_finance:
                finance_credentials = activate_qa_employee(api, activation, run_id + "-finance", "FINANCE_OFFICER")
                finance, _ = api.login("FINANCE", role="FINANCE_OFFICER",
                                       permissions=("PAYMENT_PROCESS", "PAYMENT_READ", "PAYMENT_REFUND"),
                                       credentials=finance_credentials)
            fixtures = []
            for plan, actor, other in zip(plans, ("AGENT_A", "AGENT_B"), ("AGENT_B", "AGENT_A")):
                token, uid = agents[actor]
                other_token, other_id = agents[other]
                verdict = {feature: "BLOCKED" for feature in FEATURES}
                result = {"actor": actor, "cruiseId": plan["cruiseId"], "features": verdict}
                report["trips"].append(result)
                key = "baqa-" + hashlib.sha256((database + actor).encode()).hexdigest()[:16]
                try:
                    provision(api, plan, sales, staff, uid, other_id, key, result)
                    fixtures.append((plan, actor, token, other_token, key, result))
                except Blocked as blocker:
                    result["blocker"] = str(blocker)
                    report["blocked"].append(str(blocker))
            if len(fixtures) != 2:
                raise Blocked("Cross-cruise transaction tests require two provisioned cruises")
            for plan, actor, token, other_token, key, result in fixtures:
                verdict = result["features"]
                try:
                    market = api.call("scoped sale catalogue", "GET", "/cruise?status=PUBLISHED&page=0&size=100", token)
                    require(any(row["cruiseId"] == plan["cruiseId"] for row in market["content"]),
                            "Provisioned cruise missing from scoped catalogue")
                    verdict["sale"] = "PASS"
                    saved, email, cabins = book(api, plan, token, key, pg, database)
                    verdict["create"] = "PASS"
                    verify_booking(api, plan, token, other_token, saved, email, cabins, actor, key, verdict, finance)
                except Blocked as blocker:
                    result["blocker"] = str(blocker)
                    report["blocked"].append(str(blocker))
            if any(value == "BLOCKED" for trip in report["trips"] for value in trip["features"].values()):
                report["blocked"].append("Readiness, signed contract and verified collection dependencies remain")
            if not any(plan.get("tourPackageId") for plan in plans):
                report["blocked"].append("Legacy cabin change requires a sold legacy package fixture")
    finally:
        if args.cleanup:
            try:
                pg.query("postgres", f"DROP DATABASE {database}", readonly=False)
                report["cleanup"] = "isolated clone dropped after JVM shutdown"
            except Failed as cleanup_error:
                report["cleanup"] = str(cleanup_error)
                report["failure"] = "Unable to drop this run's QA clone safely"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("qa-config", "jar", "plan", "evidence"):
        parser.add_argument("--" + arg, type=Path, required=arg in ("qa-config", "evidence"))
    parser.add_argument("--template")
    parser.add_argument("--psql", default="psql")
    parser.add_argument("--java", default="java")
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--create-agent-b", action="store_true", help="Activate a new QA employee via an isolated SMTP inbox")
    parser.add_argument("--create-staff-hr", action="store_true", help="Activate an HR QA employee for assignment")
    parser.add_argument("--create-finance", action="store_true", help="Activate Finance QA for receipt validation and cancellation")
    parser.add_argument("--cleanup", action="store_true", help="Drop only the clone generated by this run")
    args = parser.parse_args()
    report = {"http": [], "trips": [], "observed": [], "blocked": []}
    args.evidence.parent.mkdir(parents=True, exist_ok=True)
    try:
        run(args, report)
    except Blocked as blocker:
        report["blocked"].append(str(blocker))
    except (Failed, ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError) as error:
        report["failure"] = str(error) if isinstance(error, Failed) else type(error).__name__
    report["status"] = "FAIL" if "failure" in report else "BLOCKED" if report["blocked"] else "PASS"
    args.evidence.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{report['status']}: {args.evidence}")
    return {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[report["status"]]


if __name__ == "__main__":
    raise SystemExit(main())
