# AmiDog deployment runbook

This runbook covers the current Spring Boot/PostgreSQL and React/Vite
application. It does not replace provider-specific database, SMTP, proxy, or
secret-management procedures. Rehearse every operation in a staging
environment built from a verified production backup.

## Release prerequisites

- Provision PostgreSQL 17 with encrypted connections, restricted network
  access, an application role with only the required schema privileges, and
  managed storage/backup retention. Do not expose port 5432 publicly.
- Supply every required variable in
  [configuration.md](configuration.md) from the deployment platform's secret
  and configuration facilities. Never bake `.env`, database credentials,
  administrator passwords, SMTP credentials, or tokens into frontend assets
  or an image.
- Build the frontend and backend from the reviewed source. Do not enable H2,
  Hibernate schema creation, or Spring Session schema initialization.
- Terminate TLS at a trusted proxy/load balancer, preserve HTTPS information
  for the application according to that platform, and set
  `SESSION_COOKIE_SECURE=true`.
- Ensure the proxy discards untrusted forwarding headers and provides a
  verified client address. The current registration, login, contact, and
  reservation limiters are per process and are not shared between replicas.

## PostgreSQL and Flyway startup

Flyway migrations are the only production schema authority. The current
package contains immutable V1–V9 migrations; Hibernate starts with
`ddl-auto: validate`, and Spring Session expects its V1 tables because JDBC
schema initialization is `never`.

1. Put the application in a maintenance/read-only window if the release changes
   schema or is not backward compatible.
2. Take a consistent backup and complete the restore verification described
   below.
3. Point a single new application instance at the intended database and start
   it. Flyway must validate its history and apply pending migrations before JPA
   validation succeeds.
4. Stop on any Flyway checksum, ordering, permission, or JPA validation error.
   Never edit an applied migration or repair history merely to force startup.
5. Only after the canary startup and smoke checks pass, add normal traffic and
   expand to the reviewed replica count.

`baseline-on-migrate=true` and baseline version 0 support the designed
foundation path; they are not permission to attach the application to an
unknown populated schema. Inventory and restore-test any existing database
first.

## Browser origin and cookies

The preferred topology is one browser origin:

```text
https://app.example.com/       -> static frontend
https://app.example.com/api/** -> reverse proxy to Spring Boot
```

This matches the frontend's relative `/api/v1/...` calls, avoids CORS in the
browser, and keeps `SESSION` and `XSRF-TOKEN` scoped to one public origin. Set:

```properties
FRONTEND_BASE_URL=https://app.example.com
FRONTEND_ORIGIN=https://app.example.com
SESSION_COOKIE_SECURE=true
```

For local plain HTTP only, use `SESSION_COOKIE_SECURE=false`. Production
requires HTTPS and `true`; otherwise a secure session cookie will not be sent.
The session cookie remains HttpOnly and SameSite Lax. `XSRF-TOKEN` is readable
so the client can return its value in `X-XSRF-TOKEN`; it is not an
authentication token and must not be stored elsewhere.

An exact-origin alternative is supported by backend CORS when a browser client
calls the API origin directly. Configure `FRONTEND_ORIGIN` to exactly the
frontend origin, for example `https://app.example.com`, with no path, trailing
slash, wildcard, or list. The client must send credentials and the proxy/API
must keep the session and CSRF cookies. Because the current React build uses
relative `/api` URLs and cookies are SameSite Lax, a direct split-origin
deployment needs reviewed routing that targets the API origin and must remain
same-site; an unrelated cross-site frontend is not a supported assumption.
Prefer the same-origin reverse proxy above.

`FRONTEND_BASE_URL` remains independent: it is the public root used in
verification/reset email links and may differ from the CORS origin. Exercise
both email links after every hostname or routing change.

## SMTP provider and DNS

Production uses `EMAIL_DELIVERY=smtp`. Create a provider account and sending
domain dedicated to the deployment, then:

1. Publish and verify the provider's SPF and DKIM records.
2. Publish a DMARC policy and monitoring address appropriate for the domain.
   Begin with the provider's recommended monitored policy before tightening it.
3. Use a provider endpoint with required STARTTLS or implicit TLS. Keep
   `SMTP_ALLOW_PLAINTEXT=false`; plaintext is accepted only for unauthenticated
   loopback Mailpit.
4. Store the SMTP username/password in the secret manager and restrict access
   and rotation rights.
5. Configure `CLINIC_EMAIL` to a monitored clinic mailbox. The application sets
   the contact sender's Reply-To to the visitor but does not set an explicit
   From header, so confirm the provider supplies/accepts the authenticated
   default sender.
6. Test verification, password reset, clinic contact delivery, contact
   acknowledgement, bounces, and spam placement with controlled addresses.

Do not infer delivery from a successful SMTP handoff alone. Monitor provider
acceptance/bounce telemetry without logging raw tokens, links, passwords, or
message bodies.

## Administrator bootstrap and rotation

There is no public administrator registration route. For the first start only,
provide a unique `ADMIN_EMAIL` and a generated `ADMIN_PASSWORD` of at least 12
characters. The database enforces a single administrator.

After startup:

1. Verify the administrator can sign in and has `accountType: ADMIN`.
2. Remove `ADMIN_PASSWORD` (and, if the platform permits, the bootstrap pair)
   from steady-state runtime configuration. A changed bootstrap password does
   not update an existing row.
