#!/bin/bash

# Prepares the test262 frontend for local data usage.
#
# Usage: ./prepare-test262-frontend.sh <output-dir> <data-dir>
#
# This script:
# 1. Copies the self-contained test262 template HTML
# 2. Downloads the test262.fyi CSS for styling
# 3. Adds custom CSS rules for our engines
# 4. Copies data to the output directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-./_site/test262}"
DATA_DIR="${2:-./data}"

echo "Preparing test262 frontend in ${OUTPUT_DIR}"
echo "Data directory: ${DATA_DIR}"

# Create output directory
mkdir -p "${OUTPUT_DIR}"

# Copy our self-contained template (no fragile sed patching needed)
cp "${SCRIPT_DIR}/test262-template.html" "${OUTPUT_DIR}/index.html"

# Download CSS from test262.fyi for base styling
echo "Downloading test262.fyi style.css..."
curl -sL "https://test262.fyi/style.css" -o "${OUTPUT_DIR}/style.css" 2>/dev/null || true

# Add custom CSS for our engines (colors + hide/show rules)
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
    color: #000408;
}

/* Hide rules for toggling engines on/off */
.no-stat-rhino-upstream #content .stat-rhino-upstream,
.no-stat-rhino-fork #content .stat-rhino-fork,
.no-stat-quickjs #content .stat-quickjs {
    width: 0 !important;
    height: 0 !important;
    overflow: hidden !important;
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

echo "Frontend preparation complete!"
echo "Files created in ${OUTPUT_DIR}:"
ls -la "${OUTPUT_DIR}/"
