import { strict as assert } from 'node:assert';
import { TestServer } from './server.js';
import puppeteer from 'puppeteer';

const POLL_INTERVAL_MILLIS = 50;

async function eventually(predicate, timeoutMillis) {
    const deadline = Date.now() + timeoutMillis;
    while (Date.now() < deadline) {
        if (await predicate()) {
            return true;
        }
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MILLIS));
    }
    return await predicate();
}

const MAP_RENDERED = 'document.querySelector("#map .leaflet-tile-pane img") !== null';
const COORDS_TEXT = `(() => {
    const text = (id) => document.querySelector('#coords-bar #' + id).textContent.trim();
    return text('block-coords') + '  ' + text('zoom-coords') + '  ' + text('geo-coords');
})()`;

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

    async function readCoords() {
        const text = await page.evaluate(COORDS_TEXT);
        const match = /^(-?\d+), (-?\d+)  (-?\d+(?:\.\d+)?)  (-?\d+(?:\.\d+)?|--)°, (-?\d+(?:\.\d+)?|--)°$/.exec(text);
        assert.ok(match, `unexpected coordinates bar content: ${JSON.stringify(text)}`);
        return {
            x: Number(match[1]),
            z: Number(match[2]),
            zoom: Number(match[3]),
            lat: match[4] === '--' ? null : Number(match[4]),
            lon: match[5] === '--' ? null : Number(match[5]),
        };
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

    it('shows the OpenStreetMap attribution in the bottom right corner', async function () {
        await openMap();
        const control = await page.evaluate(() => {
            const element = document.querySelector('.leaflet-control-attribution');
            if (!element) {
                return null;
            }
            const rect = element.getBoundingClientRect();
            return {
                text: element.textContent.trim(),
                visible: rect.width > 0 && rect.height > 0,
                bottom: window.innerHeight - rect.bottom,
                right: window.innerWidth - rect.right,
                style: {
                    backgroundColor: getComputedStyle(element).backgroundColor,
                    color: getComputedStyle(element).color,
                    fontFamily: getComputedStyle(element).fontFamily,
                },
            };
        });
        assert.ok(control, 'attribution control should be present');
        assert.ok(control.visible, 'attribution control should be visible');
        assert.ok(control.text.includes('OpenStreetMap'), 'attribution should mention OpenStreetMap');
        assert.ok(control.bottom < 10, `attribution should touch the bottom edge, got ${control.bottom}`);
        assert.ok(control.right < 10, `attribution should touch the right edge, got ${control.right}`);
        assert.equal(control.style.backgroundColor, 'rgba(38, 38, 38, 0.8)');
        assert.equal(control.style.color, 'rgb(238, 238, 238)');
        assert.equal(control.style.fontFamily, 'monospace');
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
    it('copies each coordinate group to the clipboard', async function () {
        await page.evaluateOnNewDocument(() => {
            window.clipboardWrites = [];
            navigator.clipboard.writeText = (text) => {
                window.clipboardWrites.push(text);
                return Promise.resolve();
            };
        });
        await openMap();
        assert.ok(
            await eventually(async () => {
                const coords = await readCoords();
                return coords.lat !== null && coords.lon !== null;
            }, 10_000),
            'geographic coordinates should settle',
        );
        const expected = await page.evaluate(() => ({
            'block-coords': document.getElementById('block-coords').textContent,
            'zoom-coords': document.getElementById('zoom-coords').textContent,
            'geo-coords': document.getElementById('geo-coords').textContent,
        }));
        for (const id of Object.keys(expected)) {
            const writesBefore = await page.evaluate(() => window.clipboardWrites.length);
            await page.click(`#coords-bar .copy-button[data-copy="${id}"]`);
            assert.ok(
                await eventually(async () => {
                    const writes = await page.evaluate(() => window.clipboardWrites);
                    return writes.length > writesBefore && writes.at(-1) === expected[id];
                }, 10_000),
                `clicking the ${id} copy button should write its value to the clipboard`,
            );
        }
    });

    it('shows an error banner when the configuration is invalid', async function () {
        await openMap();
        assert.equal(
            await page.evaluate(() => getComputedStyle(document.getElementById('error-banner')).display),
            'none',
            'the error banner should be hidden while the configuration is valid',
        );
        server.error = 'Could not reload the Terra-- configuration: invalid json';
        const reloaded = page.waitForFunction(
            'document.getElementById("error-banner").style.display === "block"',
            { timeout: 60_000 },
        );
        server.publish(42);
        await reloaded;
        assert.equal(
            await page.evaluate(() => document.getElementById('error-banner').textContent),
            'Could not reload the Terra-- configuration: invalid json',
        );
        server.error = null;
        server.publish(43);
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('error-banner').style.display === 'none'),
                10_000,
            ),
            'the error banner should hide again once the configuration is valid',
        );
    });

    it('restores the view from the URL hash on reload', async function () {
        await openMap();
        await page.mouse.move(-10, -10);
        await page.mouse.move(200, 200);
        await page.mouse.down();
        await page.mouse.move(400, 300, { steps: 5 });
        await page.mouse.up();
        await page.mouse.click(400, 300);
        await page.keyboard.press('-');
        const initialZoom = await page.evaluate('map.getZoom()');
        await eventually(async () => {
            const state = await page.evaluate(() => ({
                hash: location.hash,
                zoom: map.getZoom(),
            }));
            const match = /^#(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)$/.exec(state.hash);
            return (
                match !== null
                && Number(match[3]) === state.zoom
                && state.zoom < initialZoom
            );
        }, 10_000);
        const before = await page.evaluate(() => ({
            center: map.getCenter(),
            zoom: map.getZoom(),
            hash: location.hash,
        }));
        await page.reload({ waitUntil: 'load' });
        await page.waitForFunction(MAP_RENDERED, { timeout: 60_000 });
        await eventually(async () => {
            const restored = await page.evaluate(() => ({
                hash: location.hash,
                moving: map.hasLayer ? false : true,
            }));
            return restored.hash === before.hash;
        }, 10_000);
        await new Promise((resolve) => setTimeout(resolve, 300));
        const after = await page.evaluate(() => ({
            center: map.getCenter(),
            zoom: map.getZoom(),
            hash: location.hash,
        }));
        assert.equal(after.hash, before.hash, 'the URL hash should survive the reload');
        assert.equal(after.zoom, before.zoom, 'the zoom level should be restored');
        assert.ok(
            Math.abs(after.center.lat - before.center.lat) < 0.01,
            `latitude should be restored, before=${before.center.lat} after=${after.center.lat}`,
        );
        assert.ok(
            Math.abs(after.center.lng - before.center.lng) < 0.01,
            `longitude should be restored, before=${before.center.lng} after=${after.center.lng}`,
        );
    });

    it('shows an icon before each coordinate segment', async function () {
        await openMap();
        const icons = await page.evaluate(() => {
            const segments = document.querySelectorAll('#coords-bar .coord-seg');
            return [...segments].map((segment) => {
                const svg = segment.querySelector('svg');
                return svg !== null && svg.getBoundingClientRect().width > 0;
            });
        });
        assert.equal(icons.length, 3, 'the top bar should have three coordinate segments');
        assert.ok(icons.every((visible) => visible), 'each segment should display an icon');
    });

    it('shows the block coordinates of the mouse pointer', async function () {
        await openMap();
        const center = await readCoords();
        await page.mouse.move(300, 300);
        const atPointer = await readCoords();
        assert.ok(
            atPointer.x !== center.x || atPointer.z !== center.z,
            'coordinates should follow the mouse pointer',
        );
        const mapBounds = await page.evaluate(() => {
            const rect = document.getElementById('map').getBoundingClientRect();
            return { left: rect.left, top: rect.top, width: rect.width, height: rect.height };
        });
        await page.mouse.move(mapBounds.left + mapBounds.width / 2, mapBounds.top + mapBounds.height / 2);
        assert.ok(
            await eventually(async () => {
                const coords = await readCoords();
                return coords.x === center.x && coords.z === center.z;
            }, 10_000),
            'coordinates should return to the view center',
        );
    });
    it('shows the view center coordinates when the pointer leaves the map', async function () {
        await openMap();
        const center = await readCoords();
        await page.mouse.move(10, 10);
        assert.ok(
            await eventually(async () => {
                const coords = await readCoords();
                return coords.x !== center.x || coords.z !== center.z;
            }, 10_000),
            'coordinates should follow the mouse pointer',
        );
        await page.mouse.move(-10, -10);
        assert.ok(
            await eventually(async () => {
                const coords = await readCoords();
                return coords.x === center.x && coords.z === center.z;
            }, 10_000),
            'coordinates should fall back to the view center',
        );
    });
    it('shows the latitude and longitude of the mouse pointer', async function () {
        await openMap();
        assert.ok(
            await eventually(async () => {
                const coords = await readCoords();
                return coords.lat !== null && coords.lon !== null;
            }, 10_000),
            'latitude and longitude should appear in the top bar',
        );
        const center = await readCoords();
        assert.ok(Math.abs(center.lat - center.z / 1000) < 0.0001, 'latitude should match the block z coordinate');
        assert.ok(Math.abs(center.lon - center.x / 1000) < 0.0001, 'longitude should match the block x coordinate');
    });

    it('never sends more than one geo request per second', async function () {
        await openMap();
        server.geoRequests.length = 0;
        const deadline = Date.now() + 5000;
        while (Date.now() < deadline) {
            await page.mouse.move(20 + (Date.now() % 300), 20 + ((Date.now() / 2) % 300), { steps: 2 });
            await new Promise((resolve) => setTimeout(resolve, 100));
        }
        await new Promise((resolve) => setTimeout(resolve, 1500));
        const requests = server.geoRequests;
        assert.ok(requests.length > 1, 'geo requests should keep flowing while the pointer moves');
        for (let i = 1; i < requests.length; i++) {
            assert.ok(
                requests[i].at - requests[i - 1].at >= 900,
                `geo requests ${i - 1} and ${i} were only ${requests[i].at - requests[i - 1].at}ms apart`,
            );
        }
    });

    it('keeps the top bar stable while the mouse is idle', async function () {
        await openMap();
        const settle = await eventually(async () => {
            const coords = await readCoords();
            return coords.lat !== null && coords.lon !== null;
        }, 10_000);
        assert.ok(settle, 'geographic coordinates should settle');
        const text = await page.evaluate(COORDS_TEXT);
        await new Promise((resolve) => setTimeout(resolve, 3000));
        const textAfter = await page.evaluate(COORDS_TEXT);
        assert.equal(textAfter, text);
    });

    it('shows an offline banner while the server is unreachable', async function () {
        await openMap();
        assert.equal(
            await page.evaluate(() => document.getElementById('offline-banner').style.display),
            'none',
            'the offline banner should be hidden while the server is reachable',
        );
        server.offline = true;
        for (const response of server.clients) {
            response.destroy();
        }
        server.clients.clear();
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('offline-banner').style.display === 'block'),
                10_000,
            ),
            'the offline banner should appear once the server connection drops',
        );
        server.offline = false;
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('offline-banner').style.display === 'none'),
                20_000,
            ),
            'the offline banner should hide once the server is reachable again',
        );
    });

    it('shows the map zoom level and updates it when zooming', async function () {
        await openMap();
        const initial = await readCoords();
        assert.equal(initial.zoom, 3);
        await page.mouse.click(400, 300);
        await page.keyboard.press('-');
        assert.ok(
            await eventually(async () => (await readCoords()).zoom < initial.zoom, 10_000),
            'zoom level should decrease when zooming out',
        );
        const zoomedOut = await readCoords();
        await page.keyboard.press('+');
        assert.ok(
            await eventually(async () => (await readCoords()).zoom > zoomedOut.zoom, 10_000),
            'zoom level should increase when zooming in',
        );
    });

    it('shows a loading indicator while tiles load and hides it when done', async function () {
        server.tileDelayMillis = 1000;
        await retry(() => page.goto(server.url, { waitUntil: 'domcontentloaded', timeout: 60_000 }), 3);
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('loading-indicator').style.display === 'block'),
                30_000,
            ),
            'the loading indicator should be visible while tiles are pending',
        );
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('loading-indicator').style.display === 'none'),
                30_000,
            ),
            'the loading indicator should hide once all tiles have loaded',
        );
        assert.ok(
            await page.$$eval('#map .leaflet-tile-pane img', (tiles) =>
                tiles.filter((tile) => tile.complete && tile.naturalWidth > 0).length > 0,
            ),
            'tiles should be loaded once the loading indicator hides',
        );
    });

    it('shows a loading indicator while a geocoding request is pending', async function () {
        await openMap();
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('loading-indicator').style.display === 'none'),
                30_000,
            ),
            'the loading indicator should start hidden once the initial state settles',
        );
        server.geoDelayMillis = 1500;
        await page.mouse.move(300, 300);
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('loading-indicator').style.display === 'block'),
                30_000,
            ),
            'the loading indicator should be visible while a geocoding request is pending',
        );
        assert.ok(
            await eventually(
                () => page.evaluate(() => document.getElementById('loading-indicator').style.display === 'none'),
                30_000,
            ),
            'the loading indicator should hide once the geocoding request completes',
        );
    });


    describe('search', function () {
        async function type(query, { append = false } = {}) {
            if (!append) {
                await page.evaluate(() => {
                    const input = document.getElementById('search-input');
                    input.value = '';
                });
            }
            for (const character of query) {
                await page.evaluate((char) => {
                    const input = document.getElementById('search-input');
                    input.value = input.value + char;
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                }, character);
            }
        }
        async function results() {
            return await page.$$eval('#search-results li', (items) =>
                items.map((item) => ({
                    text: item.textContent,
                    lon: Number(item.dataset.lon),
                    lat: Number(item.dataset.lat),
                })),
            );
        }
        it('shows matching places while typing', async function () {
            await openMap();
            await type('par');
            assert.ok(
                await eventually(async () => (await results()).length === 1, 10_000),
                'searching for par should find Paris',
            );
            const paris = (await results())[0];
            assert.equal(paris.lon, 2.3522);
            assert.equal(paris.lat, 48.8566);
            assert.ok(paris.text.includes('Paris'), 'result should be named Paris');
            assert.ok(paris.text.includes('city'), 'result should mention its kind');
            await type('xyz', { append: true });
            assert.ok(
                await eventually(async () => (await results()).length === 0, 10_000),
                'no known place matches parxyz',
            );
        });
        it('debounces search requests while typing', async function () {
            await openMap();
            await type('paris');
            await new Promise((resolve) => setTimeout(resolve, 1000));
            const requests = server.searchRequests.map((request) => request.query);
            assert.deepEqual(requests, ['paris'], 'only one request should be sent after typing stops');
        });
        it('teleports to the clicked result and marks it', async function () {
            await openMap();
            await type('par');
            assert.ok(
                await eventually(async () => (await results()).length === 1, 10_000),
                'searching for par should find Paris',
            );
            await page.evaluate(() => {
                const item = document.querySelector('#search-results li');
                item.scrollIntoView({ block: 'center' });
                item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
            });
            assert.ok(
                await eventually(async () => server.locateRequests.length > 0, 10_000),
                'clicking a result should locate the place',
            );
            const center = await page.evaluate(() => map.getCenter());
            assert.ok(
                Math.abs(center.lat - 48.8566) < 0.01 && Math.abs(center.lng - 2.3522) < 0.01,
                `the map should teleport to Paris, got ${JSON.stringify(center)}`,
            );
            const marker = await page.evaluate(() => {
                const circle = document.querySelector('#map path.leaflet-interactive');
                return circle !== null;
            });
            assert.ok(marker, 'a marker should be added on the map');
            assert.equal(server.locateRequests.length, 1, 'exactly one locate request should be sent');
            assert.ok(
                await page.evaluate(() =>
                    document.querySelector('.leaflet-control-attribution').textContent.includes('Photon')),
                'the attribution should credit Photon after a search',
            );
        });
        it('shows no results while the query is too short', async function () {
            await openMap();
            await type('p');
            await new Promise((resolve) => setTimeout(resolve, 500));
            assert.equal(server.searchRequests.length, 0, 'no request should be sent for a single character');
            assert.ok(
                await page.evaluate(() => document.getElementById('search-results').classList.contains('hidden')),
                'the results list should stay hidden',
            );
        });
        it('hides the results when clicking elsewhere on the map', async function () {
            await openMap();
            await type('par');
            assert.ok(
                await eventually(async () => !(await page.evaluate(() =>
                    document.getElementById('search-results').classList.contains('hidden'))), 10_000),
                'results should be visible after a search',
            );
            await page.mouse.click(400, 300);
            assert.ok(
                await eventually(async () => page.evaluate(() =>
                    document.getElementById('search-results').classList.contains('hidden')), 10_000),
                'results should be hidden after clicking the map',
            );
        });
    });
    it('updates the view center coordinates while panning without a pointer', async function () {
        await openMap();
        const initial = await readCoords();
        await page.mouse.move(-10, -10);
        await page.mouse.move(200, 200);
        await page.mouse.down();
        await page.mouse.move(300, 250, { steps: 5 });
        await page.mouse.up();
        const after = await readCoords();
        assert.ok(
            after.x !== initial.x || after.z !== initial.z,
            'coordinates should update to the new view center after panning',
        );
    });
});
