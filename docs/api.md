# AmiDog API v1

This reference describes the backend routes that exist in the current source.
The base path is `/api/v1`, request and response bodies use JSON unless noted,
and timestamps use ISO-8601. Reservation and availability timestamps contain
the real offset for `America/Santiago`; the server never assumes a fixed
UTC-3 or UTC-4 offset.

API enums and labels are stable codes. The React application is responsible
for mapping values such as `PENDING` or `NO_SHOW` to Spanish display labels.

## Exact route contract index

This table is the authoritative success contract for all 48 routes. `none`
means that the request has neither a body nor route-specific query values.
Every non-`204` response is JSON. `Location` is also returned by the four
`201 CREATED` collection routes.

| Route | Exact request | Success | Exact response |
|---|---|---|---|
| `GET /api/v1/auth/csrf` | none | `200 OK` | `CsrfResponse` |
| `POST /api/v1/auth/register` | `RegisterRequest` | `202 ACCEPTED` | `MessageResponse` |
| `POST /api/v1/auth/verify-email` | `TokenRequest` | `204 NO CONTENT` | no body |
| `POST /api/v1/auth/resend-verification` | `EmailRequest` | `202 ACCEPTED` | `MessageResponse` |
| `POST /api/v1/auth/login` | form fields `username,password` | `200 OK` | `AuthResponse` |
| `POST /api/v1/auth/logout` | none | `204 NO CONTENT` | no body |
| `GET /api/v1/auth/me` | none | `200 OK` | `AuthResponse` |
| `POST /api/v1/auth/forgot-password` | `EmailRequest` | `202 ACCEPTED` | `MessageResponse` |
| `POST /api/v1/auth/reset-password` | `ResetPasswordRequest` | `204 NO CONTENT` | no body |
| `POST /api/v1/contact` | `ContactRequest` | `202 ACCEPTED` | `ContactResponse` |
| `GET /api/v1/services` | none | `200 OK` | `ServiceResponse[]` |
| `GET /api/v1/availability` | query `from:date,to:date` | `200 OK` | `AvailableSlotResponse[]` |
| `GET /api/v1/me/profile` | none | `200 OK` | `ClientProfileResponse` |
| `PATCH /api/v1/me/profile` | `ClientProfileUpdateRequest` | `200 OK` | `ClientProfileResponse` |
| `GET /api/v1/me/pets` | none | `200 OK` | `PetResponse[]` |
| `POST /api/v1/me/pets` | `PetWriteRequest` | `201 CREATED` | `PetResponse` |
| `PATCH /api/v1/me/pets/{id}` | path `id:integer`; `PetWriteRequest` | `200 OK` | `PetResponse` |
| `DELETE /api/v1/me/pets/{id}` | path `id:integer` | `204 NO CONTENT` | no body |
| `GET /api/v1/me/reservations` | none | `200 OK` | `ReservationResponse[]` |
| `POST /api/v1/me/reservations` | `CreateReservationRequest` | `201 CREATED` | `ReservationResponse` |
| `PATCH /api/v1/me/reservations/{id}/cancel` | path `id:integer`; `CancelReservationRequest` | `200 OK` | `CancellationResponse` |
| `PATCH /api/v1/me/reservations/{id}/reschedule` | path `id:integer`; `RescheduleRequest` | `200 OK` | `RescheduleResponse` |
| `GET /api/v1/me/notifications` | none | `200 OK` | `NotificationResponse[]` |
| `PATCH /api/v1/me/notifications/{id}/read` | path `id:integer` | `200 OK` | `NotificationResponse` |
| `POST /api/v1/me/notifications/read-all` | none | `200 OK` | `ReadAllResponse` |
| `GET /api/v1/admin/dashboard` | none | `200 OK` | `DashboardResponse` |
| `GET /api/v1/admin/reservations` | query `from?:date,to?:date,status?:ReservationStatus,q?:string,page?:int,size?:int` | `200 OK` | `PageResponse<AdminReservationSummary>` |
| `GET /api/v1/admin/reservations/{id}` | path `id:integer` | `200 OK` | `AdminReservationDetail` |
| `PATCH /api/v1/admin/reservations/{id}/status` | path `id:integer`; `StatusChangeRequest` | `200 OK` | `StatusChangeResponse` |
| `PATCH /api/v1/admin/reservations/{id}/reschedule` | path `id:integer`; `RescheduleRequest` | `200 OK` | `RescheduleResponse` |
| `GET /api/v1/admin/clients` | query `q?:string,active?:boolean,page?:int,size?:int` | `200 OK` | `PageResponse<AdminClientSummary>` |
| `GET /api/v1/admin/clients/{id}` | path `id:integer` | `200 OK` | `AdminClientDetail` |
| `PATCH /api/v1/admin/clients/{id}` | path `id:integer`; `AdminClientUpdateRequest` | `200 OK` | `AdminClientSummary` |
| `GET /api/v1/admin/pets` | query `q?:string,active?:boolean,clientId?:integer,page?:int,size?:int` | `200 OK` | `PageResponse<AdminPetSummary>` |
| `GET /api/v1/admin/pets/{id}` | path `id:integer` | `200 OK` | `AdminPetDetail` |
| `PATCH /api/v1/admin/pets/{id}` | path `id:integer`; `AdminPetUpdateRequest` | `200 OK` | `AdminPetSummary` |
| `GET /api/v1/admin/services` | none | `200 OK` | `ServiceResponse[]` |
| `POST /api/v1/admin/services` | `ServiceCreateRequest` | `201 CREATED` | `ServiceResponse` |
| `PATCH /api/v1/admin/services/{id}` | path `id:integer`; `ServiceUpdateRequest` | `200 OK` | `ServiceResponse` |
| `DELETE /api/v1/admin/services/{id}` | path `id:integer` | `204 NO CONTENT` | no body |
| `GET /api/v1/admin/availability/weekly` | none | `200 OK` | `WeeklyIntervalResponse[]` |
| `PUT /api/v1/admin/availability/weekly` | `WeeklyIntervalRequest[]` | `200 OK` | `WeeklyIntervalResponse[]` |
| `GET /api/v1/admin/availability/blocks` | none | `200 OK` | `AvailabilityBlockResponse[]` |
| `POST /api/v1/admin/availability/blocks` | `AvailabilityBlockRequest` | `201 CREATED` | `AvailabilityBlockResponse` |
| `DELETE /api/v1/admin/availability/blocks/{id}` | path `id:integer` | `204 NO CONTENT` | no body |
| `GET /api/v1/admin/notifications` | none | `200 OK` | `NotificationResponse[]` |
| `PATCH /api/v1/admin/notifications/{id}/read` | path `id:integer` | `200 OK` | `NotificationResponse` |
| `POST /api/v1/admin/notifications/read-all` | none | `200 OK` | `ReadAllResponse` |

