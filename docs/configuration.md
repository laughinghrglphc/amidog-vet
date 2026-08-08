# AmiDog configuration reference

The backend imports an optional root `.env` file through Spring Boot. Vite also
loads root `.env` values whose names start with `VITE_`; those values are public
build-time configuration, never secrets. After
`docker compose up -d --wait`, copy `.env.example` to `.env` for a local run;
the direct copy matches the included PostgreSQL and Mailpit services. Keep
`.env` outside source control and delivery archives. Every credential and
identity in the template is local-only example data and must be replaced, with
secrets moved to an appropriate secret store, before production use.

The table below covers every environment variable referenced by the current
main/test application configuration or frontend source. “Required” means the
application cannot provide the described behavior safely without a value; an
empty Spring default is still listed when it makes a feature a no-op.

## Database, browser origin, and clinic time

| Variable | Purpose | Format | Profile and required status | Safe example |
|---|---|---|---|---|
| `DB_URL` | PostgreSQL JDBC datasource used by Flyway, JPA, and Spring Session. | `jdbc:postgresql://host:port/database` | Backend: required for every non-test application start; no default. | `jdbc:postgresql://localhost:5432/amidog` |
| `DB_USERNAME` | PostgreSQL login for the application datasource. | Nonblank PostgreSQL role name. | Backend: required for every non-test application start; no default. | `amidog` |
| `DB_PASSWORD` | Password for `DB_USERNAME`. | Secret string supplied through local `.env` or a production secret store. | Backend: required for every non-test application start; no default. | `amidog-local` (local Compose only) |
| `CLINIC_TIMEZONE` | IANA zone for availability, dashboard dates, reminder/cleanup cron wall time, and Chilean offsets. | Valid `ZoneId`; never a fixed UTC offset. | Backend: optional; defaults to `America/Santiago`. | `America/Santiago` |
| `FRONTEND_BASE_URL` | Public frontend URL used to build verification and password-reset links. | Absolute HTTP(S) URI. The current generated link paths start at `/`, so configure the public frontend root. | Backend: optional locally; required and HTTPS in production. Defaults to `http://localhost:5173`. | `https://app.example.com` |
| `FRONTEND_ORIGIN` | The one credentialed browser origin allowed by backend CORS. It is deliberately distinct from the email-link base URL. | One exact HTTP(S) origin: scheme, host, and optional valid port only; no userinfo, wildcard, path, trailing slash, query, fragment, comma list, or empty port. | Backend: optional locally; required to match the deployed browser origin when direct cross-origin calls are used. Defaults to `http://localhost:5173`. | `https://app.example.com` |
| `SESSION_COOKIE_SECURE` | Controls the `Secure` attribute on the server-side `SESSION` cookie. Cookies remain HttpOnly and SameSite Lax. | `true` or `false`. | Backend: `false` only for local plain HTTP; must be `true` behind production HTTPS. Defaults to `false`. | `false` (local), `true` (production) |

`FRONTEND_BASE_URL` is for links in email and is URI/path-capable.
`FRONTEND_ORIGIN` is a strict browser security boundary and must contain no
path. They can have the same origin value, but they are not interchangeable.

## Administrator and contact channels

| Variable | Purpose | Format | Profile and required status | Safe example |
|---|---|---|---|---|
| `ADMIN_EMAIL` | Email for the idempotent single-administrator bootstrap. | Valid email, at most 254 characters. | Backend: optional only when an administrator already exists. A complete email/password pair is required for first bootstrap. Empty email or password makes bootstrap a no-op. | `admin.local@example.test` |
| `ADMIN_PASSWORD` | Initial administrator password. It is hashed on first bootstrap and changing the variable later does not rotate the stored password. | Secret, 12 or more characters. | Backend: required only with `ADMIN_EMAIL` for first bootstrap. The shipped `amidog-local-admin-only` value is local-only and must be replaced before production. Remove the bootstrap secret from steady-state runtime configuration after verified bootstrap and rotate through password recovery. | `amidog-local-admin-only` (local template only) |
| `ADMIN_NAME` | Runtime display name returned for the administrator principal and dashboard profile. | Plain text; current default is `Administradora AmiDog`. | Backend: optional. | `Administradora Local AmiDog` |
| `ADMIN_PHONE` | Backend-bound administrator phone placeholder. The current bootstrap/account model does not consume it. | Plain text; reserve at most 30 characters for compatibility. | Backend: optional; empty by default. | `+56900000000` |
| `CLINIC_EMAIL` | Recipient of a valid website contact inquiry. | Valid provider-accepted mailbox. | Backend: operationally required before enabling the contact route with real delivery; empty by default. | `contacto.local@example.test` (local Mailpit only) |
| `WHATSAPP_NUMBER` | Backend-bound contact number reserved in current configuration. It does not populate the current React link. | Digits only, international format without `+`, spaces, or punctuation. | Backend: optional; empty by default. | `56900000000` |
| `VITE_WHATSAPP_NUMBER` | Public number embedded by Vite in the React contact link. This is not secret. | Digits only, international format without `+`, spaces, or punctuation. | Frontend build: optional; without it the WhatsApp link is unavailable and development shows a warning. | `56900000000` |