3. With production SMTP verified, use the normal forgot-password and
   reset-password flow for the administrator to rotate the initial credential.
4. Confirm the new password works and the old password fails, then revoke the
   bootstrap secret and record the rotation in the operator audit system.

Do not delete/recreate the administrator or hand-edit a password hash as a
routine rotation mechanism.

## Backup and verified restore

A backup is not accepted until it has restored successfully into an isolated
database and the restored application has passed smoke checks.

1. Provision a PostgreSQL password file outside the repository and restrict it
   to the operator account. Its standard entry format is
   `hostname:port:database:username:password`. Store the backup-role password
   there, not in a command, URI, shell variable containing the password, or
   command history. Point the client tools at the protected file:

   ```powershell
   $env:PGPASSFILE = 'C:\secure\amidog-backup.pgpass'
   pg_dump --host=db.internal.example --port=5432 `
     --username=amidog_backup --dbname=amidog `
     --format=custom --no-owner --no-privileges `
     --file=amidog-YYYYMMDD-HHMM.dump
   Get-FileHash -Algorithm SHA256 amidog-YYYYMMDD-HHMM.dump
   ```

2. Copy the dump and recorded checksum to protected storage with the required
   retention and encryption. Restrict restore credentials separately from the
   application role. Use a separately protected password file for that role.
3. Create an isolated restore-check database, verify the checksum, and restore:

   ```powershell
   $env:PGPASSFILE = 'C:\secure\amidog-restore.pgpass'
   pg_restore --exit-on-error --single-transaction --no-owner --no-privileges `
     --host=restore-db.internal.example --port=5432 `
     --username=amidog_restore --dbname=amidog_restore_check `
     amidog-YYYYMMDD-HHMM.dump
   psql --host=restore-db.internal.example --port=5432 `
     --username=amidog_restore --dbname=amidog_restore_check `
     --command="select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
   ```

4. Check expected tables, row counts, foreign-key consistency, one
   administrator, Spring Session tables, and Flyway history. Start the reviewed
   application against the isolated database, then run the health and
   role/session/CSRF smoke checks below.
5. Record the dump checksum, restore time, PostgreSQL/application versions,
   checks performed, and reviewer. Retain or remove the disposable restore
   database only under the operator's data-retention procedure.

Never pass database passwords in command-line arguments or connection URIs:
arguments can be exposed in process listings and shell history. Do not use
`PGPASSWORD`. A protected `PGPASSFILE`, or a protected `PGSERVICEFILE` backed
by a password file, keeps secrets out of commands. Unset the task-scoped
`PGPASSFILE` path after the operation. Never test restoration by overwriting
the live database.

## Operator-gated destructive V10

No V10 migration exists in the current package. The `legacy_*` tables retained
by V1/V8 are deliberate recovery evidence. A future destructive V10 that drops
them must not be generated, applied, or bundled automatically.

Before authoring or running such a migration, require all of the following:

- an inventory and reconciliation proving V8 imported every intended legacy
  client, pet, reservation, link, and service, including quarantined/collision
  cases;
- a current consistent database backup plus a separate export of every legacy
  table, both with recorded checksums;
- a successful isolated restore and application verification using that exact
  backup;
- a staging rehearsal of the proposed immutable V10 and the rollback-by-restore
  procedure;
- documented clinic-owner/data-owner approval and a second operator review;
- a maintenance window, monitoring owner, abort criteria, and retained recovery
  artifacts.

If any gate is missing, leave the legacy tables in place. Never use an ad-hoc
`DROP TABLE` or delete the local Compose volume as a substitute for the gated
migration.

## Health verification

The current dependency set does not expose an Actuator health endpoint, so do
not route checks to `/actuator/health`. Use layered checks:

1. PostgreSQL reports ready and accepts the application role.
2. Startup logs show Flyway validation/migration success, JPA validation
   success, and the application listening without credential values.
3. `GET /api/v1/auth/csrf` returns `200` JSON and sets `XSRF-TOKEN`.
4. `GET /api/v1/services` returns `200`; an unknown `/api/v1/**` route returns
   the controlled `404 NOT_FOUND` envelope.
5. Use the secret-free Bruno collection to verify a client login/session,
   authenticated `/auth/me`, a CSRF-protected mutation in a controlled tenant,
   logout, administrator login/dashboard, and role isolation.
6. Verify controlled SMTP delivery and the `FRONTEND_BASE_URL` verification and
   reset links. Confirm mandatory security headers and that hostile CORS
   origins receive the closed 403 envelope without allow-origin headers.

Only enable normal traffic after these checks and production monitoring are
green.

## Rollback

- If health checks fail before traffic, stop the new instances, preserve logs
  without secrets, and leave the previous version serving.
- If no incompatible migration ran, redeploy the last reviewed application
  artifact and repeat smoke checks.
- Flyway migrations are forward-only. Do not edit `flyway_schema_history`,
  rewrite an applied SQL file, or improvise a down migration.
- If a migration is incompatible or destructive, stop writers and restore the
  verified pre-release backup into a new database, point the previous
  application release to it, verify Flyway/schema/application health, and only
  then resume traffic. Preserve the failed database for investigation.
- Record the trigger, timeline, data point, artifact/database versions, and
  follow-up owner.

Local Compose data is persistent in the named `amidog-postgres` volume.
Normal stop/rollback uses `docker compose stop` or `docker compose down`
without `-v`; destructive volume deletion is never part of rollback.
