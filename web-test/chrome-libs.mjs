import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';

const FALLBACK_SOURCES = ['deb http://deb.debian.org/debian trixie main'];

const DEB_PACKAGES = [
    ['libasound2t64', 'libasound2'],
    ['libatk1.0-0t64', 'libatk1.0-0'],
    ['libatk-bridge2.0-0t64', 'libatk-bridge2.0-0'],
    ['libatspi2.0-0t64', 'libatspi2.0-0'],
    ['libcups2t64', 'libcups2'],
    ['libdbus-1-3'],
    ['libgbm1'],
    ['libglib2.0-0t64', 'libglib2.0-0'],
    ['libnss3'],
    ['libnspr4'],
    ['libpango-1.0-0'],
    ['libpangocairo-1.0-0'],
    ['libcairo2'],
    ['libx11-6'],
    ['libxcb1'],
    ['libxcomposite1'],
    ['libxdamage1'],
    ['libxext6'],
    ['libxfixes3'],
    ['libxkbcommon0'],
    ['libxrandr2'],
    ['libavahi-common3'],
    ['libavahi-client3'],
    ['libdrm2'],
    ['libfontconfig1'],
    ['libfreetype6'],
    ['libfribidi0'],
    ['libpixman-1-0'],
    ['libpng16-16t64', 'libpng16-16'],
    ['libthai0'],
    ['libxau6'],
    ['libxcb-render0'],
    ['libxcb-shm0'],
    ['libxdmcp6'],
    ['libxi6'],
    ['libxrender1'],
    ['libharfbuzz0b', 'libharfbuzz0'],
    ['libdatrie1'],
    ['libgraphite2-3'],
];

function run(command, args, options = {}) {
    return spawnSync(command, args, {
        encoding: 'utf8',
        env: { ...process.env, ...options.env },
        cwd: options.cwd,
    });
}

function toolAvailable(name) {
    return run(name, ['--version']).status === 0;
}

function detectAptSources() {
    const lines = [];
    const sourcesList = '/etc/apt/sources.list';
    if (existsSync(sourcesList)) {
        for (const line of readLines(sourcesList)) {
            const match = line.match(/^deb\s+(https?:\/\/\S+)\s+(\S+)\s+(\S+)/);
            if (match) {
                lines.push(`deb ${match[1]} ${match[2]} ${match[3]}`);
            }
        }
    }
    const sourcesDir = '/etc/apt/sources.list.d';
    if (existsSync(sourcesDir)) {
        for (const file of readdirSync(sourcesDir)) {
            for (const block of readLines(join(sourcesDir, file)).join('\n').split(/\n\s*\n/)) {
                const uris = [...block.matchAll(/^URIs:\s*(.+)$/gm)].flatMap((m) => m[1].trim().split(/\s+/));
                const suites = [...block.matchAll(/^Suites:\s*(.+)$/gm)].flatMap((m) => m[1].trim().split(/\s+/));
                const components = [...block.matchAll(/^Components:\s*(.+)$/gm)].flatMap((m) =>
                    m[1].trim().split(/\s+/),
                );
                if (block.includes('Types: deb') || !block.includes('Types:')) {
                    for (const uri of uris) {
                        for (const suite of suites) {
                            for (const component of components) {
                                lines.push(`deb ${uri} ${suite} ${component}`);
                            }
                        }
                    }
                }
            }
        }
    }
    return [...new Set(lines.filter((line) => /^deb https?:/.test(line)))];
}

function readLines(file) {
    try {
        return readFileSync(file, 'utf8').split('\n');
    } catch {
        return [];
    }
}

function missingLibraries(chromePath, libDirs) {
    const ldLibraryPath = [
        ...libDirs,
        process.env.LD_LIBRARY_PATH,
    ].filter(Boolean).join(':');
    const result = run('ldd', [chromePath], { env: { LD_LIBRARY_PATH: ldLibraryPath } });
    if (result.status !== 0) {
        return null;
    }
    const missing = new Set();
    for (const line of result.stdout.split('\n')) {
        const match = line.match(/^\s*(\S+)\s+=>\s+not found/);
        if (match) {
            missing.add(match[1]);
        }
    }
    return [...missing];
}

