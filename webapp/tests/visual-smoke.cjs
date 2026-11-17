/* Smoke tests exercise the production TeaVM output of the native screens; no app test hooks or paid-access overrides. */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const http = require('http');
const assert = require('assert/strict');
const root = path.resolve(__dirname, '../build/play');
const out = path.resolve(__dirname, '../build/ui-review');
const types = {'.js':'text/javascript','.css':'text/css','.html':'text/html','.woff2':'font/woff2','.png':'image/png','.json':'application/json','.mp3':'audio/mpeg'};
const server = http.createServer((req,res)=>{
  const name = decodeURIComponent(req.url.split('?')[0]);
  if(name==='/seed'){res.setHeader('content-type','text/html');res.end('<!doctype html><title>Seed</title>');return;}
  const file = path.resolve(root, '.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.setHeader('content-type',types[path.extname(file)]||'application/octet-stream');
  res.end(fs.readFileSync(file));
});
const profiles=['cobalt','iranic','neon','emerald','copper','ivory'];
(async()=>{
  fs.mkdirSync(out,{recursive:true});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const origin=`http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});
  const report=[];
  try {
    for(const theme of profiles){
      const context = await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1,serviceWorkers:'block'});
      await context.route('**/*',r=>r.request().url().startsWith(origin)?r.continue():r.abort());
      const page=await context.newPage(); const errors=[];
      page.on('pageerror',e=>errors.push(String(e)));
      await page.addInitScript(()=>{
        window.__labels=[];
        const fill=CanvasRenderingContext2D.prototype.fillText;
        CanvasRenderingContext2D.prototype.fillText=function(text,x,y,...rest){
          if(this.canvas.id==='screen'){
            const m=this.getTransform(),b=this.canvas.getBoundingClientRect();
            const dx=m.a*x+m.c*y+m.e,dy=m.b*x+m.d*y+m.f;
            window.__labels.push({text:String(text),x:b.left+dx*b.width/this.canvas.width,y:b.top+dy*b.height/this.canvas.height-6,time:performance.now()});
            if(window.__labels.length>10000)window.__labels.splice(0,5000);
          }
          return fill.call(this,text,x,y,...rest);
        };
      });
      await page.goto(origin+'/seed');
      await page.evaluate(theme=>new Promise((resolve,reject)=>{
        const r=indexedDB.open('divan-emtiaz',1);
        r.onupgradeneeded=()=>{r.result.createObjectStore('files');r.result.createObjectStore('prefs')};
        r.onerror=()=>reject(r.error);
        r.onsuccess=()=>{
          const db=r.result,tx=db.transaction('prefs','readwrite'),store=tx.objectStore('prefs');
          store.put(JSON.stringify({settings:['s',JSON.stringify({general:{theme,animations:false,haptic:false}})]}),'divan_emtiaz_v3');
          store.put(JSON.stringify({asked:['b',true]}),'divan_notices');
          tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>reject(tx.error);
        };
      }),theme);
      const labels=()=>page.evaluate(()=>window.__labels.filter(x=>x.x>0&&x.x<innerWidth&&x.y>0&&x.y<innerHeight).slice(-300).map(x=>x.text));
      async function click(text){
        const pos=await page.evaluate(text=>[...window.__labels].reverse().find(x=>x.text===text&&x.x>5&&x.x<innerWidth-5&&x.y>5&&x.y<innerHeight-5),text);
        assert(pos,`${theme}: label missing: ${text}; ${await labels()}`);
        await page.mouse.click(pos.x,pos.y);await page.waitForTimeout(450);
      }
      async function shot(name){assert.equal(await page.locator('#crash.on').count(),0,`${theme}: crash`);await page.screenshot({path:path.join(out,`${theme}-${name}.png`)});}
      await page.goto(origin);await page.locator('#loading').waitFor({state:'detached',timeout:30000});
      await page.waitForTimeout(1000);
      await shot('home');
      assert((await labels()).includes('انتخاب بازی'),theme+' home missing');
      await click('شلم');await shot('setup');
      await click('شروع داوری شلم');await shot('board');
      await click('ثبت دست جدید');await shot('score-entry');
      // In-app back preserves the running game and its settings.
      await page.keyboard.press('Escape');await page.waitForTimeout(400);
      await page.keyboard.press('Escape');await page.waitForTimeout(400);
      if (theme === 'neon') {
        // Orbit deliberately labels only the active tab; settings keeps the same fifth tab and hit area.
        await page.mouse.click(49, 794); await page.waitForTimeout(450);
      } else await click('تنظیمات');
      await shot('settings');
      await page.mouse.move(190,500);await page.mouse.wheel(0,550);await page.waitForTimeout(400);
      await shot('settings-controls');
      assert.deepEqual(errors,[],`${theme}: browser exceptions`);
      await page.reload();await page.waitForTimeout(2200);
      assert.equal(await page.locator('#crash.on').count(),0);
      report.push({theme,viewport:'390x844',screens:['home','setup','board','score-entry','settings','settings-controls'],errors});
      await context.close();
    }
    fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));
    console.log('UI smoke passed: six styles, 36 screen captures, game creation and reload.');
  } finally {await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);server.close();process.exitCode=1});
