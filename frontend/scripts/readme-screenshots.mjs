// Regenerates the README screenshots in .github/assets/ from a RUNNING stack seeded with demo data.
// Use a throwaway database, not one you care about:
//   docker compose up -d --build && scripts/seed-demo.sh        (from the repo root)
//   npm run screenshots                                          (from frontend/; SHOTS_BASE_URL to override)
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

import { chromium } from '@playwright/test'

const BASE = process.env.SHOTS_BASE_URL ?? 'http://localhost:3000'
const OUT = resolve(import.meta.dirname, '../../.github/assets')
const DESKTOP = { width: 1440, height: 900 }
const PHONE = { width: 390, height: 844 }
// The demo accounts created by scripts/seed-demo.sh.
const CUSTOMER = { email: 'ada@example.com', password: 'customer-password-123' }
const STAFF = { email: 'staff@stockflow.local', password: 'staff-password-123' }

async function apiToken({ email, password }) {
  const response = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!response.ok) throw new Error(`Login failed for ${email}: ${response.status}. Did you run scripts/seed-demo.sh?`)
  return (await response.json()).accessToken
}

async function shippedOrderId() {
  const token = await apiToken(CUSTOMER)
  const response = await fetch(`${BASE}/api/v1/orders?status=SHIPPED&size=1`, { headers: { Authorization: `Bearer ${token}` } })
  const id = (await response.json()).content[0]?.id
  if (id === undefined) throw new Error('The demo customer has no SHIPPED order. Did you run scripts/seed-demo.sh?')
  return id
}

async function signedIn(browser, account, viewport) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 2, colorScheme: 'dark' })
  const page = await context.newPage()
  await page.goto(`${BASE}/login`)
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password').fill(account.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL((url) => !url.pathname.endsWith('/login'))
  return page
}

async function shot(page, name, options = {}) {
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  // Toasts ("Signed in", "Added to cart") would cover part of the page.
  await page.addStyleTag({ content: '[data-sonner-toaster] { display: none !important; }' })
  await page.screenshot({ path: `${OUT}/${name}.png`, ...options })
  console.log(`  ${name}.png`)
}

await mkdir(OUT, { recursive: true })
const browser = await chromium.launch()
try {
  const orderId = await shippedOrderId()

  const customer = await signedIn(browser, CUSTOMER, DESKTOP)
  await customer.goto(`${BASE}/shop`)
  await customer.getByRole('listitem').filter({ hasText: 'Mechanical keyboard' }).getByRole('button', { name: 'Add to cart' }).click()
  await customer.getByRole('listitem').filter({ hasText: 'Standing desk' }).getByRole('button', { name: 'Add to cart' }).click()
  await customer.evaluate(() => window.scrollTo(0, 0))
  await shot(customer, 'shop')

  await customer.goto(`${BASE}/orders/${orderId}`)
  await customer.getByRole('region', { name: /Tracking for order/ }).waitFor()
  await shot(customer, 'order-tracking')

  const phone = await signedIn(browser, CUSTOMER, PHONE)
  await phone.goto(`${BASE}/orders/${orderId}`)
  await phone.getByRole('region', { name: /Tracking for order/ }).waitFor()
  // Just the first screen: the tracking label. Below it, the item table scrolls sideways on a phone.
  await shot(phone, 'order-tracking-phone', { clip: { x: 0, y: 0, ...PHONE, height: 760 } })

  const staff = await signedIn(browser, STAFF, DESKTOP)
  await staff.goto(`${BASE}/dashboard`)
  await staff.locator('.recharts-surface').first().waitFor()
  await shot(staff, 'staff-overview')

  await staff.goto(`${BASE}/inventory`)
  await staff.getByRole('row').filter({ hasText: 'Mechanical keyboard' }).getByRole('button', { name: 'History' }).click()
  await staff.getByRole('dialog').waitFor()
  await shot(staff, 'inventory-history')
} finally {
  await browser.close()
}
