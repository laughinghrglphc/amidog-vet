import { vi } from 'vitest'

export function jsonResponse(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
  })
}

export function emptyResponse(status = 204, headers = {}) {
  return new Response(null, { status, headers })
}

export function textResponse(body, status = 500, headers = {}) {
  return new Response(body, {
    status,
    headers: {
      'Content-Type': 'text/plain',
      ...headers,
    },
  })
}

function resolved(value) {
  return vi.fn().mockResolvedValue(value)
}

export function fakeAuth({
  me = null,
  loginResult = {
    userId: 1,
    clientId: 1,
    email: 'ana@example.cl',
    name: 'Ana Pérez',
    accountType: 'CLIENT',
  },
} = {}) {
  return {
    me: me instanceof Error
      ? vi.fn().mockRejectedValue(me)
      : resolved(me),
    register: resolved({ message: 'Revisa tu correo para verificar tu cuenta.' }),
    verifyEmail: resolved(undefined),
    resendVerification: resolved({ message: 'Revisa tu correo para verificar tu cuenta.' }),
    forgotPassword: resolved({
      message: 'Si la cuenta existe, enviaremos instrucciones al correo.',
    }),
    resetPassword: resolved(undefined),
    login: resolved(loginResult),
    logout: resolved(undefined),
  }
}

export function fakeClientApi({
  profile = {
    id: 1,
    name: 'Ana Pérez',
    phone: '+56912345678',
    email: 'ana@example.cl',
  },
  pets = [],
  reservations = [],
  notifications = [],
} = {}) {
  const defaultPet = pets[0] ?? {
    id: 84,
    name: 'Milo',
    species: 'Gato',
    breed: null,
    birthdate: null,
    active: true,
  }
  const defaultReservation = reservations[0] ?? {
    id: 301,
    startsAt: '2026-08-10T10:00:00-04:00',
    endsAt: '2026-08-10T10:30:00-04:00',
    status: 'PENDING',
    note: null,
    items: [{
      petId: defaultPet.id,
      petName: defaultPet.name,
      serviceId: 7,
      serviceName: 'Consulta general',
    }],
    createdAt: '2026-08-01T09:15:00-04:00',
  }
  const defaultNotification = notifications[0] ?? {
    id: 901,
    type: 'NEW_RESERVATION',
    title: 'Nueva reserva',
    body: 'Tu reserva fue recibida.',
    reservationId: defaultReservation.id,
    createdAt: '2026-08-01T13:15:00Z',
    unread: true,
  }
  return {
    profile: resolved(profile),
    updateProfile: resolved(profile),
    pets: resolved(pets),
    createPet: resolved(defaultPet),
    updatePet: resolved(defaultPet),
    archivePet: resolved(undefined),
    reservations: resolved(reservations),
    cancelReservation: resolved({
      id: defaultReservation.id,
      status: 'CANCELLED',
      startsAt: defaultReservation.startsAt,
      cancelledAt: '2026-08-02T11:00:00-04:00',
      cancelledBy: 'CLIENT',
      reason: null,
      updatedAt: '2026-08-02T11:00:00-04:00',
    }),
    rescheduleReservation: resolved({
      id: defaultReservation.id,
      previousStartsAt: defaultReservation.startsAt,
      startsAt: '2026-08-11T11:30:00-04:00',
      endsAt: '2026-08-11T12:00:00-04:00',
      previousStatus: defaultReservation.status,
      status: 'PENDING',
      updatedAt: '2026-08-02T11:10:00-04:00',
    }),
    notifications: resolved(notifications),
    readNotification: vi.fn(async (id) => ({
      ...defaultNotification,
      id,
      unread: false,
    })),
    readAllNotifications: resolved({ markedRead: 0 }),
  }
}

export function fakeBookingApi({
  pets = [],
  services = [],
  availability = [],
  reservation = {
    id: 1,
    startsAt: '2026-08-10T10:00:00-04:00',
    endsAt: '2026-08-10T10:30:00-04:00',
    status: 'PENDING',
    note: null,
    items: [],
    createdAt: '2026-08-01T09:15:00-04:00',
  },
} = {}) {
  return {
    pets: resolved(pets),
    services: resolved(services),
    availability: resolved(availability),
    createReservation: resolved(reservation),
  }
}

