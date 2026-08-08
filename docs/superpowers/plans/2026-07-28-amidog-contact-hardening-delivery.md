# AmiDog Contact, Hardening, and Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish email/WhatsApp contact, appointment reminders, operational hardening, acceptance testing, documentation, and one clean deployable source ZIP.

**Architecture:** Contact email remains a synchronous provider-neutral SMTP boundary and stores no conversation. A scheduled, idempotent notification job creates upcoming-appointment reminders. Final verification exercises the actual PostgreSQL constraints and browser flows, while environment-specific values remain outside source control.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Mail, Spring Scheduling, Spring Security, PostgreSQL 17, React 19, Vite 8, Vitest, Testing Library, Playwright, Maven, npm.

## Global Constraints

- Contact supports both website email and direct WhatsApp; neither creates an in-app conversation.
- No WhatsApp API, webhook, relay, anonymous inbox, or synchronized chat is added.
- Contact email uses configured SMTP credentials and clinic destination values from environment variables.
- The public contact endpoint is validated, honeypot-protected, length-limited, and rate-limited.
- Appointment reminders are idempotent and tied to a reservation.
- Logs never include passwords, session IDs, tokens, SMTP credentials, or contact message bodies.
- Final tests use PostgreSQL for database-specific behavior.
- Final packaging excludes `.env`, secrets, `node_modules`, `target`, `dist`, logs, temporary databases, and test reports.
- The uploaded project has no `.git`; never initialize Git implicitly.

---

## File Structure

### Contact backend

- Create `src/main/java/com/amidog/app/contact/ContactController.java`.
- Create `src/main/java/com/amidog/app/contact/ContactService.java`.
- Create `src/main/java/com/amidog/app/contact/ContactDtos.java`.
- Create `src/main/java/com/amidog/app/common/text/PlainTextSanitizer.java`.
- Create `src/test/java/com/amidog/app/contact/ContactIntegrationTests.java`.

### Contact frontend

- Create `src/api/contactApi.js`.
- Modify `src/pages/Contacto.jsx`.
- Modify `src/styles/_contacto.scss`.
- Create `src/pages/Contacto.test.jsx`.

### Scheduled maintenance

- Create `src/main/java/com/amidog/app/notification/AppointmentReminderJob.java`.
- Create `src/main/java/com/amidog/app/auth/ExpiredTokenCleanupJob.java`.
- Modify `src/main/java/com/amidog/app/AppApplication.java`.
- Create `src/test/java/com/amidog/app/notification/AppointmentReminderJobIntegrationTests.java`.
- Create `src/test/java/com/amidog/app/auth/ExpiredTokenCleanupJobIntegrationTests.java`.

### Operations and acceptance

- Modify `src/main/java/com/amidog/app/config/SecurityConfig.java`.
- Modify `src/main/resources/application.yaml`.
- Modify `.env.example`.
- Modify `vite.config.js`.
- Create `compose.yaml`.
- Create `bruno/amidog/bruno.json`.
- Create Bruno requests under `bruno/amidog/`.
- Create `playwright.config.js`.
- Create `e2e/amidog-booking.spec.js`.
- Create `e2e/helpers.js`.
- Modify `package.json` and `package-lock.json`.
- Modify `README.md`.
- Create `docs/configuration.md`.
- Create `docs/deployment.md`.
- Create `docs/testing.md`.
- Create `scripts/verify.ps1`.
- Create `scripts/package.ps1`.

## Task 1: Validated Website Email Contact

**Files:**

- Create: contact backend files listed above
- Create: `src/main/java/com/amidog/app/common/text/PlainTextSanitizer.java`
- Create: `src/test/java/com/amidog/app/contact/ContactIntegrationTests.java`
- Modify: `src/main/java/com/amidog/app/config/SecurityConfig.java`

**Interfaces:**

- Consumes: `EmailSender`, clinic email configuration, and `RateLimitService`.
- Produces: public `POST /api/v1/contact`.

- [ ] **Step 1: Write failing contact API tests**

