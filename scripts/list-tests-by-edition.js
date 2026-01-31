#!/usr/bin/env node

/**
 * Lists all test262 tests by ECMAScript edition, showing pass/fail status.
 *
 * Usage: node list-tests-by-edition.js [--edition N] [--status passing|failing] [--output json|text]
 *
 * Examples:
 *   node list-tests-by-edition.js --edition 6 --status failing    # ES6 failing tests
 *   node list-tests-by-edition.js --edition 6                     # All ES6 tests
 *   node list-tests-by-edition.js                                 # Summary of all editions
 */

const fs = require('fs');
const path = require('path');

// Exact mapping from test262-fyi
// https://github.com/test262-fyi/test262.fyi/blob/main/controller/generate.js
const featureByEdition = new Map(Object.entries({
    // Stage 3 proposals (99 = ESNext/proposal)
    "Intl.Locale-info": 99,
    "Intl.NumberFormat-v3": 99,
    "legacy-regexp": 99,
    "Temporal": 99,
    "ShadowRealm": 99,
    "decorators": 99,
    "regexp-duplicate-named-groups": 99,
    "Array.fromAsync": 99,
    "explicit-resource-management": 99,
    "source-phase-imports": 99,
    "source-phase-imports-module-source": 99,
    "Atomics.pause": 99,
    "import-defer": 99,
    "canonical-tz": 99,
    "upsert": 99,
    "immutable-arraybuffer": 99,
    "nonextensible-applies-to-private": 99,
    "host-gc-required": 99,

    // ESNext
    "Error.isError": 99,
    "iterator-sequencing": 99,
    "json-parse-with-source": 99,
    "Math.sumPrecise": 99,
    "uint8array-base64": 99,

    // ES2025 (edition 16)
    "Float16Array": 16,
    "import-attributes": 16,
    "Intl.DurationFormat": 16,
    "iterator-helpers": 16,
    "json-modules": 16,
    "promise-try": 16,
    "RegExp.escape": 16,
    "regexp-modifiers": 16,
    "set-methods": 16,

    // ES2024 (edition 15)
    "Atomics.waitAsync": 15,
    "array-grouping": 15,
    "arraybuffer-transfer": 15,
    "promise-with-resolvers": 15,
    "regexp-v-flag": 15,
    "resizable-arraybuffer": 15,
    "String.prototype.isWellFormed": 15,
    "String.prototype.toWellFormed": 15,

    // ES2023 (edition 14)
    "array-find-from-last": 14,
    "change-array-by-copy": 14,
    "hashbang": 14,
    "symbols-as-weakmap-keys": 14,

    // ES2022 (edition 13)
    "arbitrary-module-namespace-names": 13,
    "Array.prototype.at": 13,
    "class-fields-private": 13,
    "class-fields-private-in": 13,
    "class-fields-public": 13,
    "class-methods-private": 13,
    "class-static-block": 13,
    "class-static-fields-private": 13,
    "class-static-fields-public": 13,
    "class-static-methods-private": 13,
    "error-cause": 13,
    "Intl.DateTimeFormat-extend-timezonename": 13,
    "Intl.DisplayNames-v2": 13,
    "Intl-enumeration": 13,
    "Intl.Segmenter": 13,
    "Object.hasOwn": 13,
    "regexp-match-indices": 13,
    "String.prototype.at": 13,
    "top-level-await": 13,
    "TypedArray.prototype.at": 13,

    // ES2021 (edition 12)
    "AggregateError": 12,
    "align-detached-buffer-semantics-with-web-reality": 12,
    "FinalizationRegistry": 12,
    "Intl.DateTimeFormat-datetimestyle": 12,
    "Intl.DateTimeFormat-formatRange": 12,
    "Intl.DateTimeFormat-fractionalSecondDigits": 12,
    "Intl.DisplayNames": 12,
    "Intl.ListFormat": 12,
    "Intl.Locale": 12,
    "logical-assignment-operators": 12,
    "numeric-separator-literal": 12,
    "Promise.any": 12,
    "String.prototype.replaceAll": 12,
    "WeakRef": 12,

    // ES2020 (edition 11)
    "BigInt": 11,
    "coalesce-expression": 11,
    "Intl.NumberFormat-unified": 11,
    "Intl.RelativeTimeFormat": 11,
    "optional-chaining": 11,
    "dynamic-import": 11,
    "export-star-as-namespace-from-module": 11,
    "for-in-order": 11,
    "globalThis": 11,
    "import.meta": 11,
    "Promise.allSettled": 11,
    "String.prototype.matchAll": 11,
    "Symbol.matchAll": 11,

    // ES2019 (edition 10)
    "Array.prototype.flat": 10,
    "Array.prototype.flatMap": 10,
    "json-superset": 10,
    "Object.fromEntries": 10,
    "optional-catch-binding": 10,
    "stable-array-sort": 10,
    "stable-typedarray-sort": 10,
    "string-trimming": 10,
    "String.prototype.trimEnd": 10,
    "String.prototype.trimStart": 10,
    "Symbol.prototype.description": 10,
    "well-formed-json-stringify": 10,

    // ES2018 (edition 9)
    "async-iteration": 9,
    "IsHTMLDDA": 9,
    "object-rest": 9,
    "object-spread": 9,
    "Promise.prototype.finally": 9,
    "regexp-dotall": 9,
    "regexp-lookbehind": 9,
    "regexp-named-groups": 9,
    "regexp-unicode-property-escapes": 9,
    "Symbol.asyncIterator": 9,

    // ES2017 (edition 8)
    "async-functions": 8,
    "Atomics": 8,
    "Intl.DateTimeFormat-dayPeriod": 8,
    "intl-normative-optional": 8,
    "SharedArrayBuffer": 8,

    // ES2016 (edition 7)
    "Array.prototype.includes": 7,
    "exponentiation": 7,
    "u180e": 7,

    // ES6 (edition 6)
    "ArrayBuffer": 6,
    "Array.prototype.values": 6,
    "arrow-function": 6,
    "class": 6,
    "computed-property-names": 6,
    "const": 6,
    "cross-realm": 6,
    "DataView": 6,
    "DataView.prototype.getFloat32": 6,
    "DataView.prototype.getFloat64": 6,
    "DataView.prototype.getInt16": 6,
    "DataView.prototype.getInt32": 6,
    "DataView.prototype.getInt8": 6,
    "DataView.prototype.getUint16": 6,
    "DataView.prototype.getUint32": 6,
    "DataView.prototype.setUint8": 6,
    "default-parameters": 6,
    "destructuring-assignment": 6,
    "destructuring-binding": 6,
    "for-of": 6,
    "Float32Array": 6,
    "Float64Array": 6,
    "generators": 6,
    "Int8Array": 6,
    "Int16Array": 6,
    "Int32Array": 6,
    "let": 6,
    "Map": 6,
    "new.target": 6,
    "Object.is": 6,
    "Promise": 6,
    "Proxy": 6,
    "proxy-missing-checks": 6,
    "Reflect": 6,
    "Reflect.construct": 6,
    "Reflect.set": 6,
    "Reflect.setPrototypeOf": 6,
    "rest-parameters": 6,
    "Set": 6,
    "String.fromCodePoint": 6,
    "String.prototype.endsWith": 6,
    "String.prototype.includes": 6,
    "super": 6,
    "Symbol": 6,
    "Symbol.hasInstance": 6,
    "Symbol.isConcatSpreadable": 6,
    "Symbol.iterator": 6,
    "Symbol.match": 6,
    "Symbol.replace": 6,
    "Symbol.search": 6,
    "Symbol.species": 6,
    "Symbol.split": 6,
    "Symbol.toPrimitive": 6,
    "Symbol.toStringTag": 6,
    "Symbol.unscopables": 6,
    "tail-call-optimization": 6,
    "template": 6,
    "TypedArray": 6,
    "Uint8Array": 6,
    "Uint16Array": 6,
    "Uint32Array": 6,
    "Uint8ClampedArray": 6,
    "WeakMap": 6,
    "WeakSet": 6,
    "__proto__": 6,

    // ES5 (edition 5)
    "caller": 5,
    "__getter__": 8,
    "__setter__": 8,
}));

