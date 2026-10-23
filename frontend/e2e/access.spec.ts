import { expect, test } from '@playwright/test'

import { failOnBrowserErrors, productName, registerCustomer, signIn, warehouseEmail } from './support'

test.describe('access by role', () => {
  test('a visitor can browse the shop but must sign in to order', async ({ page }) => {
    const assertNoErrors = failOnBrowserErrors(page)
    await page.goto('/')
    await expect(page).toHaveURL(/\/shop$/)
    await page.getByLabel('Search products by name').fill(productName())
    const row = page.getByRole('listitem').filter({ hasText: productName() })
    await expect(row.getByRole('link', { name: 'Sign in to order' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Add to cart' })).toHaveCount(0)
    assertNoErrors()
  })

  test('a visitor opening a staff page is sent to sign in', async ({ page }) => {
    await page.goto('/inventory')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('a customer cannot reach staff pages', async ({ page, baseURL }) => {
    await signIn(page, await registerCustomer(baseURL ?? ''))
    await page.goto('/inventory')
    await expect(page).not.toHaveURL(/\/inventory/)
    await expect(page.getByRole('link', { name: 'Overview' })).toHaveCount(0)
  })

  test('the warehouse sees only fulfilment and read-only stock', async ({ page }) => {
    const assertNoErrors = failOnBrowserErrors(page)
    await signIn(page, warehouseEmail())
    await expect(page).toHaveURL(/\/orders$/)
    await expect(page.getByRole('heading', { name: 'Fulfilment' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Overview' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Products' })).toHaveCount(0)

    await page.goto('/inventory')
    await expect(page.getByRole('heading', { name: 'Inventory' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'History' }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Adjust' })).toHaveCount(0)
    assertNoErrors()
  })
})
