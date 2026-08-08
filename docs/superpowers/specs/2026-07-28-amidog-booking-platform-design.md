# AmiDog Booking and Administration Platform Design

Date: 2026-07-28

## 1. Objective

Turn the existing AmiDog public website, Phase 1 Spring Boot booking endpoint, client panel, and attached administrator dashboard into one cohesive appointment-management application.

The completed system must let verified clients:

- create and manage an account;
- register and archive their pets;
- browse the veterinarian's real availability in Chilean local time;
- reserve one 30-minute appointment for one or more of their pets;
- choose one service for each pet;
- view, reschedule, and cancel their appointments;
- contact the clinic through WhatsApp or a website email form.

The single veterinarian must be able to:

- sign in to a protected administrator dashboard;
- review, confirm, cancel, reschedule, complete, and mark appointments as no-shows;
- search and inspect clients and pets;
- edit the service catalog;
- configure weekly working hours;
- block an entire day or a specific time range;
- read in-app operational notifications.

The platform is an appointment and customer-management system. It will not store medical records.

## 2. Scope

### Included

- One consolidated React/Vite frontend with public, client, and administrator routes.
- One Spring Boot backend.
- PostgreSQL persistence controlled through Flyway migrations.
- Verified email/password client accounts.
- One seeded administrator account.
- Secure server-side sessions.
- Provider-independent transactional email delivery.
- Email verification and password reset.
- A disabled extension point for future Google authentication.
- Thirty-minute, collision-safe scheduling in `America/Santiago`.
- Editable weekly availability and temporary unavailability blocks.
- Multiple pets per reservation with one service selected per pet.
- Configurable automatic confirmation, disabled by default.
- Reservation lifecycle and audit history.
- Editable and archivable services.
- Client and pet management.
- In-app operational notifications.
- Website email contact form.
- Direct WhatsApp contact link.
- Automated backend, database, API, and frontend tests.

### Excluded

- Medical records, diagnoses, procedures, treatments, prescriptions, vaccination records, or clinical notes.
- In-app client-to-veterinarian chat.
- WhatsApp Business API, relays, webhooks, or synchronized WhatsApp conversations.
- Active Google login until credentials and product approval are supplied.
- Multiple administrator, veterinarian, or receptionist roles.
- Payments and ecommerce.
- Hard deletion of historical reservations.

The unused clinical and ecommerce skeleton classes will be removed so the final project represents the actual product.

## 3. Architecture Decision

### Chosen approach: modular monolith with one frontend

- Merge the attached administrator dashboard into the existing React application under administrator routes.
- Keep one Spring Boot application containing isolated authentication, scheduling, reservation, client, pet, service, notification, and contact modules.
- Keep one PostgreSQL database.
- Package frontend and backend together in one repository and one final ZIP.

This is the recommended fit for a single-clinic application with one administrator. It minimizes deployment, authentication, CORS, configuration, and operational complexity while retaining clear module boundaries.

### Alternatives considered

1. **Two separate React applications with one backend**
   - Advantage: administrator releases can be deployed independently.
   - Disadvantage: duplicate build configuration, authentication integration, environment configuration, and hosting.
   - Rejected because the application is small and requires a cohesive handoff.

2. **Separate backend services for authentication, scheduling, and messaging**
   - Advantage: independent scaling.
   - Disadvantage: unnecessary infrastructure, distributed transactions, more deployment failure modes, and no current scale requirement.
   - Rejected as disproportionate to one veterinarian and one clinic.

## 4. User Types and Authentication

### Client accounts

A client registers with:

- email;
- password;
- name;
- phone.

The account begins as `UNVERIFIED`. Registration creates a one-time email-verification token. The raw token is sent by email; only a secure hash is persisted. Tokens expire after 24 hours and can be used once.

Only verified clients can create reservations or manage pets. Verification-email resend and registration endpoints are rate-limited and return non-enumerating responses.

Passwords are encoded with Spring Security's recommended adaptive password encoder. Passwords, verification tokens, session identifiers, and email credentials are never logged.

Password reset uses the same secure, expiring, single-use token pattern.

### Administrator account

There is one administrator: the veterinarian. No public endpoint can create administrator accounts. The initial administrator is seeded through a Flyway-safe application bootstrap using environment-provided credentials, with the password stored only as a secure hash.