```java
@Test
void sendsClinicInquiryAndUserAcknowledgementWithoutPersistingAMessage() throws Exception {
    mvc.perform(post("/api/v1/contact").with(csrf())
            .contentType(APPLICATION_JSON)
            .content("""
                {"name":"Ana Pérez","email":"ana@example.com",
                 "message":"Quisiera consultar por una vacuna.","website":""}
                """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Tu consulta fue enviada."));

    assertThat(emailSender.messages()).satisfiesExactly(
        clinic -> {
            assertThat(clinic.to()).isEqualTo("contacto@amidog.cl");
            assertThat(clinic.replyTo()).isEqualTo("ana@example.com");
            assertThat(clinic.text()).contains("Ana Pérez", "Quisiera consultar");
        },
        acknowledgement -> {
            assertThat(acknowledgement.to()).isEqualTo("ana@example.com");
            assertThat(acknowledgement.replyTo()).isNull();
        });
}

@Test
void silentlyAcceptsAHoneypotWithoutSendingMail() throws Exception {
    mvc.perform(post("/api/v1/contact").with(csrf())
            .contentType(APPLICATION_JSON)
            .content("""
                {"name":"Bot","email":"bot@example.com",
                 "message":"Automated spam message","website":"https://spam.invalid"}
                """))
        .andExpect(status().isAccepted());
    assertThat(emailSender.messages()).isEmpty();
}
```

- [ ] **Step 2: Run and verify `404`**

Run:

```powershell
.\mvnw.cmd -Dtest=ContactIntegrationTests test
```

Expected: FAIL because the endpoint does not exist.

- [ ] **Step 3: Implement request validation and safe plain text**

```java
public record ContactRequest(
        @NotBlank @Size(min=2, max=120) String name,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=10, max=2000) String message,
        @Size(max=200) String website) {}
```

`PlainTextSanitizer`:

```java
public String clean(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
    return normalized
        .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim();
}
```

The service validates the cleaned values again, treats nonblank `website` as a successful no-op, and uses the submitted email only as `replyTo`, never as the SMTP sender.

- [ ] **Step 4: Send both messages and expose controlled failure**

Send the clinic inquiry first and the acknowledgement second. Return `202` only after both `EmailSender.send` calls return. Translate `MailException` to `503`:

```json
{
  "code": "EMAIL_DELIVERY_UNAVAILABLE",
  "message": "No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp.",
  "errors": {}
}
```

Rate limit contact to five accepted submissions per remote address per hour. Do not log the message.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=ContactIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/contact src/main/java/com/amidog/app/common src/main/java/com/amidog/app/config/SecurityConfig.java src/test/java/com/amidog/app/contact
  git commit -m "feat: add secure website email contact"
}
```

Expected: PASS for validation, honeypot, rate limit, two accepted emails, and provider failure.

## Task 2: Contact Hub Frontend and Direct WhatsApp

**Files:**

- Create: `src/api/contactApi.js`
- Modify: `src/pages/Contacto.jsx`
- Modify: `src/styles/_contacto.scss`
- Create: `src/pages/Contacto.test.jsx`
- Modify: `.env.example`

**Interfaces:**

- Produces: working email form and configured `wa.me` action.

- [ ] **Step 1: Write failing channel tests**

```jsx
it('submits a valid email inquiry to the backend', async () => {
  const api = { send: vi.fn().mockResolvedValue({ message: 'Tu consulta fue enviada.' }) }
  renderApp(<Contacto api={api} />)
  await user.type(screen.getByLabelText('Nombre'), 'Ana Pérez')
  await user.type(screen.getByLabelText(/correo/i), 'ana@example.com')
  await user.type(screen.getByLabelText('Mensaje'), 'Necesito consultar por una vacuna.')
  await user.click(screen.getByRole('button', { name: /enviar por correo/i }))
  expect(api.send).toHaveBeenCalledWith({
    name: 'Ana Pérez',
    email: 'ana@example.com',
    message: 'Necesito consultar por una vacuna.',
    website: '',
  })
})

it('creates a direct clinic WhatsApp URL with an editable prefilled message', () => {
  renderApp(<Contacto api={fakeContactApi()} whatsappNumber="56912345678" />)
  expect(screen.getByRole('link', { name: /contactar por whatsapp/i }))
    .toHaveAttribute('href',
      'https://wa.me/56912345678?text=Hola%20AmiDog%2C%20quiero%20hacer%20una%20consulta.')
})
```

- [ ] **Step 2: Run and verify current fake-success behavior fails**

Run:

```powershell
npm test -- src/pages/Contacto.test.jsx
```

Expected: FAIL because the form only shows a local toast and WhatsApp has no clinic number.

- [ ] **Step 3: Implement the contact API and honeypot field**

```js
export const contactApi = {
  send: (body) => apiRequest('/api/v1/contact', { body, method: 'POST' }),
}

