const RESERVATION_KEY = 'amidogReservaPendiente'
const CONFIRMED_KEY = 'amidogReservaConfirmada'

export function getReservation() {
  try {
    return JSON.parse(sessionStorage.getItem(RESERVATION_KEY)) || {}
  } catch {
    return {}
  }
}

export function saveReservation(data) {
  const reservation = { ...getReservation(), ...data }
  sessionStorage.setItem(RESERVATION_KEY, JSON.stringify(reservation))
  return reservation
}

export function confirmReservation(data) {
  const reservation = {
    ...getReservation(),
    ...data,
    estado: 'confirmada',
    confirmadoEn: new Date().toISOString()
  }

  localStorage.setItem(CONFIRMED_KEY, JSON.stringify(reservation))
  return reservation
}
