import { defineConfig, devices } from '@playwright/test'

const environment = globalThis.process?.env ?? {}
const frontendUrl = environment.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173'
const externalServers = environment.PLAYWRIGHT_EXTERNAL_SERVERS === 'true'
const adminEmail = environment.PLAYWRIGHT_ADMIN_EMAIL ?? 'admin@amidog.test'
const adminPassword = environment.PLAYWRIGHT_ADMIN_PASSWORD ?? 'Admin-Password-19!'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  outputDir: 'output/playwright/test-results',
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'output/playwright/report' }],
  ],
  use: {
    baseURL: frontendUrl,
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: externalServers ? undefined : [
    {
      command: '.\\mvnw.cmd spring-boot:run',
      url: 'http://127.0.0.1:8080/api/v1/auth/csrf',
      timeout: 180_000,
      reuseExistingServer: false,
      env: {
        DB_URL: 'jdbc:postgresql://127.0.0.1:5432/amidog',
        DB_USERNAME: 'amidog',
        DB_PASSWORD: 'amidog-local',
        ADMIN_EMAIL: adminEmail,
        ADMIN_PASSWORD: adminPassword,
        ADMIN_NAME: 'Administradora AmiDog Local',
        ADMIN_PHONE: '+56911111111',
        BOOKING_AUTO_CONFIRM: 'false',
        EMAIL_DELIVERY: 'smtp',
        SMTP_HOST: '127.0.0.1',
        SMTP_PORT: '1025',
        SMTP_USERNAME: '',
        SMTP_PASSWORD: '',
        SMTP_AUTH: 'false',
        SMTP_STARTTLS: 'false',
        SMTP_STARTTLS_REQUIRED: 'false',
        SMTP_SSL: 'false',
        SMTP_ALLOW_PLAINTEXT: 'true',
        CLINIC_EMAIL: 'contacto@amidog.test',
        FRONTEND_BASE_URL: frontendUrl,
        FRONTEND_ORIGIN: frontendUrl,
        SESSION_COOKIE_SECURE: 'false',
      },
    },
    {
      command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 5173 --strictPort',
      url: frontendUrl,
      timeout: 120_000,
      reuseExistingServer: false,
    },
  ],
})