export function whatsappUrl(number, message) {
  const digits = String(number || '').replace(/\D/g, '')
  if (!digits) return null
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`
}
```

Render `website` as an off-screen, keyboard-inaccessible input with `tabIndex="-1"` and `autoComplete="off"`. It remains in submitted state for bot detection.

- [ ] **Step 4: Add accessible channel choice and network states**

- change submit text to `Enviar por correo`;
- show `Contactar por WhatsApp` as the visually primary alternative;
- disable email submit while sending;
- keep values after a failed request;
- clear values only on `202`;
- announce success or provider/rate-limit error;
- omit the WhatsApp link and show a configuration warning only in development when the number is absent.

Add:

```properties
VITE_WHATSAPP_NUMBER=56900000000
```

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
npm test -- src/pages/Contacto.test.jsx
npm run lint
if (Test-Path .git) {
  git add src/api/contactApi.js src/pages/Contacto.jsx src/pages/Contacto.test.jsx src/styles/_contacto.scss .env.example
  git commit -m "feat: add email and WhatsApp contact choices"
}
```

Expected: PASS for validation, success, failure, rate limit, honeypot, and URL generation.

## Task 3: Idempotent Appointment Reminders and Token Cleanup

**Files:**

- Create: scheduled job files listed above
- Modify: notification repository/service
- Modify: token repositories
- Modify: `src/main/java/com/amidog/app/AppApplication.java`
- Create: scheduled job tests listed above

**Interfaces:**

- Produces: hourly upcoming appointment reminders and daily expired-token deletion.

- [ ] **Step 1: Write failing scheduled-service tests**

```java
@Test
void createsOneReminderInsideTheTwentyFourHourWindowEvenWhenRetried() {
    Reservation reservation = fixtures.confirmedAt(clock.instant().plus(24, HOURS));

    job.createUpcomingReminders();
    job.createUpcomingReminders();

    assertThat(notifications.findAllByReservationId(reservation.getId()))
        .filteredOn(item -> item.getType() == APPOINTMENT_REMINDER)
        .singleElement();
}

@Test
void ignoresPendingCancelledCompletedAndNoShowReservations() {
    fixtures.atStatus(PENDING, clock.instant().plus(24, HOURS));
    fixtures.atStatus(CANCELLED, clock.instant().plus(24, HOURS));
    fixtures.atStatus(COMPLETED, clock.instant().plus(24, HOURS));
    fixtures.atStatus(NO_SHOW, clock.instant().plus(24, HOURS));
    job.createUpcomingReminders();
    assertThat(notifications.findAll()).isEmpty();
}
```

- [ ] **Step 2: Run and verify missing jobs**

Run:

```powershell
.\mvnw.cmd -Dtest=AppointmentReminderJobIntegrationTests,ExpiredTokenCleanupJobIntegrationTests test
```

Expected: compilation FAIL.

- [ ] **Step 3: Implement the reminder window**

```java
@Scheduled(cron = "${amidog.reminders.cron:0 5 * * * *}")
@Transactional
public void createUpcomingReminders() {
    Instant from = clock.instant().plus(Duration.ofHours(23));
    Instant to = clock.instant().plus(Duration.ofHours(25));
    reservations.findConfirmedStartingBetween(from, to).forEach(reservation ->
        notifications.create(
            reservation.getClient().getUser().getId(),
            APPOINTMENT_REMINDER,
            "Recordatorio de cita",
            reminderBody(reservation),
            reservation.getId(),
            "APPOINTMENT_REMINDER:" + reservation.getId()));
}
```

Only `CONFIRMED` appointments receive reminders. The unique deduplication key makes repeated hourly scans safe.

- [ ] **Step 4: Implement token cleanup**

Run daily:

```java
@Scheduled(cron = "${amidog.tokens.cleanup-cron:0 30 3 * * *}")
@Transactional
public void removeExpiredAndConsumedTokens() {
    Instant cutoff = clock.instant().minus(Duration.ofDays(7));
    verificationTokens.deleteExpiredOrConsumedBefore(cutoff, clock.instant());
    resetTokens.deleteExpiredOrConsumedBefore(cutoff, clock.instant());
}
```

Enable scheduling on `AppApplication`. Scheduled methods delegate to callable methods so tests invoke them directly with a fixed `Clock`.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=AppointmentReminderJobIntegrationTests,ExpiredTokenCleanupJobIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app src/test/java/com/amidog/app/notification src/test/java/com/amidog/app/auth
  git commit -m "feat: add reminder and token maintenance jobs"
}
```

Expected: PASS for time window boundaries, statuses, deduplication, and cleanup retention.

## Task 4: Production Security and Configuration Regression

**Files:**

- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/java/com/amidog/app/config/AmidogProperties.java`
- Modify: `src/main/java/com/amidog/app/config/SecurityConfig.java`
- Modify: `.env.example`
- Create: `src/test/java/com/amidog/app/security/SecurityRegressionIntegrationTests.java`

**Interfaces:**

- Produces: exact-origin CORS when required, secure production cookies, safe headers, bounded sessions, and environment validation.

- [ ] **Step 1: Write failing security regression tests**

```java
@Test
void rejectsAuthenticatedMutationWithoutCsrf() throws Exception {
    mvc.perform(patch("/api/v1/me/profile")
            .session(accounts.verifiedClient("ana@example.com").session())
            .contentType(APPLICATION_JSON)
            .content("""{"name":"Ana","phone":"+56912345678"}"""))
        .andExpect(status().isForbidden());
}

@Test
void permitsOnlyTheConfiguredFrontendOrigin() throws Exception {
    mvc.perform(options("/api/v1/auth/me")
            .header("Origin", "https://app.amidog.cl")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().string("Access-Control-Allow-Origin", "https://app.amidog.cl"));
    mvc.perform(options("/api/v1/auth/me")
            .header("Origin", "https://evil.invalid")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
}
```

- [ ] **Step 2: Run and inspect current failures**

Run:

```powershell
.\mvnw.cmd -Dtest=SecurityRegressionIntegrationTests test
```

Expected: at least the configured-origin test FAILS before CORS configuration.

- [ ] **Step 3: Configure exact deployment behavior**

Add typed properties:

```yaml
amidog:
  frontend-origin: ${FRONTEND_ORIGIN:http://localhost:5173}
server:
  servlet:
    session:
      timeout: 8h
      cookie:
        http-only: true
        secure: ${SESSION_COOKIE_SECURE:false}
        same-site: lax
spring:
  session:
    jdbc:
      initialize-schema: never
```

Build `CorsConfiguration` from exactly one parsed origin, allow credentials, methods `GET,POST,PATCH,PUT,DELETE,OPTIONS`, and headers `Content-Type,X-XSRF-TOKEN`. Do not use `*`.

- [ ] **Step 4: Verify headers and error redaction**

Tests assert:

- frame options deny;
- content type options are disabled;
- referrer policy is `strict-origin-when-cross-origin`;
- unauthenticated protected APIs return JSON `401`, not HTML;
- unauthorized admin access returns JSON `403`;
- unexpected errors contain no class, stack, SQL, JDBC URL, or credential values.

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=SecurityRegressionIntegrationTests test
if (Test-Path .git) {
  git add src/main/resources/application.yaml src/main/java/com/amidog/app/config .env.example src/test/java/com/amidog/app/security
  git commit -m "security: harden production session and origin policy"
}
```

Expected: PASS.

## Task 5: Local Operations and Bruno API Collection

**Files:**

- Create: `compose.yaml`
- Create: Bruno files listed above
- Modify: `vite.config.js`
- Create: `docs/configuration.md`
- Create: `docs/deployment.md`

**Interfaces:**

- Produces: reproducible local PostgreSQL/SMTP setup and manual API inspection without embedding secrets.

- [ ] **Step 1: Create local service definitions**

`compose.yaml` defines:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: amidog
      POSTGRES_USER: amidog
      POSTGRES_PASSWORD: amidog-local
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U amidog -d amidog"]
      interval: 5s
      timeout: 3s
      retries: 10
    volumes:
      - amidog-postgres:/var/lib/postgresql/data
  mailpit:
    image: axllent/mailpit:v1.30.0
    ports:
      - "1025:1025"
      - "8025:8025"
volumes:
  amidog-postgres:
```

- [ ] **Step 2: Expand the Vite proxy**

```js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
},
```

Remove the obsolete `/reservation` proxy.

- [ ] **Step 3: Create a secret-free Bruno collection**

Use collection variables:

```json
{
  "version": "1",
  "name": "AmiDog API",
  "type": "collection",
  "ignore": ["node_modules", ".git"]
}
```

Requests cover CSRF, register, verify, login, current account, services, availability, pets, reservations, admin dashboard, status changes, blocks, notifications, contact, and logout. Mutation pre-request scripts read the `XSRF-TOKEN` cookie and set `X-XSRF-TOKEN`. Example emails/passwords remain collection variables and are not production credentials.

- [ ] **Step 4: Document local and deployment configuration**

`docs/configuration.md` maps each environment variable to purpose, format, required profile, and safe example. `docs/deployment.md` describes:

- PostgreSQL provisioning and Flyway startup;
- HTTPS requirement for secure cookies;
- frontend/backend same-origin preference;
- exact-origin alternative;
- SMTP DNS/provider setup;
- administrator bootstrap rotation;
- backup and restore;
- health verification and rollback.

- [ ] **Step 5: Validate compose and checkpoint**

Run:

```powershell
docker compose config
if (Test-Path .git) {
  git add compose.yaml bruno vite.config.js docs
  git commit -m "docs: add local services and API collection"
}
```

Expected: compose configuration parses without warnings about missing variables.

## Task 6: Browser Acceptance Test

**Files:**

- Modify: `package.json`
- Modify: `package-lock.json`
- Create: `playwright.config.js`
- Create: `e2e/amidog-booking.spec.js`
- Create: `docs/testing.md`

**Interfaces:**

- Produces: automated verification of the critical client/admin booking lifecycle in a real browser.

- [ ] **Step 1: Install and configure Playwright**

Run:

```powershell
npm install --save-dev @playwright/test
npx playwright install chromium
```

Add:

```json
{
  "scripts": {
    "test:e2e": "playwright test"
  }
}
```

Configure `baseURL: 'http://127.0.0.1:5173'`, Chromium only, one worker, trace on first retry, and screenshots only on failure.

Configure two Playwright web servers so their processes are owned and stopped by the test runner:

```js
const maven = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `${maven} spring-boot:run`,
      url: 'http://127.0.0.1:8080/api/v1/auth/csrf',
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        DB_URL: 'jdbc:postgresql://127.0.0.1:5432/amidog',
        DB_USERNAME: 'amidog',
        DB_PASSWORD: 'amidog-local',
        ADMIN_EMAIL: 'admin@amidog.test',
        ADMIN_PASSWORD: 'Admin-Password-19!',
        ADMIN_NAME: 'Administradora AmiDog',
        ADMIN_PHONE: '+56900000000',
        EMAIL_DELIVERY: 'smtp',
        SMTP_HOST: '127.0.0.1',
        SMTP_PORT: '1025',
        CLINIC_EMAIL: 'contacto@amidog.test',
        FRONTEND_BASE_URL: 'http://127.0.0.1:5173',
      },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: false,
      timeout: 60_000,
    },
  ],
})
```

- [ ] **Step 2: Write the acceptance flow**

```js
test('verified client books and admin confirms a multi-pet reservation', async ({ browser, request }) => {
  const client = uniqueClient()
  const clientContext = await browser.newContext()
  const clientPage = await clientContext.newPage()
  await clientPage.goto('/crear-cuenta')
  await register(clientPage, client)
  const verificationUrl = await readMailpitVerificationUrl(request, client.email)
  await clientPage.goto(verificationUrl)
  await login(clientPage, client.email, client.password)
  await addPet(clientPage, { name: 'Milo', species: 'Gato' })
  await addPet(clientPage, { name: 'Luna', species: 'Perro' })

  const adminContext = await browser.newContext()
  const adminPage = await adminContext.newPage()
  await login(adminPage, 'admin@amidog.test', 'Admin-Password-19!')
  await createService(adminPage, 'consulta-general', 'Consulta general')
  await createService(adminPage, 'vacunacion', 'Vacunación')
  await configureMondayHours(adminPage, '09:00', '12:00')

  await clientPage.goto('/reservar')
  await selectPetService(clientPage, 'Milo', 'Consulta general')
  await selectPetService(clientPage, 'Luna', 'Vacunación')
  await selectFirstAvailableSlot(clientPage)
  await confirmReservation(clientPage)
  await expect(clientPage.getByText(/pendiente/i)).toBeVisible()

  await adminPage.goto('/admin')
  await openReservationFor(adminPage, 'Milo')
  await adminPage.getByRole('button', { name: 'Confirmar' }).click()
  await expect(adminPage.getByText(/confirmada/i)).toBeVisible()

  await clientPage.reload()
  await expect(clientPage.getByText(/confirmada/i)).toBeVisible()
})
```

`e2e/helpers.js` exports every helper shown above. `readMailpitVerificationUrl` polls:

```js
import { expect } from '@playwright/test'

