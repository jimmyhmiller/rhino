#!/usr/bin/env node

/**
 * Test262 Status Report by Directory Category
 *
 * Shows test262 conformance grouped by directory path (e.g., language/, built-ins/).
 * Skipped categories are counted as failures.
 *
 * Usage:
 *   node scripts/test-categories.js                    # Top-level summary (depth 1)
 *   node scripts/test-categories.js 2                  # Depth 2 (e.g., language/statements)
 *   node scripts/test-categories.js 3                  # Depth 3
 *   node scripts/test-categories.js language            # Drill into a category
 *   node scripts/test-categories.js built-ins/Array     # Drill deeper
 *   node scripts/test-categories.js language --all      # Show individual failing tests
 *   node scripts/test-categories.js --failing           # Only show categories with failures
 *   node scripts/test-categories.js --help              # Show help
 */

const fs = require('fs');
const path = require('path');

function parseTest262Properties(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.split('\n');

    const failingTests = new Set();
    const skippedCategories = new Set();
    const failingDirs = new Set();

    let currentPath = [];

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (!line.trim() || line.trim().startsWith('#')) continue;

        if (line.trim().startsWith('~')) {
            const category = line.trim().slice(1).split(' ')[0];
            skippedCategories.add(category);
            continue;
        }

        const indent = line.match(/^(\s*)/)[1].length;
        const indentLevel = Math.floor(indent / 4);
        const trimmed = line.trim();

        if (/^\d+\/\d+/.test(trimmed)) continue;

        const statsMatch = trimmed.match(/^(.+?)\s+(\d+)\/(\d+)/);

        if (statsMatch) {
            const dirName = statsMatch[1];
            const failCount = parseInt(statsMatch[2], 10);
            const totalCount = parseInt(statsMatch[3], 10);

            currentPath = currentPath.slice(0, indentLevel);
            currentPath.push(dirName);

            const fullPath = currentPath.join('/');

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
                failingDirs.add(fullPath);
            }
        } else {
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

function isInFailingDir(testPath, failingDirs) {
    for (const dir of failingDirs) {
        if (testPath.startsWith(dir + '/')) {
            return true;
        }
    }
    return false;
}

function walkDir(dir, callback, relativePath = '') {
    if (!fs.existsSync(dir)) return;

    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        const relPath = relativePath ? `${relativePath}/${entry.name}` : entry.name;

        if (entry.isDirectory()) {
            if (entry.name === 'harness' || entry.name.startsWith('_')) continue;
            walkDir(fullPath, callback, relPath);
        } else if (entry.name.endsWith('.js') && !entry.name.startsWith('_')) {
            callback(fullPath, relPath);
        }
    }
}

function collectTests(test262Dir, failingTests, skippedCategories, failingDirs) {
    const tests = []; // { path, status: 'passing' | 'failing' | 'skipped' }

    walkDir(test262Dir, (fullPath, relPath) => {
        let isSkipped = false;
        for (const cat of skippedCategories) {
            if (relPath.startsWith(cat + '/') || relPath === cat) {
                isSkipped = true;
                break;
            }
        }

        let status;
        if (isSkipped) {
            status = 'skipped';
        } else if (failingTests.has(relPath) || isInFailingDir(relPath, failingDirs)) {
            status = 'failing';
        } else {
            status = 'passing';
        }

        tests.push({ path: relPath, status });
    });

    return tests;
}

function getCategoryKey(testPath, depth) {
    const parts = testPath.split('/');
    // Use up to `depth` directory components
    const dirParts = parts.slice(0, -1); // remove filename
    if (dirParts.length <= depth) {
        return dirParts.join('/');
    }
    return dirParts.slice(0, depth).join('/');
}

function groupByCategory(tests, depth, filterPrefix) {
    const categories = {};

    for (const test of tests) {
        // If filtering by prefix, skip tests not under that prefix
        if (filterPrefix && !test.path.startsWith(filterPrefix + '/')) continue;

        let key;
        if (filterPrefix) {
            // When filtering, show paths relative to the filter prefix at depth levels below it
            const relPath = test.path.slice(filterPrefix.length + 1);
            const parts = relPath.split('/');
            const dirParts = parts.slice(0, -1);
            if (dirParts.length <= depth) {
                key = filterPrefix + '/' + dirParts.join('/');
            } else {
                key = filterPrefix + '/' + dirParts.slice(0, depth).join('/');
            }
            // Clean up trailing slash if dirParts was empty
            key = key.replace(/\/$/, '');
        } else {
            key = getCategoryKey(test.path, depth);
        }

        if (!categories[key]) {
            categories[key] = { passing: 0, failing: 0, skipped: 0, failingPaths: [] };
        }

        if (test.status === 'skipped') {
            categories[key].skipped++;
            categories[key].failing++;
            categories[key].failingPaths.push(test.path);
        } else if (test.status === 'failing') {
            categories[key].failing++;
            categories[key].failingPaths.push(test.path);
        } else {
            categories[key].passing++;
        }
    }

    return categories;
}

