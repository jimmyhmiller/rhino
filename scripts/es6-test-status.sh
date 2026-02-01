#!/bin/bash
#
# ES6 Test Status Report
#
# This script analyzes test262 tests by ES6 edition (based on feature metadata)
# and cross-references them with the test262.properties failure list to show
# current ES6 conformance status.
#
# Uses the same feature-to-edition mapping as list-tests-by-edition.js
# (from test262-fyi). A test is classified as ES6 if its highest-edition
# feature is ES6 (tests using newer features are excluded).
#
# Usage: ./scripts/es6-test-status.sh [--all]
#
# Options:
#   --all    Show individual failing tests within each category
#

set -e

# Parse arguments
SHOW_ALL=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --all)
            SHOW_ALL=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--all]"
            echo ""
            echo "Options:"
            echo "  --all    Show individual failing tests within each category"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--all]"
            exit 1
            ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
TEST262_DIR="$ROOT_DIR/tests/test262/test"
PROPERTIES_FILE="$ROOT_DIR/tests/testsrc/test262.properties"

# Temp files
TEMP_DIR=$(mktemp -d)
ES6_PASSING="$TEMP_DIR/es6_passing.txt"
ES6_FAILING="$TEMP_DIR/es6_failing.txt"
ES6_CAT_PASS="$TEMP_DIR/es6_cat_pass.txt"
ES6_CAT_FAIL="$TEMP_DIR/es6_cat_fail.txt"
ES6_TOTALS="$TEMP_DIR/es6_totals.txt"
ES6_FAILS="$TEMP_DIR/es6_fails.txt"

cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Check prerequisites
if [ ! -d "$TEST262_DIR" ]; then
    echo "Error: test262 directory not found at $TEST262_DIR"
    echo "Make sure the test262 submodule is initialized."
    exit 1
fi

if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "Error: test262.properties not found at $PROPERTIES_FILE"
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "Error: Node.js is required but not installed"
    exit 1
fi

echo "========================================"
echo "  ES6 Test Status Report"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"
echo ""

# Use list-tests-by-edition.js to get ES6 tests with proper feature-based classification
echo "Scanning for ES6 tests (by feature metadata)..."

# Get passing ES6 tests
node "$SCRIPT_DIR/list-tests-by-edition.js" -e 6 -s passing -o json 2>/dev/null | \
    node -e "
        const data = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
        data.tests.forEach(t => console.log(t.path));
    " > "$ES6_PASSING"

# Get failing ES6 tests
node "$SCRIPT_DIR/list-tests-by-edition.js" -e 6 -s failing -o json 2>/dev/null | \
    node -e "
        const data = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
        data.tests.forEach(t => console.log(t.path));
    " > "$ES6_FAILING"

TOTAL_PASS=$(wc -l < "$ES6_PASSING" | tr -d ' ')
TOTAL_FAIL=$(wc -l < "$ES6_FAILING" | tr -d ' ')
TOTAL_ES6=$((TOTAL_PASS + TOTAL_FAIL))

echo "Found $TOTAL_ES6 ES6 tests (by highest feature edition)"
echo ""

# Build category mapping for passing tests
awk -F'/' '{
    # Get category (first 2 path components)
    cat = $1"/"$2
    print cat
}' "$ES6_PASSING" | sort | uniq -c > "$ES6_CAT_PASS"

# Build category mapping for failing tests
awk -F'/' '{
    # Get category (first 2 path components)
    cat = $1"/"$2
    print cat
}' "$ES6_FAILING" | sort | uniq -c > "$ES6_CAT_FAIL"

# Combine to get totals per category
cat "$ES6_CAT_PASS" "$ES6_CAT_FAIL" | awk '{
    counts[$2] += $1
} END {
    for (cat in counts) print counts[cat], cat
}' | sort -k2 > "$ES6_TOTALS"

# Calculate pass percentage
if [ "$TOTAL_ES6" -gt 0 ]; then
    PASS_PCT=$(echo "scale=1; $TOTAL_PASS * 100 / $TOTAL_ES6" | bc)
else
    PASS_PCT="0.0"
fi

echo "========================================"
echo "  Summary"
echo "========================================"
echo ""
printf "  Total ES6 tests:    %5d\n" "$TOTAL_ES6"
printf "  Passing:            %5d (%s%%)\n" "$TOTAL_PASS" "$PASS_PCT"
printf "  Failing:            %5d\n" "$TOTAL_FAIL"
echo ""

echo "========================================"
echo "  Failures by Category"
echo "========================================"
echo ""
printf "  %4s   %4s   %6s   %s\n" "Fail" "Total" "%" "Category"
printf "  %4s   %4s   %6s   %s\n" "----" "-----" "------" "--------"

# Show failures by category (sorted by failure count descending)
# First, create sorted list of categories by failure count
sort -rn "$ES6_CAT_FAIL" > "$TEMP_DIR/sorted_cat_fail.txt"

while read count cat; do
    total=$(grep " $cat$" "$ES6_TOTALS" | awk '{print $1}')
    if [ -n "$total" ] && [ "$total" -gt 0 ]; then
        pct=$(echo "scale=1; $count * 100 / $total" | bc)
        printf "  %4d / %4d   %5.1f%%   %s\n" "$count" "$total" "$pct" "$cat"

        # If --all flag is set, show individual failing tests in this category
        if [ "$SHOW_ALL" = true ]; then
            grep "^$cat/" "$ES6_FAILING" | sort | while read test_path; do
                printf "                         - %s\n" "$test_path"
            done
            echo ""
        fi
    fi
done < "$TEMP_DIR/sorted_cat_fail.txt"

echo ""
echo "========================================"
echo "  Categories with 100% Pass Rate"
echo "========================================"
echo ""

# Find categories with no failures
comm -23 <(awk '{print $2}' "$ES6_TOTALS" | sort) <(awk '{print $2}' "$ES6_CAT_FAIL" | sort) 2>/dev/null | while read cat; do
    total=$(grep " $cat$" "$ES6_TOTALS" | awk '{print $1}')
    if [ -n "$total" ]; then
        printf "  %4d tests - %s\n" "$total" "$cat"
    fi
done | sort -t'-' -k1 -rn

echo ""
echo "========================================"
echo "  To improve ES6 conformance:"
echo "========================================"
echo ""
echo "  1. Run specific tests:"
echo "     ./gradlew :tests:test --tests org.mozilla.javascript.tests.Test262SuiteTest \\"
echo "         -Dtest262filter=\"built-ins/Promise/*\" -Dtest262raw"
echo ""
echo "  2. Update test262.properties after fixes:"
echo "     RHINO_TEST_JAVA_VERSION=11 ./gradlew :tests:test \\"
echo "         --tests org.mozilla.javascript.tests.Test262SuiteTest \\"
echo "         --rerun-tasks -DupdateTest262properties"
echo ""
