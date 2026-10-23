import { expect, type Page } from '@playwright/test'

/** Password for every account the e2e tests create (they exist only in test runs). */
export const E2E_PASSWORD = 'e2e-password-123'

export function required(name: string): string {
  const value = process.env[name]
  if (value === undefined || value === '') throw new Error(`${name} is not set; did global-setup run?`)
  return value
}

export const staffEmail = () => required('E2E_STAFF_EMAIL')
export const warehouseEmail = () => required('E2E_WAREHOUSE_EMAIL')
export const productName = () => required('E2E_PRODUCT_NAME')

let customerCounter = 0

/** A unique customer email for this run. */
export function newCustomerEmail(): string {
  customerCounter += 1
  return `e2e-customer-${required('E2E_RUN')}-${customerCounter}-${Date.now()}@example.com`
}

export async function login(baseURL: string, email: string, password: string): Promise<string> {
  const response = await fetch(new URL('/api/v1/auth/login', baseURL), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!response.ok) throw new Error(`Login as ${email} failed: ${response.status}`)
  const body = await response.json() as { accessToken?: unknown }
  if (typeof body.accessToken !== 'string') throw new Error('Login response had no accessToken')
  return body.accessToken
}

export async function apiPost(baseURL: string, path: string, token: string | null, body: unknown, expected: number): Promise<unknown> {
  const response = await fetch(new URL(path, baseURL), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token === null ? {} : { Authorization: `Bearer ${token}` }) },
    body: JSON.stringify(body),
  })
  if (response.status !== expected) {
    throw new Error(`POST ${path} returned ${response.status}, expected ${expected}: ${await response.text()}`)
  }
  const text = await response.text()
  return text === '' ? null : JSON.parse(text)
}

/** Registers a customer through the API (fast); the registration UI has its own test. */
export async function registerCustomer(baseURL: string): Promise<string> {
  const email = newCustomerEmail()
  await apiPost(baseURL, '/api/v1/auth/register', null, { email, password: E2E_PASSWORD, fullName: 'E2E Customer' }, 201)
  return email
}

export async function signIn(page: Page, email: string, password: string = E2E_PASSWORD): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).not.toHaveURL(/\/login$/)
}

/** Fails the test on any browser error: an uncaught exception, a console error, or a 5xx response. */
export function failOnBrowserErrors(page: Page): () => void {
  const problems: string[] = []
  page.on('pageerror', (error) => problems.push(`page error: ${error.message}`))
  page.on('console', (message) => {
    // The page-load session check answers 401 when nobody is signed in: expected, not a problem.
    if (message.type() === 'error' && !message.text().includes('status of 401')) problems.push(`console: ${message.text()}`)
  })
  page.on('response', (response) => {
    if (response.status() >= 500) problems.push(`${response.status()} ${response.url()}`)
  })
  return () => expect(problems, 'browser errors').toEqual([])
}

/** Puts one unit of the e2e product in the cart and places the order. Returns the order id. */
export async function placeOrder(page: Page): Promise<number> {
  await page.goto('/shop')
  await page.getByLabel('Search products by name').fill(productName())
  const row = page.getByRole('listitem').filter({ hasText: productName() })
  await row.getByRole('button', { name: 'Add to cart' }).click()
  await expect(page.getByText(`Added 1 × ${productName()}`)).toBeVisible()
  await page.getByRole('link', { name: /^Cart,/ }).click()
  await page.getByRole('button', { name: 'Place order' }).click()
  await expect(page).toHaveURL(/\/orders\/\d+$/)
  return Number(page.url().split('/').pop())
}
