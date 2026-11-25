# Testing guide: unit → integration → end to end

How to verify the APAC Claims Platform at every level, from pure unit tests up to a full run through the browser. Every command below runs locally; nothing is deployed.

| Level | What it proves | Needs | Command | Count |
|---|---|---|---|---|
| 1. Backend unit | Lifecycle rules, access rules, entity behaviour, poller logic, codec, summaries | JDK 17+ | `.\mvnw.cmd test -Dtest=…` (list in [1.2](#12-run-only-the-unit-tests-no-docker)) | 105 |
| 2. Backend integration | SQL schema, REST API, outbox → real Kafka, idempotent projections, REST → Kafka → read models | JDK + **Docker** (Testcontainers) | `.\mvnw.cmd verify` | 155 total |
| 3. Frontend unit | `X-User` interceptor, error parsing, money format, action-button and attention rules, exposure bars | Node 20.19+ / 22.12+ / 24+ | `npx ng test --watch=false` | 31 |
| 4. API end to end | The running system: every role, every error code, Kafka read models catching up | Compose stack + running backend | [PowerShell script](#4-api-end-to-end-script) | 31 checks |
| 5. UI end to end | The Angular app against the live API | Everything running | [Browser walkthrough](#5-ui-end-to-end-walkthrough) | manual |

---

## 0. Prerequisites

- **Docker Desktop running.** Integration tests start their own Postgres + Kafka containers; levels 4–5 use `docker compose`.
- **JDK 17+** with `JAVA_HOME` pointing to it (tested on Temurin 25). Check with `.\mvnw.cmd -v` in `backend/`.
- **Node.js** 20.19+, 22.12+ or 24+, and `npm install` run once in `frontend/`.

Seeded demo users (from `V4__seed_reference_data.sql`), sent as the `X-User` header:

| Username | Role | Notes |
|---|---|---|
| `alice` | CLAIMANT | holds SG motor (SGD 80,000) and AU property policies, among others |
| `ben` | CLAIMANT | holds HK, MY, TH policies |
| `olivia`, `oscar` | OFFICER | |
| `maya` | MANAGER | |
| `mallory` | *(not seeded)* | use to see 401 |

---

## 1. Backend unit tests

Pure JUnit 5 + AssertJ (+ Mockito for the poller). No Spring context, no database, no Docker.

### 1.1 What each class covers

| Test class | Tests | Covers |
|---|---|---|
| `claim/ClaimLifecycleTest` | 69 | Transition table matches the blueprint; **every** invalid (state, action) pair → 409; terminal states; role permissions; permission checked before state |
| `claim/ClaimTest` | 8 | Approve aligns reserve, reject/settle release reserve and set `closedAt`, reassignment keeps status, closed claims are immutable |
| `claim/ClaimAccessTest` | 8 | Claimant sees own claims only (others → 404), officer acts only on assigned claims, manager on any; `availableActions` filtered by assignment |
| `outbox/OutboxPollerTest` | 4 | Publishes in order keyed by claimId; **stops the batch at the first failure**; broker timeout counts as failure; empty outbox |
| `common/events/ClaimEventCodecTest` | 3 | JSON round trip keeps wire names and exact BigDecimal amounts; tolerates reordered keys (jsonb); poison messages skipped, not thrown |
| `timeline/TimelineSummariesTest` | 11 | Timeline wording per event; reserve changes are staff-only; long text truncated to column size |
| `exposure/ExposureTotalsTest` | 2 | Totals sum within a currency and never across currencies |

### 1.2 Run only the unit tests (no Docker)

```powershell
cd backend
.\mvnw.cmd test "-Dtest=ClaimLifecycleTest,ClaimTest,ClaimAccessTest,OutboxPollerTest,ClaimEventCodecTest,TimelineSummariesTest,ExposureTotalsTest"
```

Expected: `Tests run: 105, Failures: 0, Errors: 0`.

Run one class or one method:

```powershell
.\mvnw.cmd test -Dtest=ClaimLifecycleTest
.\mvnw.cmd test "-Dtest=OutboxPollerTest#stopsAtFirstFailureSoLaterEventsAreNotPublishedOutOfOrder"
```

---

## 2. Backend integration tests

`@SpringBootTest` with **Testcontainers**: each run starts throwaway `postgres:16-alpine` and `apache/kafka:4.1.0` containers (the same images as `docker-compose.yml`), applies the Flyway migrations, and tears everything down afterwards. Your compose stack does not need to be running and is never touched.

The scheduled outbox poller is **off** in tests (`src/test/resources/application.properties`), so tests decide exactly when events are published.

| Test class | Tests | Covers |
|---|---|---|
| `SchemaMigrationTest` | 7 | Migrations apply; seed data; DB constraints (status CHECK, officer required past SUBMITTED, one open info request, decision amount rules) |
| `ClaimsServiceApplicationTests` | 1 | Application context starts |
| `claim/ClaimApiIntegrationTest` | 25 | Full lifecycle over HTTP (MockMvc); 401/403/404/409/400/422 cases; claimant view hides reserve and notes; outbox rows in order with versions 0..7 |
| `outbox/OutboxIntegrationTest` | 4 | Writer refuses to run outside a transaction; a 422 after a state change rolls back claim **and** event; poller → **real Kafka**, ordered, keyed by claimId; no re-send |
| `workload/WorkloadProjectionIntegrationTest` | 5 | Duplicate ignored; stale event after a newer one ignored; full replay changes nothing; time-in-status survives reassignment; officer figures |
| `exposure/ExposureProjectionIntegrationTest` | 4 | Reserve changes replace, not add; 5× redelivery doesn't double count; a closed claim can't be resurrected by a stale event; unreserved count |
| `timeline/TimelineProjectionIntegrationTest` | 2 | Redelivered event stored once; claimant sees only visible entries of own claims |
| `ReadModelsEndToEndTest` | 2 | **REST → outbox → poller → Kafka → three consumer groups → dashboards/timeline**, plus role checks on the read endpoints |

### 2.1 Run everything

```powershell
cd backend
.\mvnw.cmd verify
```

Expected (about 1–2 minutes; the first run also downloads images):

```text
[INFO] Tests run: 155, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Reports: `backend/target/surefire-reports/*.txt`.

### 2.2 Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop isn't running |
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` points to a folder that doesn't exist; fix it, then open a new terminal |
| `ERROR ... Skipping unparseable claim event` in the log | Expected: `ClaimEventCodecTest` feeds deliberately corrupt messages |

---

## 3. Frontend unit tests

Vitest via the Angular CLI (`@angular/build:unit-test`), jsdom environment. No backend needed: HTTP is faked with `HttpTestingController`.

| Spec | Tests | Covers |
|---|---|---|
| `core/user.interceptor.spec.ts` | 4 | Adds `X-User` to `/api/*` only; follows a user switch; **never sends a role** |
| `core/problem.spec.ts` | 6 | RFC 7807 parsing incl. 400 field errors; 409/422 detail kept; status 0 and the dev-proxy's empty 500 → "Backend unreachable"; genuine 500 kept |
| `core/format.spec.ts` | 5 | Money always shows the ISO code (no `$`), per-currency minor units, compact figures, open/closed |
| `features/claim-detail/action-panel.spec.ts` | 6 | Which buttons each role sees: claimant only `Respond`; unassigned officer nothing; manager adds `Reassign`; no reserve on closed claims |
| `features/claims-list/attention.spec.ts` | 4 | "Needing attention" rules per role, oldest first, never closed claims |
| `features/dashboard/exposure-view.spec.ts` | 4 | Bars only for the selected currency and scaled within it |
| `shared/problem-alert.spec.ts` | 2 | Renders status, hint and each field error |

```powershell
cd frontend
npx ng test --watch=false                                  # all specs, once
npx ng test --watch=false --include src/app/core/problem.spec.ts   # one file
npx ng test                                                # watch mode while developing
npm run build                                              # production build must also be clean
```

Expected: `Test Files 7 passed (7)  Tests 31 passed (31)`.

---

## 4. API end-to-end script

This drives the **running** system over HTTP as five different users: one claim from submission to settlement, every error code on the way, then a wait for the Kafka-fed read models to catch up. It prints `PASS`/`FAIL` per check and exits non-zero on any failure.

### 4.1 Start the stack

```powershell
# terminal 1, repo root
docker compose up -d --wait
cd backend
.\mvnw.cmd spring-boot:run     # wait for "Started ClaimsServiceApplication"
```

### 4.2 Run the script

Save as `e2e.ps1` anywhere (it's not part of the repo) and run it with `powershell -ExecutionPolicy Bypass -File e2e.ps1`, or paste it into a PowerShell window. Tested with Windows PowerShell 5.1 (the version built into Windows). Each run creates a new claim, so it can be re-run any number of times.

```powershell
# e2e.ps1: end-to-end check of the claims API (local only)
$Base = 'http://localhost:8080'
$script:Failed = 0

function Call([string]$User, [string]$Method, [string]$Path, $Body = $null) {
    $req = @{ Uri = "$Base$Path"; Method = $Method; UseBasicParsing = $true; Headers = @{ 'X-User' = $User } }
    if ($Method -ne 'GET') { $req.ContentType = 'application/json'; $req.Body = if ($null -eq $Body) { '{}' } else { $Body | ConvertTo-Json -Compress } }
    try {
        $r = Invoke-WebRequest @req
        $json = if ($r.Content) { $r.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $json }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw "No response from $Base. Is the backend running?" }
        $text = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        $json = if ($text) { $text | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $json }
    }
}

function Check([string]$Label, $Actual, $Expected) {
    # Numbers compare by value (the API returns 1000.00 for 1000); everything else as text.
    $inv = [Globalization.CultureInfo]::InvariantCulture; $a = 0.0; $e = 0.0
    $numeric = [double]::TryParse("$Actual", 'Float', $inv, [ref]$a) -and [double]::TryParse("$Expected", 'Float', $inv, [ref]$e)
    $ok = if ($numeric) { $a -eq $e } else { "$Actual" -eq "$Expected" }
    if ($ok) { Write-Host "PASS  $Label" -ForegroundColor Green }
    else { Write-Host "FAIL  $Label (expected '$Expected', got '$Actual')" -ForegroundColor Red; $script:Failed++ }
}

$SgPolicy = '00000000-0000-0000-0000-000000001001'   # alice, SG motor, SGD 80,000

Write-Host "`n== Identity"
Check 'alice resolves as CLAIMANT'           (Call alice GET /api/me).Body.role   'CLAIMANT'
Check 'unknown user -> 401'                  (Call mallory GET /api/me).Status    401

Write-Host "`n== Submit (claimant)"
$bad = Call alice POST /api/claims @{ policyId = $SgPolicy; description = '' }
Check 'missing fields -> 400'                $bad.Status 400
Check '400 lists claimedAmount error'        ($null -ne $bad.Body.errors.claimedAmount) $true
Check 'above sum insured -> 422'             (Call alice POST /api/claims @{ policyId = $SgPolicy; incidentDate = '2026-11-01'; description = 'x'; claimedAmount = 80000.01 }).Status 422
$sub = Call alice POST /api/claims @{ policyId = $SgPolicy; incidentDate = '2026-11-01'; description = 'E2E: rear-ended at lights'; claimedAmount = 1200 }
Check 'submit -> 201'                        $sub.Status 201
Check 'new claim is SUBMITTED'               $sub.Body.status 'SUBMITTED'
$id = $sub.Body.id
Write-Host "      claim $($sub.Body.claimNumber) ($id)"

Write-Host "`n== Access rules"
Check "another claimant (ben) -> 404"        (Call ben GET "/api/claims/$id").Status 404
Check 'claimant cannot assess -> 403'        (Call alice POST "/api/claims/$id/start-assessment").Status 403

Write-Host "`n== Assessment (officer)"
Check 'olivia picks up -> ASSIGNED'          (Call olivia POST "/api/claims/$id/assign").Body.status 'ASSIGNED'
Check 'oscar (not assigned) -> 403'          (Call oscar POST "/api/claims/$id/start-assessment").Status 403
Check 'approve while ASSIGNED -> 409'        (Call olivia POST "/api/claims/$id/decision" @{ outcome = 'APPROVED'; amount = 100; reason = 'ok' }).Status 409
Check 'start assessment'                     (Call olivia POST "/api/claims/$id/start-assessment").Body.status 'UNDER_ASSESSMENT'
$ask = Call olivia POST "/api/claims/$id/info-requests" @{ question = 'Please upload the police report' }
Check 'request info -> AWAITING_INFO'        $ask.Body.status 'AWAITING_INFO'
$reqId = $ask.Body.infoRequests[0].id
Check 'claimant responds -> UNDER_ASSESSMENT' (Call alice POST "/api/claims/$id/info-requests/$reqId/response" @{ response = 'Report 12345 attached' }).Body.status 'UNDER_ASSESSMENT'
Check 'set reserve'                          (Call olivia PUT "/api/claims/$id/reserve" @{ amount = 1000; reason = 'Repair quote' }).Body.reserveAmount 1000
$aliceView = (Call alice GET "/api/claims/$id").Body
Check 'claimant view hides reserve'          ($null -eq $aliceView.reserveAmount) $true
Check 'claimant view hides notes'            ($null -eq $aliceView.notes) $true

Write-Host "`n== Decision and settlement"
Check 'approve without amount -> 400'        (Call olivia POST "/api/claims/$id/decision" @{ outcome = 'APPROVED'; reason = 'ok' }).Status 400
Check 'approve 900'                          (Call olivia POST "/api/claims/$id/decision" @{ outcome = 'APPROVED'; amount = 900; reason = 'Covered, excess applied' }).Body.status 'APPROVED'
Check 'settle above approved -> 422'         (Call olivia POST "/api/claims/$id/settlement" @{ amount = 950 }).Status 422
$done = Call olivia POST "/api/claims/$id/settlement" @{ amount = 900 }
Check 'settle 900 -> SETTLED'                $done.Body.status 'SETTLED'
Check 'reserve released on settlement'       $done.Body.reserveAmount 0
$version = $done.Body.version

Write-Host "`n== Kafka read models (eventually consistent)"
$expected = $version + 1        # one event per claim version: 0..$version
$deadline = (Get-Date).AddSeconds(20)
do {
    $staffTimeline = @((Call olivia GET "/api/claims/$id/timeline").Body)
    if ($staffTimeline.Count -ge $expected) { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)
Check "timeline caught up ($expected events)" $staffTimeline.Count $expected
Check 'staff timeline ends with ClaimSettled' $staffTimeline[-1].eventType 'ClaimSettled'
$claimantTimeline = @((Call alice GET "/api/claims/$id/timeline").Body)
Check 'claimant timeline hides reserve change' (@($claimantTimeline | Where-Object eventType -eq 'ReserveChanged').Count) 0
Check "ben sees an empty timeline"           @((Call ben GET "/api/claims/$id/timeline").Body).Count 0
Check 'officer exposure -> 403'              (Call olivia GET /api/dashboard/exposure).Status 403
Check 'manager exposure -> 200'              (Call maya GET /api/dashboard/exposure).Status 200
Check 'claimant workload -> 403'             (Call alice GET /api/dashboard/workload).Status 403
Check 'officer sees only own workload row'   @((Call olivia GET /api/dashboard/workload).Body.officers).Count 1

Write-Host ""
if ($script:Failed -eq 0) { Write-Host 'ALL CHECKS PASSED' -ForegroundColor Green; exit 0 }
else { Write-Host "$($script:Failed) CHECK(S) FAILED" -ForegroundColor Red; exit 1 }
```

Expected output: 31 `PASS` lines ending with `ALL CHECKS PASSED`. If the timeline check fails, the outbox poller or consumers aren't running; check the backend log for `partitions assigned` lines.

### 4.3 Optional: prove read models can be rebuilt from Kafka

Projections are idempotent and their consumers start from the earliest offset, so a read model can be wiped and rebuilt by replaying the topic:

```powershell
# 1. stop the backend (Ctrl+C in terminal 1), then wipe the timeline read model
docker exec claims-postgres psql -U claims -d claims -c "TRUNCATE claim_timeline_entry"

# 2. rewind only that consumer group to the start of the topic
docker exec claims-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 `
  --group timeline-projection --topic claim-events --reset-offsets --to-earliest --execute

# 3. start the backend again, then re-check (row count should return to what it was)
docker exec claims-postgres psql -U claims -d claims -tAc "SELECT count(*) FROM claim_timeline_entry"
```

Running the e2e script again afterwards still passes. Duplicate deliveries are ignored, so replaying a group that was **not** truncated changes nothing either.

---

## 5. UI end-to-end walkthrough

```powershell
# terminal 2 (backend still running from 4.1)
cd frontend
npm start        # http://localhost:4200, /api proxied to :8080
```

> If the app shows **"Backend unreachable"** (and the terminal logs `ECONNREFUSED`), the backend isn't running.

Switch users with the **X-User** switcher at the top right. Tick each expected result:

| # | As | Do | Expect |
|---|---|---|---|
| 1 | `mallory` | Open the app | 401 card "We couldn't identify you"; no data |
| 2 | `alice` | **Report an incident** → pick `SG-MOT-0001` | Policy card shows SG · MOTOR, cover period, SGD 80,000 sum insured |
| 3 | `alice` | Submit with an empty description | Field error under the input (client-side); with amount 90000 → **422** "exceeds the sum insured" |
| 4 | `alice` | Submit a valid claim | Lands on the detail page, status **Submitted**, no Reserve or Officer tiles, "Nothing needed from you" |
| 5 | `ben` | Stay on the same URL | **404 "Claim not found"** with the "existence isn't revealed" note |
| 6 | `oscar` | **Claims** → *Unassigned* | The new claim appears, and under *Claims needing attention* with "Unassigned: pick it up" |
| 7 | `olivia` | Open the claim → **Pick up claim**, **Start assessment** | Status changes immediately; flash shows the new version |
| 8 | `oscar` | Open the same claim | No action buttons (not assigned to him) |
| 9 | `olivia` | **Request info** with a question | Status **Awaiting info**; officer has no actions now |
| 10 | `alice` | Claim → **Respond** | Question quoted; after sending, status **Under assessment**; request marked *Answered* |
| 11 | `olivia` | **Set reserve** 1000, **Add note** | Reserve tile updates; reserve history and internal notes appear (staff only) |
| 12 | `alice` | Reload the claim | No reserve, reserve history or notes anywhere |
| 13 | `olivia` | **Decide** → Approve 900 | Decision card; **Settle** with 950 → 422 shown in the action panel; settle 900 → **Settled** |
| 14 | `olivia` | Timeline → **Refresh** | Events appear oldest-to-newest (shown newest first); pill shows "Up to date with claim vN" (or "Catching up" if pressed instantly) |
| 15 | `alice` | Timeline | Same events minus the reserve change |
| 16 | `olivia` | **My workload** | One row (Olivia) plus the unassigned queue |
| 17 | `maya` | **Workload & exposure** | All officers; cobalt exposure card with one tab per currency, amounts never combined; market × product bars |
| 18 | `maya` | Any open claim assigned to Olivia → **Reassign** to Oscar | Status unchanged; switch to `olivia`: no actions on that claim any more |

### Optional: Kafka UI

```powershell
docker compose --profile tools up -d    # http://localhost:8090
```

Browse topic `claim-events`: one message per claim version, keyed by claim id, and three consumer groups (`workload-projection`, `exposure-projection`, `timeline-projection`) with their own offsets.

---

## 6. Run everything in one go

```powershell
# repo root
cd backend;  .\mvnw.cmd verify;               cd ..   # levels 1-2 (Docker needed)
cd frontend; npx ng test --watch=false; npm run build; cd ..   # level 3
docker compose up -d --wait                            # then start the backend and run e2e.ps1 (level 4)
```
