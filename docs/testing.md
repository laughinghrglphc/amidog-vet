# Testing AmiDog

## Prerequisites

- Java 21 for Maven and Spring Boot.
- Node.js/npm with the lockfile installed through `npm install` or `npm ci`.
- Docker Compose with a running daemon. The authoritative backend suite uses PostgreSQL 17 Testcontainers; browser acceptance uses the PostgreSQL 17 and Mailpit services from `compose.yaml`.
- Free loopback ports `5432`, `8025`, `1025`, `8080`, and `5173` for browser acceptance.
- Playwright Chromium installed once with `npx playwright install chromium`.

Do not remove the Compose PostgreSQL volume as part of testing. Browser data uses unique email addresses and service codes so the suite can run against persistent local data.

## Frontend unit and component tests

Run the complete deterministic Vitest suite once:

```powershell
npm test
```

Run a focused file or use watch mode during development:

```powershell
npm test -- src/pages/Confirmacion.test.jsx
npm run test:watch
```

The remaining frontend gates are:

```powershell
npm run lint
npm run build
```

Run these resource-intensive commands sequentially on constrained machines; concurrent Vitest, ESLint, and Vite builds can cause misleading test timeouts.

## Backend tests

The release-authoritative backend command is:

```powershell
.\mvnw.cmd test
```

It includes deterministic domain/configuration tests, Spring integration tests, Flyway migration tests, and PostgreSQL-specific constraint/concurrency coverage through Testcontainers. Docker must be available for the PostgreSQL tests.

Useful focused PostgreSQL and collision coverage is:

```powershell
.\mvnw.cmd -Dtest=SchedulingMigrationIntegrationTests,ReservationConcurrencyIntegrationTests,ReservationLifecycleConcurrencyIntegrationTests test
```

When Docker is temporarily unavailable, the deterministic non-Testcontainers boundary can still run:

```powershell
.\mvnw.cmd -Dtest='!*IntegrationTests,!*PostgresIntegrationTests,!AppApplicationTests' test
.\mvnw.cmd test-compile
.\mvnw.cmd -DskipTests package
```

This fallback is diagnostic only. It does not replace the PostgreSQL 17 suite before release.

## Browser acceptance

Start and health-check the persistent infrastructure, then run the two real-Chromium lifecycles:

```powershell
docker compose up -d --wait
npm run test:e2e
```

Playwright owns the Spring Boot backend and a Vite production build/preview for the duration of the run, waits on `/api/v1/auth/csrf` and `/`, uses one Desktop Chrome worker, and stops both processes afterward. Do not start separate servers on `8080` or `5173`; `reuseExistingServer` is intentionally disabled.

The backend connects to the healthy Compose database and delivers verification email to Mailpit over explicitly allowed unauthenticated loopback plaintext SMTP. The tests poll Mailpit with a bounded timeout, follow the delivered verification URL, use current-relative availability, and never embed a token, database ID, or calendar date.

## Mailpit

- SMTP: `127.0.0.1:1025`.
- Web UI: `http://127.0.0.1:8025`.
- Message API used by acceptance: `http://127.0.0.1:8025/api/v1/messages`.

Acceptance config requires `EMAIL_DELIVERY=smtp`, `SMTP_AUTH=false`, TLS disabled/not required, `SMTP_ALLOW_PLAINTEXT=true`, and the loopback host. Never reuse these plaintext settings for a remote SMTP provider.

## Browser reports, screenshots, and traces

The HTML report is written to `output/playwright/report/index.html`. Failure screenshots and error context are under `output/playwright/test-results/`. A retry records `trace.zip` because tracing is configured for the first retry.

Open a recorded trace with:

```powershell
npx playwright show-trace output\playwright\test-results\<failed-test>-retry1\trace.zip
```

## Common failures

- **Docker/Testcontainers unavailable:** start the Docker daemon and rerun `docker compose up -d --wait`; confirm both services are healthy with `docker compose ps`.
- **Port already in use:** stop the process on `8080` or `5173`. Playwright must own these servers, so it will not reuse an existing process.
- **Backend readiness failure with SMTP:** confirm all local Mailpit flags above are present and `SMTP_USERNAME`/`SMTP_PASSWORD` are blank when auth is disabled.
- **Mailpit polling timeout:** verify port `1025` is reachable, port `8025` serves the API, and `FRONTEND_BASE_URL` is exactly `http://127.0.0.1:5173`.
- **Administrator login fails with persistent data:** bootstrap creates an administrator only when none exists. Use a database initialized with the documented local account or the supported account-recovery/operational procedure; the suite intentionally does not erase the PostgreSQL volume.
- **Vite navigation stalls in development mode:** acceptance uses a build plus preview so readiness covers compiled Sass/assets. Use `npm run build` to diagnose compilation independently.
- **Browser binary missing:** run `npx playwright install chromium`.
- **Transient frontend unit timeouts:** rerun `npm test` by itself rather than concurrently with lint/build.
