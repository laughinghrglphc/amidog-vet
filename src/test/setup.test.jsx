import { useEffect } from 'react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

function MountedProvider() {
  useEffect(() => {
    const onProbe = () => {
      document.body.dataset.providerListener = 'still-mounted'
    }
    window.addEventListener('amidog:test-cleanup', onProbe)
    return () => window.removeEventListener('amidog:test-cleanup', onProbe)
  }, [])

  return <div data-testid="mounted-provider">Provider activo</div>
}

let mountProbeCompleted = false

describe.sequential('explicit React Testing Library cleanup', { shuffle: false }, () => {
  it('mounts a React root and provider listener for the isolation probe', () => {
    render(<MountedProvider />)

    expect(screen.getByTestId('mounted-provider')).toBeInTheDocument()
    mountProbeCompleted = true
  })

  it('starts the next test without the previous React root or provider listener', () => {
    expect(
      mountProbeCompleted,
      'the mount phase must run before the cleanup assertion',
    ).toBe(true)
    expect(screen.queryByTestId('mounted-provider')).not.toBeInTheDocument()

    delete document.body.dataset.providerListener
    window.dispatchEvent(new CustomEvent('amidog:test-cleanup'))
    expect(document.body).not.toHaveAttribute('data-provider-listener')
    mountProbeCompleted = false
  })
})
