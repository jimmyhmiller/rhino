#!/bin/bash
#
# ES6 Test Status Report
#
# This script analyzes test262 tests that have the es6id: field (ES6-specific tests)
# and cross-references them with the test262.properties failure list to show
# current ES6 conformance status.
#
# Usage: ./scripts/es6-test-status.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
TEST262_DIR="$ROOT_DIR/tests/test262/test"
PROPERTIES_FILE="$ROOT_DIR/tests/testsrc/test262.properties"

# Temp files
TEMP_DIR=$(mktemp -d)
ES6_TESTS="$TEMP_DIR/es6_tests.txt"
ES6_CAT_FILE="$TEMP_DIR/es6_cat_file.txt"
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

echo "========================================"
echo "  ES6 Test Status Report"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"
echo ""

# Find all ES6 tests (those with es6id: field)
echo "Scanning for ES6 tests..."
grep -r "^es6id:" "$TEST262_DIR" --include="*.js" -l 2>/dev/null | \
    sed "s|$TEST262_DIR/||" | \
    sed 's|\.js$||' > "$ES6_TESTS"

TOTAL_ES6=$(wc -l < "$ES6_TESTS" | tr -d ' ')
echo "Found $TOTAL_ES6 ES6-specific tests"
echo ""

# Build category mapping - store full path and category
awk -F'/' '{
    # Get category (first 2 path components)
    cat = $1"/"$2
    # Full test path
    print cat, $0".js"
}' "$ES6_TESTS" | sort > "$ES6_CAT_FILE"

# Count totals per category
cut -d' ' -f1 "$ES6_CAT_FILE" | sort | uniq -c > "$ES6_TOTALS"

# Count failures per category
# test262.properties lists expected failures with indented filenames
# We need to check if the test's relative path appears in the file
while read cat fullpath; do
    # Extract just the filename for matching against indented entries
    file=$(basename "$fullpath")
    # Use fixed string grep (-F) to avoid regex issues with . in filenames
    # Also check that it's an indented entry (starts with spaces) to avoid matching headers
    if grep -F "    $file" "$PROPERTIES_FILE" >/dev/null 2>&1; then
        echo "$cat"
    fi
done < "$ES6_CAT_FILE" | sort | uniq -c > "$ES6_FAILS"

# Calculate totals
TOTAL_FAIL=$(awk '{sum+=$1} END {print sum+0}' "$ES6_FAILS")
TOTAL_PASS=$((TOTAL_ES6 - TOTAL_FAIL))
PASS_PCT=$(echo "scale=1; $TOTAL_PASS * 100 / $TOTAL_ES6" | bc)

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

cat "$ES6_FAILS" | while read count cat; do
    total=$(grep " $cat$" "$ES6_TOTALS" | awk '{print $1}')
    pct=$(echo "scale=1; $count * 100 / $total" | bc)
    printf "  %4d / %4d   %5.1f%%   %s\n" "$count" "$total" "$pct" "$cat"
done | sort -t'/' -k1 -rn

echo ""
echo "========================================"
echo "  Categories with 100% Pass Rate"
echo "========================================"
echo ""

comm -23 <(awk '{print $2}' "$ES6_TOTALS" | sort) <(awk '{print $2}' "$ES6_FAILS" | sort) | while read cat; do
    total=$(grep " $cat$" "$ES6_TOTALS" | awk '{print $1}')
    printf "  %4d tests - %s\n" "$total" "$cat"
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
