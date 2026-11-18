/* Executes the actual TeaVM application, not a mock replacement. Only remote account/advert APIs are offline. */
const { chromium } = require('playwright');
const http = require('http'), fs = require('fs'), path = require('path'), assert = require('assert/strict');
const root=path.resolve('webapp/build/play'), out=path.resolve('web-runtime-results');
fs.mkdirSync(out,{recursive:true});
let pemRequests=0;
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.woff2':'font/woff2','.webp':'image/webp','.png':'image/png','.mp3':'audio/mpeg','.webmanifest':'application/manifest+json'};
const server=http.createServer((req,res)=>{
 const route=decodeURIComponent(req.url.split('?')[0]);
 if(/\.(pem|sql|log|md|bak)$/.test(route)){pemRequests++;res.writeHead(403);return res.end('Protected by hosting rules');}
 const file=path.resolve(root,'.'+(route==='/'?'/index.html':route));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||fs.statSync(file).isDirectory()){res.writeHead(404);return res.end();}
 res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');
 fs.createReadStream(file).pipe(res);
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,...(process.env.DIVAN_TEST_CHROMIUM?{executablePath:process.env.DIVAN_TEST_CHROMIUM}:{}),args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
 const page=await context.newPage(), errors=[];
 page.on('pageerror',e=>errors.push(String(e)));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
 // A stale generated config may still list the Android certificate: boot must skip it defensively.
 await page.route(base+'/**',async route=>{
  if(route.request().resourceType()!=='document')return route.continue();
  const response=await route.fetch();const text=await response.text();
  const patched=text.replace(/("assets"\s*:\s*\[)/,'$1"certs/isrg-root-x2.pem",');
  await route.fulfill({response,body:patched});
 });
 await page.route('https://**/*',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify({ok:true,data:{},server_time:Math.floor(Date.now()/1000)})}));
 async function ready(){
  await page.waitForFunction(()=>window.DH&&DH.boot&&DH.boot.assets&& !document.querySelector('#loading')&&!document.querySelector('#crash.on'),{},{timeout:90000});
  assert.equal(await page.locator('#crash.on').count(),0);
  assert.equal(await page.evaluate(()=>{const a=DH.ctx.getImageData(0,0,document.querySelector('#screen').width,document.querySelector('#screen').height).data;return new Set(Array.from(a).filter((_,i)=>i%4===0)).size>4}),true,'Shared-code app really rendered, not just a blank canvas');
 }
 try {
  await page.goto(base);await ready();
  const cfg=await page.evaluate(()=>DIVAN_CONFIG);
  assert.equal(cfg.versionName,'5.6.2');assert.equal(pemRequests,0,'no request for protected Android certificate');
  // Persist representative existing game data through the actual host storage, reload it, then boot offline.
  await page.evaluate(()=>{DH.saveFile('/data/data/com.meysam.divanemtiaz.site/files/existing-game.json',new Int8Array([7,11,33,99]),12345);DH.savePrefs('existing-user-settings',JSON.stringify({score11:33,preset:4}));});
  let stored=false;
  for(let attempt=0;attempt<50&&!stored;attempt++){
   stored=await page.evaluate(()=>new Promise(resolve=>{
    const q=indexedDB.open('divan-emtiaz',1);
    q.onsuccess=()=>{const d=q.result,t=d.transaction(['files','prefs']);let file=false,prefs=false;
     const f=t.objectStore('files').get('/data/data/com.meysam.divanemtiaz.site/files/existing-game.json');f.onsuccess=()=>file=!!f.result;
     const p=t.objectStore('prefs').get('existing-user-settings');p.onsuccess=()=>prefs=!!p.result;
     t.oncomplete=()=>{d.close();resolve(file&&prefs)};
    };
   }));
   if(!stored)await new Promise(resolve=>setTimeout(resolve,100));
  }
  assert.equal(stored,true,'both file and preference transactions committed before navigation');
  await page.reload();await ready();
  const savedFile=await page.evaluate(()=>{const f=DH.boot.files.find(x=>x[0].endsWith('existing-game.json'));return f?Array.from(f[1]):null});
  assert.deepEqual(savedFile,[7,11,33,99],'committed existing game bytes are restored at next application boot');
  assert.equal(await page.evaluate(()=>DH.boot.prefs['existing-user-settings']),'{"score11":33,"preset":4}');
  await page.waitForFunction(()=>navigator.serviceWorker.controller!==null,{},{timeout:30000});
  await context.setOffline(true);await page.reload();await ready();
  assert.equal(pemRequests,0);assert.deepEqual(errors,[],'no JavaScript or shared-code runtime errors');
  await page.screenshot({path:path.join(out,'web-boot-offline.png')});
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({version:cfg.versionName,checks:['actual TeaVM application boot','stale config ignores protected PEM','canvas rendered','stored game survives reload','stored preferences survive reload','service worker offline boot','no JavaScript runtime errors'],pemRequests},null,2));
  console.log('PASS: real PWA boot, protected PEM, existing game/settings retained, offline reload.');
 }catch(e){await page.screenshot({path:path.join(out,'failure.png')}).catch(()=>{});fs.writeFileSync(path.join(out,'failure.txt'),String(e)+'\n'+errors.join('\n'));fs.writeFileSync(path.join(out,'storage-state.json'),JSON.stringify(await page.evaluate(()=>({url:location.href,files:(DH.boot&&DH.boot.files||[]).map(x=>x[0]),prefs:Object.keys(DH.boot&&DH.boot.prefs||{})})).catch(()=>({})),null,2));throw e;}
 finally {await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
