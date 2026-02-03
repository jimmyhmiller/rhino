#!/usr/bin/env node

/**
 * Merges test262 JSON results from multiple engine directories into a single
 * combined structure in test262.fyi format.
 *
 * Usage: node merge-test262-results.js <engine1-name>:<engine1-dir> <engine2-name>:<engine2-dir> ... --output <dir>
 *
 * Example:
 *   node merge-test262-results.js upstream:./upstream-results fork:./fork-results --output ./combined
 */

const fs = require('fs');
const path = require('path');

function parseArgs(args) {
    const engines = [];
    let outputDir = null;

    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--output') {
            outputDir = args[++i];
        } else if (args[i].includes(':')) {
            const [name, dir] = args[i].split(':');
            engines.push({ name, dir });
        }
    }

    if (engines.length === 0 || !outputDir) {
        console.error('Usage: node merge-test262-results.js <engine1-name>:<engine1-dir> ... --output <dir>');
        process.exit(1);
    }

    return { engines, outputDir };
}

function readJsonSafe(filePath) {
    try {
        return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (e) {
        return null;
    }
}

function mergeEngineData(engines, fileName) {
    const results = {};

    for (const engine of engines) {
        const filePath = path.join(engine.dir, fileName);
        const data = readJsonSafe(filePath);
        if (data) {
            results[engine.name] = data;
        }
    }

    return results;
}

function mergeIndexJson(engines) {
    const merged = {
        total: 0,
        engines: {},
        files: {}
    };

    // First pass: get structure and find max total
    let baseData = null;
    let maxTotal = 0;
    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, 'index.json'));
        if (data) {
            if (!baseData) baseData = data;
            if (data.total > maxTotal) maxTotal = data.total;
        }
    }

    if (!baseData) {
        return merged;
    }

    merged.total = maxTotal;

    // Collect engine pass counts
    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, 'index.json'));
        if (data && data.engines) {
            // The engine's own data has its name as key
            const engineCount = Object.values(data.engines)[0];
            merged.engines[engine.name] = engineCount || 0;
        }
    }

    // Merge files structure
    if (baseData.files) {
        merged.files = mergeFilesRecursive(engines, baseData.files, '');
    }

    return merged;
}

function mergeFilesRecursive(engines, baseFiles, parentPath) {
    const merged = {};

    for (const [key, value] of Object.entries(baseFiles)) {
        if (typeof value === 'object' && value !== null) {
            // Find max total across all engines for this file/directory
            let maxTotal = value.total || 0;
            for (const engine of engines) {
                const engineIndex = readJsonSafe(path.join(engine.dir, 'index.json'));
                if (engineIndex && engineIndex.files) {
                    const engineValue = getNestedValue(engineIndex.files, parentPath ? `${parentPath}.${key}` : key);
                    if (engineValue && engineValue.total > maxTotal) {
                        maxTotal = engineValue.total;
                    }
                }
            }

            const entry = {
                total: maxTotal,
                engines: {}
            };

            // Get pass count from each engine
            for (const engine of engines) {
                const engineIndex = readJsonSafe(path.join(engine.dir, 'index.json'));
                if (engineIndex && engineIndex.files) {
                    const engineValue = getNestedValue(engineIndex.files, parentPath ? `${parentPath}.${key}` : key);
                    if (engineValue && engineValue.engines) {
                        entry.engines[engine.name] = Object.values(engineValue.engines)[0] || 0;
                    }
                }
            }

            merged[key] = entry;
        }
    }

    return merged;
}

function getNestedValue(obj, path) {
    const keys = path.split('.');
    let current = obj;
    for (const key of keys) {
        if (current && typeof current === 'object' && key in current) {
            current = current[key];
        } else {
            return undefined;
        }
    }
    return current;
}

function mergeEnginesJson(engines) {
    const merged = {};

    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, 'engines.json'));
        if (data) {
            // Get the version from the engine's own data
            const version = Object.values(data)[0];
            merged[engine.name] = version || 'unknown';
        }
    }

    return merged;
}

function mergeDirectoryJson(engines, relativePath) {
    const merged = {
        total: 0,
        engines: {},
        files: {},
        tests: {}
    };

    // Get base structure from first engine and find max total across all engines
    let baseData = null;
    let maxTotal = 0;
    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, relativePath));
        if (data) {
            if (!baseData) baseData = data;
            // Use the maximum total across all engines
            if (data.total > maxTotal) maxTotal = data.total;
        }
    }

    if (!baseData) {
        return null;
    }

    merged.total = maxTotal;

    // Merge engine pass counts
    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, relativePath));
        if (data && data.engines) {
            merged.engines[engine.name] = Object.values(data.engines)[0] || 0;
        }
    }

    // Merge subdirectory info
    if (baseData.files) {
        for (const [subdir, subdirInfo] of Object.entries(baseData.files)) {
            // Find max total for this subdirectory
            let maxSubdirTotal = subdirInfo.total || 0;
            for (const engine of engines) {
                const data = readJsonSafe(path.join(engine.dir, relativePath));
                if (data && data.files && data.files[subdir] && data.files[subdir].total > maxSubdirTotal) {
                    maxSubdirTotal = data.files[subdir].total;
                }
            }

            merged.files[subdir] = {
                total: maxSubdirTotal,
                engines: {}
            };

            for (const engine of engines) {
                const data = readJsonSafe(path.join(engine.dir, relativePath));
                if (data && data.files && data.files[subdir] && data.files[subdir].engines) {
                    merged.files[subdir].engines[engine.name] =
                        Object.values(data.files[subdir].engines)[0] || 0;
                }
            }
        }
    }

    // Merge individual test results
    if (baseData.tests) {
        for (const testName of Object.keys(baseData.tests)) {
            merged.tests[testName] = {};

            for (const engine of engines) {
                const data = readJsonSafe(path.join(engine.dir, relativePath));
                if (data && data.tests && data.tests[testName]) {
                    merged.tests[testName][engine.name] =
                        Object.values(data.tests[testName])[0] || false;
                }
            }
        }
    }

    // Clean up empty objects
    if (Object.keys(merged.files).length === 0) delete merged.files;
    if (Object.keys(merged.tests).length === 0) delete merged.tests;

    return merged;
}