export async function readMailpitVerificationUrl(request, email) {
  const endpoint = `http://127.0.0.1:8025/view/latest.txt?query=${encodeURIComponent(`to:"${email}"`)}`
  await expect.poll(async () => (await request.get(endpoint)).status(), {
    timeout: 15_000,
  }).toBe(200)
  const body = await (await request.get(endpoint)).text()
  const match = body.match(/https?:\/\/127\.0\.0\.1:5173\/verificar-correo\?token=[^\s]+/)
  if (!match) throw new Error('Verification URL was not present in the captured email.')
  return match[0]
}
```

The remaining helpers use visible roles and labels, never CSS implementation selectors. `uniqueClient()` returns a timestamp/UUID email plus a 12-character test password; service helpers use unique codes so reruns do not collide.

- [ ] **Step 3: Add collision and cancellation acceptance cases**

Second test opens two client contexts, selects the same slot, confirms concurrently, and asserts one success plus one `SLOT_ALREADY_BOOKED` recovery message. It then cancels the successful reservation, verifies it remains in history as `Cancelada`, and verifies the slot becomes selectable again.

- [ ] **Step 4: Run the browser suite against local services**

Run:

```powershell
docker compose up -d --wait
npm run test:e2e
```

Playwright owns and stops backend/frontend through `webServer`. Leave PostgreSQL data intact unless the operator explicitly requests removal.

Expected: the booking/confirmation and collision/cancellation tests PASS.

- [ ] **Step 5: Document and checkpoint**

`docs/testing.md` explains unit, integration, PostgreSQL constraint, frontend, and browser suites plus Docker prerequisites.

```powershell
if (Test-Path .git) {
  git add package.json package-lock.json playwright.config.js e2e docs/testing.md
  git commit -m "test: add browser booking acceptance flow"
}
```

## Task 7: One-Command Verification and Clean ZIP

**Files:**

- Create: `scripts/verify.ps1`
- Create: `scripts/package.ps1`
- Modify: `README.md`
- Delete: preview logs and generated artifacts from the source handoff

**Interfaces:**

- Produces: repeatable verification plus `outputs/amidog-complete.zip`.

- [ ] **Step 1: Create a fail-fast verification script**

`scripts/verify.ps1`:

```powershell
$ErrorActionPreference = 'Stop'