The all-zero numbers above are obvious placeholders, not clinic contact
details. Never put a private credential in a `VITE_` variable because the
value is shipped to the browser.

## Booking and notification scheduling

| Variable | Purpose | Format | Profile and required status | Safe example |
|---|---|---|---|---|
| `BOOKING_AUTO_CONFIRM` | Chooses `CONFIRMED` instead of `PENDING` for client creation/rescheduling. | `true` or `false`. | Backend: optional; defaults to `false`. | `false` |
| `BOOKING_DURATION_MINUTES` | Fixed reservation duration. | Integer exactly `30`; other values fail validation. | Backend: optional; defaults to `30`. | `30` |
| `BOOKING_MIN_NOTICE_HOURS` | Minimum notice before an available start. | Integer `0` or greater. | Backend: optional; defaults to `2`. | `2` |
| `BOOKING_HORIZON_DAYS` | Furthest bookable clinic-local date. | Positive integer. | Backend: optional; defaults to `90`. | `90` |
| `NOTIFICATION_REMINDERS_ENABLED` | Enables the in-app appointment reminder scan. | `true` or `false`. | Backend: optional; defaults to `true` in main config and `false` in test config. | `true` |
| `NOTIFICATION_REMINDER_WINDOW_START_HOURS` | Inclusive lower lead-time bound. | Integer `1..167`. | Backend: optional; defaults to `23`. | `23` |
| `NOTIFICATION_REMINDER_WINDOW_END_HOURS` | Exclusive upper lead-time bound and must exceed the start. | Integer `2..168`. | Backend: optional; defaults to `25`. | `25` |
| `NOTIFICATION_REMINDER_BATCH_SIZE` | Maximum reminder candidates claimed per scan. | Integer `1..500`. | Backend: optional; defaults to `100`. | `100` |
| `NOTIFICATION_REMINDER_CRON` | Spring six-field cron for reminder scans, evaluated in `CLINIC_TIMEZONE`. | `second minute hour day-of-month month day-of-week`. | Backend: optional; defaults to `0 5 * * * *`. | `0 5 * * * *` |
| `TOKEN_CLEANUP_CRON` | Spring six-field cron for verification/reset token cleanup, evaluated in `CLINIC_TIMEZONE`. | Spring six-field cron. | Backend: optional; defaults to `0 30 3 * * *`. | `0 30 3 * * *` |

## Email transport