### Exact JSON object fields

The table below is machine-checked against the current Java records for the
administrator, availability, lifecycle, and notification contracts most
likely to affect the frontend.

| Schema | Fields in serialized order |
|---|---|
| `PageResponse` | `content,page,size,totalElements,totalPages` |
| `ReservationItemSummary` | `petId,petName,species,breed,serviceId,serviceName` |
| `AdminReservationSummary` | `id,clientId,clientName,clientEmail,clientPhone,startsAt,endsAt,status,clientNote,createdAt,updatedAt,items` |
| `ReservationEventSummary` | `id,eventType,actor,previousStatus,newStatus,previousStartsAt,newStartsAt,reason,createdAt` |
| `AdminReservationDetail` | `id,clientId,clientName,clientEmail,clientPhone,startsAt,endsAt,status,clientNote,createdAt,updatedAt,cancelledAt,cancelledBy,cancellationReason,items,events` |
| `AdminClientSummary` | `id,name,email,phone,active,createdAt,updatedAt` |
| `ClientPetSummary` | `id,name,species,breed,birthdate,active` |
| `ReservationCounts` | `total,upcoming,pending,confirmed,cancelled,completed,noShow` |
| `AdminClientDetail` | `id,name,email,phone,active,createdAt,updatedAt,pets,reservationCounts,upcomingReservations` |
| `AdminClientUpdateRequest` | `name,phone,active` |
| `AdminPetSummary` | `id,clientId,ownerName,name,species,breed,birthdate,active,createdAt,updatedAt` |
| `AdminPetDetail` | `id,clientId,ownerName,ownerEmail,name,species,breed,birthdate,active,createdAt,updatedAt,recentReservations` |
| `AdminPetUpdateRequest` | `name,species,breed,birthdate` |
| `StatusChangeRequest` | `status,reason` |
| `StatusChangeResponse` | `id,previousStatus,status,reason,updatedAt` |
| `WeeklyIntervalRequest` | `dayOfWeek,start,end,active` |
| `WeeklyIntervalResponse` | `id,dayOfWeek,start,end,active` |
| `AvailabilityBlockRequest` | `startsAt,endsAt,reason` |
| `AvailabilityBlockResponse` | `id,startsAt,endsAt,reason,createdAt` |
| `NotificationResponse` | `id,type,title,body,reservationId,createdAt,unread` |
| `ReadAllResponse` | `markedRead` |

### Reusable schema definitions

The notation below is exact: `?` means the JSON value may be `null`, `[]`
means an array, `date` is `YYYY-MM-DD`, `time` is ISO local time, `offset`
is ISO-8601 with the real Chilean offset, and `instant` is an ISO-8601 UTC
instant. Integer IDs are positive when supplied by a caller.

Authentication:

