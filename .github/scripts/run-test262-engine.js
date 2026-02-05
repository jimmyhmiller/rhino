#!/usr/bin/env node

/**
 * Runs test262 tests with a JavaScript engine and outputs results in the same
 * format as the Rhino Test262JsonRunner for consistent comparison.
 *
 * This script matches the methodology used by test262.fyi for accurate comparison.
 * See: https://github.com/test262-fyi/data/tree/main/engines/qjs
 *
 * Usage: node run-test262-engine.js --engine <path-to-engine> --test262 <test262-dir> --output <output-dir> --name <engine-name>
 */

const { execSync, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

function parseArgs(args) {
    const result = {
        engine: './qjs',
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
            } else if (value.startsWith('[') && value.endsWith(']')) {
                result[key] = value.slice(1, -1).split(',').map(s => s.trim().replace(/^['"]|['"]$/g, ''));
                currentArray = null;
            } else {
                result[key] = value.trim();
                currentArray = null;
            }
        }
    }

    // Parse negative block if present
    const negativeMatch = content.match(/negative:\s*\n\s*phase:\s*(\S+)\s*\n\s*type:\s*(\S+)/);
    if (negativeMatch) {
        result.negative = {
            phase: negativeMatch[1],
            type: negativeMatch[2]
        };
    } else if (result.negative === '') {
        result.negative = true;
    }

    return result;
}

function loadPreludes(harnessDir) {
    const preludes = {};
    const entries = fs.readdirSync(harnessDir, { withFileTypes: true });

    for (const entry of entries) {
        if (entry.isFile() && entry.name.endsWith('.js')) {
            preludes[entry.name] = fs.readFileSync(path.join(harnessDir, entry.name), 'utf8');
        } else if (entry.isDirectory()) {
            // Handle subdirectories like 'VerifyProperty'
            const subdir = path.join(harnessDir, entry.name);
            for (const subfile of fs.readdirSync(subdir)) {
                if (subfile.endsWith('.js')) {
                    preludes[`${entry.name}/${subfile}`] = fs.readFileSync(path.join(subdir, subfile), 'utf8');
                }
            }
        }
    }

    return preludes;
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
            } else if (entry.name.endsWith('.js') && !entry.name.includes('_FIXTURE')) {
                tests.push({ fullPath, relativePath: relPath });
            }
        }
    }

    walk(testDir);
    return tests;
}

function runTest(enginePath, testFile, isModule, timeout) {
    // run-test262 uses -N flag for test262 mode
    const args = ['-N', testFile];
    if (isModule) args.unshift('--module');

    const result = spawnSync(enginePath, args, {
        encoding: 'utf8',
        timeout,
        maxBuffer: 10 * 1024 * 1024
    });

    const combined = (result.stdout || '') + '\n' + (result.stderr || '');
    const lowered = combined.toLowerCase();
    const hasError = result.signal !== null || !!result.error || result.status !== 0 ||
        lowered.includes('error') || lowered.includes('exception') || lowered.includes('panic');

    return { combined, hasError };
}

function evaluateResult(result, flags, negative) {
    const { combined, hasError } = result;

    if (negative) {
        if (!hasError) return false;
        if (typeof negative === 'object' && negative.type && !combined.includes(negative.type)) return false;
        return true;
    }

    if (hasError) return false;
    if (flags.async && !combined.includes('Test262:AsyncTestComplete')) return false;
    return true;
}

function processTest(enginePath, test262Dir, testPath, preludes, timeout) {
    const content = fs.readFileSync(testPath, 'utf8');
    const metadata = parseYamlFrontmatter(content);
    const flags = {};

    // Parse flags
    let flagsRaw = content.match(/^flags: \[(.*)\]$/m)?.[1];
    if (!flagsRaw && content.includes('flags:')) {
        // Check for md style list as fallback
        flagsRaw = content.match(/^flags:\n(  - .*\s*\n)+/m);
        if (flagsRaw) flagsRaw = flagsRaw[0].replaceAll('\n  - ', ',').slice(7, -1);
    }
    if (flagsRaw) {
        for (const x of flagsRaw.split(',')) {
            flags[x.trim()] = true;
        }
    }

    // Get includes
    const includesMatch = content.match(/^includes: \[(.*)\]$/m);
    const includes = includesMatch ? includesMatch[1].split(',').map(s => s.trim()) : [];

    // Build test contents - matching test262.fyi methodology
    let contents = '';

    if (!flags.raw) {
        // Add strict mode prefix if onlyStrict
        if (flags.onlyStrict) contents += '"use strict";\n';

        // Add async harness for async tests
        if (flags.async) contents += preludes['doneprintHandle.js'] || '';

        // Add includes
        for (const inc of includes) {
            contents += preludes[inc] || '';
        }

        // Always add assert.js and sta.js
        contents += preludes['assert.js'] || '';
        contents += preludes['sta.js'] || '';
    }

    contents += content;

    // Determine if we need strict rerun
    const strictRerun = !flags.module && !flags.onlyStrict && !flags.noStrict && !flags.raw;

    // Write temp file in same dir as test for module resolution
    const tmpFile = testPath + '.tmp.js';
    fs.writeFileSync(tmpFile, contents);

    try {
        const result = runTest(enginePath, tmpFile, flags.module, timeout);
        let pass = evaluateResult(result, flags, metadata.negative);

        // Strict rerun - both must pass
        if (pass && strictRerun) {
            const strictContents = '"use strict";\n' + contents;
            fs.writeFileSync(tmpFile, strictContents);
            const strictResult = runTest(enginePath, tmpFile, flags.module, timeout);
            pass = pass && evaluateResult(strictResult, flags, metadata.negative);
        }

        fs.unlinkSync(tmpFile);
        return { pass };
    } catch (e) {
        try { fs.unlinkSync(tmpFile); } catch {}
        return { pass: false, reason: e.message };
    }
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
        if (!result) continue;

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

    // Verify engine exists
    if (!fs.existsSync(config.engine)) {
        throw new Error(`Engine not found: ${config.engine}`);
    }
    console.log(`  engine path: ${config.engine}`);

    const version = getEngineVersion(config.engine);
    console.log(`  engine version: ${version}`);

    // Load preludes (harness files)
    const harnessDir = path.join(config.test262Dir, 'harness');
    console.log(`Loading harness files from ${harnessDir}...`);
    const preludes = loadPreludes(harnessDir);
    console.log(`Loaded ${Object.keys(preludes).length} harness files`);

    // Find tests
    const testDir = path.join(config.test262Dir, 'test');
    console.log(`Finding tests in ${testDir}...`);
    const tests = findAllTests(testDir);
    console.log(`Found ${tests.length} test files`);

    // Run tests
    const results = new Map();
    let completed = 0, passed = 0, failed = 0;
    const startTime = Date.now();

    for (const test of tests) {
        const result = processTest(config.engine, config.test262Dir, test.fullPath, preludes, config.timeout);
        results.set(test.relativePath, result);

        completed++;
        if (result.pass) passed++;
        else failed++;

        if (completed % 500 === 0) {
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            const rate = (completed / parseFloat(elapsed)).toFixed(0);
            console.log(`  ${completed}/${tests.length} (${passed} pass, ${failed} fail) ${rate}/s`);
        }
    }

    console.log(`\nCompleted in ${((Date.now() - startTime) / 1000).toFixed(1)}s`);
    console.log(`  Passed: ${passed}`);
    console.log(`  Failed: ${failed}`);
    console.log(`  Total: ${tests.length}`);

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
