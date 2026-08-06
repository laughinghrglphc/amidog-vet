# AmiDog Frontend Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the public React site, client panel, and attached administrator dashboard into one tested frontend that uses the real `/api/v1` backend.

**Architecture:** The original Vite application remains the shell. A shared credentialed fetch client handles CSRF and normalized API failures, an authentication provider owns session state, and role guards protect client/admin routes. The attached administrator UI is copied into an `admin` feature folder, stripped of messages/mock persistence, and adapted to backend DTOs.

**Tech Stack:** React 19.2.8, React Router 7.18.1, Vite 8.1.5, Sass 1.101.3, Vitest 4.1.x, Testing Library, jsdom, ESLint 10.

## Global Constraints

- The application is one React build with public, client, and administrator routes.
- Browser authentication uses server sessions with `credentials: 'include'`; no JWT or password is stored.
- Mutating requests send Spring's `X-XSRF-TOKEN` value.
- `sessionStorage` may retain only an unfinished booking draft.
- Client, pet, service, reservation, notification, and admin data never use `localStorage`.
- Reservations select one or more registered pets and exactly one service per selected pet.
- Dates/times display in `America/Santiago` using the API's offset timestamps.
- A `409 SLOT_ALREADY_BOOKED` response refreshes availability and keeps the user's pet/service selection.
- Spanish labels map backend status codes without changing the codes.
- Messages/replies are removed; notifications remain.
- Reservation cancellation wording never implies deletion.
- The uploaded project has no `.git`; never initialize Git implicitly.

---

## File Structure

### Shared infrastructure

- Create `src/api/http.js`.
- Create `src/api/authApi.js`.
- Create `src/api/clientApi.js`.
- Create `src/api/bookingApi.js`.
- Create `src/api/adminApi.js`.
- Create `src/auth/AuthProvider.jsx`.
- Create `src/auth/RequireRole.jsx`.
- Create `src/utils/clinicTime.js`.
- Create `src/utils/statusLabels.js`.
- Create `src/test/renderApp.jsx`.
- Create `src/test/setup.js`.
- Create `src/test/fakes.js`.
- Create `eslint.config.js`.

### Authentication pages

- Modify `src/pages/Login.jsx`.
- Create `src/pages/Register.jsx`.
- Create `src/pages/VerifyEmail.jsx`.
- Create `src/pages/ForgotPassword.jsx`.
- Create `src/pages/ResetPassword.jsx`.

### Client panel and booking

- Modify `src/pages/Panel.jsx`.
- Modify `src/pages/Reservation.jsx`.
- Modify `src/pages/Calendario.jsx`.
- Modify `src/pages/Confirmacion.jsx`.
- Create `src/hooks/useClientPanel.js`.
- Create `src/hooks/useBookingDraft.js`.
- Modify `src/components/panel/AppointmentForm.jsx`.
- Modify `src/components/panel/AppointmentCard.jsx`.
- Modify `src/components/panel/PetForm.jsx`.
- Modify `src/components/panel/PetCard.jsx`.

### Administrator merge

- Create `src/admin/AdminDashboardPage.jsx`.
- Create `src/admin/PanelAdministrador.jsx`.
- Create `src/admin/hooks/useAdminDashboard.js`.
- Create `src/admin/services/adminDashboardApi.js`.
- Create `src/admin/components/dashboard/*` from the attached dashboard.
- Create `src/admin/components/layout/*` from the attached dashboard.
- Create `src/admin/components/ui/*` from the attached dashboard.
- Create `src/admin/components/services/ServiceManager.jsx`.
- Create `src/admin/components/availability/AvailabilityManager.jsx`.
- Create `src/admin/components/reservations/ReservationActions.jsx`.
- Create `src/admin/data/imageRegistry.js`.
- Create `src/admin/utils/date.js`.
- Create `src/admin/utils/search.js`.
- Copy attached images to `src/assets/admin/`.
- Merge attached `_panel-administrador.scss` into `src/styles/admin/_panel-administrador.scss`.

### Application shell

- Modify `src/App.jsx`.
- Modify `src/main.jsx`.
- Modify `src/styles/main.scss`.
- Modify `vite.config.js`.
- Modify `package.json`.
- Modify `package-lock.json` through `npm install`.