- `CsrfResponse {headerName:string, parameterName:string, token:string}`
- `RegisterRequest {email:string(email,max254), password:string(12..128),
  name:string(1..120), phone:string(1..30)}`
- `TokenRequest {token:string(1..256)}`
- `EmailRequest {email:string(email,max254)}`
- `ResetPasswordRequest {token:string(1..256), password:string(12..128)}`
- `MessageResponse {message:string}`
- `AuthResponse {userId:integer, clientId:integer?, email:string,
  name:string, accountType:CLIENT|ADMIN}`
- `ContactRequest {name:string(2..120), email:string(email,max254),
  message:string(10..2000), website:string?(max200)}`
- `ContactResponse {message:string}`

Client, pet, and service catalog:

- `ClientProfileResponse {id:integer, name:string, phone:string,
  email:string}`
- `ClientProfileUpdateRequest {name:string(1..120), phone:string(1..30)}`
- `PetWriteRequest {name:string(1..80), species:string(1..40),
  breed:string?(max80), birthdate:date?(past-or-present)}`
- `PetResponse {id:integer, name:string, species:string, breed:string?,
  birthdate:date?, active:boolean}`
- `ServiceCreateRequest {code:string(1..60), name:string(1..100),
  description:string?(max500), displayOrder:integer(min0)}`
- `ServiceUpdateRequest {name:string(1..100),
  description:string?(max500), displayOrder:integer(min0),
  active:boolean}`
- `ServiceResponse {id:integer, code:string, name:string,
  description:string?, active:boolean, displayOrder:integer}`

Scheduling, reservations, and notifications:

- `AvailableSlotResponse {startsAt:offset, endsAt:offset}`
- `WeeklyIntervalRequest {dayOfWeek:integer(1..7), start:time,
  end:time, active:boolean}`
- `WeeklyIntervalResponse {id:integer, dayOfWeek:integer(1..7),
  start:time, end:time, active:boolean}`
- `AvailabilityBlockRequest {startsAt:offset, endsAt:offset,
  reason:string?(max200)}`
- `AvailabilityBlockResponse {id:integer, startsAt:offset, endsAt:offset,
  reason:string?, createdAt:offset}`
- `ReservationItemRequest {petId:integer, serviceId:integer}`
- `CreateReservationRequest {startsAt:offset,
  items:ReservationItemRequest[1..10], note:string?(max500)}`
- `ReservationItemResponse {petId:integer, petName:string,
  serviceId:integer, serviceName:string}`
- `ReservationResponse {id:integer, startsAt:offset, endsAt:offset,
  status:ReservationStatus, note:string?, items:ReservationItemResponse[],
  createdAt:offset}`
- `CancelReservationRequest {reason:string?(max300)}`
- `CancellationResponse {id:integer, status:CANCELLED, startsAt:offset,
  cancelledAt:offset, cancelledBy:CLIENT|ADMIN, reason:string?,
  updatedAt:offset}`
- `RescheduleRequest {startsAt:offset}`
- `RescheduleResponse {id:integer, previousStartsAt:offset,
  startsAt:offset, endsAt:offset, previousStatus:ReservationStatus,
  status:ReservationStatus, updatedAt:offset}`
- `StatusChangeRequest {status:ReservationStatus,
  reason:string?(max300)}`
- `StatusChangeResponse {id:integer, previousStatus:ReservationStatus,
  status:ReservationStatus, reason:string?, updatedAt:offset}`
- `NotificationResponse {id:integer, type:NotificationType, title:string,
  body:string, reservationId:integer?, createdAt:instant, unread:boolean}`
- `ReadAllResponse {markedRead:integer(min0)}`

Administrator read and update models:

- `PageResponse<T> {content:T[], page:integer, size:integer,
  totalElements:integer, totalPages:integer}`
- `ReservationItemSummary {petId:integer, petName:string, species:string,
  breed:string?, serviceId:integer, serviceName:string}`
- `AdminReservationSummary {id:integer, clientId:integer,
  clientName:string, clientEmail:string, clientPhone:string,
  startsAt:offset, endsAt:offset, status:ReservationStatus,
  clientNote:string?, createdAt:offset, updatedAt:offset,
  items:ReservationItemSummary[]}`
- `ReservationEventSummary {id:integer, eventType:ReservationEventType,
  actor:CLIENT|ADMIN, previousStatus:ReservationStatus?,
  newStatus:ReservationStatus?, previousStartsAt:offset?,
  newStartsAt:offset?, reason:string?, createdAt:offset}`
- `AdminReservationDetail {id:integer, clientId:integer,
  clientName:string, clientEmail:string, clientPhone:string,
  startsAt:offset, endsAt:offset, status:ReservationStatus,
  clientNote:string?, createdAt:offset, updatedAt:offset,
  cancelledAt:offset?, cancelledBy:CLIENT|ADMIN|MIGRATION?,
  cancellationReason:string?, items:ReservationItemSummary[],
  events:ReservationEventSummary[]}`
