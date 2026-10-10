import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

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

run('npx', ['mocha', ...process.argv.slice(2)]);