const editionNames = {
    5: 'ES5',
    6: 'ES6/ES2015',
    7: 'ES2016',
    8: 'ES2017',
    9: 'ES2018',
    10: 'ES2019',
    11: 'ES2020',
    12: 'ES2021',
    13: 'ES2022',
    14: 'ES2023',
    15: 'ES2024',
    16: 'ES2025',
    99: 'ESNext/Proposals',
};

function parseArgs(args) {
    const result = {
        edition: null,
        status: null, // 'passing', 'failing', or null for both
        output: 'text', // 'text' or 'json'
        help: false,
    };

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--edition' || args[i] === '-e') {
            result.edition = parseInt(args[++i], 10);
        } else if (args[i] === '--status' || args[i] === '-s') {
            result.status = args[++i];
        } else if (args[i] === '--output' || args[i] === '-o') {
            result.output = args[++i];
        } else if (args[i] === '--help' || args[i] === '-h') {
            result.help = true;
        }
    }

    return result;
}

function extractFeatures(content) {
    // Try single-line format: features: [feat1, feat2]
    let match = content.match(/^features:\s*\[(.*)\]$/m);
    if (match) {
        return match[1].split(',').map(x => x.trim()).filter(x => x);
    }

    // Try multi-line format:
    // features:
    //   - feat1
    //   - feat2
    match = content.match(/^features:\s*\n((?:\s+-\s+.*\n?)+)/m);
    if (match) {
        return match[1].split('\n')
            .map(x => x.replace(/^\s+-\s+/, '').trim())
            .filter(x => x);
    }

    return [];
}

