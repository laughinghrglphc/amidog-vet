import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import Sidebar from './Sidebar'

function SidebarHarness() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button
        type="button"
        aria-controls="admin-sidebar"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        Menú
      </button>
      <Sidebar
        activeItem="Panel"
        isMobile
        items={[{ icon: 'home', label: 'Panel' }]}
        open={open}
        onClose={() => setOpen(false)}
        onNavigate={() => undefined}
      />
    </>
  )
}

describe('Sidebar', () => {
  it('removes closed mobile navigation from keyboard interaction', async () => {
    const user = userEvent.setup()
    render(<SidebarHarness />)

    const sidebar = screen.getByLabelText('Navegación principal')
    const menuButton = screen.getByRole('button', { name: 'Menú' })

    expect(sidebar).toHaveAttribute('aria-hidden', 'true')
    expect(sidebar).toHaveAttribute('inert')
    expect(menuButton).toHaveAttribute('aria-expanded', 'false')

    await user.click(menuButton)

    expect(sidebar).toHaveAttribute('aria-hidden', 'false')
    expect(sidebar).not.toHaveAttribute('inert')
    expect(menuButton).toHaveAttribute('aria-expanded', 'true')
  })

  it('renders only the supported navigation items supplied by the dashboard shell', () => {
    render(
      <Sidebar
        activeItem="Panel"
        isMobile={false}
        items={[
          { icon: 'home', label: 'Panel' },
          { icon: 'calendar', label: 'Reservas' },
        ]}
        open={false}
        onClose={() => undefined}
        onNavigate={() => undefined}
      />,
    )

    expect(screen.getByRole('button', { name: 'Panel' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reservas' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /mensajes/i })).not.toBeInTheDocument()
  })

  it('shows a capped accessible count for unread notifications', () => {
    render(
      <Sidebar
        activeItem="Panel"
        isMobile={false}
        items={[{ icon: 'info', label: 'Notificaciones', modal: 'notifications' }]}
        unreadCount={120}
        open={false}
        onClose={() => undefined}
        onNavigate={() => undefined}
      />,
    )

    expect(screen.getByRole('button', {
      name: 'Notificaciones, 120 sin leer',
    })).toBeVisible()
    expect(screen.getByText('99+')).toBeVisible()
  })

  it('keeps the notification control unchanged when there is nothing unread', () => {
    render(
      <Sidebar
        activeItem="Panel"
        isMobile={false}
        items={[{ icon: 'info', label: 'Notificaciones', modal: 'notifications' }]}
        unreadCount={0}
        open={false}
        onClose={() => undefined}
        onNavigate={() => undefined}
      />,
    )

    expect(screen.getByRole('button', { name: 'Notificaciones' })).toBeVisible()
    expect(screen.queryByText('99+')).not.toBeInTheDocument()
  })
})
