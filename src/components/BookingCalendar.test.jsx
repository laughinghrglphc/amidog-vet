import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import BookingCalendar from './BookingCalendar'

describe('BookingCalendar', () => {
  it('enables only dates with availability and reports a selected date', async () => {
    const onSelectDate = vi.fn()
    const { rerender } = render(
      <BookingCalendar
        availableDates={['2026-08-10', '2026-08-12']}
        from="2026-08-05"
        to="2026-11-02"
        selectedDate=""
        onSelectDate={onSelectDate}
      />,
    )

    const available = screen.getByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    })
    expect(available).toBeEnabled()
    expect(screen.getByRole('button', {
      name: /martes,? 11 de agosto de 2026, sin horarios/i,
    })).toBeDisabled()

    await userEvent.click(available)

    expect(onSelectDate).toHaveBeenCalledWith('2026-08-10')

    rerender(
      <BookingCalendar
        availableDates={['2026-08-10', '2026-08-12']}
        from="2026-08-05"
        to="2026-11-02"
        selectedDate="2026-08-10"
        onSelectDate={onSelectDate}
      />,
    )
    expect(screen.getByRole('button', {
      name: /lunes,? 10 de agosto de 2026, disponible/i,
    })).toHaveAttribute('aria-pressed', 'true')
  })

  it('navigates only inside the loaded month range', async () => {
    render(
      <BookingCalendar
        availableDates={['2026-08-10', '2026-09-03', '2026-11-02']}
        from="2026-08-05"
        to="2026-11-02"
        selectedDate=""
        onSelectDate={() => undefined}
      />,
    )
    const previous = screen.getByRole('button', { name: 'Mes anterior' })
    const next = screen.getByRole('button', { name: 'Mes siguiente' })

    expect(previous).toBeDisabled()
    expect(screen.getByRole('heading', { name: /agosto de 2026/i })).toBeVisible()

    await userEvent.click(next)
    expect(screen.getByRole('heading', { name: /septiembre de 2026/i })).toBeVisible()
    expect(screen.getByRole('button', {
      name: /jueves,? 3 de septiembre de 2026, disponible/i,
    })).toBeEnabled()

    await userEvent.click(next)
    await userEvent.click(next)
    expect(screen.getByRole('heading', { name: /noviembre de 2026/i })).toBeVisible()
    expect(next).toBeDisabled()
  })
})