function getTestEdition(features) {
    if (features.length === 0) {
        return 5; // No features = ES5 baseline
    }

    let maxEdition = 5;
    for (const feature of features) {
        const edition = featureByEdition.get(feature);
        if (edition !== undefined && edition > maxEdition) {
            maxEdition = edition;
        }
    }
    return maxEdition;
}

function parseTest262Properties(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.split('\n');

    const failingTests = new Set();
    const skippedCategories = new Set();

    let currentPath = [];
    let currentIndent = 0;

    for (const line of lines) {
        // Skip empty lines and comments
        if (!line.trim() || line.trim().startsWith('#')) continue;

        // Check for skipped category (starts with ~)
        if (line.trim().startsWith('~')) {
            const category = line.trim().slice(1).split(' ')[0];
            skippedCategories.add(category);
            continue;
        }

        // Calculate indentation level (4 spaces = 1 level)
        const indent = line.match(/^(\s*)/)[1].length;
        const indentLevel = Math.floor(indent / 4);

        // Get the content after indentation
        const content = line.trim();

        // Skip lines that are just stats like "3/53 (5.66%)"
        if (/^\d+\/\d+/.test(content)) continue;

        // Check if this is a directory (contains stats) or a file
        const statsMatch = content.match(/^(.+?)\s+\d+\/\d+/);

        if (statsMatch) {
            // This is a directory with stats
            const dirName = statsMatch[1];
            // Adjust path based on indent
            currentPath = currentPath.slice(0, indentLevel);
            currentPath.push(dirName);
        } else if (content.endsWith('.js')) {
            // This is a test file
            const fileName = content.split(' ')[0]; // Remove any trailing comments like {unsupported: ...}
            const testPath = [...currentPath.slice(0, indentLevel), fileName].join('/');
            failingTests.add(testPath);
        }
    }

    return { failingTests, skippedCategories };
}

function walkDir(dir, callback, relativePath = '') {
    if (!fs.existsSync(dir)) return;

    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        const relPath = relativePath ? `${relativePath}/${entry.name}` : entry.name;

        if (entry.isDirectory()) {
            // Skip harness and _FIXTURE directories
            if (entry.name === 'harness' || entry.name.startsWith('_')) continue;
            walkDir(fullPath, callback, relPath);
        } else if (entry.name.endsWith('.js') && !entry.name.startsWith('_')) {
            callback(fullPath, relPath);
        }
    }
}

