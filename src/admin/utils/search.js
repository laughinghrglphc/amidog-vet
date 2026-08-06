export function normalizeForSearch(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim()
    .toLocaleLowerCase('es-CL')
}

export function findDashboardResults(query, { appointments }) {
  const normalizedQuery = normalizeForSearch(query)
  if (normalizedQuery.length < 2) return []

  return appointments
    .filter((appointment) => {
      const values = [
        appointment.clientName,
        appointment.clientEmail,
        appointment.clientPhone,
        ...appointment.items.flatMap((item) => [
          item.petName,
          item.species,
          item.breed,
          item.serviceName,
        ]),
      ]
      return values.some((value) =>
        normalizeForSearch(value).includes(normalizedQuery))
    })
    .map((appointment) => ({
      id: appointment.id,
      kind: 'appointment',
      eyebrow: 'Reserva',
      title: `${appointment.items.map((item) => item.petName).join(', ')} · ${appointment.clientName}`,
      detail: [...new Set(appointment.items.map((item) => item.serviceName))].join(', '),
    }))
    .slice(0, 6)
}
