import { strict as assert } from 'node:assert';
import { TestServer } from './server.js';
import puppeteer from 'puppeteer';

const POLL_INTERVAL_MILLIS = 50;

async function eventually(predicate, timeoutMillis) {
    const deadline = Date.now() + timeoutMillis;
    while (Date.now() < deadline) {
        if (predicate()) {
            return true;
        }
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MILLIS));
    }
    return predicate();
}

const MAP_RENDERED = 'document.querySelector("#map .leaflet-tile-pane img") !== null';

describe('TerraPreview web UI', function () {
    this.timeout(60_000);

    let server;
    let browser;
    let page;

    beforeEach(async function () {
        server = new TestServer();
        await server.listen();
        browser = await puppeteer.launch({
            args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
        });
        page = await browser.newPage();
    });

    afterEach(async function () {
        if (browser) {
            await browser.close();
        }
        if (server) {
            await server.close();
        }
    });

    async function openMap() {
        await retry(() => page.goto(server.url, { waitUntil: 'load', timeout: 60_000 }), 3);
        await page.waitForFunction(MAP_RENDERED, { timeout: 60_000 });
    }

    async function retry(action, attempts) {
        for (let i = 1; ; i++) {
            try {
                await action();
                return;
            } catch (error) {
                if (i >= attempts) {
                    throw error;
                }
            }
        }
    }

    it('renders a leaflet map with chunk tiles', async function () {
        await openMap();
        const tiles = await page.$$eval('#map .leaflet-tile-pane img', (tiles) =>
            tiles.filter((tile) => tile.complete && tile.naturalWidth > 0),
        );
        assert.ok(tiles.length > 0, 'map should display loaded tiles');
        assert.ok(
            server.tileRequests.every((request) => /^\/tiles\/-?\d+\/-?\d+\/-?\d+\.png$/.test(request.path)),
            'tiles should be requested from the chunk tile endpoint',
        );
    });

    it('loads no script errors', async function () {
        const errors = [];
        page.on('pageerror', (error) => errors.push(error));
        await openMap();
        assert.deepEqual(errors, []);
    });

    it('requests tiles with the current version after an event', async function () {
        await openMap();
        assert.equal(server.tileRequests[0].version, '0');
        const reloaded = page.waitForResponse(
            (response) => response.url().includes('.png?v=42'),
            { timeout: 60_000 },
        );
        server.publish(42);
        await reloaded;
        assert.equal(server.tileRequests.at(-1).version, '42');
    });

    it('reloads tiles again on a second event', async function () {
        await openMap();
        const first = page.waitForResponse(
            (response) => response.url().includes('.png?v=1'),
            { timeout: 60_000 },
        );
        server.publish(1);
        await first;
        assert.ok(server.tileRequests.some((request) => request.version === '1'));
        server.publish(2);
        assert.ok(
            await eventually(() => server.tileRequests.some((request) => request.version === '2'), 10_000),
            'no tiles were requested with the second version',
        );
    });
});