function main() {
    const args = parseArgs(process.argv.slice(2));

    if (args.help) {
        console.log(`
Usage: node list-tests-by-edition.js [options]

Options:
  --edition, -e N       Filter by edition (5-16, or 99 for ESNext)
  --status, -s STATUS   Filter by status: 'passing' or 'failing'
  --output, -o FORMAT   Output format: 'text' (default) or 'json'
  --help, -h            Show this help

Examples:
  node list-tests-by-edition.js                           # Summary of all editions
  node list-tests-by-edition.js -e 6                      # All ES6 tests
  node list-tests-by-edition.js -e 6 -s failing           # ES6 failing tests only
  node list-tests-by-edition.js -e 6 -s failing -o json   # ES6 failing as JSON
`);
        process.exit(0);
    }

    const scriptDir = __dirname;
    const rootDir = path.dirname(scriptDir);
    const test262Dir = path.join(rootDir, 'tests', 'test262', 'test');
    const propsFile = path.join(rootDir, 'tests', 'testsrc', 'test262.properties');

    if (!fs.existsSync(test262Dir)) {
        console.error(`Error: test262 directory not found at ${test262Dir}`);
        console.error('Make sure the test262 submodule is initialized: git submodule update --init');
        process.exit(1);
    }

    if (!fs.existsSync(propsFile)) {
        console.error(`Error: test262.properties not found at ${propsFile}`);
        process.exit(1);
    }

    console.error('Parsing test262.properties...');
    const { failingTests, skippedCategories } = parseTest262Properties(propsFile);
    console.error(`Found ${failingTests.size} expected failing tests`);
    console.error(`Skipped categories: ${[...skippedCategories].join(', ')}`);

    console.error('Scanning test262 tests...');

    // Collect all tests by edition
    const testsByEdition = {};
    let totalTests = 0;
    let skippedTests = 0;

    walkDir(test262Dir, (fullPath, relPath) => {
        // Check if test is in a skipped category
        for (const cat of skippedCategories) {
            if (relPath.startsWith(cat)) {
                skippedTests++;
                return;
            }
        }

        try {
            const content = fs.readFileSync(fullPath, 'utf8');
            const features = extractFeatures(content);
            const edition = getTestEdition(features);
            const isFailing = failingTests.has(relPath);

            if (!testsByEdition[edition]) {
                testsByEdition[edition] = { passing: [], failing: [] };
            }

            const testInfo = {
                path: relPath,
                features: features,
            };

            if (isFailing) {
                testsByEdition[edition].failing.push(testInfo);
            } else {
                testsByEdition[edition].passing.push(testInfo);
            }

            totalTests++;
        } catch (e) {
            // Skip files we can't read
        }
    });

    console.error(`Processed ${totalTests} tests (${skippedTests} skipped)`);
    console.error('');

    // Output results
    if (args.edition !== null) {
        // Show specific edition
        const data = testsByEdition[args.edition];
        if (!data) {
            console.error(`No tests found for edition ${args.edition}`);
            process.exit(1);
        }

        const editionName = editionNames[args.edition] || `ES${args.edition}`;
        let tests = [];

        if (args.status === 'passing') {
            tests = data.passing;
        } else if (args.status === 'failing') {
            tests = data.failing;
        } else {
            tests = [
                ...data.passing.map(t => ({ ...t, status: 'passing' })),
                ...data.failing.map(t => ({ ...t, status: 'failing' })),
            ];
        }

        if (args.output === 'json') {
            console.log(JSON.stringify({
                edition: args.edition,
                editionName,
                status: args.status || 'all',
                count: tests.length,
                tests: tests,
            }, null, 2));
        } else {
            console.log(`${editionName} Tests (${args.status || 'all'}): ${tests.length}`);
            console.log('='.repeat(60));
            for (const test of tests) {
                const status = test.status ? ` [${test.status}]` : '';
                console.log(`${test.path}${status}`);
            }
        }
    } else {
        // Show summary of all editions
        const summary = {};
        const editions = Object.keys(testsByEdition).map(Number).sort((a, b) => a - b);

        for (const edition of editions) {
            const data = testsByEdition[edition];
            const editionName = editionNames[edition] || `ES${edition}`;
            summary[edition] = {
                name: editionName,
                passing: data.passing.length,
                failing: data.failing.length,
                total: data.passing.length + data.failing.length,
                passRate: ((data.passing.length / (data.passing.length + data.failing.length)) * 100).toFixed(1),
            };
        }

        if (args.output === 'json') {
            console.log(JSON.stringify(summary, null, 2));
        } else {
            console.log('Test262 Tests by ECMAScript Edition');
            console.log('='.repeat(70));
            console.log('Edition          | Passing | Failing | Total   | Pass Rate');
            console.log('-'.repeat(70));

            for (const edition of editions) {
                const s = summary[edition];
                console.log(
                    `${s.name.padEnd(16)} | ${String(s.passing).padStart(7)} | ${String(s.failing).padStart(7)} | ${String(s.total).padStart(7)} | ${s.passRate.padStart(6)}%`
                );
            }
        }
    }
}

main();