& .\mvnw.cmd test
if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }

& npm ci
if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }

& npm test
if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }

& npm run lint
if ($LASTEXITCODE -ne 0) { throw 'Frontend lint failed.' }

& npm run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }

& .\mvnw.cmd -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }
```

The browser suite remains a documented optional flag because it starts services and downloads Chromium:

```powershell
param([switch]$IncludeE2E)
if ($IncludeE2E) {
  & npm run test:e2e
  if ($LASTEXITCODE -ne 0) { throw 'Browser tests failed.' }
}
```

- [ ] **Step 2: Create a path-checked packaging script**

`scripts/package.ps1` resolves the project and output paths, asserts that the output is under the shared workspace `outputs` directory, stages only source/config/docs into a temporary directory, and excludes:

```text
.git
.env
node_modules
target
dist
playwright-report
test-results
*.log
*.db
*.sqlite
```

It creates `outputs/amidog-complete.zip`, then removes only its validated temporary staging directory.

- [ ] **Step 3: Update the root README**

README sections:

1. product scope and excluded medical/message features;
2. architecture;
3. prerequisites;
4. environment setup;
5. local start;
6. account verification and admin bootstrap;
7. booking/availability rules;
8. tests;
9. Bruno;
10. production deployment;
11. final project structure.

- [ ] **Step 4: Run the clean final verification and inspect the archive**

Run:

```powershell
.\scripts\verify.ps1
.\scripts\package.ps1
tar -tf ..\..\outputs\amidog-complete.zip
```

Expected:

- backend tests PASS;
- frontend tests and lint PASS;
- both production builds PASS;
- ZIP contains source, migrations, tests, docs, scripts, `.env.example`, and Bruno;
- ZIP contains none of the excluded paths or secrets.

- [ ] **Step 5: Final checkpoint**

```powershell
if (Test-Path .git) {
  git add -A
  git commit -m "release: package complete AmiDog platform"
  git status --short
}
```

Expected: empty status when Git exists. When it does not, record all changed files and verification results in the final handoff.
