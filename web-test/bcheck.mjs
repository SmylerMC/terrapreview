import { TestServer } from './server.js';
import puppeteer from 'puppeteer';
import { ensureChromeLibs } from './chrome-libs.mjs';
const libDirs = ensureChromeLibs('/home/appuser/.cache/puppeteer/chrome/linux-155.0.8059.39/chrome-linux64/chrome') ?? [];
if (libDirs.length) process.env.LD_LIBRARY_PATH = libDirs.join(':');
try {
  const server = new TestServer();
  await server.listen();
  const browser = await puppeteer.launch({ args: ['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage'], protocolTimeout: 120000 });
  const page = await browser.newPage();
  await page.goto(server.url, { waitUntil: 'load', timeout: 60000 });
  await page.waitForFunction('document.querySelector("#map .leaflet-tile-pane img") !== null', { timeout: 60000 });
  console.log('ALL OK');
  await browser.close();
  await server.close();
} catch (e) {
  console.log('ERROR:', e.message.slice(0, 200));
}
