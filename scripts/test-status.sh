#!/bin/bash
#
# Test262 Status Report by ECMAScript Edition
#
# This script shows test262 conformance status by ECMAScript edition.
# Without arguments, it shows a summary of all editions.
# With an edition argument, it shows detailed breakdown for that edition.
#
# Usage:
#   ./scripts/test-status.sh              # Summary of all editions
#   ./scripts/test-status.sh 6            # Detailed ES6 report
#   ./scripts/test-status.sh 6 --all      # ES6 report with individual failing tests
#   ./scripts/test-status.sh --help       # Show help
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
TEST262_DIR="$ROOT_DIR/tests/test262/test"
PROPERTIES_FILE="$ROOT_DIR/tests/testsrc/test262.properties"

# Check prerequisites
check_prereqs() {
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
}

show_help() {
    cat << 'EOF'
Test262 Status Report by ECMAScript Edition

Usage:
  ./scripts/test-status.sh              # Summary of all editions
  ./scripts/test-status.sh EDITION      # Detailed report for an edition
  ./scripts/test-status.sh EDITION --all  # Include individual failing tests

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
  ./scripts/test-status.sh           # See pass rates for all editions
  ./scripts/test-status.sh 6         # Detailed ES6 breakdown
  ./scripts/test-status.sh 11 --all  # ES2020 with all failing test paths
EOF
}

show_summary() {
    echo "========================================"
    echo "  Test262 Status by Edition"
    echo "  $(date '+%Y-%m-%d %H:%M:%S')"
    echo "========================================"
    echo ""

    # Get JSON summary from list-tests-by-edition.js
    node "$SCRIPT_DIR/list-tests-by-edition.js" -o json 2>/dev/null | node -e "
        const data = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
        const editions = Object.keys(data).map(Number).sort((a, b) => a - b);

        console.log('  Edition           Passing   Failing     Total    Pass %');
        console.log('  ---------------   -------   -------   -------   ------');

        let totalPass = 0;
        let totalFail = 0;

        for (const ed of editions) {
            const s = data[ed];
            totalPass += s.passing;
            totalFail += s.failing;
            console.log(
                '  ' + s.name.padEnd(15) +
                '   ' + String(s.passing).padStart(7) +
                '   ' + String(s.failing).padStart(7) +
                '   ' + String(s.total).padStart(7) +
                '   ' + s.passRate.padStart(5) + '%'
            );
        }

        const total = totalPass + totalFail;
        const pct = ((totalPass / total) * 100).toFixed(1);
        console.log('  ---------------   -------   -------   -------   ------');
        console.log(
            '  ' + 'TOTAL'.padEnd(15) +
            '   ' + String(totalPass).padStart(7) +
            '   ' + String(totalFail).padStart(7) +
            '   ' + String(total).padStart(7) +
            '   ' + pct.padStart(5) + '%'
        );
    "
    echo ""
}

