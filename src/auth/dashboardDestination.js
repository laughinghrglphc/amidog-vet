export function dashboardDestination(user) {
  if (user?.accountType === 'CLIENT') {
    return { to: '/panel', label: 'Mi panel' }
  }

  if (user?.accountType === 'ADMIN') {
    return { to: '/admin', label: 'Panel admin' }
  }

  return null
}