| Variable | Purpose | Format | Profile and required status | Safe example |
|---|---|---|---|---|
| `EMAIL_DELIVERY` | Selects metadata-only logging or real SMTP delivery. | Exact value `log` or `smtp`. | Backend: optional; defaults to `log`. The shipped local template uses `smtp` for Mailpit; production also requires a reviewed real provider. | `smtp` (local Mailpit) |
| `EMAIL_RECONCILIATION_DELAY_MS` | Fixed delay between durable outbox reconciliation runs. | Positive millisecond integer. | Backend: optional; defaults to `30000`. | `30000` |
| `SMTP_HOST` | SMTP server hostname. The test configuration also reads it for Spring Mail. | DNS name or IP literal. | Backend: required when `EMAIL_DELIVERY=smtp`; empty in main config, `localhost` default in tests. | `localhost` |
| `SMTP_PORT` | SMTP TCP port. | Integer `1..65535`. | Backend: required for SMTP; defaults to `587` in main config and `1025` in tests. | `1025` |
| `SMTP_USERNAME` | SMTP authentication username. The test configuration also reads it. | Provider credential or empty string. | Backend: required with `SMTP_PASSWORD` when `SMTP_AUTH=true`; must be blank when auth is false. | empty for local Mailpit |
| `SMTP_PASSWORD` | SMTP authentication password. The test configuration also reads it. | Secret string or empty string. | Backend: required with `SMTP_USERNAME` when `SMTP_AUTH=true`; must be blank when auth is false. | empty for local Mailpit |
| `SMTP_AUTH` | Enables SMTP authentication. | `true` or `false`. | Backend SMTP: optional; defaults to `true`. Must be `false` for local Mailpit. | `false` |
| `SMTP_STARTTLS` | Enables STARTTLS negotiation. | `true` or `false`. | Backend SMTP: optional; defaults to `true`. | `false` (local Mailpit) |
| `SMTP_STARTTLS_REQUIRED` | Rejects delivery if STARTTLS is unavailable; requires `SMTP_STARTTLS=true`. | `true` or `false`. | Backend SMTP: optional; defaults to `true`. Required for remote STARTTLS providers. | `false` (local Mailpit) |
| `SMTP_SSL` | Enables implicit TLS, normally on port 465; mutually exclusive with STARTTLS. | `true` or `false`. | Backend SMTP: optional; defaults to `false`. | `false` |
| `SMTP_ALLOW_PLAINTEXT` | Explicitly permits a plaintext fallback only for unauthenticated loopback SMTP. | `true` or `false`. | Backend SMTP: may be `true` only for `localhost`, `127.0.0.1`, or `::1` with auth disabled. It covers TLS disabled and optional STARTTLS (`SMTP_STARTTLS=true`, `SMTP_STARTTLS_REQUIRED=false`), because optional negotiation can fall back to plaintext. Defaults to `false`. | `true` (local Mailpit only) |

Remote or authenticated SMTP is rejected unless implicit TLS is enabled or
STARTTLS is both enabled and required. For implicit TLS, use
`SMTP_SSL=true`, `SMTP_STARTTLS=false`, and
`SMTP_STARTTLS_REQUIRED=false`.

`SMTP_ALLOW_PLAINTEXT=true` never permits remote or authenticated plaintext
delivery. Its only purpose is an explicit local-development exception for
unauthenticated loopback delivery, whether TLS is disabled or STARTTLS is
enabled but optional.

## Local Compose profile

`compose.yaml` starts PostgreSQL 17 and Mailpit with local-only credentials.
The complete `.env.example` already contains this profile, including a valid
single-administrator bootstrap identity; `Copy-Item .env.example .env` is
sufficient for this local stack:

```properties
DB_URL=jdbc:postgresql://localhost:5432/amidog
DB_USERNAME=amidog
DB_PASSWORD=amidog-local
ADMIN_EMAIL=admin.local@example.test
ADMIN_PASSWORD=amidog-local-admin-only
ADMIN_NAME=Administradora Local AmiDog
EMAIL_DELIVERY=smtp
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
SMTP_STARTTLS_REQUIRED=false
SMTP_SSL=false
SMTP_ALLOW_PLAINTEXT=true
CLINIC_EMAIL=contacto.local@example.test
SESSION_COOKIE_SECURE=false
FRONTEND_BASE_URL=http://localhost:5173
FRONTEND_ORIGIN=http://localhost:5173
```

Start services with `docker compose up -d --wait` and open Mailpit at
`http://localhost:8025`. Start the backend on port 8080 and Vite on port 5173.
Vite proxies the complete `/api` prefix to the backend without path rewriting,
so local browser calls remain same-origin.

The database password and administrator credentials above are deliberately
obvious local examples. Never carry them into production. Replace every
example identity/contact value, require HTTPS with a secure session cookie and
the deployed exact frontend origin, and use secret storage plus authenticated,
TLS-required remote SMTP in production.

Use `docker compose stop` to pause services or `docker compose down` to remove
containers while retaining the named `amidog-postgres` volume. Do not use
`docker compose down -v`; volume deletion is destructive and is not part of
normal local operation.
