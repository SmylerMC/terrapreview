import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src', 'main', 'resources', 'web');

const TILE_SIZE = 256;
const CHUNK_SIZE = 16;
const MIN_ZOOM = -5;
const MAX_ZOOM = 3;
const CENTER_LAT = TILE_SIZE / 2;
const CENTER_LNG = TILE_SIZE / 2;

const PIXEL = Buffer.from([
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
    0xde, 0x00, 0x00, 0x00, 0x0c, 0x49, 0x44, 0x41,
    0x54, 0x08, 0xd7, 0x63, 0xf8, 0xcf, 0xc0, 0x00,
    0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xdd, 0x8d,
    0xb0, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e,
    0x44, 0xae, 0x42, 0x60, 0x82,
]);

export class TestServer {
    constructor() {
        this.searchResults = [
            {
                geometry: { coordinates: [2.3522, 48.8566] },
                properties: { name: 'Paris', city: 'Paris', state: 'Île-de-France', country: 'France', osm_value: 'city' },
            },
            {
                geometry: { coordinates: [-0.1276, 51.5072] },
                properties: { name: 'London', state: 'England', country: 'United Kingdom', osm_value: 'city' },
            },
            {
                geometry: { coordinates: [4.8357, 45.764] },
                properties: { name: 'Lyon', state: 'Auvergne-Rhône-Alpes', country: 'France', osm_value: 'city' },
            },
        ];
        this.tileRequests = [];
        this.geoRequests = [];
        this.error = null;
        this.tileDelayMillis = 0;
        this.geoDelayMillis = 0;
        this.searchRequests = [];
        this.locateRequests = [];
        this.clients = new Set();
        this.offline = false;
        this.server = http.createServer((request, response) => this.handle(request, response));
    }

    listen() {
        return new Promise((resolve) => this.server.listen(0, '127.0.0.1', () => resolve(this.port)));
    }

    get port() {
        return this.server.address().port;
    }

    get url() {
        return `http://127.0.0.1:${this.port}`;
    }

    async close() {
        for (const response of this.clients) {
            response.end();
        }
        await new Promise((resolve) => this.server.close(resolve));
    }

    publish(version) {
        for (const response of this.clients) {
            response.write(`data: ${version}\n\n`);
        }
    }

    async page() {
        const template = await readFile(path.join(root, 'index.html'), 'utf8');
        return template
            .replaceAll('@TILE_SIZE@', String(TILE_SIZE))
            .replaceAll('@CHUNK_SIZE@', String(CHUNK_SIZE))
            .replaceAll('@MIN_ZOOM@', String(MIN_ZOOM))
            .replaceAll('@MAX_ZOOM@', String(MAX_ZOOM))
            .replaceAll('@CENTER_LAT@', String(CENTER_LAT))
            .replaceAll('@CENTER_LNG@', String(CENTER_LNG))
            .replaceAll('@GEOCODER_NAME@', 'Photon')
            .replaceAll('@GEOCODER_URL@', this.url);
    }

    async handle(request, response) {
        if (this.offline) {
            response.writeHead(503);
            response.end();
            return;
        }
        const url = new URL(request.url, this.url);
        if (url.pathname === '/tiles' || url.pathname.startsWith('/tiles/')) {
            this.tileRequests.push({ path: url.pathname, version: url.searchParams.get('v') });
            setTimeout(() => {
                response.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' });
                response.end(PIXEL);
            }, this.tileDelayMillis);
            return;
        }
        if (url.pathname === '/geo' || url.pathname.startsWith('/geo/')) {
            const match = /^\/geo\/(-?\d+(?:\.\d+)?)\/(-?\d+(?:\.\d+)?)$/.exec(url.pathname);
            if (!match) {
                response.writeHead(404);
                response.end();
                return;
            }
            const x = Number(match[1]);
            const z = Number(match[2]);
            this.geoRequests.push({ at: Date.now(), path: url.pathname });
            setTimeout(() => {
                const body = JSON.stringify({ longitude: x / 1000, latitude: z / 1000 });
                response.writeHead(200, { 'Content-Type': 'application/json' });
                response.end(body);
            }, this.geoDelayMillis);
            return;
        }
        if (url.pathname === '/error') {
            response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
            response.end(JSON.stringify(this.error));
            return;
        }
        if (url.pathname === '/api/') {
            const query = url.searchParams.get('q') ?? '';
            this.searchRequests.push({ at: Date.now(), query });
            const features = this.searchResults.filter((place) =>
                place.properties.name.toLowerCase().includes(query.toLowerCase().trim()));
            response.writeHead(200, { 'Content-Type': 'application/json' });
            response.end(JSON.stringify({ features }));
            return;
        }
        if (url.pathname === '/locate' || url.pathname.startsWith('/locate/')) {
            const match = /^\/locate\/(-?\d+(?:\.\d+)?)\/(-?\d+(?:\.\d+)?)$/.exec(url.pathname);
            if (!match) {
                response.writeHead(404);
                response.end();
                return;
            }
            const longitude = Number(match[1]);
            const latitude = Number(match[2]);
            this.locateRequests.push({ at: Date.now(), longitude, latitude });
            const scale = this.locateScale ?? CHUNK_SIZE / TILE_SIZE;
            const body = JSON.stringify({ x: longitude * scale, z: latitude * scale });
            response.writeHead(200, { 'Content-Type': 'application/json' });
            response.end(body);
            return;
        }
        if (url.pathname === '/events') {
            response.writeHead(200, { 'Content-Type': 'text/event-stream' });
            response.write('retry: 1000\n\n');
            this.clients.add(response);
            request.on('close', () => this.clients.delete(response));
            return;
        }
        if (url.pathname === '/') {
            response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            response.end(await this.page());
            return;
        }
        response.writeHead(404);
        response.end();
    }
}