function mergeFeaturesJson(engines) {
    const merged = {};

    // Get base structure from first engine that has features.json
    let baseData = null;
    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, 'features.json'));
        if (data) {
            baseData = data;
            break;
        }
    }

    if (!baseData) {
        return null;
    }

    // Merge each feature
    for (const [feature, featureInfo] of Object.entries(baseData)) {
        merged[feature] = {
            total: featureInfo.total || 0,
            engines: {},
            proposal: featureInfo.proposal || null
        };

        // Get counts from each engine
        for (const engine of engines) {
            const data = readJsonSafe(path.join(engine.dir, 'features.json'));
            if (data && data[feature] && data[feature].engines) {
                // The engine's data has its own name as the key
                const engineCount = Object.values(data[feature].engines)[0];
                merged[feature].engines[engine.name] = engineCount || 0;
            }
        }
    }

    return merged;
}

function mergeEditionsJson(engines) {
    const merged = {};

    // Get base structure from first engine that has editions.json
    let baseData = null;
    for (const engine of engines) {
        const data = readJsonSafe(path.join(engine.dir, 'editions.json'));
        if (data) {
            baseData = data;
            break;
        }
    }

    if (!baseData) {
        return null;
    }

    // Merge each edition
    for (const [edition, editionInfo] of Object.entries(baseData)) {
        merged[edition] = {
            total: editionInfo.total || 0,
            engines: {}
        };

        // Get counts from each engine
        for (const engine of engines) {
            const data = readJsonSafe(path.join(engine.dir, 'editions.json'));
            if (data && data[edition] && data[edition].engines) {
                // The engine's data has its own name as the key
                const engineCount = Object.values(data[edition].engines)[0];
                merged[edition].engines[engine.name] = engineCount || 0;
            }
        }
    }

    return merged;
}

function findAllJsonFiles(dir, basePath = '') {
    const files = [];

    if (!fs.existsSync(dir)) {
        return files;
    }

    const entries = fs.readdirSync(dir, { withFileTypes: true });

    for (const entry of entries) {
        const relativePath = basePath ? path.join(basePath, entry.name) : entry.name;

        if (entry.isDirectory()) {
            files.push(...findAllJsonFiles(path.join(dir, entry.name), relativePath));
        } else if (entry.name.endsWith('.json') &&
                   entry.name !== 'index.json' &&
                   entry.name !== 'engines.json' &&
                   entry.name !== 'features.json' &&
                   entry.name !== 'editions.json') {
            files.push(relativePath);
        }
    }

    return files;
}

function ensureDir(filePath) {
    const dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

function writeJson(filePath, data) {
    ensureDir(filePath);
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
}

function main() {
    const { engines, outputDir } = parseArgs(process.argv.slice(2));

    console.log(`Merging results from ${engines.length} engines into ${outputDir}`);

    // Create output directory
    if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir, { recursive: true });
    }

    // Merge and write index.json
    const indexData = mergeIndexJson(engines);
    writeJson(path.join(outputDir, 'index.json'), indexData);
    console.log('Wrote index.json');

    // Merge and write engines.json
    const enginesData = mergeEnginesJson(engines);
    writeJson(path.join(outputDir, 'engines.json'), enginesData);
    console.log('Wrote engines.json');

    // Merge and write features.json
    const featuresData = mergeFeaturesJson(engines);
    if (featuresData) {
        writeJson(path.join(outputDir, 'features.json'), featuresData);
        console.log('Wrote features.json');
    }

    // Merge and write editions.json
    const editionsData = mergeEditionsJson(engines);
    if (editionsData) {
        writeJson(path.join(outputDir, 'editions.json'), editionsData);
        console.log('Wrote editions.json');
    }

    // Find all directory JSON files from first engine (as reference)
    const firstEngineDir = engines[0].dir;
    const jsonFiles = findAllJsonFiles(firstEngineDir);

    console.log(`Found ${jsonFiles.length} JSON files to merge`);

    // Merge each directory JSON file
    for (const jsonFile of jsonFiles) {
        const merged = mergeDirectoryJson(engines, jsonFile);
        if (merged) {
            writeJson(path.join(outputDir, jsonFile), merged);
        }
    }

    console.log('Merge complete!');
}

main();
