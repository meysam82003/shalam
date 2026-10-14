// Divan Emtiaz for Windows: the web version (the Android app's own code compiled to JavaScript) in its own window.
// Game data stays in this app's profile (IndexedDB under %APPDATA%), kept across updates and uninstall/reinstall.
'use strict';
const { app, BrowserWindow, protocol, net, shell, session } = require('electron');
const path = require('path');
const { pathToFileURL } = require('url');

const ROOT = path.join(__dirname, 'play');
const ORIGIN = 'app://divan';
const API_HOSTS = ['https://httpuser87890.ir/*', 'https://www.httpuser87890.ir/*'];

protocol.registerSchemesAsPrivileged([{ scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }]);

if (!app.requestSingleInstanceLock()) app.quit();

let win = null;

function createWindow() {
  win = new BrowserWindow({
    width: 1366, height: 820, minWidth: 900, minHeight: 600, backgroundColor: '#050B1A', title: 'دیوان امتیاز',
    icon: path.join(__dirname, 'play', 'icons', 'icon-512.png'), autoHideMenuBar: true, show: false,
    webPreferences: { contextIsolation: true, sandbox: true, preload: path.join(__dirname, 'preload.js'), spellcheck: false }
  });
  win.removeMenu();
  // Wide and filling the screen; F11 switches to true full screen (Esc leaves it).
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') { e.preventDefault(); win.setFullScreen(!win.isFullScreen()); }
    else if (input.key === 'Escape' && win.isFullScreen()) { win.setFullScreen(false); }
  });
  // Payment: the gateway opens in a separate in-app window (no address bar); other links go to the system browser.
  win.webContents.setWindowOpenHandler(({ url, frameName }) => {
    if (frameName === 'divan-pay' && /^https:\/\//i.test(url)) {
      return { action: 'allow', overrideBrowserWindowOptions: { width: 520, height: 860, parent: win, modal: false, autoHideMenuBar: true, title: 'پرداخت امن',
        backgroundColor: '#050B1A', webPreferences: { sandbox: true, contextIsolation: true } } };
    }
    if (/^(https?|mailto|tel):/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('did-create-window', (child) => {
    child.removeMenu();
    child.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/i.test(url)) shell.openExternal(url); return { action: 'deny' }; });
    child.webContents.on('will-navigate', (e, url) => { if (/^divan:/i.test(url)) { e.preventDefault(); child.close(); } });
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (!url.startsWith(ORIGIN + '/')) { e.preventDefault(); if (/^(https?|mailto|tel):/i.test(url)) shell.openExternal(url); }
  });
  win.loadURL(ORIGIN + '/index.html');
}

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

app.whenReady().then(() => {
  // The app's files, served from inside the package.
  protocol.handle('app', (request) => {
    const u = new URL(request.url);
    let p = decodeURIComponent(u.pathname);
    if (p === '/' || p === '') p = '/index.html';
    const file = path.normalize(path.join(ROOT, p));
    if (!file.startsWith(ROOT)) return new Response('Forbidden', { status: 403 });
    return net.fetch(pathToFileURL(file).toString());
  });
  // The Divan server answers the site's own origin; this window's origin (app://divan) is added for the API.
  session.defaultSession.webRequest.onHeadersReceived({ urls: API_HOSTS }, (d, cb) => {
    const h = Object.assign({}, d.responseHeaders);
    for (const k of Object.keys(h)) if (k.toLowerCase().startsWith('access-control-')) delete h[k];
    h['Access-Control-Allow-Origin'] = [ORIGIN];
    h['Access-Control-Allow-Headers'] = ['Authorization, Content-Type, Accept, X-Requested-With'];
    h['Access-Control-Allow-Methods'] = ['GET, POST, OPTIONS'];
    h['Access-Control-Max-Age'] = ['600'];
    if (d.method === 'OPTIONS') cb({ responseHeaders: h, statusLine: 'HTTP/1.1 204 No Content' });
    else cb({ responseHeaders: h });
  });
  session.defaultSession.setPermissionRequestHandler((wc, permission, done) => {
    done(['media', 'notifications', 'clipboard-sanitized-write', 'fullscreen'].includes(permission));
  });
  createWindow();
});

app.on('window-all-closed', () => app.quit());
