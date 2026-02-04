#!/usr/bin/env node

/**
 * Generates features.json and editions.json from test262 test results.
 *
 * This uses the exact featureByEdition mapping from test262-fyi:
 * https://github.com/test262-fyi/test262.fyi/blob/main/controller/generate.js
 *
 * Usage: node generate-features-editions.js <results-dir> <test262-path> --output <dir>
 */

const fs = require('fs');
const path = require('path');

// Proposal metadata for stage 3+ proposals
// Based on https://github.com/test262-fyi/test262.fyi/blob/main/controller/generate.js
const proposalInfo = {
    "Intl.Locale-info": { name: "Intl Locale Info", stage: 3, link: "https://github.com/tc39/proposal-intl-locale-info" },
    "Intl.NumberFormat-v3": { name: "Intl.NumberFormat V3", stage: 3, link: "https://github.com/tc39/proposal-intl-numberformat-v3" },
    "legacy-regexp": { name: "Legacy RegExp features", stage: 3, link: "https://github.com/tc39/proposal-regexp-legacy-features" },
    "Temporal": { name: "Temporal", stage: 3, link: "https://github.com/tc39/proposal-temporal" },
    "ShadowRealm": { name: "ShadowRealm", stage: 3, link: "https://github.com/tc39/proposal-shadowrealm" },
    "decorators": { name: "Decorators", stage: 3, link: "https://github.com/tc39/proposal-decorators" },
    "regexp-duplicate-named-groups": { name: "Duplicate named capturing groups", stage: 4, link: "https://github.com/tc39/proposal-duplicate-named-capturing-groups" },
    "Array.fromAsync": { name: "Array.fromAsync", stage: 4, link: "https://github.com/tc39/proposal-array-from-async" },
    "explicit-resource-management": { name: "Explicit Resource Management", stage: 3, link: "https://github.com/tc39/proposal-explicit-resource-management" },
    "source-phase-imports": { name: "Source Phase Imports", stage: 3, link: "https://github.com/tc39/proposal-source-phase-imports" },
    "source-phase-imports-module-source": { name: "Source Phase Imports (Module Source)", stage: 3, link: "https://github.com/tc39/proposal-source-phase-imports" },
    "Atomics.pause": { name: "Atomics.pause", stage: 3, link: "https://github.com/tc39/proposal-atomics-microwait" },
    "import-defer": { name: "Deferred Import Evaluation", stage: 3, link: "https://github.com/tc39/proposal-defer-import-eval" },
    "canonical-tz": { name: "Time Zone Canonicalization", stage: 3, link: "https://github.com/tc39/proposal-canonical-tz" },
    "upsert": { name: "Map.prototype.upsert", stage: 2, link: "https://github.com/tc39/proposal-upsert" },
    "immutable-arraybuffer": { name: "Immutable ArrayBuffer", stage: 3, link: "https://github.com/tc39/proposal-immutable-arraybuffer" },
    "json-parse-with-source": { name: "JSON.parse with source", stage: 4, link: "https://github.com/tc39/proposal-json-parse-with-source" },
    "Math.sumPrecise": { name: "Math.sumPrecise", stage: 3, link: "https://github.com/tc39/proposal-math-sum" },
    "uint8array-base64": { name: "Uint8Array Base64", stage: 4, link: "https://github.com/tc39/proposal-arraybuffer-base64" },
    "iterator-sequencing": { name: "Iterator Sequencing", stage: 3, link: "https://github.com/tc39/proposal-iterator-sequencing" },
    "Error.isError": { name: "Error.isError", stage: 3, link: "https://github.com/tc39/proposal-is-error" },
};

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

function parseArgs(args) {
    let resultsDir = null;
    let test262Path = null;
    let outputDir = null;
    let engineName = null;

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--output') {
            outputDir = args[++i];
        } else if (args[i] === '--engine') {
            engineName = args[++i];
        } else if (!resultsDir) {
            resultsDir = args[i];
        } else if (!test262Path) {
            test262Path = args[i];
        }
    }

    if (!resultsDir || !test262Path || !outputDir || !engineName) {
        console.error('Usage: node generate-features-editions.js <results-dir> <test262-path> --engine <name> --output <dir>');
        process.exit(1);
    }

    return { resultsDir, test262Path, outputDir, engineName };
}

function readJsonSafe(filePath) {
    try {
        return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (e) {
        return null;
    }
}

function walkDir(dir, callback, relativePath = '') {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        const relPath = relativePath ? `${relativePath}/${entry.name}` : entry.name;
        if (entry.isDirectory()) {
            walkDir(fullPath, callback, relPath);
        } else if (entry.name.endsWith('.js')) {
            callback(fullPath, relPath);
        }
    }
}

function extractFeatures(content) {
    // Try single-line format: features: [feat1, feat2]
    let match = content.match(/^features: \[(.*)\]$/m);
    if (match) {
        return match[1].split(',').map(x => x.trim()).filter(x => x);
    }

    // Try multi-line format:
    // features:
    //   - feat1
    //   - feat2
    match = content.match(/^features:\n((?:  - .*\n?)+)/m);
    if (match) {
        return match[1].split('\n')
            .map(x => x.replace(/^  - /, '').trim())
            .filter(x => x);
    }

    return [];
}