## Task 1: Frontend Test, Lint, API, and Time Foundations

**Files:**

- Modify: `package.json`
- Modify: `package-lock.json`
- Create: `eslint.config.js`
- Create: `src/test/setup.js`
- Create: `src/test/renderApp.jsx`
- Create: `src/api/http.js`
- Create: `src/utils/clinicTime.js`
- Create: `src/utils/statusLabels.js`
- Create: `src/api/http.test.js`
- Create: `src/utils/clinicTime.test.js`
- Modify: `vite.config.js`

**Interfaces:**

- Produces: `apiRequest(path, options)`, `resetCsrf()`, `ApiError`, clinic formatting utilities, and the frontend test harness.

- [ ] **Step 1: Install the attached dashboard's tested tooling**

Merge these scripts and dev dependencies into the main package:

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "lint": "eslint ."
  },
  "devDependencies": {
    "@eslint/js": "^10.0.1",
    "@testing-library/jest-dom": "^7.0.0",
    "@testing-library/react": "^16.3.2",
    "@testing-library/user-event": "^14.6.1",
    "eslint": "^10.8.0",
    "eslint-plugin-react-hooks": "^7.1.1",
    "eslint-plugin-react-refresh": "^0.5.3",
    "globals": "^17.8.0",
    "jsdom": "^30.0.0",
    "vitest": "^4.1.10"
  }
}
```

Run `npm install` to update the lockfile. Configure Vitest with `environment: 'jsdom'` and `setupFiles: './src/test/setup.js'`.

- [ ] **Step 2: Write failing CSRF and timezone tests**

```js
it('fetches CSRF once and sends it on mutations', async () => {
  fetch
    .mockResolvedValueOnce(jsonResponse({
      headerName: 'X-XSRF-TOKEN',
      token: 'csrf-123',
    }))
    .mockResolvedValueOnce(jsonResponse({ id: 7 }, 201))

  await apiRequest('/api/v1/me/pets', {
    method: 'POST',
    body: { name: 'Milo', species: 'Gato' },
  })

  expect(fetch).toHaveBeenNthCalledWith(
    2,
    '/api/v1/me/pets',
    expect.objectContaining({
      credentials: 'include',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-123' }),
    }),
  )
})
```

```js
it('formats API timestamps in the clinic timezone', () => {
  expect(formatClinicDateTime('2026-07-13T13:00:00Z'))
    .toMatchObject({ date: '13-07-2026', time: '09:00' })
})
```

- [ ] **Step 3: Run and verify missing modules**

Run:

```powershell
npm test -- src/api/http.test.js src/utils/clinicTime.test.js
```

Expected: FAIL because the shared utilities do not exist.

- [ ] **Step 4: Implement the shared HTTP contract**

```js
export class ApiError extends Error {
  constructor(message, { code = 'HTTP_ERROR', errors = {}, status = 0 } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.errors = errors
    this.status = status
  }
}

let csrf = null

export function resetCsrf() {
  csrf = null
}

async function csrfHeader() {
  if (csrf) return csrf
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  if (!response.ok) throw new ApiError('No pudimos iniciar una solicitud segura.', {
    status: response.status,
  })
  const payload = await response.json()
  csrf = [payload.headerName, payload.token]
  return csrf
}

