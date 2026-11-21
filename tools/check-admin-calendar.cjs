const assert = require('node:assert/strict')
const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const {chromium} = require('playwright')

const assets = path.resolve(__dirname, '../server/public/assets')
const fixture = `<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><form>
  <label for="start">شروع ارسال</label><input id="start" name="send_at" type="datetime-local" value="2025-03-20T23:55">
  <label for="day">تاریخ گزارش</label><input id="day" name="day" type="date" value="2025-03-21" required>
  <script src="/assets/js/admin-calendar.js"></script>
</form></html>`
const server = http.createServer((req, res) => {
    if (req.url === '/admin/index.php') {
        res.writeHead(200, {'content-type':'text/html; charset=utf-8'})
        res.end(fixture)
    } else if (req.url === '/assets/js/admin-calendar.js' || req.url.startsWith('/assets/calendar/iran-years.json')) {
        const filename = req.url.split('?')[0].slice('/assets/'.length)
        fs.createReadStream(path.join(assets,filename)).pipe(res)
    } else res.writeHead(404).end()
})
;(async () => {
    await new Promise(resolve => server.listen(0,'127.0.0.1',resolve))
    let browser
    try {
        browser = await chromium.launch({headless:true})
        const page = await browser.newPage({viewport:{width:360,height:640}})
        const errors=[]
        page.on('pageerror', e=>errors.push(e.message))
        await page.goto(`http://127.0.0.1:${server.address().port}/admin/index.php`)
        await page.getByRole('button',{name:'شروع ارسال، ۳۰ اسفند ۱۴۰۳، ساعت ۲۳:۵۵'}).click()
        const picker=page.getByRole('dialog',{name:'تقویم شمسی'})
        assert.equal(await picker.getByRole('button',{name:'۳۰ اسفند'}).count(),1,'leap day exists')
        await picker.getByLabel('سال',{exact:true}).selectOption('1404')
        assert.equal(await picker.getByRole('button',{name:'۳۰ اسفند'}).count(),0,'the next year is not leap')
        await picker.getByLabel('ماه',{exact:true}).selectOption('1')
        await picker.getByRole('button',{name:'۱ فروردین',exact:true}).click()
        await picker.getByLabel('ساعت تهران',{exact:true}).selectOption('9')
        await picker.getByLabel('دقیقه',{exact:true}).selectOption('5')
        await picker.getByRole('button',{name:'تأیید',exact:true}).click()
        assert.equal(await page.locator('#start').inputValue(),'2025-03-21T09:05')
        await page.getByRole('button',{name:'شروع ارسال، ۱ فروردین ۱۴۰۴، ساعت ۰۹:۰۵'}).click()
        await picker.getByRole('button',{name:'پاک کردن'}).click()
        assert.equal(await page.locator('#start').inputValue(),'')
        await page.getByRole('button',{name:'تاریخ گزارش، ۱ فروردین ۱۴۰۴'}).click()
        assert.equal(await picker.getByRole('button',{name:'پاک کردن'}).count(),0,'required date cannot be cleared')
        await picker.getByRole('button',{name:'انصراف'}).click()
        assert.equal(await page.locator('#day').inputValue(),'2025-03-21')
        assert.deepEqual(errors,[])
        console.log('PASS: mobile web Persian picker, leap day, date-only, Tehran time, clear and ISO form values.')
    } finally {
        await browser?.close()
        server.close()
    }
})().catch(e=>{console.error(e);process.exitCode=1})
