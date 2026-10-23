import { expect, test } from '@playwright/test'

import { failOnBrowserErrors, productName, registerCustomer, signIn, staffEmail } from './support'

test.describe('staff tools', () => {
  test('the overview shows figures, the stock chart and the queues', async ({ page }) => {
    const assertNoErrors = failOnBrowserErrors(page)
    await signIn(page, staffEmail())
    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByRole('heading', { name: 'Stock by product' })).toBeVisible()
    await expect(page.locator('.recharts-bar-rectangle').first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Confirmation queue' })).toBeVisible()
    assertNoErrors()
  })

  test('stock adjustments are validated, saved and recorded in the history', async ({ page }) => {
    await signIn(page, staffEmail())
    await page.goto('/inventory')
    await page.getByLabel('Search stock by name or SKU').fill(productName())
    const row = page.getByRole('row').filter({ hasText: productName() })
    await row.getByRole('button', { name: 'Adjust' }).click()
    await page.getByLabel('Change').fill('0')
    await page.getByLabel('Reason').fill('e2e check')
    await page.getByRole('button', { name: 'Save adjustment' }).click()
    await expect(page.getByText('A change of 0 does nothing')).toBeVisible()

    await page.getByLabel('Change').fill('5')
    await page.getByRole('button', { name: 'Save adjustment' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await row.getByRole('button', { name: 'History' }).click()
    await expect(page.getByText('e2e check').first()).toBeVisible()
  })

  test('a price like 19.90 is accepted (floating-point regression)', async ({ page }) => {
    await signIn(page, staffEmail())
    await page.goto('/products')
    await page.getByRole('button', { name: 'New product' }).click()
    await page.getByLabel('SKU').fill(`E2E-P-${Date.now()}`)
    await page.getByLabel('Name').fill('E2E price check')
    await page.getByLabel('Price (USD)').fill('19.90')
    await page.getByRole('button', { name: 'Create product' }).click()
    await expect(page.getByText('E2E price check created')).toBeVisible()
  })
})

test.describe('session', () => {
  test('three tabs reloading at the same moment all stay signed in', async ({ browser, baseURL }) => {
    const context = await browser.newContext()
    const first = await context.newPage()
    await signIn(first, await registerCustomer(baseURL ?? ''))
    const tabs = [first, await context.newPage(), await context.newPage()]

    // Every tab restores its session from the shared refresh cookie at once. Without serialized
    // refreshes the second refresh would look like token theft and log everyone out.
    await Promise.all(tabs.map((tab) => tab.goto('/orders')))
    for (const tab of tabs) {
      await expect(tab.getByRole('heading', { name: 'My orders' })).toBeVisible()
    }
  })

  test('signing out really ends the session', async ({ page, baseURL }) => {
    await signIn(page, await registerCustomer(baseURL ?? ''))
    await page.getByRole('button', { name: 'E2E Customer' }).click()
    await page.getByRole('menuitem', { name: 'Sign out' }).click()
    await expect(page).toHaveURL(/\/shop$/)
    await page.reload()
    await page.goto('/orders')
    await expect(page).toHaveURL(/\/login$/)
  })
})

test.describe('mobile', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('order pages never scroll sideways on a phone', async ({ page, baseURL }) => {
    await signIn(page, await registerCustomer(baseURL ?? ''))
    await page.goto('/shop')
    await page.getByLabel('Search products by name').fill(productName())
    await page.getByRole('listitem').filter({ hasText: productName() }).getByRole('button', { name: 'Add to cart' }).click()
    await page.goto('/cart')
    await page.getByRole('button', { name: 'Place order' }).click()
    await expect(page).toHaveURL(/\/orders\/\d+$/)
    await expect(page.getByRole('region', { name: /Tracking for order/ })).toBeVisible()
    const scrollsSideways = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
    expect(scrollsSideways).toBe(false)
  })
})
