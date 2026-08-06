# AmiDog Delivery Roadmap

Design source: `docs/superpowers/specs/2026-07-28-amidog-booking-platform-design.md`

The approved design contains four substantial subsystems. Implement them in this order so every plan ends with working, testable software:

1. `2026-07-28-amidog-backend-foundation-auth.md`
   - PostgreSQL/Flyway foundation
   - package-by-feature domain baseline
   - verified email/password accounts
   - server-side JDBC sessions, CSRF, password recovery, and one administrator bootstrap

2. `2026-07-28-amidog-scheduling-administration-api.md`
   - service and pet management
   - Chilean availability and blackout rules
   - collision-safe multi-pet reservations
   - lifecycle history, administrator dashboard APIs, and notifications

3. `2026-07-28-amidog-frontend-consolidation.md`
   - shared API/auth infrastructure
   - registration and recovery UI
   - real client panel and reservation flow
   - merge and adapt the attached administrator frontend

4. `2026-07-28-amidog-contact-hardening-delivery.md`
   - provider-neutral email contact flow
   - WhatsApp contact action
   - reminder scheduling
   - security regression checks, documentation, clean builds, and final ZIP

## Cross-plan checkpoints

After each plan:

- run all Maven tests that do not require an unavailable external service;
- run the complete frontend unit suite, lint, and production build once frontend work begins;
- inspect `git status --short` when Git metadata exists;
- preserve unrelated user files and changes;
- update the plan checkboxes and the implementation log;
- do not package secrets, `.env`, `node_modules`, `target`, `dist`, logs, or test databases.

Before deploying scheduling V10, the operator must take and verify a restorable
PostgreSQL backup plus separate legacy-table exports, validate V8 import in
staging, and record the clinic owner's explicit approval. V10 removes the
medical/ecommerce graph and imported `legacy_*` tables in enumerated,
foreign-key-safe order. The backup retains old records for the agreed retention
period; the live database does not. Test both a fresh install and an upgrade
from the real Phase 1 schema.

The uploaded project currently has no `.git` directory. The implementation must not initialize a repository implicitly. Commit commands in the detailed plans are conditional checkpoints and run only if the project owner later supplies Git metadata.

## Approved-spec coverage

| Design requirement | Implementation location |
|---|---|
| Verified client accounts, password reset, one admin, JDBC sessions, CSRF | Backend foundation Tasks 3–7 |
| Disabled Google-ready identity link | Backend foundation Tasks 1–2 |
| Remove clinical/ecommerce model | Scheduling Task 9 after backup/export, import validation, and operator approval |
| Editable/archivable services | Scheduling Task 2; frontend Task 6 |
| Client profile and pet ownership/archive | Scheduling Task 2; frontend Task 3 |
| Multiple pets with one service each | Scheduling Task 4; frontend Task 4 |
| Fixed 30-minute appointments | Scheduling Tasks 1 and 4 |
| No overlapping occupied blocks, including concurrency | Scheduling Tasks 1 and 4 |
| `PENDING`/auto-confirm switch and full status lifecycle | Scheduling Tasks 4–5; frontend Tasks 3–4 and 6 |
| Cancellation/history and reschedule audit | Scheduling Task 5 |
| Chilean timezone, notice, horizon, weekly hours, partial/full blocks | Scheduling Task 3; frontend Tasks 4 and 6 |
| Administrator dashboard, client/pet/reservation tracking | Scheduling Task 7; frontend Tasks 5–6 |
| Operational notifications and reminders | Scheduling Task 6; contact/hardening Task 3 |
| Remove messages/replies | Frontend Task 5 and regression Task 7 |
| Website email plus direct WhatsApp | Contact/hardening Tasks 1–2 |
| Rate limiting, safe errors, exact origins, secure cookies | Backend foundation Task 7; contact/hardening Tasks 1 and 4 |
| Flyway migration and Phase 1 preservation/retirement | Foundation freezes V1-V6; scheduling Tasks 1 and 9 add V7-V10 |
| Unit, PostgreSQL integration, frontend, and browser acceptance tests | Every detailed plan; contact/hardening Task 6 |
| README, environment examples, Bruno, deployment docs, clean ZIP | Contact/hardening Tasks 5 and 7 |