### Sessions and authorization

- Spring Security manages authenticated sessions.
- Spring Session JDBC stores sessions in PostgreSQL so they survive application restarts.
- Cookies are `HttpOnly`, `Secure` in production, and `SameSite=Lax`.
- CSRF remains enabled for authenticated mutations.
- Client APIs derive the current client from the session rather than accepting arbitrary client IDs.
- Administrator APIs require the `ADMIN` authority.
- The public contact form, registration, verification, password-reset request, active-service listing, and availability query remain unauthenticated.

### Google-ready extension

Local credentials remain on the user account. Future OAuth identities are linked through a separate `user_external_identities` table, allowing the same client account to support both password and Google sign-in:

- `id`
- `user_id`
- `provider`
- `provider_subject`
- `created_at`

The pair (`provider`, `provider_subject`) is unique. A Google-verified email can later be linked to an existing client account only through an authenticated or explicitly verified account-linking flow. No OAuth endpoints are active in this release.

No Google button, callback, dependency configuration, or active OAuth flow is exposed in this version. Activating Google later must not require schema redesign.

## 5. Core Data Model

### `users`

- `id`
- `email_normalized` - unique and non-null
- `password_hash` - nullable only for a future Google-only account
- `email_verified_at`
- `account_type` - `CLIENT` or `ADMIN`
- `enabled`
- `created_at`
- `updated_at`
- optimistic-lock version

### `clients`

- `id`
- `user_id` - unique and non-null
- `name`
- `phone`
- `active`
- `created_at`
- `updated_at`
- optimistic-lock version

### `pets`

- `id`
- `client_id`
- `name`
- `species`
- `breed` - optional
- `birthdate` - optional
- `active`
- `created_at`
- `updated_at`
- optimistic-lock version

Pet health status is intentionally absent. Medical information remains in the veterinarian's external application.

Archiving a pet is rejected while it belongs to a future `PENDING` or `CONFIRMED` reservation. Those reservations must be cancelled or otherwise resolved first.

### `services`

- `id`
- `code` - stable unique identifier
- `name`
- `description` - optional
- `active`
- `display_order`
- `created_at`
- `updated_at`
- optimistic-lock version

Services are archived rather than deleted when referenced by a reservation.

### `reservations`

- `id`
- `client_id`
- `scheduled_start`
- `scheduled_end`
- `status`
- `client_note` - optional and non-clinical
- `created_at`
- `updated_at`
- `cancelled_at` - optional
- `cancelled_by` - optional
- `cancellation_reason` - optional
- optimistic-lock version

Every reservation lasts exactly 30 minutes in total, regardless of how many pets it contains. A database constraint requires `scheduled_end = scheduled_start + 30 minutes`.

### `reservation_items`

- `id`
- `reservation_id`
- `pet_id`
- `service_id`
- `service_name_snapshot`

Each pet appears at most once in a reservation. The pet must belong to the authenticated reservation owner. The service snapshot preserves historical meaning if the administrator later renames a service.

### `reservation_events`

- `id`
- `reservation_id`
- `event_type`
- `actor_type`
- `previous_status` - optional
- `new_status` - optional
- `previous_start` - optional
- `new_start` - optional
- `reason` - optional
- `created_at`

Events preserve confirmation, cancellation, completion, no-show, and rescheduling history.

### `weekly_availability`

- `id`
- `day_of_week`
- `local_start_time`
- `local_end_time`
- `active`

More than one interval per day is allowed, enabling a split working day.

### `availability_blocks`

- `id`
- `start_at`
- `end_at`
- `reason` - optional
- `created_at`

A block can cover a partial day or an entire local date. Creating a block that overlaps an active reservation returns `409 Conflict` with the conflicting reservations so the administrator can reschedule or cancel them first.

### `notifications`

- `id`
- `recipient_user_id`
- `type`
- `title`
- `body`
- `reservation_id` - optional
- `read_at` - optional
- `created_at`

### `email_verification_tokens` and `password_reset_tokens`

- token hash;
- user reference;
- expiration;
- consumed timestamp;
- creation timestamp.

Expired and consumed tokens are periodically removed.

## 6. Reservation Lifecycle