function libraryDirs(root) {
    const dirs = [];
    for (const base of ['usr/lib', 'usr/lib64', 'lib']) {
        const dir = join(root, base);
        if (!existsSync(dir)) {
            continue;
        }
        for (const entry of readdirSync(dir, { withFileTypes: true })) {
            if (entry.isDirectory() && entry.name.includes('linux-gnu')) {
                dirs.push(join(dir, entry.name));
            }
        }
        dirs.push(dir);
    }
    return dirs;
}

function aptOptions(stateDir, cacheDir, sourcesFile) {
    return [
        '-o', `Dir::State=${stateDir}`,
        '-o', `Dir::State::lists=${stateDir}/lists`,
        '-o', `Dir::Cache=${cacheDir}`,
        '-o', `Dir::Cache::archives=${cacheDir}/archives`,
        '-o', `Dir::Etc::sourcelist=${sourcesFile}`,
        '-o', 'Dir::Etc::sourceparts=-',
        '-o', 'Apt::Get::AllowUnauthenticated=true',
    ];
}

function downloadPackages(options, tempDir) {
    let downloaded = 0;
    for (const candidates of DEB_PACKAGES) {
        for (const pkg of candidates) {
            const result = run('apt-get', [...options, 'download', pkg], { cwd: tempDir });
            if (result.status === 0) {
                downloaded++;
                break;
            }
        }
    }
    return downloaded;
}

function extractPackages(tempDir, libsDir) {
    for (const file of readdirSync(tempDir)) {
        if (file.endsWith('.deb')) {
            run('dpkg-deb', ['-x', file, libsDir], { cwd: tempDir });
        }
    }
}

export function ensureChromeLibs(chromePath) {
    const cacheDir = join(homedir(), '.cache', 'terrapreview', 'chrome-libs');
    const libsDir = join(cacheDir, 'libs');
    const libDirs = existsSync(libsDir) ? libraryDirs(libsDir) : [];

    if (!existsSync(chromePath)) {
        return null;
    }
    const missing = missingLibraries(chromePath, libDirs);
    if (missing === null) {
        return null;
    }
    if (missing.length === 0) {
        return libDirs.length > 0 ? libDirs : null;
    }

    console.log(`Chrome is missing ${missing.length} shared libraries, setting them up...`);

    if (!toolAvailable('apt-get') || !toolAvailable('dpkg-deb')) {
        throw new Error(
            'apt-get and dpkg-deb are required to fetch Chrome system libraries automatically. ' +
                'Install them, or point CHROME_LIBS_DIR at a directory that already contains them.',
        );
    }

    const stateDir = join(cacheDir, 'apt');
    const cacheAptDir = join(cacheDir, 'apt-cache');
    mkdirSync(join(stateDir, 'lists', 'partial'), { recursive: true });
    mkdirSync(join(cacheAptDir, 'archives', 'partial'), { recursive: true });
    mkdirSync(libsDir, { recursive: true });

    const sourcesFile = join(cacheDir, 'sources.conf');
    const sources = detectAptSources();
    writeFileSync(sourcesFile, (sources.length > 0 ? sources : FALLBACK_SOURCES).join('\n') + '\n');

    const options = aptOptions(stateDir, cacheAptDir, sourcesFile);
    let update = run('apt-get', [...options, 'update']);
    if (update.status !== 0 && sources.length > 0) {
        console.log('System package sources are unusable, falling back to the Debian mirror...');
        writeFileSync(sourcesFile, FALLBACK_SOURCES.join('\n') + '\n');
        update = run('apt-get', [...options, 'update']);
    }
    if (update.status !== 0) {
        throw new Error(`apt-get update failed: ${update.stderr}`);
    }

    const tempDir = mkdtempSync(join(tmpdir(), 'terrapreview-debs-'));
    try {
        const downloaded = downloadPackages(options, tempDir);
        if (downloaded === 0) {
            throw new Error('no Chrome dependency packages could be downloaded');
        }
        extractPackages(tempDir, libsDir);
    } finally {
        rmSync(tempDir, { recursive: true, force: true });
    }

    const stillMissing = missingLibraries(chromePath, libraryDirs(libsDir));
    if (stillMissing.length > 0) {
        throw new Error(
            `Chrome is still missing shared libraries after setup: ${stillMissing.join(', ')}. ` +
                'Point CHROME_LIBS_DIR at a directory that contains them.',
        );
    }

    console.log('Chrome system libraries installed in', libsDir);
    return libraryDirs(libsDir);
}
