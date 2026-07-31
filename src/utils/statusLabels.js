export const STATUS_LABELS = Object.freeze({
  PENDING: 'Pendiente',
  CONFIRMED: 'Confirmada',
  CANCELLED: 'Cancelada',
  COMPLETED: 'Completada',
  NO_SHOW: 'No asistió',
})

export function statusLabel(status) {
  if (typeof status !== 'string') {
    return ''
  }
  return STATUS_LABELS[status] ?? status
}