Statuses:

- `PENDING`
- `CONFIRMED`
- `CANCELLED`
- `COMPLETED`
- `NO_SHOW`

Allowed transitions:

- `PENDING -> CONFIRMED`
- `PENDING -> CANCELLED`
- `CONFIRMED -> CANCELLED`
- `CONFIRMED -> COMPLETED`
- `CONFIRMED -> NO_SHOW`

Terminal statuses are not modified through normal user flows. Administrative correction, if ever needed, must create an explicit audit event rather than silently rewriting history.

Cancellation is a status transition, never a hard delete. It records who cancelled, when, and why.

Client-initiated rescheduling revalidates availability and applies the configured initial status policy again. With the default configuration, a rescheduled appointment becomes `PENDING`. Administrator-initiated rescheduling preserves `CONFIRMED` when the administrator has explicitly selected the new slot.

Automatic confirmation is implemented as a tested configuration:

```properties
BOOKING_AUTO_CONFIRM=false
```

- `false`: new or client-rescheduled reservations become `PENDING`.
- `true`: they become `CONFIRMED`.

Both branches remain active and tested. No commented-out implementation is used.

## 7. Scheduling and Chilean Time

The clinic timezone is the IANA zone:

```text
America/Santiago
```

Rules:

- The frontend displays and collects clinic dates and times in `America/Santiago`.
- The backend converts local clinic selections with `ZoneId.of("America/Santiago")`.
- PostgreSQL stores timezone-aware instants using `timestamptz`.
- API responses use ISO-8601 timestamps containing the applicable Chilean UTC offset.
- UTC-3 or UTC-4 is never hard-coded because Chilean daylight-saving rules change the offset.
- Whole-day blocks run from local midnight to the next local midnight in `America/Santiago`.
- Ambiguous or nonexistent local times produced by daylight-saving transitions are rejected or normalized by an explicitly tested scheduling policy.

Slots:

- are generated in 30-minute increments within active weekly availability;
- are removed when they overlap an availability block;
- are removed when they overlap a `PENDING` or `CONFIRMED` reservation;
- are available again when a reservation becomes `CANCELLED`;
- can be queried only within the configured booking horizon;
- respect a configured minimum-notice period.

Defaults:

```properties
BOOKING_DURATION_MINUTES=30
BOOKING_MIN_NOTICE_HOURS=2
BOOKING_HORIZON_DAYS=90
CLINIC_TIMEZONE=America/Santiago
```

The duration is fixed by the product requirement. Notice and horizon remain documented configuration rather than hard-coded business assumptions.

### Concurrency protection

Frontend availability is advisory. The backend performs the final check inside the reservation transaction.

PostgreSQL enforces a range-exclusion constraint preventing overlapping `PENDING` or `CONFIRMED` reservations. A concurrent request for the same block returns `409 Conflict`, and the frontend refreshes availability with a clear message.

## 8. Availability Administration

The administrator can:

- create, edit, activate, and deactivate recurring weekly intervals;
- add a partial-day block;
- add a whole-day block;
- view reservations affected before attempting a block;
- remove a future block;
- see generated availability in the same calendar used by clients.

Changing weekly hours does not silently cancel existing reservations. Creating an explicit block cannot displace an active reservation without an administrator first resolving the conflict.

## 9. Notifications

In-app notifications remain, but the in-app messaging system is removed.

Initial notification triggers:

- administrator: new reservation;
- administrator: client cancellation;
- administrator: client rescheduling;
- client: reservation confirmed;
- client: reservation cancelled by administrator;
- client: reservation rescheduled by administrator;
- client: appointment reminder approximately 24 hours before the appointment.

Notifications are idempotent, associated with a reservation where applicable, and can be marked individually or collectively as read.

## 10. Contact Channels

The Contact page remains as a channel-selection hub.

### WhatsApp

- A primary `Contactar por WhatsApp` action opens the configured clinic number through `wa.me`.
- The message is prefilled but editable.
- The destination number comes from `VITE_WHATSAPP_NUMBER`.
- No WhatsApp API, relay, webhook, or stored conversation is involved.

### Email form

The existing form becomes functional through:

```text
POST /api/v1/contact
```

It validates and sanitizes:

- name;
- reply email;
- message.