export function fakeAdminApi({
  dashboard = {
    profile: {
      name: 'Administradora AmiDog',
      email: 'admin@example.cl',
      role: 'ADMIN',
    },
    stats: { todayAppointments: 0, clients: 0, pets: 0 },
    appointments: [],
    services: [],
    chart: {
      currentWeek: { label: 'Esta semana', labels: [], values: [] },
      previousWeek: { label: 'Semana pasada', labels: [], values: [] },
      currentMonth: { label: 'Este mes', labels: [], values: [] },
    },
    schedule: [],
  },
} = {}) {
  const dashboardRequest = resolved(dashboard)
  const summary = dashboard.appointments[0] ?? (
    dashboard.schedule[0]
      ? {
          id: dashboard.schedule[0].reservationId,
          clientId: 1,
          clientName: 'Cliente',
          clientEmail: 'cliente@example.cl',
          clientPhone: '+56900000000',
          startsAt: dashboard.schedule[0].startsAt,
          endsAt: dashboard.schedule[0].endsAt,
          status: dashboard.schedule[0].status,
          clientNote: null,
          createdAt: dashboard.schedule[0].startsAt,
          updatedAt: dashboard.schedule[0].startsAt,
          items: dashboard.schedule[0].items,
        }
      : null
  )
  const reservationDetail = summary
    ? {
        ...summary,
        cancelledAt: null,
        cancelledBy: null,
        cancellationReason: null,
        events: [],
      }
    : null
  const defaultStatus = summary?.status ?? 'PENDING'
  const defaultClient = {
    id: 12,
    name: 'Ana Pérez',
    email: 'ana@example.cl',
    phone: '+56912345678',
    active: true,
    createdAt: '2025-01-02T10:00:00-03:00',
    updatedAt: '2026-08-02T11:00:00-04:00',
  }
  const defaultPet = {
    id: 84,
    clientId: defaultClient.id,
    ownerName: defaultClient.name,
    name: 'Milo',
    species: 'Gato',
    breed: null,
    birthdate: null,
    active: true,
    createdAt: '2025-01-02T10:00:00-03:00',
    updatedAt: '2026-08-02T11:00:00-04:00',
  }
  const defaultService = {
    id: 7,
    code: 'consulta-general',
    name: 'Consulta general',
    description: null,
    active: true,
    displayOrder: 10,
  }
  const defaultNotification = {
    id: 901,
    type: 'NEW_RESERVATION',
    title: 'Nueva reserva',
    body: 'Se registró una reserva.',
    reservationId: summary?.id ?? 301,
    createdAt: '2026-08-01T13:15:00Z',
    unread: true,
  }

  return {
    dashboard: dashboardRequest,
    reservations: resolved({
      content: dashboard.appointments,
      page: 0,
      size: 25,
      totalElements: dashboard.appointments.length,
      totalPages: dashboard.appointments.length ? 1 : 0,
    }),
    reservation: resolved(reservationDetail),
    changeStatus: vi.fn(async (id, body) => ({
      id,
      previousStatus: defaultStatus,
      status: body.status,
      reason: body.reason ?? null,
      updatedAt: '2026-08-02T11:00:00-04:00',
    })),
    reschedule: vi.fn(async (id, startsAt) => ({
      id,
      previousStartsAt: summary?.startsAt ?? '2026-08-10T10:00:00-04:00',
      startsAt,
      endsAt: '2026-08-11T12:00:00-04:00',
      previousStatus: defaultStatus,
      status: defaultStatus,
      updatedAt: '2026-08-02T11:10:00-04:00',
    })),
    clients: resolved({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
    client: resolved(null),
    updateClient: vi.fn(async (id, body) => ({
      ...defaultClient,
      ...body,
      id,
      email: defaultClient.email,
      updatedAt: '2026-08-02T11:00:00-04:00',
    })),
    pets: resolved({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
    pet: resolved(null),
    updatePet: vi.fn(async (id, body) => ({
      ...defaultPet,
      ...body,
      id,
      clientId: defaultPet.clientId,
      ownerName: defaultPet.ownerName,
      active: defaultPet.active,
      updatedAt: '2026-08-02T11:00:00-04:00',
    })),
    services: resolved([]),
    createService: vi.fn(async (body) => ({
      ...defaultService,
      ...body,
      id: 9,
      active: true,
    })),
    updateService: vi.fn(async (id, body) => ({
      ...defaultService,
      ...body,
      id,
      code: defaultService.code,
    })),
    archiveService: resolved(undefined),
    weeklyAvailability: resolved([]),
    replaceWeeklyAvailability: vi.fn(async (body) =>
      body.map((interval, index) => ({ id: index + 1, ...interval }))),
    blocks: resolved([]),
    createBlock: vi.fn(async (body) => ({
      id: 90,
      ...body,
      createdAt: '2026-08-02T11:00:00-04:00',
    })),
    deleteBlock: resolved(undefined),
    notifications: resolved([]),
    readNotification: vi.fn(async (id) => ({
      ...defaultNotification,
      id,
      unread: false,
    })),
    readAllNotifications: resolved({ markedRead: 0 }),
  }
}

export function fakeContactApi() {
  return {
    send: resolved({ message: 'Mensaje enviado.' }),
  }
}
