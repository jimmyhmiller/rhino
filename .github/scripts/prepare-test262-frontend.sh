#!/bin/bash

# Prepares the test262.fyi frontend for local data usage.
#
# Usage: ./prepare-test262-frontend.sh <output-dir> <data-dir>
#
# This script:
# 1. Downloads the test262.fyi HTML
# 2. Modifies fetch URLs to use local data
# 3. Updates engine configuration for upstream/fork comparison

set -e

OUTPUT_DIR="${1:-./_site/test262}"
DATA_DIR="${2:-./data}"

echo "Preparing test262.fyi frontend in ${OUTPUT_DIR}"
echo "Data directory: ${DATA_DIR}"

# Create output directory
mkdir -p "${OUTPUT_DIR}"

# Download the main page
echo "Downloading test262.fyi..."
curl -sL "https://test262.fyi/" -o "${OUTPUT_DIR}/index.html"

# Download CSS and other assets
curl -sL "https://test262.fyi/style.css" -o "${OUTPUT_DIR}/style.css" 2>/dev/null || true

# Modify the HTML to use local data
echo "Modifying HTML for local data..."

# Replace the data URL from https://data.test262.fyi/ to ./data/
sed -i.bak 's|https://data\.test262\.fyi/|./data/|g' "${OUTPUT_DIR}/index.html"

# Also handle any fetch calls that might use the full URL
sed -i.bak 's|"https://data\.test262\.fyi"|"./data"|g' "${OUTPUT_DIR}/index.html"

# Update engine configuration to show upstream, fork, and QuickJS
# This modifies the JavaScript to filter out other engines and set display order
cat >> "${OUTPUT_DIR}/config.js" << 'EOF'
// Custom configuration for Rhino fork comparison
window.TEST262_CONFIG = {
    filterOutEngines: [],
    niceEngineOrder: ['rhino-upstream', 'rhino-fork', 'quickjs'],
    engineNames: {
        'rhino-upstream': 'Rhino (upstream)',
        'rhino-fork': 'Rhino (fork)',
        'quickjs': 'QuickJS'
    }
};
EOF

# Inject the config script into the HTML
sed -i.bak 's|</head>|<script src="config.js"></script></head>|' "${OUTPUT_DIR}/index.html"

# Put editions at the top (before proposals)
sed -i.bak 's|content.append(proposalsDetails, editionsDetails)|content.append(editionsDetails, proposalsDetails)|' "${OUTPUT_DIR}/index.html"

# Set niceEngineOrder to our engines
sed -i.bak "s/const niceEngineOrder = .*/const niceEngineOrder = ['rhino-upstream', 'rhino-fork', 'quickjs'];/" "${OUTPUT_DIR}/index.html"

# Clear filterOutEngines so all engines are shown
sed -i.bak "s/let cwd = '', filterOutEngines = .*, init = false;/let cwd = '', filterOutEngines = [], init = false;/" "${OUTPUT_DIR}/index.html"

# Default to vertical graphs
sed -i.bak 's/<input type="checkbox" id="vertical_file_graphs">/<input type="checkbox" id="vertical_file_graphs" checked>/' "${OUTPUT_DIR}/index.html"

# Add edition 16 (ES2025) to the editions loop
sed -i.bak 's/for (let i of \[5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, undefined\])/for (let i of [5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, undefined])/' "${OUTPUT_DIR}/index.html"

# Add custom CSS colors for Rhino engines
cat >> "${OUTPUT_DIR}/style.css" << 'CSSEOF'

/* Custom colors for Rhino engines and QuickJS */
.stat-rhino-upstream {
    background: #ef4946 !important;
}
.stat-rhino-fork {
    background: #498af4 !important;
}
.stat-quickjs {
    background: #f5a623 !important;
}
CSSEOF

# Copy data to the output directory (symlinks don't work with artifact uploads)
if [ "${DATA_DIR}" != "./data" ]; then
    if [ -d "${DATA_DIR}" ]; then
        cp -r "${DATA_DIR}" "${OUTPUT_DIR}/data"
    fi
else
    mkdir -p "${OUTPUT_DIR}/data"
fi

# Clean up backup files
rm -f "${OUTPUT_DIR}"/*.bak

echo "Frontend preparation complete!"
echo "Files created in ${OUTPUT_DIR}:"
ls -la "${OUTPUT_DIR}/"
