import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { compile } from 'sass'
import { describe, expect, it } from 'vitest'

describe('public and client reduced motion', () => {
  it('disables smooth scrolling and nonessential global motion', () => {
    const css = compile(resolve(cwd(), 'src/styles/_reset.scss')).css

    expect(css).toContain('@media (prefers-reduced-motion: reduce)')
    expect(css).toMatch(/scroll-behavior:\s*auto\s*!important/)
    expect(css).toMatch(/animation-duration:\s*0\.01ms\s*!important/)
    expect(css).toMatch(/transition-duration:\s*0\.01ms\s*!important/)
  })

  it('keeps client rescheduling and saved-refresh warnings in the client theme', () => {
    const css = compile(resolve(cwd(), 'src/styles/_panel.scss')).css

    expect(css).toContain('.client-reschedule-slots')
    expect(css).toContain('.panel-refresh-warning')
    expect(css).toContain('.appointment-card__actions')
  })
})
