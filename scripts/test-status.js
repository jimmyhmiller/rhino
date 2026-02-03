#!/usr/bin/env node

/**
 * Test262 Status Report by ECMAScript Edition
 *
 * A self-contained script that shows test262 conformance status by ECMAScript edition.
 * Skipped test categories are counted as failures (since they're skipped because
 * Rhino can't run them).
 *
 * Usage:
 *   node scripts/test-status.js              # Summary of all editions
 *   node scripts/test-status.js 6            # Detailed ES6 report
 *   node scripts/test-status.js 6 --all      # ES6 report with individual failing tests
 *   node scripts/test-status.js --diff branch1 branch2   # Compare two branches
 *   node scripts/test-status.js --diff branch1 branch2 6 # Compare ES6 only
 *   node scripts/test-status.js --help       # Show help
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Feature-to-edition mapping (from test262-fyi)
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

function parseTest262PropertiesContent(content) {
    const lines = content.split('\n');

    const failingTests = new Set();
    const skippedCategories = new Set();
    const failingDirs = new Set(); // Directories where all tests fail

    let currentPath = [];
    let lastDirAtLevel = {}; // Track last directory seen at each indent level

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
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
        const trimmed = line.trim();

        // Skip lines that are just stats like "3/53 (5.66%)"
        if (/^\d+\/\d+/.test(trimmed)) continue;

        // Check if this is a directory (contains stats) or a file
        const statsMatch = trimmed.match(/^(.+?)\s+(\d+)\/(\d+)/);

        if (statsMatch) {
            // This is a directory with stats
            const dirName = statsMatch[1];
            const failCount = parseInt(statsMatch[2], 10);
            const totalCount = parseInt(statsMatch[3], 10);

            // Adjust path based on indent
            currentPath = currentPath.slice(0, indentLevel);
            currentPath.push(dirName);

            const fullPath = currentPath.join('/');
            lastDirAtLevel[indentLevel] = { path: fullPath, failCount, totalCount };

            // Check if next non-empty line is at same or lower indent level
            // If so, this directory has no children listed = all tests fail
            let hasChildren = false;
            for (let j = i + 1; j < lines.length; j++) {
                const nextLine = lines[j];
                if (!nextLine.trim() || nextLine.trim().startsWith('#')) continue;
                const nextIndent = nextLine.match(/^(\s*)/)[1].length;
                const nextLevel = Math.floor(nextIndent / 4);
                if (nextLevel > indentLevel) {
                    hasChildren = true;
                }
                break;
            }

            if (!hasChildren && failCount === totalCount) {
                // All tests in this directory fail, none listed individually
                failingDirs.add(fullPath);
            }
        } else {
            // Check if this line contains a .js file (may have annotations after)
            const jsMatch = trimmed.match(/^(\S+\.js)/);
            if (jsMatch) {
                const fileName = jsMatch[1];
                const testPath = [...currentPath.slice(0, indentLevel), fileName].join('/');
                failingTests.add(testPath);
            }
        }
    }

    return { failingTests, skippedCategories, failingDirs };
}

function parseTest262Properties(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    return parseTest262PropertiesContent(content);
}

function isInFailingDir(testPath, failingDirs) {
    for (const dir of failingDirs) {
        if (testPath.startsWith(dir + '/')) {
            return true;
        }
    }
    return false;
}

function getPropertiesFromBranch(branch, rootDir) {
    const propsPath = 'tests/testsrc/test262.properties';
    try {
        const content = execSync(`git show ${branch}:${propsPath}`, {
            cwd: rootDir,
            encoding: 'utf8',
            stdio: ['pipe', 'pipe', 'pipe']
        });
        return parseTest262PropertiesContent(content);
    } catch (e) {
        console.error(`Error reading test262.properties from branch '${branch}':`);
        console.error(e.message);
        process.exit(1);
    }
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

function collectTests(test262Dir, failingTests, skippedCategories, failingDirs = new Set()) {
    const testsByEdition = {};
    let totalTests = 0;
    let skippedTests = 0;

    walkDir(test262Dir, (fullPath, relPath) => {
        // Check if test is in a skipped category - these count as FAILING
        let isInSkippedCategory = false;
        for (const cat of skippedCategories) {
            if (relPath.startsWith(cat + '/') || relPath === cat) {
                isInSkippedCategory = true;
                skippedTests++;
                break;
            }
        }

        try {
            const content = fs.readFileSync(fullPath, 'utf8');
            const features = extractFeatures(content);
            const edition = getTestEdition(features);

            if (!testsByEdition[edition]) {
                testsByEdition[edition] = { passing: [], failing: [], skipped: [] };
            }

            const testInfo = {
                path: relPath,
                features: features,
            };

            if (isInSkippedCategory) {
                testsByEdition[edition].skipped.push(testInfo);
                testsByEdition[edition].failing.push(testInfo);
            } else if (failingTests.has(relPath) || isInFailingDir(relPath, failingDirs)) {
                testsByEdition[edition].failing.push(testInfo);
            } else {
                testsByEdition[edition].passing.push(testInfo);
            }

            totalTests++;
        } catch (e) {
            // Skip files we can't read
        }
    });

    return { testsByEdition, totalTests, skippedTests };
}

function showHelp() {
    console.log(`
Test262 Status Report by ECMAScript Edition

Usage:
  node test-status.js              # Summary of all editions
  node test-status.js EDITION      # Detailed report for an edition
  node test-status.js EDITION --all  # Include individual failing tests
  node test-status.js --diff BRANCH1 BRANCH2         # Compare two branches
  node test-status.js --diff BRANCH1 BRANCH2 EDITION # Compare specific edition

Editions:
  5    ES5 (baseline)
  6    ES6/ES2015
  7    ES2016
  8    ES2017
  9    ES2018
  10   ES2019
  11   ES2020
  12   ES2021
  13   ES2022
  14   ES2023
  15   ES2024
  16   ES2025
  99   ESNext/Proposals

Examples:
  node test-status.js           # See pass rates for all editions
  node test-status.js 6         # Detailed ES6 breakdown
  node test-status.js 11 --all  # ES2020 with all failing test paths
  node test-status.js --diff master HEAD      # Compare current branch to master
  node test-status.js --diff master HEAD 6    # Compare ES6 only
`);
}

function showSummary(testsByEdition) {
    const now = new Date().toISOString().replace('T', ' ').slice(0, 19);
    console.log('========================================');
    console.log('  Test262 Status by Edition');
    console.log(`  ${now}`);
    console.log('========================================');
    console.log('');
    console.log("  Note: 'Skipped' = tests in categories Rhino can't run (included in Failing)");
    console.log('');
    console.log('  Edition           Passing   Failing   Skipped     Total    Pass %');
    console.log('  ---------------   -------   -------   -------   -------   ------');

    const editions = Object.keys(testsByEdition).map(Number).sort((a, b) => a - b);
    let totalPass = 0;
    let totalFail = 0;
    let totalSkip = 0;

    for (const edition of editions) {
        const data = testsByEdition[edition];
        const name = editionNames[edition] || `ES${edition}`;
        const passing = data.passing.length;
        const failing = data.failing.length;
        const skipped = data.skipped.length;
        const total = passing + failing;
        const passRate = total > 0 ? ((passing / total) * 100).toFixed(1) : '0.0';

        totalPass += passing;
        totalFail += failing;
        totalSkip += skipped;

        console.log(
            '  ' + name.padEnd(15) +
            '   ' + String(passing).padStart(7) +
            '   ' + String(failing).padStart(7) +
            '   ' + String(skipped).padStart(7) +
            '   ' + String(total).padStart(7) +
            '   ' + passRate.padStart(5) + '%'
        );
    }

    const grandTotal = totalPass + totalFail;
    const grandPct = grandTotal > 0 ? ((totalPass / grandTotal) * 100).toFixed(1) : '0.0';

    console.log('  ---------------   -------   -------   -------   -------   ------');
    console.log(
        '  ' + 'TOTAL'.padEnd(15) +
        '   ' + String(totalPass).padStart(7) +
        '   ' + String(totalFail).padStart(7) +
        '   ' + String(totalSkip).padStart(7) +
        '   ' + String(grandTotal).padStart(7) +
        '   ' + grandPct.padStart(5) + '%'
    );
    console.log('');
}

function showEditionDetails(testsByEdition, edition, showAll) {
    const data = testsByEdition[edition];
    if (!data) {
        console.error(`No tests found for edition ${edition}`);
        process.exit(1);
    }

    const editionName = editionNames[edition] || `ES${edition}`;
    const now = new Date().toISOString().replace('T', ' ').slice(0, 19);

    console.log('========================================');
    console.log(`  ${editionName} Test Status Report`);
    console.log(`  ${now}`);
    console.log('========================================');
    console.log('');

    const passing = data.passing.length;
    const failing = data.failing.length;
    const skipped = data.skipped.length;
    const total = passing + failing;
    const passRate = total > 0 ? ((passing / total) * 100).toFixed(1) : '0.0';

    console.log('========================================');
    console.log('  Summary');
    console.log('========================================');
    console.log('');
    console.log(`  Total ${editionName} tests:  ${String(total).padStart(5)}`);
    console.log(`  Passing:            ${String(passing).padStart(5)} (${passRate}%)`);
    console.log(`  Failing:            ${String(failing).padStart(5)}`);
    console.log(`  (Skipped):          ${String(skipped).padStart(5)}`);
    console.log('');

    if (failing > 0) {
        // Group failures by category
        const catFail = {};
        const catTotal = {};

        for (const test of data.failing) {
            const parts = test.path.split('/');
            const cat = parts.slice(0, 2).join('/');
            catFail[cat] = (catFail[cat] || 0) + 1;
            catTotal[cat] = (catTotal[cat] || 0) + 1;
        }
        for (const test of data.passing) {
            const parts = test.path.split('/');
            const cat = parts.slice(0, 2).join('/');
            catTotal[cat] = (catTotal[cat] || 0) + 1;
        }

        console.log('========================================');
        console.log('  Failures by Category');
        console.log('========================================');
        console.log('');
        console.log('  Fail   Total      %   Category');
        console.log('  ----   -----   ----   --------');

        // Sort by failure count descending
        const sortedCats = Object.entries(catFail).sort((a, b) => b[1] - a[1]);

        // Build subcategory data for categories with >100 failures
        const subCatFail = {};
        const subCatTotal = {};
        for (const test of data.failing) {
            const parts = test.path.split('/');
            if (parts.length >= 3) {
                const subCat = parts.slice(0, 3).join('/');
                subCatFail[subCat] = (subCatFail[subCat] || 0) + 1;
                subCatTotal[subCat] = (subCatTotal[subCat] || 0) + 1;
            }
        }
        for (const test of data.passing) {
            const parts = test.path.split('/');
            if (parts.length >= 3) {
                const subCat = parts.slice(0, 3).join('/');
                subCatTotal[subCat] = (subCatTotal[subCat] || 0) + 1;
            }
        }

        for (const [cat, failCount] of sortedCats) {
            const totalCount = catTotal[cat];
            const pct = ((failCount / totalCount) * 100).toFixed(1);
            console.log(
                '  ' + String(failCount).padStart(4) +
                ' / ' + String(totalCount).padStart(4) +
                '   ' + pct.padStart(4) + '%   ' + cat
            );

            // Show subcategory breakdown for categories with >100 failures
            if (failCount > 100) {
                const subCats = Object.entries(subCatFail)
                    .filter(([subCat]) => subCat.startsWith(cat + '/'))
                    .sort((a, b) => b[1] - a[1]);

                for (const [subCat, subFailCount] of subCats) {
                    const subTotalCount = subCatTotal[subCat];
                    const subPct = ((subFailCount / subTotalCount) * 100).toFixed(1);
                    const subName = subCat.split('/').slice(2).join('/'); // Just the subcategory name
                    console.log(
                        '        ' + String(subFailCount).padStart(4) +
                        ' / ' + String(subTotalCount).padStart(4) +
                        '   ' + subPct.padStart(4) + '%     └─ ' + subName
                    );
                }
            }

            if (showAll) {
                const testsInCat = data.failing
                    .filter(t => t.path.startsWith(cat + '/'))
                    .sort((a, b) => a.path.localeCompare(b.path));
                for (const t of testsInCat) {
                    console.log('                         - ' + t.path);
                }
                console.log('');
            }
        }
        console.log('');
    }

    // Categories with 100% pass rate
    const catPass = {};
    const catTotal = {};

    for (const test of data.passing) {
        const parts = test.path.split('/');
        const cat = parts.slice(0, 2).join('/');
        catPass[cat] = (catPass[cat] || 0) + 1;
        catTotal[cat] = (catTotal[cat] || 0) + 1;
    }
    for (const test of data.failing) {
        const parts = test.path.split('/');
        const cat = parts.slice(0, 2).join('/');
        catTotal[cat] = (catTotal[cat] || 0) + 1;
    }

    const perfectCats = Object.entries(catTotal)
        .filter(([cat, total]) => (catPass[cat] || 0) === total)
        .sort((a, b) => b[1] - a[1]);

    if (perfectCats.length > 0) {
        console.log('========================================');
        console.log('  Categories with 100% Pass Rate');
        console.log('========================================');
        console.log('');
        for (const [cat, total] of perfectCats) {
            console.log(`  ${String(total).padStart(4)} tests - ${cat}`);
        }
        console.log('');
    }

    console.log('========================================');
    console.log(`  To improve ${editionName} conformance:`);
    console.log('========================================');
    console.log('');
    console.log('  1. Run specific tests:');
    console.log('     ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \\');
    console.log('         -Dtest262filter="built-ins/Promise/*" -Dtest262raw');
    console.log('');
    console.log('  2. Update test262.properties after fixes:');
    console.log('     RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test \\');
    console.log('         --tests org.mozilla.javascript.tests.Test262SuiteTest \\');
    console.log('         --rerun-tasks -DupdateTest262properties');
    console.log('');
}

function showDiff(branch1, branch2, testsByEdition1, testsByEdition2, filterEdition, showAll) {
    const now = new Date().toISOString().replace('T', ' ').slice(0, 19);
    console.log('========================================');
    console.log(`  Test262 Diff: ${branch1} → ${branch2}`);
    console.log(`  ${now}`);
    console.log('========================================');
    console.log('');

    // Get all editions
    const allEditions = new Set([
        ...Object.keys(testsByEdition1).map(Number),
        ...Object.keys(testsByEdition2).map(Number)
    ]);
    const editions = [...allEditions].sort((a, b) => a - b);

    // Filter if requested
    const editionsToShow = filterEdition !== null
        ? editions.filter(e => e === filterEdition)
        : editions;

    if (filterEdition !== null && !editionsToShow.length) {
        console.error(`No tests found for edition ${filterEdition}`);
        process.exit(1);
    }

    // Collect improvements and regressions
    let totalImprovements = 0;
    let totalRegressions = 0;
    const improvementsByEdition = {};
    const regressionsByEdition = {};

    for (const edition of editionsToShow) {
        const data1 = testsByEdition1[edition] || { passing: [], failing: [] };
        const data2 = testsByEdition2[edition] || { passing: [], failing: [] };

        const failing1 = new Set(data1.failing.map(t => t.path));
        const failing2 = new Set(data2.failing.map(t => t.path));
        const passing1 = new Set(data1.passing.map(t => t.path));
        const passing2 = new Set(data2.passing.map(t => t.path));

        // Improvements: was failing in branch1, now passing in branch2
        const improvements = [...failing1].filter(t => passing2.has(t)).sort();
        // Regressions: was passing in branch1, now failing in branch2
        const regressions = [...passing1].filter(t => failing2.has(t)).sort();

        if (improvements.length > 0) {
            improvementsByEdition[edition] = improvements;
            totalImprovements += improvements.length;
        }
        if (regressions.length > 0) {
            regressionsByEdition[edition] = regressions;
            totalRegressions += regressions.length;
        }
    }

    // Summary table
    console.log('  Edition           ' + branch1.padEnd(12) + branch2.padEnd(12) + '  Change');
    console.log('  ---------------   ----------  ----------  -------');

    for (const edition of editionsToShow) {
        const data1 = testsByEdition1[edition] || { passing: [], failing: [] };
        const data2 = testsByEdition2[edition] || { passing: [], failing: [] };

        const pass1 = data1.passing.length;
        const total1 = pass1 + data1.failing.length;
        const pass2 = data2.passing.length;
        const total2 = pass2 + data2.failing.length;

        const pct1 = total1 > 0 ? ((pass1 / total1) * 100).toFixed(1) : '0.0';
        const pct2 = total2 > 0 ? ((pass2 / total2) * 100).toFixed(1) : '0.0';

        const diff = pass2 - pass1;
        const diffStr = diff > 0 ? `+${diff}` : diff === 0 ? '0' : String(diff);
        const diffColor = diff > 0 ? '\x1b[32m' : diff < 0 ? '\x1b[31m' : '';
        const reset = diff !== 0 ? '\x1b[0m' : '';

        const name = editionNames[edition] || `ES${edition}`;
        console.log(
            '  ' + name.padEnd(15) +
            '   ' + `${pct1}%`.padStart(10) +
            '  ' + `${pct2}%`.padStart(10) +
            '  ' + diffColor + diffStr.padStart(7) + reset
        );
    }

    console.log('');
    console.log(`  Total: ${totalImprovements} improvements, ${totalRegressions} regressions`);
    console.log('');

    if (!showAll) {
        if (totalImprovements > 0 || totalRegressions > 0) {
            console.log('  Use --all to see individual test changes.');
            console.log('');
        }
        return;
    }

    // Show improvements
    if (totalImprovements > 0) {
        console.log('========================================');
        console.log('  Improvements (now passing)');
        console.log('========================================');
        console.log('');

        for (const edition of editionsToShow) {
            const improvements = improvementsByEdition[edition];
            if (!improvements) continue;

            const name = editionNames[edition] || `ES${edition}`;
            console.log(`  ${name} (+${improvements.length}):`);
            for (const test of improvements) {
                console.log(`    \x1b[32m+ ${test}\x1b[0m`);
            }
            console.log('');
        }
    }

    // Show regressions
    if (totalRegressions > 0) {
        console.log('========================================');
        console.log('  Regressions (now failing)');
        console.log('========================================');
        console.log('');

        for (const edition of editionsToShow) {
            const regressions = regressionsByEdition[edition];
            if (!regressions) continue;

            const name = editionNames[edition] || `ES${edition}`;
            console.log(`  ${name} (-${regressions.length}):`);
            for (const test of regressions) {
                console.log(`    \x1b[31m- ${test}\x1b[0m`);
            }
            console.log('');
        }
    }

    if (totalImprovements === 0 && totalRegressions === 0) {
        console.log('  No changes in test pass/fail status between branches.');
        console.log('');
    }
}

function main() {
    const args = process.argv.slice(2);

    // Parse arguments
    let edition = null;
    let showAll = false;
    let diffMode = false;
    let branch1 = null;
    let branch2 = null;

    for (let i = 0; i < args.length; i++) {
        const arg = args[i];
        if (arg === '-h' || arg === '--help') {
            showHelp();
            process.exit(0);
        } else if (arg === '--all') {
            showAll = true;
        } else if (arg === '--diff') {
            diffMode = true;
            // Next two args should be branches
            if (i + 2 >= args.length) {
                console.error('--diff requires two branch names');
                console.error('Usage: node test-status.js --diff BRANCH1 BRANCH2 [EDITION]');
                process.exit(1);
            }
            branch1 = args[++i];
            branch2 = args[++i];
        } else if (/^\d+$/.test(arg)) {
            edition = parseInt(arg, 10);
        } else {
            console.error(`Unknown option: ${arg}`);
            console.error('Use --help for usage information.');
            process.exit(1);
        }
    }

    // Find paths relative to script location
    const scriptDir = __dirname;
    const rootDir = path.dirname(scriptDir);
    const test262Dir = path.join(rootDir, 'tests', 'test262', 'test');
    const propsFile = path.join(rootDir, 'tests', 'testsrc', 'test262.properties');

    // Check prerequisites
    if (!fs.existsSync(test262Dir)) {
        console.error(`Error: test262 directory not found at ${test262Dir}`);
        console.error('Make sure the test262 submodule is initialized: git submodule update --init');
        process.exit(1);
    }

    if (diffMode) {
        // Diff mode: compare two branches
        process.stderr.write(`Comparing ${branch1} → ${branch2}...\n`);

        process.stderr.write(`Reading test262.properties from ${branch1}...\n`);
        const props1 = getPropertiesFromBranch(branch1, rootDir);
        process.stderr.write(`Found ${props1.failingTests.size} expected failing tests, ${props1.failingDirs.size} failing dirs\n`);

        process.stderr.write(`Reading test262.properties from ${branch2}...\n`);
        const props2 = getPropertiesFromBranch(branch2, rootDir);
        process.stderr.write(`Found ${props2.failingTests.size} expected failing tests, ${props2.failingDirs.size} failing dirs\n`);

        process.stderr.write('Scanning test262 tests...\n');
        const { testsByEdition: tests1, totalTests } = collectTests(test262Dir, props1.failingTests, props1.skippedCategories, props1.failingDirs);
        const { testsByEdition: tests2 } = collectTests(test262Dir, props2.failingTests, props2.skippedCategories, props2.failingDirs);
        process.stderr.write(`Processed ${totalTests} tests\n\n`);

        showDiff(branch1, branch2, tests1, tests2, edition, showAll);
    } else {
        // Normal mode
        if (!fs.existsSync(propsFile)) {
            console.error(`Error: test262.properties not found at ${propsFile}`);
            process.exit(1);
        }

        // Parse test262.properties
        process.stderr.write('Parsing test262.properties...\n');
        const { failingTests, skippedCategories, failingDirs } = parseTest262Properties(propsFile);
        process.stderr.write(`Found ${failingTests.size} expected failing tests, ${failingDirs.size} failing dirs\n`);
        process.stderr.write(`Skipped categories: ${[...skippedCategories].slice(0, 5).join(', ')}${skippedCategories.size > 5 ? '...' : ''}\n`);

        // Collect all tests
        process.stderr.write('Scanning test262 tests...\n');
        const { testsByEdition, totalTests, skippedTests } = collectTests(test262Dir, failingTests, skippedCategories, failingDirs);
        process.stderr.write(`Processed ${totalTests} tests (${skippedTests} in skipped categories, counted as failing)\n\n`);

        // Show results
        if (edition !== null) {
            showEditionDetails(testsByEdition, edition, showAll);
        } else {
            showSummary(testsByEdition);
        }
    }
}

main();