The endpoint uses rate limiting and a hidden honeypot. It sends the inquiry to the configured clinic email and sends a short acknowledgement to the user. It returns success only after the delivery provider accepts the messages. It does not create an in-app conversation.

Email delivery is provider-independent through an `EmailSender` interface and Spring Mail SMTP configuration. Development uses a non-delivering logger/test adapter. Production uses environment-provided SMTP credentials from a transactional provider.

## 11. API Shape

All new endpoints use `/api/v1`.

### Authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/resend-verification`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `GET /api/v1/auth/csrf`

### Public

- `GET /api/v1/services`
- `GET /api/v1/availability?from=&to=`
- `POST /api/v1/contact`

### Authenticated client

- `GET /api/v1/me/profile`
- `PATCH /api/v1/me/profile`
- `GET /api/v1/me/pets`
- `POST /api/v1/me/pets`
- `PATCH /api/v1/me/pets/{id}`
- `DELETE /api/v1/me/pets/{id}` - archives the pet
- `GET /api/v1/me/reservations`
- `POST /api/v1/me/reservations`
- `PATCH /api/v1/me/reservations/{id}/reschedule`
- `PATCH /api/v1/me/reservations/{id}/cancel`
- `GET /api/v1/me/notifications`
- `PATCH /api/v1/me/notifications/{id}/read`
- `POST /api/v1/me/notifications/read-all`

### Administrator

- `GET /api/v1/admin/dashboard`
- `GET /api/v1/admin/reservations`
- `GET /api/v1/admin/reservations/{id}`
- `PATCH /api/v1/admin/reservations/{id}/status`
- `PATCH /api/v1/admin/reservations/{id}/reschedule`
- `GET /api/v1/admin/clients`
- `GET /api/v1/admin/clients/{id}`
- `PATCH /api/v1/admin/clients/{id}`
- `GET /api/v1/admin/pets`
- `GET /api/v1/admin/pets/{id}`
- `PATCH /api/v1/admin/pets/{id}`
- `GET /api/v1/admin/services`
- `POST /api/v1/admin/services`
- `PATCH /api/v1/admin/services/{id}`
- `DELETE /api/v1/admin/services/{id}` - archives the service
- `GET /api/v1/admin/availability/weekly`
- `PUT /api/v1/admin/availability/weekly`
- `GET /api/v1/admin/availability/blocks`
- `POST /api/v1/admin/availability/blocks`
- `DELETE /api/v1/admin/availability/blocks/{id}`
- `GET /api/v1/admin/notifications`
- `PATCH /api/v1/admin/notifications/{id}/read`
- `POST /api/v1/admin/notifications/read-all`

Responses use dedicated DTOs. JPA entities are never serialized directly.

Validation errors use a consistent JSON envelope containing a message and field errors. Authentication failures return `401`, authorization failures return `403`, missing data returns `404`, invalid transitions return `409`, and scheduling collisions return `409`.

## 12. Frontend Consolidation

The original AmiDog frontend remains the application shell. The administrator dashboard is merged into it.

### Public and client changes

- Add real registration, verification, login, forgot-password, and reset-password flows.
- Protect client panel and reservation routes.
- Replace localStorage client/pet/appointment CRUD with API calls.
- Keep only temporary unfinished form state in sessionStorage where useful.
- Make the reservation form select one or more registered pets.
- Display a service selector for each selected pet.
- Load real available slots from the backend.
- Handle `409 Conflict` by refreshing slots and explaining that another client selected the time.
- Show pending versus confirmed language based on the backend response.
- Update the Contact page with functional email and WhatsApp options.

### Administrator changes

- Merge the attached dashboard components and styles under `/admin`.
- Add administrator login and route protection.
- Replace the mock dashboard adapter with the real API.
- Remove Messages navigation, message modals, reply controls, and message API methods.
- Retain notifications.
- Change destructive `Eliminar reserva` wording to `Cancelar reserva`.
- Add service management.
- Add weekly availability and blackout management.
- Add rescheduling, completion, and no-show actions.
- Use backend status codes and map them to Spanish labels in the UI.
- Derive decorative pet imagery from species rather than persisting frontend image keys.