show_edition_details() {
    local EDITION="$1"
    local SHOW_ALL="$2"

    # Temp files
    TEMP_DIR=$(mktemp -d)
    ED_PASSING="$TEMP_DIR/ed_passing.txt"
    ED_FAILING="$TEMP_DIR/ed_failing.txt"
    ED_CAT_PASS="$TEMP_DIR/ed_cat_pass.txt"
    ED_CAT_FAIL="$TEMP_DIR/ed_cat_fail.txt"
    ED_TOTALS="$TEMP_DIR/ed_totals.txt"

    cleanup() {
        rm -rf "$TEMP_DIR"
    }
    trap cleanup EXIT

    # Get edition name
    EDITION_NAME=$(node -e "
        const names = {
            5: 'ES5', 6: 'ES6/ES2015', 7: 'ES2016', 8: 'ES2017', 9: 'ES2018',
            10: 'ES2019', 11: 'ES2020', 12: 'ES2021', 13: 'ES2022',
            14: 'ES2023', 15: 'ES2024', 16: 'ES2025', 99: 'ESNext/Proposals'
        };
        console.log(names[$EDITION] || 'ES' + $EDITION);
    ")

    echo "========================================"
    echo "  $EDITION_NAME Test Status Report"
    echo "  $(date '+%Y-%m-%d %H:%M:%S')"
    echo "========================================"
    echo ""

    echo "Scanning for $EDITION_NAME tests (by feature metadata)..."

    # Get passing tests for this edition
    node "$SCRIPT_DIR/list-tests-by-edition.js" -e "$EDITION" -s passing -o json 2>/dev/null | \
        node -e "
            const data = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
            data.tests.forEach(t => console.log(t.path));
        " > "$ED_PASSING"

    # Get failing tests for this edition
    node "$SCRIPT_DIR/list-tests-by-edition.js" -e "$EDITION" -s failing -o json 2>/dev/null | \
        node -e "
            const data = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
            data.tests.forEach(t => console.log(t.path));
        " > "$ED_FAILING"

    TOTAL_PASS=$(wc -l < "$ED_PASSING" | tr -d ' ')
    TOTAL_FAIL=$(wc -l < "$ED_FAILING" | tr -d ' ')
    TOTAL_ED=$((TOTAL_PASS + TOTAL_FAIL))

    echo "Found $TOTAL_ED $EDITION_NAME tests (by highest feature edition)"
    echo ""

    # Build category mapping for passing tests
    awk -F'/' '{
        cat = $1"/"$2
        print cat
    }' "$ED_PASSING" | sort | uniq -c > "$ED_CAT_PASS"

    # Build category mapping for failing tests
    awk -F'/' '{
        cat = $1"/"$2
        print cat
    }' "$ED_FAILING" | sort | uniq -c > "$ED_CAT_FAIL"

    # Combine to get totals per category
    cat "$ED_CAT_PASS" "$ED_CAT_FAIL" | awk '{
        counts[$2] += $1
    } END {
        for (cat in counts) print counts[cat], cat
    }' | sort -k2 > "$ED_TOTALS"

    # Calculate pass percentage
    if [ "$TOTAL_ED" -gt 0 ]; then
        PASS_PCT=$(echo "scale=1; $TOTAL_PASS * 100 / $TOTAL_ED" | bc)
    else
        PASS_PCT="0.0"
    fi

    echo "========================================"
    echo "  Summary"
    echo "========================================"
    echo ""
    printf "  Total $EDITION_NAME tests:  %5d\n" "$TOTAL_ED"
    printf "  Passing:            %5d (%s%%)\n" "$TOTAL_PASS" "$PASS_PCT"
    printf "  Failing:            %5d\n" "$TOTAL_FAIL"
    echo ""

    # Only show failures section if there are failures
    if [ "$TOTAL_FAIL" -gt 0 ]; then
        echo "========================================"
        echo "  Failures by Category"
        echo "========================================"
        echo ""
        printf "  %4s   %4s   %6s   %s\n" "Fail" "Total" "%" "Category"
        printf "  %4s   %4s   %6s   %s\n" "----" "-----" "------" "--------"

        # Show failures by category (sorted by failure count descending)
        sort -rn "$ED_CAT_FAIL" > "$TEMP_DIR/sorted_cat_fail.txt"

        while read count cat; do
            total=$(grep " $cat$" "$ED_TOTALS" | awk '{print $1}')
            if [ -n "$total" ] && [ "$total" -gt 0 ]; then
                pct=$(echo "scale=1; $count * 100 / $total" | bc)
                printf "  %4d / %4d   %5.1f%%   %s\n" "$count" "$total" "$pct" "$cat"

                # If --all flag is set, show individual failing tests in this category
                if [ "$SHOW_ALL" = true ]; then
                    grep "^$cat/" "$ED_FAILING" | sort | while read test_path; do
                        printf "                         - %s\n" "$test_path"
                    done
                    echo ""
                fi
            fi
        done < "$TEMP_DIR/sorted_cat_fail.txt"

        echo ""
    fi

    echo "========================================"
    echo "  Categories with 100% Pass Rate"
    echo "========================================"
    echo ""

    # Find categories with no failures
    comm -23 <(awk '{print $2}' "$ED_TOTALS" | sort) <(awk '{print $2}' "$ED_CAT_FAIL" | sort) 2>/dev/null | while read cat; do
        total=$(grep " $cat$" "$ED_TOTALS" | awk '{print $1}')
        if [ -n "$total" ]; then
            printf "  %4d tests - %s\n" "$total" "$cat"
        fi
    done | sort -t'-' -k1 -rn

    echo ""
    echo "========================================"
    echo "  To improve $EDITION_NAME conformance:"
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
}

# Parse arguments
EDITION=""
SHOW_ALL=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        --all)
            SHOW_ALL=true
            shift
            ;;
        *)
            if [[ "$1" =~ ^[0-9]+$ ]]; then
                EDITION="$1"
            else
                echo "Unknown option: $1"
                echo "Use --help for usage information."
                exit 1
            fi
            shift
            ;;
    esac
done

check_prereqs

if [ -z "$EDITION" ]; then
    show_summary
else
    show_edition_details "$EDITION" "$SHOW_ALL"
fi
