import { Fragment } from 'react'
import { render } from '@testing-library/react'
import {
  createMemoryRouter,
  RouterProvider,
} from 'react-router'

export function renderApp(element, {
  path = '*',
  route = '/',
  routes,
  Wrapper = Fragment,
  wrapperProps = {},
} = {}) {
  const router = createMemoryRouter(
    routes ?? [{ path, element }],
    { initialEntries: [route] },
  )
  const result = render(
    <Wrapper {...wrapperProps}>
      <RouterProvider router={router} />
    </Wrapper>,
  )

  return { ...result, router }
}
