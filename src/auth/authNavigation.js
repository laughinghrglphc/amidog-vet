const ROLE_HOMES = Object.freeze({
  ADMIN: '/admin',
  CLIENT: '/panel',
})

const CLIENT_ROUTES = new Set([
  '/panel',
  '/reservar',
  '/calendario',
  '/confirmacion',
])

export function homeForRole(role) {
  return ROLE_HOMES[role] ?? '/'
}

function decodedForInspection(value) {
  let decoded = value
  for (let index = 0; index < 3; index += 1) {
    try {
      const next = decodeURIComponent(decoded)
      if (next === decoded) {
        return decoded
      }
      decoded = next
    } catch {
      return null
    }
  }
  return decoded
}

function hasControlCharacter(value) {
  return [...value].some((character) => {
    const code = character.charCodeAt(0)
    return code <= 31 || code === 127
  })
}

export function safeNextPath(candidate, role) {
  const fallback = homeForRole(role)
  if (typeof candidate !== 'string' || !candidate.startsWith('/')) {
    return fallback
  }

  const inspected = decodedForInspection(candidate)
  if (
    inspected === null
    || !inspected.startsWith('/')
    || inspected.startsWith('//')
    || inspected.includes('\\')
    || hasControlCharacter(inspected)
  ) {
    return fallback
  }

  let parsed
  try {
    parsed = new URL(candidate, 'https://amidog.local')
  } catch {
    return fallback
  }
  if (parsed.origin !== 'https://amidog.local') {
    return fallback
  }

  const allowed = role === 'ADMIN'
    ? parsed.pathname === '/admin' || parsed.pathname.startsWith('/admin/')
    : CLIENT_ROUTES.has(parsed.pathname)

  return allowed ? candidate : fallback
}