- `AdminClientSummary {id:integer, name:string, email:string,
  phone:string, active:boolean, createdAt:offset, updatedAt:offset}`
- `ClientPetSummary {id:integer, name:string, species:string,
  breed:string?, birthdate:date?, active:boolean}`
- `ReservationCounts {total:integer, upcoming:integer, pending:integer,
  confirmed:integer, cancelled:integer, completed:integer,
  noShow:integer}`
- `AdminClientDetail {id:integer, name:string, email:string,
  phone:string, active:boolean, createdAt:offset, updatedAt:offset,
  pets:ClientPetSummary[], reservationCounts:ReservationCounts,
  upcomingReservations:AdminReservationSummary[]}`
- `AdminClientUpdateRequest {name:string(1..120), phone:string(1..30),
  active:boolean}`
- `AdminPetSummary {id:integer, clientId:integer, ownerName:string,
  name:string, species:string, breed:string?, birthdate:date?,
  active:boolean, createdAt:offset, updatedAt:offset}`
- `AdminPetDetail {id:integer, clientId:integer, ownerName:string,
  ownerEmail:string, name:string, species:string, breed:string?,
  birthdate:date?, active:boolean, createdAt:offset, updatedAt:offset,
  recentReservations:AdminReservationSummary[]}`
- `AdminPetUpdateRequest {name:string(1..80), species:string(1..40),
  breed:string?(max80), birthdate:date?(past-or-present)}`
- `AdminProfile {name:string, email:string, role:ADMIN}`
- `DashboardStats {todayAppointments:integer, clients:integer,
  pets:integer}`
- `ServiceUsage {name:string, count:integer, percentage:integer}`
- `ChartSeries {label:string, labels:string[], values:integer[]}`
- `DashboardChart {currentWeek:ChartSeries, previousWeek:ChartSeries,
  currentMonth:ChartSeries}`
- `AgendaItem {reservationId:integer, startsAt:offset, endsAt:offset,
  status:ReservationStatus, items:ReservationItemSummary[]}`
- `DashboardResponse {profile:AdminProfile, stats:DashboardStats,
  appointments:AdminReservationSummary[], services:ServiceUsage[],
  chart:DashboardChart, schedule:AgendaItem[]}`

## Session and CSRF workflow

A successful login creates the server-side session represented by the
`SESSION` cookie. Browser requests must use `credentials: "include"` so the
same cookie is sent on later calls.

Before any `POST`, `PUT`, `PATCH`, or `DELETE`, including login and logout:

1. call the CSRF endpoint with credentials included;
2. keep the `XSRF-TOKEN` cookie;
3. copy the returned `token` into the returned `headerName` header;
4. send the mutation with both the cookie and that header.

- `GET /api/v1/auth/csrf`

Response `200`:

```json
{
  "headerName": "X-XSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "opaque-csrf-token"
}
```

Security failures use the same JSON envelope as application failures.
Unauthenticated protected calls return `401 UNAUTHENTICATED`; a wrong role or
missing CSRF token returns `403 FORBIDDEN`. Security JSON is UTF-8.

## Authentication

- `POST /api/v1/auth/register`

Creates an unverified `CLIENT`. Body:

```json
{
  "email": "ana@example.cl",
  "password": "una-clave-segura-2026",
  "name": "Ana Pérez",
  "phone": "+56912345678"
}
```

Passwords contain 12–128 characters. Response `202` is deliberately
non-enumerating:

```json
{"message":"Revisa tu correo para verificar tu cuenta."}
```

The verification token expires after 24 hours and is single-use.

- `POST /api/v1/auth/verify-email`

Body:

```json
{"token":"opaque-email-token"}
```

Response: `204` with no body. An invalid, expired, or consumed token returns
`400 INVALID_VERIFICATION_TOKEN`.

- `POST /api/v1/auth/resend-verification`

Body:

```json
{"email":"ana@example.cl"}
```

Response `202` uses the same registration message whether the eligible account
exists or not.

- `POST /api/v1/auth/login`

This one endpoint is form encoded, not JSON:

```text
Content-Type: application/x-www-form-urlencoded
username=ana%40example.cl&password=una-clave-segura-2026
```

Response `200` for a client:

```json
{
  "userId": 41,
  "clientId": 12,
  "email": "ana@example.cl",
  "name": "Ana Pérez",
  "accountType": "CLIENT"
}
```

An administrator has `accountType: "ADMIN"` and `clientId: null`. Invalid
credentials, disabled user accounts, inactive client profiles, and unverified
accounts share the same `401 INVALID_CREDENTIALS` response. An inactive client
profile is never disclosed by the login response.

- `POST /api/v1/auth/logout`

Invalidates the session and removes the session/CSRF cookies. Response: `204`.

- `GET /api/v1/auth/me`