function showHelp() {
    console.log(`
Test262 Status Report by Directory Category

Usage:
  node scripts/test-categories.js                     # Top-level summary
  node scripts/test-categories.js DEPTH               # Summary at depth N
  node scripts/test-categories.js CATEGORY             # Drill into a category
  node scripts/test-categories.js CATEGORY --all       # Show individual failing tests
  node scripts/test-categories.js --failing            # Only categories with failures
  node scripts/test-categories.js --help               # Show this help

Arguments:
  DEPTH       A number (1-5) to set grouping depth (default: 1)
  CATEGORY    A path prefix to drill into (e.g., "language", "built-ins/Array")

Options:
  --all       Show individual failing test paths
  --failing   Only show categories that have failures
  --depth N   Explicit depth when combined with CATEGORY

Examples:
  node scripts/test-categories.js                     # language, built-ins, annexB, etc.
  node scripts/test-categories.js 2                   # language/statements, built-ins/Array, etc.
  node scripts/test-categories.js language             # Subcategories under language/
  node scripts/test-categories.js language 2           # Two levels deep under language/
  node scripts/test-categories.js built-ins/Array      # Subcategories under built-ins/Array/
  node scripts/test-categories.js --failing            # Only failing categories
  node scripts/test-categories.js language --all       # Show all failing test paths
`);
}

function main() {
    const args = process.argv.slice(2);

    let filterPrefix = null;
    let depth = 1;
    let showAll = false;
    let failingOnly = false;
    let depthSet = false;

    for (let i = 0; i < args.length; i++) {
        const arg = args[i];
        if (arg === '-h' || arg === '--help') {
            showHelp();
            process.exit(0);
        } else if (arg === '--all') {
            showAll = true;
        } else if (arg === '--failing') {
            failingOnly = true;
        } else if (arg === '--depth') {
            if (i + 1 >= args.length) {
                console.error('--depth requires a number');
                process.exit(1);
            }
            depth = parseInt(args[++i], 10);
            depthSet = true;
        } else if (/^\d+$/.test(arg) && parseInt(arg, 10) <= 10) {
            // Small number = depth
            depth = parseInt(arg, 10);
            depthSet = true;
        } else if (arg.startsWith('-')) {
            console.error(`Unknown option: ${arg}`);
            console.error('Use --help for usage information.');
            process.exit(1);
        } else {
            // Category filter
            filterPrefix = arg.replace(/\/+$/, ''); // strip trailing slash
        }
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

    process.stderr.write('Parsing test262.properties...\n');
    const { failingTests, skippedCategories, failingDirs } = parseTest262Properties(propsFile);

    process.stderr.write('Scanning test262 tests...\n');
    const tests = collectTests(test262Dir, failingTests, skippedCategories, failingDirs);
    process.stderr.write(`Processed ${tests.length} tests\n\n`);

    const categories = groupByCategory(tests, depth, filterPrefix);

    // Sort by failure count descending
    const sorted = Object.entries(categories).sort((a, b) => b[1].failing - a[1].failing);

    const now = new Date().toISOString().replace('T', ' ').slice(0, 19);
    const title = filterPrefix
        ? `Test262 Status: ${filterPrefix}/`
        : `Test262 Status by Category (depth ${depth})`;

    console.log('========================================');
    console.log(`  ${title}`);
    console.log(`  ${now}`);
    console.log('========================================');
    console.log('');

    // Compute totals
    let totalPassing = 0;
    let totalFailing = 0;
    let totalSkipped = 0;

    for (const [, data] of sorted) {
        totalPassing += data.passing;
        totalFailing += data.failing;
        totalSkipped += data.skipped;
    }

    const grandTotal = totalPassing + totalFailing;
    const grandPct = grandTotal > 0 ? ((totalPassing / grandTotal) * 100).toFixed(1) : '0.0';

    console.log(`  Total: ${grandTotal} tests, ${totalPassing} passing (${grandPct}%), ${totalFailing} failing`);
    if (totalSkipped > 0) {
        console.log(`  (${totalSkipped} skipped, counted as failing)`);
    }
    console.log('');

    // Table header
    console.log('  Passing   Failing  Skipped    Total   Pass%   Category');
    console.log('  -------   -------  -------   ------   -----   --------');

    for (const [cat, data] of sorted) {
        if (failingOnly && data.failing === 0) continue;

        const total = data.passing + data.failing;
        const pct = total > 0 ? ((data.passing / total) * 100).toFixed(1) : '0.0';

        const displayCat = filterPrefix ? cat.slice(filterPrefix.length + 1) || filterPrefix : cat;

        console.log(
            '  ' + String(data.passing).padStart(7) +
            '   ' + String(data.failing).padStart(7) +
            '  ' + String(data.skipped).padStart(7) +
            '   ' + String(total).padStart(6) +
            '   ' + pct.padStart(5) + '%   ' + displayCat
        );

        if (showAll && data.failing > 0) {
            const paths = data.failingPaths.sort();
            for (const p of paths) {
                console.log('                                                - ' + p);
            }
        }
    }

    console.log('  -------   -------  -------   ------   -----');
    console.log(
        '  ' + String(totalPassing).padStart(7) +
        '   ' + String(totalFailing).padStart(7) +
        '  ' + String(totalSkipped).padStart(7) +
        '   ' + String(grandTotal).padStart(6) +
        '   ' + grandPct.padStart(5) + '%   TOTAL'
    );
    console.log('');
}

main();