export async function apiRequest(path, { body, headers = {}, method = 'GET' } = {}) {
  const options = { credentials: 'include', headers: { Accept: 'application/json', ...headers }, method }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const [name, value] = await csrfHeader()
    options.headers[name] = value
  }
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }
  const response = await fetch(path, options)
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('amidog:unauthorized'))
    }
    throw new ApiError(payload.message || 'No se pudo completar la solicitud.', {
      code: payload.code,
      errors: payload.errors,
      status: response.status,
    })
  }
  if (response.status === 204) return undefined
  return response.json()
}
```

Login uses a dedicated form-encoded helper but still calls `csrfHeader()`:

```js
export async function loginFormEncoded(email, password) {
  const [headerName, token] = await csrfHeader()
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded',
      [headerName]: token,
    },
    body: new URLSearchParams({ username: email.trim(), password }),
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new ApiError(payload.message || 'No pudimos iniciar sesión.', {
      status: response.status,
      code: payload.code,
    })
  }
  resetCsrf()
  return payload
}
```

After logout, call `resetCsrf()` because Spring rotates the token with authentication state.

`src/test/setup.js` imports `@testing-library/jest-dom/vitest`, restores mocks after each test, and initializes `globalThis.fetch = vi.fn()`. `src/test/fakes.js` exports `fakeAuth`, `fakeClientApi`, `fakeBookingApi`, `fakeAdminApi`, `fakeContactApi`, and response builders with every method named in the API modules. `renderApp` uses `createMemoryRouter`, wraps `AuthProvider`, and returns the router so route assertions read `router.state.location.pathname` rather than `window.location`.

- [ ] **Step 5: Implement timezone/status utilities and pass checks**

```js
const CLINIC_TIME_ZONE = 'America/Santiago'

