import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ensureChromeLibs } from './chrome-libs.mjs';

const webTestDir = dirname(fileURLToPath(import.meta.url));

function run(command, args, env = {}) {
    const result = spawnSync(command, args, {
        cwd: webTestDir,
        env: { ...process.env, ...env },
        stdio: 'inherit',
        shell: process.platform === 'win32',
    });
    if (result.status !== 0) {
        process.exit(result.status ?? 1);
    }
}

if (!existsSync(join(webTestDir, 'node_modules'))) {
    console.log('Installing browser test dependencies...');
    run('npm', ['ci', '--no-fund', '--no-audit']);
}

if (!process.env.PUPPETEER_EXECUTABLE_PATH) {
    console.log('Ensuring Chrome is available to Puppeteer...');
    run('npx', ['puppeteer', 'browsers', 'install', 'chrome']);
}

if (process.env.CHROME_LIBS_DIR && existsSync(process.env.CHROME_LIBS_DIR)) {
    process.env.LD_LIBRARY_PATH =
        `${process.env.CHROME_LIBS_DIR}${process.env.LD_LIBRARY_PATH ? `:${process.env.LD_LIBRARY_PATH}` : ''}`;
}

const chromePath = await resolveChromePath();
if (chromePath) {
    const libDirs = ensureChromeLibs(chromePath) ?? [];
    const extra = process.env.CHROME_LIBS_DIR ? [process.env.CHROME_LIBS_DIR] : [];
    const ldLibraryPath = [...libDirs, ...extra, process.env.LD_LIBRARY_PATH].filter(Boolean).join(':');
    if (ldLibraryPath) {
        process.env.LD_LIBRARY_PATH = ldLibraryPath;
    }
}

run('npx', ['mocha', ...process.argv.slice(2)]);

async function resolveChromePath() {
    if (process.env.PUPPETEER_EXECUTABLE_PATH) {
        return process.env.PUPPETEER_EXECUTABLE_PATH;
    }
    const puppeteer = await import('puppeteer');
    try {
        return puppeteer.default.executablePath();
    } catch {
        return null;
    }
}