Returns the same authenticated account shape as login. Without a session it
returns `401 UNAUTHENTICATED`.

- `POST /api/v1/auth/forgot-password`

Body:

```json
{"email":"ana@example.cl"}
```

Response `202` is non-enumerating:

```json
{"message":"Si la cuenta existe, enviaremos instrucciones al correo."}
```

Reset tokens expire after one hour and are single-use.

- `POST /api/v1/auth/reset-password`

Body:

```json
{
  "token": "opaque-reset-token",
  "password": "otra-clave-segura-2026"
}
```

Response: `204`. Invalid, expired, or consumed tokens return
`400 INVALID_PASSWORD_RESET_TOKEN`.

## Contact

- `POST /api/v1/contact`

This endpoint is public and does not require an authenticated session. Because
it is a `POST`, the normal CSRF workflow still applies: browser clients must
send the `XSRF-TOKEN` cookie and returned CSRF header.

Body:

```json
{
  "name": "Ana Pérez",
  "email": "ana@example.cl",
  "message": "Quisiera consultar por una vacuna.",
  "website": ""
}
```

`name` is required and contains 2–120 characters, `email` is required and must
be a valid address of at most 254 characters, and `message` is required and
contains 10–2000 characters. `website` is the optional honeypot and is limited
to 200 characters. Values are normalized and cleaned as plain text, then
validated again.

A blank honeypot sends the clinic inquiry followed by the user
acknowledgement. A nonblank honeypot is silently accepted with the same `202`
response and sends no email:

```json
{"message":"Tu consulta fue enviada."}
```

The rate limit admits five valid submissions per servlet remote address in a
fixed one-hour window. A sixth returns `429 RATE_LIMITED`. Raw or cleaned field
violations return `400 VALIDATION_ERROR` with field messages in `errors`.

The `202` response is returned only after both email handoffs complete. A
provider failure returns the exact controlled `503` response:

```json
{
  "code": "EMAIL_DELIVERY_UNAVAILABLE",
  "message": "No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp.",
  "errors": {}
}
```

## Client profile and pets

All routes in this section require an enabled, verified `CLIENT` session.
Client ownership comes only from the session principal; the API does not
accept a client ID.

- `GET /api/v1/me/profile`

Response `200`:

```json
{
  "id": 12,
  "name": "Ana Pérez",
  "phone": "+56912345678",
  "email": "ana@example.cl"
}
```

- `PATCH /api/v1/me/profile`

Full profile body:

```json
{"name":"Ana Pérez","phone":"+56912345678"}
```

Returns the updated profile. Name is limited to 120 characters and phone to
30.

- `GET /api/v1/me/pets`

Lists only active pets owned by the current client:

```json
[
  {
    "id": 84,
    "name": "Milo",
    "species": "Gato",
    "breed": "Mestizo",
    "birthdate": "2022-05-10",
    "active": true
  }
]
```

- `POST /api/v1/me/pets`

Body:

```json
{
  "name": "Milo",
  "species": "Gato",
  "breed": "Mestizo",
  "birthdate": "2022-05-10"
}
```

`breed` and `birthdate` are optional; a birthdate cannot be in the future.
Response `201` includes `Location: /api/v1/me/pets/{id}` and the pet body.

- `PATCH /api/v1/me/pets/{id}`

Uses the same full-form body as creation and returns the updated pet. A foreign,
inactive, or missing pet is indistinguishable through the generic `404`
response.

- `DELETE /api/v1/me/pets/{id}`

Archives rather than hard-deletes the pet. Response: `204`. A future
`PENDING` or `CONFIRMED` reservation using that pet produces
`409 PET_HAS_FUTURE_RESERVATION`; resolve the reservation first.

## Services

- `GET /api/v1/services`

Public. Returns active services ordered for display:

```json
[
  {
    "id": 7,
    "code": "consulta-general",
    "name": "Consulta general",
    "description": "Evaluación general",
    "active": true,
    "displayOrder": 10
  }
]
```

Service codes are stable identifiers. Names may change, while reservation
items keep a historical `serviceName` snapshot.

## Availability

- `GET /api/v1/availability`

Public. Required query parameters are inclusive clinic-local dates:

```text
?from=2026-08-10&to=2026-08-12
```

The result is sorted and contains only 30-minute starts inside active weekly
hours, after minimum notice, within the booking horizon, and outside occupied
reservations or blackout blocks:

```json
[
  {
    "startsAt": "2026-08-10T10:00:00-04:00",
    "endsAt": "2026-08-10T10:30:00-04:00"
  }
]
```

Past portions are clipped to today and dates after the configured horizon are
clipped to the horizon. `from > to` returns `400 INVALID_DATE_RANGE`.
Nonexistent Chilean DST local times are skipped; the server uses the earlier
offset for an ambiguous overlap.

## Client reservations