export function formatClinicDateTime(value) {
  const date = new Date(value)
  const parts = new Intl.DateTimeFormat('es-CL', {
    timeZone: CLINIC_TIME_ZONE,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type) => parts.find((part) => part.type === type)?.value
  return {
    date: `${get('day')}-${get('month')}-${get('year')}`,
    time: `${get('hour')}:${get('minute')}`,
  }
}
```

Status labels:

```js
export const STATUS_LABELS = Object.freeze({
  PENDING: 'Pendiente',
  CONFIRMED: 'Confirmada',
  CANCELLED: 'Cancelada',
  COMPLETED: 'Completada',
  NO_SHOW: 'No asistió',
})
```

Run:

```powershell
npm test -- src/api/http.test.js src/utils/clinicTime.test.js
npm run lint
if (Test-Path .git) {
  git add package.json package-lock.json vite.config.js eslint.config.js src/api src/utils src/test
  git commit -m "build: add frontend API and test foundation"
}
```

Expected: tests and lint PASS.

## Task 2: Authentication State, Route Guards, and Account Pages

**Files:**

- Create: `src/api/authApi.js`
- Create: `src/auth/AuthProvider.jsx`
- Create: `src/auth/RequireRole.jsx`
- Modify: `src/main.jsx`
- Modify: `src/App.jsx`
- Modify: `src/pages/Login.jsx`
- Create: account pages listed above
- Create: `src/auth/AuthProvider.test.jsx`
- Create: `src/pages/Login.test.jsx`
- Create: `src/pages/Register.test.jsx`

**Interfaces:**

- Produces: `useAuth()`, `RequireRole`, real account routes, and login role redirection.

- [ ] **Step 1: Write failing route and login tests**

```jsx
it('redirects an anonymous client route to login with a return path', async () => {
  const { router } = renderApp(<RequireRole role="CLIENT"><h1>Panel privado</h1></RequireRole>, {
    route: '/panel',
    authApi: fakeAuth({ me: null }),
  })
  expect(await screen.findByRole('heading', { name: /iniciar sesión/i })).toBeVisible()
  expect(router.state.location.pathname).toBe('/login')
})

it('submits credentials and sends an admin to the admin dashboard', async () => {
  const api = fakeAuth({ loginResult: { accountType: 'ADMIN' } })
  const { router } = renderApp(<Login />, { authApi: api, route: '/login?next=/admin' })
  await user.type(screen.getByLabelText(/correo/i), 'admin@amidog.cl')
  await user.type(screen.getByLabelText(/contraseña/i), 'Secret-Password-9!')
  await user.click(screen.getByRole('button', { name: /ingresar/i }))
  expect(api.login).toHaveBeenCalled()
  expect(router.state.location.pathname).toBe('/admin')
})
```

- [ ] **Step 2: Run and verify failure**

Run:

```powershell
npm test -- src/auth/AuthProvider.test.jsx src/pages/Login.test.jsx src/pages/Register.test.jsx
```

Expected: FAIL because authentication context and account pages are absent.

- [ ] **Step 3: Implement API and provider**

```js
export const authApi = {
  me: () => apiRequest('/api/v1/auth/me'),
  register: (body) => apiRequest('/api/v1/auth/register', { body, method: 'POST' }),
  verifyEmail: (token) => apiRequest('/api/v1/auth/verify-email', {
    body: { token }, method: 'POST',
  }),
  resendVerification: (email) => apiRequest('/api/v1/auth/resend-verification', {
    body: { email }, method: 'POST',
  }),
  forgotPassword: (email) => apiRequest('/api/v1/auth/forgot-password', {
    body: { email }, method: 'POST',
  }),
  resetPassword: (token, password) => apiRequest('/api/v1/auth/reset-password', {
    body: { token, password }, method: 'POST',
  }),
  login: loginFormEncoded,
  logout: async () => {
    const result = await apiRequest('/api/v1/auth/logout', { method: 'POST' })
    resetCsrf()
    return result
  },
}
```

`AuthProvider` loads `/auth/me` once, treats `401` as anonymous, exposes `{user, loading, login, logout, refresh}`, and clears state on the global `amidog:unauthorized` event emitted by `http.js`.

- [ ] **Step 4: Implement route guards and account flows**

Routes:

```jsx
<Route path="/login" element={<Login />} />
<Route path="/crear-cuenta" element={<Register />} />
<Route path="/verificar-correo" element={<VerifyEmail />} />
<Route path="/olvide-contrasena" element={<ForgotPassword />} />
<Route path="/restablecer-contrasena" element={<ResetPassword />} />
<Route path="/panel" element={<RequireRole role="CLIENT"><Panel /></RequireRole>} />
<Route path="/admin/*" element={<RequireRole role="ADMIN"><AdminDashboardPage /></RequireRole>} />
```

Registration collects exactly email, password, confirmation, name, and phone. It validates 12–128 password characters and matching confirmation. Verification and reset pages read `token` from `useSearchParams`; they never persist it.

- [ ] **Step 5: Pass authentication tests and checkpoint**

Run:

```powershell
npm test -- src/auth src/pages/Login.test.jsx src/pages/Register.test.jsx
npm run lint
if (Test-Path .git) {
  git add src/api/authApi.js src/auth src/pages src/App.jsx src/main.jsx
  git commit -m "feat: add account and protected route flows"
}
```

Expected: PASS for loading, anonymous, verified client, admin, failed login, verification, recovery, and logout states.

## Task 3: Real Client Profile, Pet, Reservation, and Notification Panel

**Files:**

- Create: `src/api/clientApi.js`
- Create: `src/hooks/useClientPanel.js`
- Modify: `src/pages/Panel.jsx`
- Modify: client panel components listed above
- Create: `src/pages/Panel.test.jsx`
- Modify: `src/styles/_panel.scss`

**Interfaces:**

- Produces: client dashboard data/actions backed exclusively by `/api/v1/me/**`.

- [ ] **Step 1: Write failing panel integration tests**

```jsx
it('loads pets and reservations without localStorage', async () => {
  const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
  const api = fakeClientApi({
    pets: [{ id: 1, name: 'Milo', species: 'Gato', active: true }],
    reservations: [{ id: 9, status: 'PENDING', items: [
      { petId: 1, petName: 'Milo', serviceName: 'Consulta general' },
    ] }],
    notifications: [],
  })
  renderApp(<Panel api={api} />)
  expect(await screen.findByText('Milo')).toBeVisible()
  expect(screen.getByText('Pendiente')).toBeVisible()
  expect(storageWrite).not.toHaveBeenCalled()
})

it('shows the future-reservation conflict when pet archive is rejected', async () => {
  const api = fakeClientApi()
  api.archivePet.mockRejectedValue(new ApiError(
    'Cancela o resuelve primero las reservas futuras de esta mascota.',
    { code: 'PET_HAS_FUTURE_RESERVATION', status: 409 },
  ))
  renderApp(<Panel api={api} />)
  await archiveFirstPet()
  expect(await screen.findByRole('alert')).toHaveTextContent(/reservas futuras/i)
})
```

- [ ] **Step 2: Run and verify mock/localStorage behavior fails**

Run:

```powershell
npm test -- src/pages/Panel.test.jsx
```

Expected: FAIL because the panel uses seeded arrays and `localStorage`.

- [ ] **Step 3: Implement client API and data hook**

```js
export const clientApi = {
  profile: () => apiRequest('/api/v1/me/profile'),
  updateProfile: (body) => apiRequest('/api/v1/me/profile', { body, method: 'PATCH' }),
  pets: () => apiRequest('/api/v1/me/pets'),
  createPet: (body) => apiRequest('/api/v1/me/pets', { body, method: 'POST' }),
  updatePet: (id, body) => apiRequest(`/api/v1/me/pets/${id}`, { body, method: 'PATCH' }),
  archivePet: (id) => apiRequest(`/api/v1/me/pets/${id}`, { method: 'DELETE' }),
  reservations: () => apiRequest('/api/v1/me/reservations'),
  cancelReservation: (id, reason) => apiRequest(`/api/v1/me/reservations/${id}/cancel`, {
    body: { reason }, method: 'PATCH',
  }),
  rescheduleReservation: (id, startsAt) =>
    apiRequest(`/api/v1/me/reservations/${id}/reschedule`, {
      body: { startsAt }, method: 'PATCH',
    }),
  notifications: () => apiRequest('/api/v1/me/notifications'),
  readNotification: (id) =>
    apiRequest(`/api/v1/me/notifications/${id}/read`, { method: 'PATCH' }),
  readAllNotifications: () =>
    apiRequest('/api/v1/me/notifications/read-all', { method: 'POST' }),
}
```

`useClientPanel` loads profile, pets, reservations, and notifications with `Promise.all`, then reloads only the affected collection after a mutation.

- [ ] **Step 4: Adapt panel behavior and copy**

- remove `initialPets`, `initialAppointments`, `readStorage`, and both storage effects;
- remove pet health status;
- display all pets/items in a multi-pet reservation card;
- map status via `STATUS_LABELS`;
- change “Eliminar mascota” to “Archivar mascota”;
- change “Anular hora” to “Cancelar reserva”;
- navigate “Agregar hora” to `/reservar`;
- expose loading, empty, validation, `409`, and retry states.

- [ ] **Step 5: Pass panel tests and checkpoint**

Run:

```powershell
npm test -- src/pages/Panel.test.jsx
npm run lint
if (Test-Path .git) {
  git add src/api/clientApi.js src/hooks/useClientPanel.js src/pages/Panel.jsx src/components/panel src/styles/_panel.scss
  git commit -m "feat: connect client panel to backend"
}
```

Expected: PASS for load, pet create/edit/archive, cancellation, notifications, conflict, and no local persistence.

## Task 4: Registered Multi-Pet Booking and Real Availability

**Files:**

- Create: `src/api/bookingApi.js`
- Create: `src/hooks/useBookingDraft.js`
- Modify: `src/pages/Reservation.jsx`
- Modify: `src/pages/Calendario.jsx`
- Modify: `src/pages/Confirmacion.jsx`
- Modify: `src/utils/reservation.js`
- Modify: reservation/calendar/confirmation styles
- Create: `src/pages/Reservation.test.jsx`
- Create: `src/pages/Calendario.test.jsx`
- Create: `src/pages/Confirmacion.test.jsx`

**Interfaces:**

- Produces: authenticated draft → real availability → reservation creation flow.

- [ ] **Step 1: Write failing multi-pet and collision tests**

```jsx
it('requires one service for each selected pet', async () => {
  renderApp(<Reservation api={fakeBookingApi({
    pets: [milo, luna], services: [consultation, vaccine],
  })} />)
  await user.click(screen.getByLabelText('Milo'))
  await user.click(screen.getByLabelText('Luna'))
  await user.selectOptions(screen.getByLabelText(/servicio para Milo/i), consultation.id)
  await user.click(screen.getByRole('button', { name: /continuar/i }))
  expect(screen.getByRole('alert')).toHaveTextContent(/servicio para Luna/i)
})

it('keeps selections and refreshes slots after a booking collision', async () => {
  const api = fakeBookingApi()
  api.createReservation.mockRejectedValueOnce(new ApiError(
    'Ese horario acaba de ser reservado. Elige otro bloque disponible.',
    { code: 'SLOT_ALREADY_BOOKED', status: 409 },
  ))
  renderApp(<Confirmacion api={api} />)
  await user.click(screen.getByRole('button', { name: /confirmar reserva/i }))
  expect(await screen.findByRole('alert')).toHaveTextContent(/otro bloque/i)
  expect(sessionStorage.getItem('amidogBookingDraft')).toContain('petId')
})
```

- [ ] **Step 2: Run and verify old anonymous single-pet flow fails**

Run:

```powershell
npm test -- src/pages/Reservation.test.jsx src/pages/Calendario.test.jsx src/pages/Confirmacion.test.jsx
```

Expected: FAIL because the current pages collect typed client/pet data and use hard-coded slots.

- [ ] **Step 3: Implement API and booking draft**

```js
export const bookingApi = {
  pets: () => apiRequest('/api/v1/me/pets'),
  services: () => apiRequest('/api/v1/services'),
  availability: (from, to) => apiRequest(
    `/api/v1/availability?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  ),
  createReservation: (draft) => apiRequest('/api/v1/me/reservations', {
    method: 'POST',
    body: {
      startsAt: draft.startsAt,
      items: draft.items.map(({ petId, serviceId }) => ({ petId, serviceId })),
      note: draft.note || null,
    },
  }),
}
```

`useBookingDraft` stores only:

```js
{
  items: [{ petId: 1, serviceId: 4 }],
  startsAt: '2026-08-10T10:00:00-04:00',
  note: ''
}
```

It clears the draft after a successful reservation.

- [ ] **Step 4: Replace all hard-coded booking inputs**

`Reservation` loads active registered pets and services, supports one-to-ten selected pets, and renders a service `<select>` next to each selected pet. It no longer asks for client name/email/phone, veterinarian, typed pet name, preferred free-text date, or time-of-day preference.

`Calendario` groups returned slots by their Chilean local date and displays only server-provided starts. `Confirmacion` resolves pet/service names from loaded data, posts once, disables double submission, and uses the returned status:

```jsx
<p role="status">
  {reservation.status === 'CONFIRMED'
    ? 'Tu reserva quedó confirmada.'
    : 'Recibimos tu solicitud y está pendiente de confirmación.'}
</p>
```

- [ ] **Step 5: Pass flow tests and checkpoint**

Run:

```powershell
npm test -- src/pages/Reservation.test.jsx src/pages/Calendario.test.jsx src/pages/Confirmacion.test.jsx
npm run lint
if (Test-Path .git) {
  git add src/api/bookingApi.js src/hooks/useBookingDraft.js src/pages src/utils/reservation.js src/styles
  git commit -m "feat: connect multi-pet booking to live availability"
}
```

Expected: PASS for empty data, one/multiple pets, per-pet service validation, server slots, `PENDING`, `CONFIRMED`, and `409`.

## Task 5: Merge the Attached Administrator Dashboard and Remove Messages

**Files:**

- Create: administrator files/assets/styles listed above
- Modify: `src/App.jsx`
- Modify: `src/styles/main.scss`
- Create: `src/admin/AdminDashboardPage.test.jsx`
- Create: `src/admin/components/layout/Sidebar.test.jsx`

**Interfaces:**

- Consumes: authenticated admin route and `/api/v1/admin/dashboard`.
- Produces: the attached responsive dashboard inside the main application, with no mock/message feature.

- [ ] **Step 1: Copy the reusable administrator UI into feature-scoped paths**

Copy source from:

```text
work/admin-frontend-analysis-20260728/amidog-admin-frontend/src
```

to `src/admin`, updating relative imports and moving assets to `src/assets/admin`. Preserve the tested modal/sidebar/search/date components; do not copy the attached app entrypoint, `mockDashboard.js`, or mock API implementation.

- [ ] **Step 2: Write failing no-message and live-load tests**

```jsx
it('loads the dashboard API and exposes no messages navigation', async () => {
  const api = fakeAdminApi({ dashboard })
  renderApp(<AdminDashboardPage api={api} />)
  expect(await screen.findByText(/bienvenida/i)).toBeVisible()
  expect(api.getDashboard).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('button', { name: /mensajes/i })).not.toBeInTheDocument()
})

it('calls logout and returns to login', async () => {
  const { router } = renderApp(<AdminDashboardPage api={fakeAdminApi({ dashboard })} />)
  await user.click(await screen.findByRole('button', { name: /cerrar sesión/i }))
  expect(router.state.location.pathname).toBe('/login')
})
```

- [ ] **Step 3: Run and verify the feature is not yet integrated**

Run:

```powershell
npm test -- src/admin/AdminDashboardPage.test.jsx src/admin/components/layout/Sidebar.test.jsx
```

Expected: FAIL because `src/admin` does not exist.

- [ ] **Step 4: Adapt dashboard data and remove unsupported behavior**

`adminDashboardApi.getDashboard()` calls `/api/v1/admin/dashboard`. Remove:

- `messages` from state/search;
- Messages navigation;
- message/reply modals;
- `updateMessage` and `sendReply`;
- fake payment notification;
- destructive `deleteAppointment`.

Rename delete UI to cancellation. Image keys are derived client-side from the first pet species:

```js
export function imageKeyForSpecies(species = '') {
  const value = species.toLocaleLowerCase('es-CL')
  if (value.includes('gato')) return 'cat'
  return 'dog'
}
```

Logout calls `auth.logout()` and navigates to `/login`.

- [ ] **Step 5: Pass merged-dashboard tests and checkpoint**

Run:

```powershell
npm test -- src/admin
npm run lint
if (Test-Path .git) {
  git add src/admin src/assets/admin src/styles src/App.jsx
  git commit -m "feat: merge administrator dashboard"
}
```

Expected: all retained attached-dashboard tests PASS after their imports and expectations are adapted.

## Task 6: Administrator Reservation, Service, Availability, Client, Pet, and Notification Actions

**Files:**

- Modify: `src/api/adminApi.js`
- Modify: `src/admin/hooks/useAdminDashboard.js`
- Create: management components listed above
- Modify: `src/admin/components/dashboard/DashboardModals.jsx`
- Modify: `src/admin/PanelAdministrador.jsx`
- Create: `src/admin/AdminManagement.test.jsx`

**Interfaces:**

- Produces: all administrator mutations and list/detail views required by the approved design.

- [ ] **Step 1: Write failing management tests**

```jsx
it('confirms a pending reservation without deleting history', async () => {
  const api = fakeAdminApi({ dashboard })
  renderApp(<AdminDashboardPage api={api} />)
  await openReservation('Milo')
  await user.click(screen.getByRole('button', { name: /confirmar/i }))
  expect(api.changeStatus).toHaveBeenCalledWith(expect.anything(), {
    status: 'CONFIRMED',
    reason: '',
  })
})

it('creates a partial blackout and displays reservation conflicts', async () => {
  const api = fakeAdminApi()
  api.createBlock.mockRejectedValue(new ApiError(
    'El bloqueo se superpone con reservas activas.',
    { code: 'BLOCK_OVERLAPS_RESERVATIONS', status: 409 },
  ))
  renderApp(<AvailabilityManager api={api} />)
  await fillBlock('2026-08-10T10:00', '2026-08-10T12:00')
  expect(await screen.findByRole('alert')).toHaveTextContent(/reservas activas/i)
})
```

- [ ] **Step 2: Run and verify missing actions**

Run:

```powershell
npm test -- src/admin/AdminManagement.test.jsx
```

Expected: FAIL because the merged shell has detail-only mock-era modals.

- [ ] **Step 3: Implement the exact administrator API**

```js
export const adminApi = {
  dashboard: () => apiRequest('/api/v1/admin/dashboard'),
  reservations: (query = '') => apiRequest(`/api/v1/admin/reservations${query}`),
  changeStatus: (id, body) => apiRequest(`/api/v1/admin/reservations/${id}/status`, {
    body, method: 'PATCH',
  }),
  reschedule: (id, startsAt) =>
    apiRequest(`/api/v1/admin/reservations/${id}/reschedule`, {
      body: { startsAt }, method: 'PATCH',
    }),
  clients: (query = '') => apiRequest(`/api/v1/admin/clients${query}`),
  updateClient: (id, body) => apiRequest(`/api/v1/admin/clients/${id}`, {
    body, method: 'PATCH',
  }),
  pets: (query = '') => apiRequest(`/api/v1/admin/pets${query}`),
  updatePet: (id, body) => apiRequest(`/api/v1/admin/pets/${id}`, {
    body, method: 'PATCH',
  }),
  services: () => apiRequest('/api/v1/admin/services'),
  createService: (body) => apiRequest('/api/v1/admin/services', { body, method: 'POST' }),
  updateService: (id, body) => apiRequest(`/api/v1/admin/services/${id}`, {
    body, method: 'PATCH',
  }),
  archiveService: (id) => apiRequest(`/api/v1/admin/services/${id}`, {
    method: 'DELETE',
  }),
  weeklyAvailability: () => apiRequest('/api/v1/admin/availability/weekly'),
  replaceWeeklyAvailability: (body) =>
    apiRequest('/api/v1/admin/availability/weekly', { body, method: 'PUT' }),
  blocks: () => apiRequest('/api/v1/admin/availability/blocks'),
  createBlock: (body) => apiRequest('/api/v1/admin/availability/blocks', {
    body, method: 'POST',
  }),
  deleteBlock: (id) => apiRequest(`/api/v1/admin/availability/blocks/${id}`, {
    method: 'DELETE',
  }),
}
```

- [ ] **Step 4: Implement explicit administrator controls**

- reservation actions shown according to the backend lifecycle matrix;
- cancel modal requires optional reason and says history remains;
- reschedule uses public availability results;
- services support create/edit/archive/reactivate;
- weekly availability supports multiple intervals per day;
- blackout supports local full-day or partial interval input;
- client/pet detail fields are editable, with no health or medical fields;
- notifications retain individual/read-all behavior.

All successful mutations reload only the relevant resource and show an accessible status toast. A `409` remains in the modal with a corrective explanation.

- [ ] **Step 5: Pass management tests and checkpoint**

Run:

```powershell
npm test -- src/admin/AdminManagement.test.jsx
npm run lint
if (Test-Path .git) {
  git add src/api/adminApi.js src/admin
  git commit -m "feat: connect administrator management actions"
}
```

Expected: PASS for every lifecycle action, service mutation, interval/block action, filters, edits, and notification read state.

## Task 7: Consolidated Frontend Regression

**Files:**

- Modify: `src/App.jsx`
- Modify: `src/styles/main.scss`
- Modify: `README.md`
- Modify or delete: obsolete `src/utils/reservation.test.js`

**Interfaces:**

- Produces: one lint-clean, test-clean, production-building frontend.

- [ ] **Step 1: Add a route-level smoke test**

```jsx
it.each([
  ['/', /amidog/i],
  ['/login', /iniciar sesión/i],
  ['/crear-cuenta', /crear cuenta/i],
  ['/panel', /iniciar sesión/i],
  ['/admin', /iniciar sesión/i],
])('renders %s without crashing', async (route, expected) => {
  renderWholeApp({ route, authApi: fakeAuth({ me: null }) })
  expect(await screen.findByText(expected)).toBeVisible()
})
```

- [ ] **Step 2: Search for retired persistence and message code**

Run:

```powershell
rg -n 'amidog-appointments|amidog-pets|mockDashboard|messages|sendReply|updateMessage|deleteAppointment|HORARIOS_DISPONIBLES' src
```

Expected: no runtime hits. Test fixture names that deliberately assert removal may remain.

- [ ] **Step 3: Run the complete frontend verification**

Run:

```powershell
npm test
npm run lint
npm run build
```

Expected: all tests PASS, ESLint reports no errors, and Vite creates `dist`.

- [ ] **Step 4: Update frontend documentation**

Document:

- public/account/client/admin routes;
- required backend and proxy setup;
- why only booking drafts use `sessionStorage`;
- `VITE_WHATSAPP_NUMBER` is added by the final contact plan;
- test/lint/build commands.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add -A
  git commit -m "test: verify consolidated AmiDog frontend"
}
```
