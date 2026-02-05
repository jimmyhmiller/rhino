#!/usr/bin/env node

/**
 * Runs test262 tests with a JavaScript engine and outputs results in the same
 * format as the Rhino Test262JsonRunner for consistent comparison.
 *
 * Usage: node run-test262-engine.js --engine <path-to-engine> --test262 <test262-dir> --output <output-dir> --name <engine-name>
 */

const { execSync, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

function parseArgs(args) {
    const result = {
        engine: 'qjs',
        test262Dir: './tests/test262',
        outputDir: './build/test262-engine',
        engineName: 'quickjs',
        timeout: 10000,
        threads: os.cpus().length
    };

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--engine') result.engine = args[++i];
        else if (args[i] === '--test262') result.test262Dir = args[++i];
        else if (args[i] === '--output') result.outputDir = args[++i];
        else if (args[i] === '--name') result.engineName = args[++i];
        else if (args[i] === '--timeout') result.timeout = parseInt(args[++i], 10);
        else if (args[i] === '--threads') result.threads = parseInt(args[++i], 10);
    }

    return result;
}

function findEngine(engine) {
    const candidates = [
        engine,
        `/usr/local/bin/${engine}`,
        `/usr/bin/${engine}`,
        `/opt/homebrew/bin/${engine}`,
        path.resolve(engine)
    ];

    for (const candidate of candidates) {
        try {
            const result = spawnSync(candidate, ['-e', '1'], { timeout: 5000 });
            if (result.status === 0) return candidate;
        } catch (e) {}
    }

    throw new Error(`Engine '${engine}' not found`);
}

function getEngineVersion(enginePath) {
    try {
        const result = spawnSync(enginePath, ['--help'], { encoding: 'utf8', timeout: 5000 });
        const output = result.stdout + result.stderr;
        const match = output.match(/version\s+(\S+)/i);
        return match ? match[1] : 'unknown';
    } catch (e) {
        return 'unknown';
    }
}