All reservations last exactly 30 minutes in total, regardless of pet count.
Each selected pet appears once and has one active service. Pets must be active
and owned by the authenticated client.

- `POST /api/v1/me/reservations`

Body:

```json
{
  "startsAt": "2026-08-10T10:00:00-04:00",
  "items": [
    {"petId":84,"serviceId":7},
    {"petId":85,"serviceId":9}
  ],
  "note": "Control anual"
}
```

`startsAt` must exactly match a returned availability slot, including the real
Chilean offset. There must be 1–10 items; `note` is optional and limited to
500 characters. Response `201` includes
`Location: /api/v1/me/reservations/{id}`:

```json
{
  "id": 301,
  "startsAt": "2026-08-10T10:00:00-04:00",
  "endsAt": "2026-08-10T10:30:00-04:00",
  "status": "PENDING",
  "note": "Control anual",
  "items": [
    {
      "petId": 84,
      "petName": "Milo",
      "serviceId": 7,
      "serviceName": "Consulta general"
    },
    {
      "petId": 85,
      "petName": "Luna",
      "serviceId": 9,
      "serviceName": "Vacunación"
    }
  ],
  "createdAt": "2026-08-01T09:15:00-04:00"
}
```

With `BOOKING_AUTO_CONFIRM=false` (default), creation returns `PENDING`. With
the switch set to `true`, creation returns `CONFIRMED`. Both occupy the single
calendar. A database collision returns the exact `409` below. Refresh
availability and ask the client to select a new slot:

```json
{
  "code": "SLOT_ALREADY_BOOKED",
  "message": "Ese horario acaba de ser reservado. Elige otro bloque disponible.",
  "errors": {}
}
```

- `GET /api/v1/me/reservations`

Returns only the current client's reservations, newest scheduled start first,
using the same reservation response shape.

- `PATCH /api/v1/me/reservations/{id}/cancel`

Body:

```json
{"reason":"Cambio de planes"}
```

`reason` is optional, trimmed, and limited to 300 meaningful characters. Only
a future `PENDING` or `CONFIRMED` reservation can be cancelled. Response:

```json
{
  "id": 301,
  "status": "CANCELLED",
  "startsAt": "2026-08-10T10:00:00-04:00",
  "cancelledAt": "2026-08-02T11:00:00-04:00",
  "cancelledBy": "CLIENT",
  "reason": "Cambio de planes",
  "updatedAt": "2026-08-02T11:00:00-04:00"
}
```

Cancellation preserves the reservation, items, and event history and releases
the slot.

- `PATCH /api/v1/me/reservations/{id}/reschedule`

Body:

```json
{"startsAt":"2026-08-11T11:30:00-04:00"}
```

The new start is validated like creation. A client reschedule reapplies
`BOOKING_AUTO_CONFIRM`: it becomes `PENDING` by default or `CONFIRMED` when
auto-confirm is enabled.

```json
{
  "id": 301,
  "previousStartsAt": "2026-08-10T10:00:00-04:00",
  "startsAt": "2026-08-11T11:30:00-04:00",
  "endsAt": "2026-08-11T12:00:00-04:00",
  "previousStatus": "CONFIRMED",
  "status": "PENDING",
  "updatedAt": "2026-08-02T11:10:00-04:00"
}
```

## Client notifications

Lists are capped at 100 rows and ordered by `createdAt` descending, then ID.
Clients can only read their own notifications. Every operation resolves the
current active client from the database, so administrator deactivation also
blocks a previously authenticated session without exposing notification
existence. Reactivation restores the same session's recipient-scoped access.

- `GET /api/v1/me/notifications`

Response:

```json
[
  {
    "id": 901,
    "type": "RESERVATION_CONFIRMED",
    "title": "Reserva confirmada",
    "body": "Tu reserva fue confirmada.",
    "reservationId": 301,
    "createdAt": "2026-08-02T15:10:00Z",
    "unread": true
  }
]
```

- `PATCH /api/v1/me/notifications/{id}/read`

Marks an owned notification read and returns it with `unread: false`.
Repeated calls preserve the first read timestamp internally.

- `POST /api/v1/me/notifications/read-all`

Response:

```json
{"markedRead":3}
```

Repeating the call when nothing is unread returns `{"markedRead":0}`.

## Administrator dashboard and reservations

All `/api/v1/admin/**` routes require the single enabled `ADMIN` account.

- `GET /api/v1/admin/dashboard`

Returns:

```json
{
  "profile": {
    "name": "Administradora AmiDog",
    "email": "admin@example.cl",
    "role": "ADMIN"
  },
  "stats": {
    "todayAppointments": 2,
    "clients": 18,
    "pets": 27
  },
  "appointments": [],
  "services": [],
  "chart": {
    "currentWeek": {
      "label": "Esta semana",
      "labels": ["Lun","Mar","Mié","Jue","Vie","Sáb","Dom"],
      "values": [0,0,0,0,0,0,0]
    },
    "previousWeek": {
      "label": "Semana pasada",
      "labels": ["Lun","Mar","Mié","Jue","Vie","Sáb","Dom"],
      "values": [0,0,0,0,0,0,0]
    },
    "currentMonth": {
      "label": "Este mes",
      "labels": ["Sem 1","Sem 2","Sem 3","Sem 4","Sem 5"],
      "values": [0,0,0,0,0]
    }
  },
  "schedule": []
}
```

Appointments are bounded to 10 upcoming rows, today's agenda to 100, and
service usage to 10 groups. Counts and date buckets use clinic-local
boundaries.

- `GET /api/v1/admin/reservations`

Optional filters:

```text
?from=2026-08-01&to=2026-08-31&status=CONFIRMED&q=milo&page=0&size=25
```

`page` is zero-based; `size` defaults to 25 and must be 1–100. Search is
trimmed and limited to 120 characters. Dates are inclusive and must be within
`1900-01-01` through `2100-12-31`.

```json
{
  "content": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0
}
```

Reservation summaries contain client identity/contact fields, Chilean start
and end, status, note, timestamps, and ordered multi-pet items.

- `GET /api/v1/admin/reservations/{id}`

Returns the summary fields plus cancellation data and ordered events. Historical
`cancelledBy` values are `CLIENT`, `ADMIN`, or the migration-only read value
`MIGRATION`.

- `PATCH /api/v1/admin/reservations/{id}/status`

Body:

```json
{"status":"CONFIRMED","reason":"Agenda revisada"}
```

Allowed transitions are:

- `PENDING -> CONFIRMED`
- `PENDING -> CANCELLED`
- `CONFIRMED -> CANCELLED`
- `CONFIRMED -> COMPLETED`
- `CONFIRMED -> NO_SHOW`

Other transitions return
`409 INVALID_RESERVATION_STATUS_TRANSITION`. The response contains `id`,
`previousStatus`, `status`, normalized `reason`, and Chilean `updatedAt`.

- `PATCH /api/v1/admin/reservations/{id}/reschedule`

Uses `{"startsAt":"..."}`. It accepts only `PENDING` or `CONFIRMED` and
preserves that current status. The response shape matches client rescheduling.

## Administrator clients and pets

- `GET /api/v1/admin/clients`

Optional `q`, `active`, `page`, and `size` filters. Returns `PageResponse`
with safe profile fields only.

- `GET /api/v1/admin/clients/{id}`

Returns the profile plus at most 100 pets, status counts, and at most 10
upcoming reservations.

- `PATCH /api/v1/admin/clients/{id}`

Body:

```json
{"name":"Ana Pérez","phone":"+56912345678","active":true}
```

Deactivation is reversible and preserves pets, reservations, events, and
notifications. It blocks fresh client login and all client-owned API operations,
including notification list/read/read-all calls made through an existing
session. Reactivation restores access; foreign notification identifiers remain
indistinguishable from missing identifiers.

- `GET /api/v1/admin/pets`

Optional `q`, `active`, `clientId`, `page`, and `size` filters. `clientId`, when
provided, must be positive.

- `GET /api/v1/admin/pets/{id}`

Returns owner identity, non-medical pet demographics, and at most 10 recent
reservations.

- `PATCH /api/v1/admin/pets/{id}`

Uses the same full-form pet body as the client update. It cannot change the
owner or archive state. Null/blank optional breed and null birthdate clear
those fields.

## Administrator services

- `GET /api/v1/admin/services`

Lists active and archived services.

- `POST /api/v1/admin/services`

Body:

```json
{
  "code": "consulta-general",
  "name": "Consulta general",
  "description": "Evaluación general",
  "displayOrder": 10
}
```

Response `201` includes `Location: /api/v1/admin/services/{id}`. Codes are
normalized to lowercase kebab form and cannot later be changed.

- `PATCH /api/v1/admin/services/{id}`

Body:

```json
{
  "name": "Consulta general",
  "description": "Evaluación general y preventiva",
  "displayOrder": 10,
  "active": true
}
```

The `active` field can explicitly reactivate an archived service.

- `DELETE /api/v1/admin/services/{id}`

Archives the service. Response: `204`. Historical reservation snapshots remain.

## Administrator weekly availability and blocks

ISO day values are `1` Monday through `7` Sunday. Replacing weekly hours does
not cancel or move existing reservations.

- `GET /api/v1/admin/availability/weekly`

Returns all active and inactive intervals ordered by day/start/end.

- `PUT /api/v1/admin/availability/weekly`

Replaces the entire schedule atomically:

```json
[
  {"dayOfWeek":1,"start":"09:00:00","end":"13:00:00","active":true},
  {"dayOfWeek":1,"start":"15:00:00","end":"18:00:00","active":true}
]
```

