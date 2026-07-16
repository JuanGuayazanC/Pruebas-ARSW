import { test, expect } from '@playwright/test';

test('usuario puede consultar la página principal', async ({ page }) => {
  await page.goto('http://localhost:5173');
  await expect(page).toHaveTitle(/Orders|Pedidos|App/);
});

test('usuario puede crear un pedido', async ({ page }) => {
  await page.goto('http://localhost:5173');

  await page.fill('[data-testid="customer-id"]', 'CUS-01');
  await page.fill('[data-testid="order-total"]', '120000');
  await page.click('[data-testid="create-order"]');

  await expect(page.locator('[data-testid="order-status"]')).toContainText('CREATED');
});
