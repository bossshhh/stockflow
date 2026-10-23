import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

import type { FullConfig } from '@playwright/test'

import { E2E_PASSWORD, apiPost, login } from './support'

/**
 * Runs once before all tests. Uses the admin account (ADMIN_EMAIL / ADMIN_PASSWORD from the environment
 * or the repo's .env) to create everything the tests need, uniquely named per run:
 * a staff account, a warehouse account, and a product with plenty of stock.
 * Values are handed to the tests through environment variables (Playwright passes them to workers).
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL
  if (baseURL === undefined) throw new Error('playwright.config.ts must set use.baseURL')

  const admin = readAdminCredentials()
  const run = Date.now().toString(36)
  const staffEmail = `e2e-staff-${run}@stockflow.local`
  const warehouseEmail = `e2e-warehouse-${run}@stockflow.local`

  const adminToken = await login(baseURL, admin.email, admin.password)
  await apiPost(baseURL, '/api/v1/admin/users', adminToken,
    { email: staffEmail, password: E2E_PASSWORD, fullName: 'E2E Staff', role: 'STAFF' }, 201)
  await apiPost(baseURL, '/api/v1/admin/users', adminToken,
    { email: warehouseEmail, password: E2E_PASSWORD, fullName: 'E2E Warehouse', role: 'WAREHOUSE' }, 201)

  const staffToken = await login(baseURL, staffEmail, E2E_PASSWORD)
  const productName = `E2E lamp ${run}`
  const product = await apiPost(baseURL, '/api/v1/products', staffToken,
    { sku: `E2E-${run}`.toUpperCase(), name: productName, price: 12.5 }, 201) as { id: number }
  await apiPost(baseURL, `/api/v1/inventory/${product.id}/adjustments`, staffToken,
    { quantityChange: 500, reason: 'E2E stock' }, 200)

  process.env.E2E_RUN = run
  process.env.E2E_STAFF_EMAIL = staffEmail
  process.env.E2E_WAREHOUSE_EMAIL = warehouseEmail
  process.env.E2E_PRODUCT_NAME = productName
}

function readAdminCredentials(): { email: string; password: string } {
  const fromEnvFile = readDotEnv(resolve(import.meta.dirname, '../../.env'))
  const email = process.env.ADMIN_EMAIL ?? fromEnvFile.ADMIN_EMAIL
  const password = process.env.ADMIN_PASSWORD ?? fromEnvFile.ADMIN_PASSWORD
  if (email === undefined || email === '' || password === undefined || password === '') {
    throw new Error('Set ADMIN_EMAIL and ADMIN_PASSWORD (environment or the repo .env) to run the e2e tests')
  }
  return { email, password }
}

/** Minimal KEY=VALUE reader for the repo's .env (comments and blank lines ignored). */
function readDotEnv(path: string): Record<string, string> {
  if (!existsSync(path)) return {}
  const values: Record<string, string> = {}
  for (const line of readFileSync(path, 'utf8').split('\n')) {
    const match = /^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/.exec(line)
    if (match?.[1] !== undefined && match[2] !== undefined) values[match[1]] = match[2]
  }
  return values
}