An empty list closes the recurring schedule. Active same-day intervals may be
adjacent but cannot overlap.

- `GET /api/v1/admin/availability/blocks`

Lists blocks in chronological order with `id`, Chilean `startsAt`/`endsAt`,
optional `reason`, and `createdAt`.

- `POST /api/v1/admin/availability/blocks`

Body:

```json
{
  "startsAt": "2026-08-15T00:00:00-04:00",
  "endsAt": "2026-08-16T00:00:00-04:00",
  "reason": "Cirugía externa"
}
```

Response `201` includes `Location` for the block. A block cannot displace an
occupied reservation. The typed conflict includes only reviewed reservation
IDs:

```json
{
  "code": "BLOCK_OVERLAPS_RESERVATIONS",
  "message": "El bloqueo se superpone con reservas activas.",
  "errors": {},
  "details": {"reservationIds":[301,302]}
}
```

- `DELETE /api/v1/admin/availability/blocks/{id}`

Deletes the schedule block, not a reservation. Response: `204`.

## Administrator notifications

The shape, 100-row cap, ordering, and first-read behavior match client
notifications, scoped to the authenticated administrator.

- `GET /api/v1/admin/notifications`

Lists administrator operational notifications.

- `PATCH /api/v1/admin/notifications/{id}/read`

Marks one owned notification read and returns it.

- `POST /api/v1/admin/notifications/read-all`

Returns `{"markedRead":N}`.

## Stable values

Reservation statuses:

```text
PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
```

Notification types:

```text
NEW_RESERVATION
CLIENT_CANCELLED
CLIENT_RESCHEDULED
RESERVATION_CONFIRMED
ADMIN_CANCELLED
ADMIN_RESCHEDULED
APPOINTMENT_REMINDER
```

Reservation event types:

```text
CREATED, STATUS_CHANGED, CANCELLED, RESCHEDULED
```

Operational event actors:

```text
CLIENT, ADMIN
```

`MIGRATION` is only a historical administrator read value for a preserved
cancellation; it is not an operational actor accepted by any mutation.

## Error envelope

Every API error has:

```json
{
  "code": "SOME_CODE",
  "message": "Mensaje seguro.",
  "errors": {}
}
```

Bean-validation failures use `VALIDATION_ERROR` and put field messages in
`errors`. Malformed JSON, missing/type-invalid request values, and unrecognized
enum/date values use `INVALID_REQUEST`. Missing domain rows and every unknown
`/api/v1/**` path use `NOT_FOUND`, for anonymous and authenticated callers,
including paths that merely look like protected `/me/**` or `/admin/**`
routes. Real mapped routes still enforce authentication, roles, and CSRF before
their controller executes. Rate limits use `RATE_LIMITED`.
Unexpected failures use `INTERNAL_ERROR`, an empty `errors` object, and an
`X-Correlation-ID` response header. No error response exposes exception text,
stack traces, SQL, constraint names, paths, credentials, or tokens.

Reviewed conflict codes include:

```text
SERVICE_CODE_EXISTS
SERVICE_CONCURRENT_UPDATE
CLIENT_CONCURRENT_UPDATE
PET_CONCURRENT_UPDATE
PET_HAS_FUTURE_RESERVATION
NONEXISTENT_LOCAL_TIME
WEEKLY_INTERVAL_OVERLAP
BLOCK_OVERLAPS_RESERVATIONS
SLOT_UNAVAILABLE
SLOT_ALREADY_BOOKED
INVALID_RESERVATION_STATUS_TRANSITION
RESERVATION_NOT_IN_FUTURE
RESERVATION_START_UNCHANGED
```

Scheduling/admin 400 codes include:

```text
INVALID_DATE_RANGE
INVALID_WEEKLY_INTERVAL
DUPLICATE_WEEKLY_INTERVAL
INVALID_BLOCK_RANGE
INVALID_BLOCK_REASON
INVALID_RESERVATION_ITEMS
DUPLICATE_RESERVATION_PET
INVALID_RESERVATION_START
INVALID_RESERVATION_STATUS_CHANGE
INVALID_ADMIN_PAGINATION
INVALID_ADMIN_SEARCH
INVALID_ADMIN_DATE_RANGE
INVALID_ADMIN_FILTER
SERVICE_CODE_INVALID
```

## Implemented rate limits

The current limiter is per running application process and uses fixed windows:

| Operation | Key | Limit |
|---|---|---:|
| Registration | servlet remote address | 5 per hour |
| Login | servlet remote address | 10 per 15 minutes |
| Verification resend | normalized email | 3 per hour |
| Forgot password | normalized email | 3 per hour |
| Website contact | servlet remote address | 5 per hour |
| Reservation creation | authenticated user ID | 10 per hour |

Reverse proxies must replace untrusted forwarding headers and preserve a
verified client address. Limits are not currently shared between multiple
application instances.