function parseYamlFrontmatter(content) {
    const match = content.match(/\/\*---([\s\S]*?)---\*\//);
    if (!match) return {};

    const yaml = match[1];
    const result = {};
    let currentKey = null;
    let currentArray = null;

    for (const line of yaml.split('\n')) {
        const arrayMatch = line.match(/^\s+-\s+(.+)$/);
        if (arrayMatch && currentArray) {
            currentArray.push(arrayMatch[1].trim());
            continue;
        }

        const kvMatch = line.match(/^(\w+):\s*(.*)$/);
        if (kvMatch) {
            const [, key, value] = kvMatch;
            if (value === '' || value.trim() === '') {
                result[key] = [];
                currentArray = result[key];
                currentKey = key;
            } else if (value.startsWith('[') && value.endsWith(']')) {
                result[key] = value.slice(1, -1).split(',').map(s => s.trim().replace(/^['"]|['"]$/g, ''));
                currentArray = null;
            } else {
                result[key] = value.trim();
                currentArray = null;
            }
        }
    }

    return result;
}

function findAllTests(testDir) {
    const tests = [];

    function walk(dir, relativePath = '') {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
            const fullPath = path.join(dir, entry.name);
            const relPath = path.join(relativePath, entry.name);

            if (entry.isDirectory() && entry.name !== 'harness' && entry.name !== '.git') {
                walk(fullPath, relPath);
            } else if (entry.name.endsWith('.js') && !entry.name.startsWith('_')) {
                tests.push({ fullPath, relativePath: relPath });
            }
        }
    }

    walk(testDir);
    return tests;
}

function runSingleTest(enginePath, test262Dir, testPath, isStrict, timeout) {
    const content = fs.readFileSync(testPath, 'utf8');
    const metadata = parseYamlFrontmatter(content);
    const harnessDir = path.join(test262Dir, 'harness');

    // Check flags
    const flags = metadata.flags || [];
    if (flags.includes('module')) return { skip: true, reason: 'module' };
    if (flags.includes('async')) return { skip: true, reason: 'async' };
    if (flags.includes('raw')) return { skip: true, reason: 'raw' };
    if (isStrict && flags.includes('noStrict')) return { skip: true };
    if (!isStrict && flags.includes('onlyStrict')) return { skip: true };

    // Check for unsupported features
    const unsupported = ['Atomics', 'SharedArrayBuffer', 'Temporal', 'ShadowRealm', 'decorators', 'tail-call-optimization'];
    for (const feat of (metadata.features || [])) {
        if (unsupported.includes(feat)) return { skip: true, reason: `unsupported: ${feat}` };
    }

    // Check for negative test
    const isNegative = !!metadata.negative;
    const expectedPhase = metadata.negative?.phase;
    const expectedType = metadata.negative?.type;

    // Build test script
    let script = '';
    if (isStrict) script += '"use strict";\n';

    // Add harness
    script += fs.readFileSync(path.join(harnessDir, 'assert.js'), 'utf8') + '\n';
    script += fs.readFileSync(path.join(harnessDir, 'sta.js'), 'utf8') + '\n';

    for (const inc of (metadata.includes || [])) {
        const incPath = path.join(harnessDir, inc);
        if (fs.existsSync(incPath)) {
            script += fs.readFileSync(incPath, 'utf8') + '\n';
        }
    }

    script += content;

    // Write temp file
    const tmpFile = path.join(os.tmpdir(), `test262-${process.pid}-${Date.now()}.js`);
    fs.writeFileSync(tmpFile, script);

    try {
        const result = spawnSync(enginePath, [tmpFile], {
            encoding: 'utf8',
            timeout,
            maxBuffer: 10 * 1024 * 1024
        });

        fs.unlinkSync(tmpFile);

        if (result.status === 0) {
            // No error - test passed (unless it was supposed to fail)
            return { pass: !isNegative };
        } else {
            // Error occurred
            if (isNegative) {
                const output = (result.stderr || '') + (result.stdout || '');
                if (!expectedType || output.includes(expectedType)) {
                    return { pass: true };
                }
            }
            return { pass: false };
        }
    } catch (e) {
        try { fs.unlinkSync(tmpFile); } catch {}
        return { pass: false, reason: e.message };
    }
}

function runTest(enginePath, test262Dir, testPath, timeout) {
    const content = fs.readFileSync(testPath, 'utf8');
    const metadata = parseYamlFrontmatter(content);
    const flags = metadata.flags || [];

    const runStrict = !flags.includes('noStrict');
    const runNonStrict = !flags.includes('onlyStrict');

    let strictResult = { skip: true };
    let nonStrictResult = { skip: true };

    if (runStrict) {
        strictResult = runSingleTest(enginePath, test262Dir, testPath, true, timeout);
    }
    if (runNonStrict) {
        nonStrictResult = runSingleTest(enginePath, test262Dir, testPath, false, timeout);
    }

    // Pass if either mode passes
    if (strictResult.pass || nonStrictResult.pass) return { pass: true };
    if (strictResult.skip && nonStrictResult.skip) return { skip: true };
    return { pass: false };
}

function buildResults(tests, results, engineName) {
    const index = {
        total: 0,
        engines: { [engineName]: 0 },
        files: {}
    };

    const directories = {};

    for (const test of tests) {
        const result = results.get(test.relativePath);
        if (!result || result.skip) continue;

        index.total++;
        if (result.pass) index.engines[engineName]++;

        // Build directory structure
        const parts = test.relativePath.split(path.sep);
        const category = parts[0];

        if (!index.files[category]) {
            index.files[category] = { total: 0, engines: { [engineName]: 0 } };
        }
        index.files[category].total++;
        if (result.pass) index.files[category].engines[engineName]++;

        // Build per-directory JSON
        const dirPath = parts.slice(0, -1).join('/');
        if (!directories[dirPath]) {
            directories[dirPath] = {
                total: 0,
                engines: { [engineName]: 0 },
                tests: {}
            };
        }
        directories[dirPath].total++;
        if (result.pass) directories[dirPath].engines[engineName]++;
        directories[dirPath].tests[parts[parts.length - 1]] = { [engineName]: result.pass };
    }

    return { index, directories };
}

function writeJson(filePath, data) {
    const dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
}

async function main() {
    const config = parseArgs(process.argv.slice(2));

    console.log(`Running test262 with ${config.engine}`);
    console.log(`  test262 dir: ${config.test262Dir}`);
    console.log(`  output dir: ${config.outputDir}`);
    console.log(`  engine name: ${config.engineName}`);

    const enginePath = findEngine(config.engine);
    console.log(`  engine path: ${enginePath}`);

    const version = getEngineVersion(enginePath);
    console.log(`  engine version: ${version}`);

    // Find tests
    const testDir = path.join(config.test262Dir, 'test');
    console.log(`Finding tests in ${testDir}...`);
    const tests = findAllTests(testDir);
    console.log(`Found ${tests.length} test files`);

    // Run tests
    const results = new Map();
    let completed = 0, passed = 0, failed = 0, skipped = 0;
    const startTime = Date.now();

    for (const test of tests) {
        const result = runTest(enginePath, config.test262Dir, test.fullPath, config.timeout);
        results.set(test.relativePath, result);

        completed++;
        if (result.skip) skipped++;
        else if (result.pass) passed++;
        else failed++;

        if (completed % 500 === 0) {
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            const rate = (completed / parseFloat(elapsed)).toFixed(0);
            console.log(`  ${completed}/${tests.length} (${passed} pass, ${failed} fail, ${skipped} skip) ${rate}/s`);
        }
    }

    console.log(`\nCompleted in ${((Date.now() - startTime) / 1000).toFixed(1)}s`);
    console.log(`  Passed: ${passed}`);
    console.log(`  Failed: ${failed}`);
    console.log(`  Skipped: ${skipped}`);

    // Build output
    const { index, directories } = buildResults(tests, results, config.engineName);

    // Write files
    if (!fs.existsSync(config.outputDir)) fs.mkdirSync(config.outputDir, { recursive: true });

    writeJson(path.join(config.outputDir, 'engines.json'), { [config.engineName]: version });
    writeJson(path.join(config.outputDir, 'index.json'), index);

    for (const [dirPath, data] of Object.entries(directories)) {
        writeJson(path.join(config.outputDir, dirPath + '.json'), data);
    }

    console.log(`Results written to ${config.outputDir}`);
}

main().catch(e => {
    console.error('Error:', e);
    process.exit(1);
});
