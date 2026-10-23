import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  failOnBrowserErrors,
  newCustomerEmail,
  placeOrder,
  registerCustomer,
  signIn,
  staffEmail,
  warehouseEmail,
} from './support'

async function signedInPage(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await signIn(page, email)
  return page
}

test.describe('order journey', () => {
  test('a new customer registers, orders, and sees it pending', async ({ page }) => {
    const assertNoErrors = failOnBrowserErrors(page)
    await page.goto('/register')
    await page.getByLabel('Full name').fill('Journey Customer')
    await page.getByLabel('Email').fill(newCustomerEmail())
    await page.getByLabel('Password').fill('short')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText('Use at least 12 characters')).toBeVisible()

    await page.getByLabel('Password').fill('journey-password-1')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page).toHaveURL(/\/shop$/)

    await placeOrder(page)
    await expect(page.getByText('Waiting for the warehouse to confirm')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Cart, 0 items' })).toBeVisible()
    assertNoErrors()
  })

  test('staff confirm, the warehouse packs, ships and delivers, the customer tracks every step', async ({ browser, baseURL }) => {
    const customer = await signedInPage(browser, await registerCustomer(baseURL ?? ''))
    const assertCustomerOk = failOnBrowserErrors(customer)
    const orderId = await placeOrder(customer)

    const staff = await signedInPage(browser, staffEmail())
    await staff.goto(`/orders/${orderId}`)
    await staff.getByRole('button', { name: 'Confirm order' }).click()
    await expect(staff.getByRole('button', { name: 'Mark as packed' })).toBeVisible()

    const warehouse = await signedInPage(browser, warehouseEmail())
    const assertWarehouseOk = failOnBrowserErrors(warehouse)
    await warehouse.goto(`/orders/${orderId}`)
    await expect(warehouse.getByRole('button', { name: 'Mark as packed' })).toBeVisible()
    await expect(warehouse.getByRole('button', { name: 'Cancel order' })).toHaveCount(0)
    for (const step of ['packed', 'shipped', 'delivered']) {
      await warehouse.getByRole('button', { name: `Mark as ${step}` }).click()
      await expect(warehouse.getByRole('button', { name: `Mark as ${step}` })).toHaveCount(0)
    }

    await customer.goto(`/orders/${orderId}`)
    const tracking = customer.getByRole('region', { name: /Tracking for order/ })
    for (const checkpoint of ['Placed', 'Confirmed', 'Packed', 'Shipped', 'Delivered']) {
      await expect(tracking.getByText(checkpoint, { exact: true }).first()).toBeVisible()
    }
    await expect(tracking.getByText('Not yet')).toHaveCount(0)
    await expect(customer.getByRole('button', { name: 'Cancel order' })).toHaveCount(0)
    assertCustomerOk()
    assertWarehouseOk()
  })

  test('a confirmed order can be cancelled by the customer, and stock comes back', async ({ browser, baseURL }) => {
    const customer = await signedInPage(browser, await registerCustomer(baseURL ?? ''))
    const orderId = await placeOrder(customer)
    const staff = await signedInPage(browser, staffEmail())
    await staff.goto(`/orders/${orderId}`)
    await staff.getByRole('button', { name: 'Confirm order' }).click()
    await expect(staff.getByRole('button', { name: 'Mark as packed' })).toBeVisible()

    await customer.reload()
    await expect(customer.getByText(/Can still be cancelled for/)).toBeVisible()
    await customer.getByRole('button', { name: 'Cancel order' }).click()
    await customer.getByRole('alertdialog').getByRole('button', { name: 'Cancel order' }).click()
    await expect(customer.getByText('Items returned to stock')).toBeVisible()
  })

  test('once packed, the customer can no longer cancel', async ({ browser, baseURL }) => {
    const customer = await signedInPage(browser, await registerCustomer(baseURL ?? ''))
    const orderId = await placeOrder(customer)
    const staff = await signedInPage(browser, staffEmail())
    await staff.goto(`/orders/${orderId}`)
    await staff.getByRole('button', { name: 'Confirm order' }).click()
    await staff.getByRole('button', { name: 'Mark as packed' }).click()
    await expect(staff.getByRole('button', { name: 'Mark as shipped' })).toBeVisible()

    await customer.reload()
    await expect(customer.getByText('Packed and ready to go')).toBeVisible()
    await expect(customer.getByRole('button', { name: 'Cancel order' })).toHaveCount(0)
  })
})