function collectTestResults(resultsDir, engineName) {
    // Recursively find all JSON files and extract test results
    const results = {};

    function processJsonFile(filePath, relPath) {
        const data = readJsonSafe(filePath);
        if (!data) return;

        // Handle test results in the "tests" field
        if (data.tests) {
            for (const [testFile, engineResults] of Object.entries(data.tests)) {
                // Get the result for this engine
                const passed = engineResults[engineName];
                if (passed !== undefined) {
                    // Construct full test path - relPath is like "language/statements/class/definition.json"
                    // Strip .json to get "language/statements/class/definition"
                    const dir = relPath.replace(/\.json$/, '');
                    const fullPath = `${dir}/${testFile}`;
                    results[fullPath] = passed;
                }
            }
        }
    }

    function walkJsonDir(dir, relPath = '') {
        if (!fs.existsSync(dir)) return;
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
            const fullPath = path.join(dir, entry.name);
            const newRelPath = relPath ? `${relPath}/${entry.name}` : entry.name;
            if (entry.isDirectory()) {
                walkJsonDir(fullPath, newRelPath);
            } else if (entry.name.endsWith('.json') && entry.name !== 'index.json' && entry.name !== 'engines.json') {
                processJsonFile(fullPath, newRelPath);
            }
        }
    }

    walkJsonDir(resultsDir);
    return results;
}

function main() {
    const { resultsDir, test262Path, outputDir, engineName } = parseArgs(process.argv.slice(2));

    console.log(`Generating features.json and editions.json`);
    console.log(`  Results dir: ${resultsDir}`);
    console.log(`  Test262 path: ${test262Path}`);
    console.log(`  Engine: ${engineName}`);
    console.log(`  Output: ${outputDir}`);

    // Collect test results
    const testResults = collectTestResults(resultsDir, engineName);
    console.log(`Loaded ${Object.keys(testResults).length} test results`);

    // Read features.txt from test262
    const featuresPath = path.join(test262Path, 'features.txt');
    const featuresContent = fs.readFileSync(featuresPath, 'utf8');
    const features = featuresContent.split('\n')
        .filter(x => x && x[0] !== '#')
        .map(x => x.split('#')[0].trim())
        .filter(x => x);

    console.log(`Found ${features.length} features in features.txt`);

    // Map tests to their features
    const testsWithFeatures = {};
    const testDir = path.join(test262Path, 'test');

    walkDir(testDir, (fullPath, relPath) => {
        try {
            const content = fs.readFileSync(fullPath, 'utf8');
            const testFeatures = extractFeatures(content);
            if (testFeatures.length > 0) {
                testsWithFeatures[relPath] = testFeatures;
            }
        } catch (e) {
            // Skip files we can't read
        }
    });

    console.log(`Parsed features for ${Object.keys(testsWithFeatures).length} tests`);

    // Build feature results and edition results
    // Use Object.create(null) to avoid issues with __proto__ as a key
    const featureResults = Object.create(null);
    const editionResults = Object.create(null);

    for (const feature of features) {
        let edition = featureByEdition.get(feature);
        if (edition === undefined) {
            console.warn(`Feature '${feature}' has no associated edition, treating as ESNext`);
            edition = 99;
        }
        // Edition 99 becomes undefined (ESNext)
        const editionKey = edition === 99 ? 'undefined' : String(edition);

        if (!featureResults[feature]) {
            featureResults[feature] = { total: 0, engines: {}, proposal: proposalInfo[feature] || null };
        }
        if (!editionResults[editionKey]) {
            editionResults[editionKey] = { total: 0, engines: {} };
        }

        const featureResult = featureResults[feature];
        const editionResult = editionResults[editionKey];

        // Initialize engine counts
        if (featureResult.engines[engineName] === undefined) {
            featureResult.engines[engineName] = 0;
        }
        if (editionResult.engines[engineName] === undefined) {
            editionResult.engines[engineName] = 0;
        }

        // Find all tests that use this feature AND were actually run
        // Tests not in testResults were skipped (async, etc.) and shouldn't count
        for (const [testPath, testFeatures] of Object.entries(testsWithFeatures)) {
            if (testFeatures.includes(feature)) {
                // Only count tests that were actually run (present in testResults)
                if (testResults[testPath] !== undefined) {
                    featureResult.total++;
                    editionResult.total++;

                    // Check if test passed
                    if (testResults[testPath]) {
                        featureResult.engines[engineName]++;
                        editionResult.engines[engineName]++;
                    }
                }
            }
        }
    }

    // Write output files
    fs.mkdirSync(outputDir, { recursive: true });
    fs.writeFileSync(
        path.join(outputDir, 'features.json'),
        JSON.stringify(featureResults, null, 2)
    );
    fs.writeFileSync(
        path.join(outputDir, 'editions.json'),
        JSON.stringify(editionResults, null, 2)
    );

    console.log('Wrote features.json and editions.json');

    // Print summary
    let totalFeatureTests = 0;
    let totalFeaturePassed = 0;
    for (const [feature, result] of Object.entries(featureResults)) {
        totalFeatureTests += result.total;
        totalFeaturePassed += result.engines[engineName] || 0;
    }
    console.log(`Features: ${totalFeaturePassed}/${totalFeatureTests} tests passed`);

    for (const [edition, result] of Object.entries(editionResults)) {
        const passed = result.engines[engineName] || 0;
        const editionName = edition === 'undefined' ? 'ESNext' : `ES${edition}`;
        console.log(`  ${editionName}: ${passed}/${result.total}`);
    }
}

main();
