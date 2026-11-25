# APAC Claims Workbench (frontend)

Angular 21 (standalone components, signals, zoneless) for claimants, officers and managers.
See the root [README](../README.md) to run the full stack.

```powershell
npm install
npm start                    # http://localhost:4200, /api -> http://localhost:8080 (proxy.conf.json)
npm run build                # production build into dist/
npx ng test --watch=false    # Vitest unit tests
```

| Path | What |
|---|---|
| `src/app/core` | API types and typed client, `X-User` interceptor, session (`/api/me`), RFC 7807 parsing, formatting |
| `src/app/shared` | Status pill, problem alert, money/date pipes |
| `src/app/features/claims-list` | Claims list with status/assignee filters and a "needing attention" section |
| `src/app/features/submit-claim` | Report an incident (claimant) |
| `src/app/features/claim-detail` | Claim detail, action panel with inline forms, Kafka-fed timeline |
| `src/app/features/dashboard` | Workload (staff) and per-currency exposure (manager) |