Unauthorized API responses redirect to the appropriate login route. Loading, empty, validation, conflict, and server-error states remain accessible and retryable.

## 13. Database Migration

- Introduce Flyway as the only production schema authority.
- Set Hibernate production mode to `ddl-auto: validate`.
- Keep test schema creation under migration control.
- Remove the `Reservation_Pets`, clinical, procedure, cart, purchase, and product tables/entities from the final domain.
- Migrate existing Phase 1 reservations into the new reservation and reservation-item structure where data is unambiguous.
- Convert `confirmed=false` to `PENDING` and `confirmed=true` to `CONFIRMED`.
- Preserve the public Phase 1 route temporarily as a documented compatibility adapter during development, then update the frontend to `/api/v1/me/reservations`.
- Never package production secrets or a populated production database.

## 14. Security and Abuse Controls

- Normalize email addresses consistently.
- Verify ownership of every pet and reservation.
- Use secure password hashing and constant-time token comparison.
- Store only token hashes.
- Rate-limit registration, login, verification resend, password reset, contact, and reservation creation.
- Use generic authentication and account-recovery responses to prevent email enumeration.
- Keep CSRF protection enabled for session-authenticated mutations.
- Configure exact deployed frontend origins when CORS is required.
- Validate service activity and availability again on the server.
- Sanitize free-text contact and non-clinical reservation notes.
- Apply reasonable maximum lengths to every user-controlled string.
- Log security-relevant events without logging credentials, tokens, or private message content.

## 15. Testing Strategy

### Backend unit and integration tests

- Repository generic contracts.
- Flyway migration success.
- PostgreSQL schema constraints using Testcontainers.
- Registration, verification, expiration, one-time token consumption, and resend throttling.
- Password reset.
- Password hashing and non-enumerating auth responses.
- Session persistence, CSRF, client ownership, and administrator authorization.
- Thirty-minute slot generation.
- `America/Santiago` daylight-saving boundaries.
- Whole-day and partial blackout handling.
- Minimum notice and booking horizon.
- Collision rejection for sequential and concurrent requests.
- Pending and confirmed reservations occupying slots.
- Cancelled reservations releasing slots.
- Multiple pets with one service per pet.
- Service snapshots and service archiving.
- Allowed and rejected status transitions.
- Cancellation and rescheduling audit events.
- Contact validation, rate limiting, and email-provider failure.
- Notification creation and idempotence.
- Dashboard projections and aggregate counts.

### Frontend tests

- Registration and verification states.
- Login and protected-route handling.
- Pet CRUD and ownership-shaped API calls.
- Multi-pet selection and one service per pet.
- Availability loading and timezone display.
- Collision refresh and retry.
- Pending, confirmed, cancelled, completed, and no-show labels.
- Client cancellation and rescheduling flows.
- Administrator status, service, weekly-hours, and blackout actions.
- Notification read state.
- Contact email success/failure and WhatsApp URL generation.
- Removal of mock/localStorage persistence.

### End-to-end acceptance flow

1. Register and verify a client.
2. Add multiple pets.
3. Administrator creates services and availability.
4. Client books multiple pets with different services.
5. Concurrent booking of the same slot is rejected.
6. Administrator confirms the reservation.
7. Client receives a notification.
8. Administrator blocks a future interval.
9. Client reschedules and the appointment returns to the configured initial status.
10. Client cancels; history remains and the slot becomes available.
11. Contact email and WhatsApp paths work.

Required final commands include Maven tests, frontend tests, lint, and production builds. The final verification must start from a clean dependency/build state.

## 16. Delivery

Implementation proceeds in tested stages:

1. dependencies, Flyway, and domain migration;
2. authentication and email verification;
3. scheduling, availability, and conflict protection;
4. reservations, services, notifications, and administration APIs;
5. public/client frontend integration;
6. administrator frontend merge and integration;
7. contact channels;
8. complete test, lint, build, documentation, and packaging pass.

The final handoff is one clean ZIP containing:

- frontend and backend source;
- Flyway migrations;
- automated tests;
- README setup and deployment instructions;
- `.env.example`;
- API and configuration documentation.

The ZIP excludes:

- real secrets;
- `.env`;
- `node_modules`;
- `target`;
- `dist`;
- preview logs;
- temporary databases and generated artifacts.
