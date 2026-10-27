import {test,expect,Page} from '@playwright/test';
async function login(page:Page,user:string) {
 await page.goto('/'); await page.locator('#username').fill(user); await page.locator('#password').fill(process.env['REVIEW_LOGIN_PASSWORD']!);
 const request=page.waitForRequest(r=>r.url().includes('/api/requests')&&!!r.headers()['authorization']);
 await page.locator('#kc-login').click(); const token=(await request).headers()['authorization'];
 await expect(page.getByRole('heading',{name:'Team queue'})).toBeVisible(); return token;
}
test('real login, creation, lifecycle, conflict and tenant isolation',async({page,browser})=>{
 const token=await login(page,'alice'); const title='VPN enrollment '+Date.now();
 await page.getByRole('button',{name:'Create request'}).click(); await expect(page.getByText('Enter a title, up to 160 characters.')).toBeVisible();
 await page.getByLabel('What do you need?').fill(title); await page.getByLabel('A little more context').fill('New laptop cannot connect. <script>window.bad=true</script>');
 const created=page.waitForResponse(r=>r.url().endsWith('/api/requests')&&r.request().method()==='POST');
 await page.getByRole('button',{name:'Create request'}).click(); const item=await (await created).json();
 await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible(); await expect(page.getByRole('status')).toContainText('Request created');
 await page.getByRole('button',{name:'Start work'}).click(); await expect(page.getByRole('button',{name:'Mark resolved'})).toBeVisible();
 const stale=await page.request.patch('/api/requests/'+item.id+'/status',{headers:{Authorization:token},data:{status:'RESOLVED',version:0}}); expect(stale.status()).toBe(409);
 await page.getByRole('button',{name:'Mark resolved'}).click(); await expect(page.getByRole('button',{name:'Reopen request'})).toBeVisible();
 await page.getByRole('button',{name:'Resolved',exact:true}).click(); await expect(page.getByRole('button').filter({hasText:title})).toBeVisible();
 expect(await page.evaluate(()=>Boolean((window as any).bad))).toBe(false);
 const otherContext=await browser.newContext({baseURL:process.env['DESK_URL']||'http://localhost:8098'}); const other=await otherContext.newPage();
 const outsider=await login(other,'outsider'); const denied=await other.request.get('/api/requests/'+item.id,{headers:{Authorization:outsider}});expect(denied.status()).toBe(404);
 await expect(other.getByText(title,{exact:true})).toHaveCount(0);await otherContext.close();
 await page.screenshot({path:process.env['DESK_SCREENSHOT']||'test-results/service-desk.png',fullPage:true});
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
